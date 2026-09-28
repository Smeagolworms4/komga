package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Sidecar
import org.gotson.komga.domain.service.LibraryLifecycle
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import java.net.URL
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class LibraryLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle =
    LibraryLifecycle(db.libraryDao, graph.seriesLifecycle, db.seriesDao, db.sidecarDao, graph.taskEmitter, graph.publisher, graph.transactionTemplate, graph.libraryScanScheduler)

  private val dir by lazy { tempDir.resolve("libraries").createDirectories() }

  private fun url(rel: String) = URL("file:$dir/$rel")

  private fun lib(
    id: String,
    rel: String,
  ) = library(id, url(rel))

  private fun current(id: String) = db.libraryDao.findById(id)

  /** Changes of [block]: library, tasks, schedules, events */
  private fun run(
    id: String?,
    block: () -> Any?,
  ) = attempt(dir) {
    val r = block()
    listOf(r, id?.let { db.libraryDao.findByIdOrNull(it) }, graph.takeTasks(), graph.scheduler.log.toList().also { graph.scheduler.log.clear() }, graph.takeEvents())
  }

  override fun cases() {
    func("addLibrary") {
      case("setup") {
        listOf("lib1/sub", "lib2", "other", "a b", "lib10").forEach { dir.resolve(it).createDirectories() }
        dir.resolve("file.txt").writeText("x")
        true
      }
      case("missing root") { run("L0") { lifecycle.addLibrary(lib("L0", "missing")) } }
      case("root is a file") { run("L0") { lifecycle.addLibrary(lib("L0", "file.txt")) } }
      case("first library") { run("L1") { lifecycle.addLibrary(lib("L1", "lib1")) } }
      case("duplicate name") { run("L2") { lifecycle.addLibrary(lib("L2", "lib2").copy(name = "lib L1")) } }
      case("same name other case") { run("L2") { lifecycle.addLibrary(lib("L2", "lib2").copy(name = "LIB L1")) } }
      case("child of existing") { run("L3") { lifecycle.addLibrary(lib("L3", "lib1/sub")) } }
      case("same path as existing") { run("L3") { lifecycle.addLibrary(lib("L3", "lib1")) } }
      case("sibling with common prefix") { run("L10") { lifecycle.addLibrary(lib("L10", "lib10")) } }
      case("parent of existing") { run("L4") { lifecycle.addLibrary(lib("L4", "")) } }
      case("path with space") { run("L5") { lifecycle.addLibrary(lib("L5", "a%20b")) } }
      case("duplicate id") { run("L1") { lifecycle.addLibrary(lib("L1", "other")) } }
    }
    func("checkLibraryValidity") {
      case("update to missing root") { run("L1") { lifecycle.updateLibrary(current("L1").copy(root = url("missing"))) } }
      case("update to own path") { run("L1") { lifecycle.updateLibrary(current("L1").copy(name = "renamed")) } }
      case("update into other library") { run("L1") { lifecycle.updateLibrary(current("L1").copy(root = url("lib10/x"))) } }
      case("update name to other library name") { run("L1") { lifecycle.updateLibrary(current("L1").copy(name = "lib L10")) } }
    }
    func("updateLibrary") {
      case("unknown library") { run(null) { lifecycle.updateLibrary(lib("L9", "other")) } }
      case("no change") { run("L1") { lifecycle.updateLibrary(current("L1")) } }
      case("scan interval") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanInterval = Library.ScanInterval.DAILY)) } }
      case("scan interval disabled") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanInterval = Library.ScanInterval.DISABLED)) } }
      case("hash files off then on") {
        db.seriesDao.insert(series("S1", "L1"))
        db.bookDao.insert(book("B1", "S1", "L1"))
        db.bookDao.insert(book("B2", "S1", "L1").copy(fileHash = "abc", fileHashKoreader = "def"))
        listOf(
          run("L1") { lifecycle.updateLibrary(current("L1").copy(hashFiles = false)) },
          run("L1") { lifecycle.updateLibrary(current("L1").copy(hashFiles = true)) },
        )
      }
      case("hash koreader on") { run("L1") { lifecycle.updateLibrary(current("L1").copy(hashKoreader = true)) } }
      case("hash koreader on again") { run("L1") { lifecycle.updateLibrary(current("L1").copy(hashKoreader = true)) } }
      case("hash pages on") { run("L1") { lifecycle.updateLibrary(current("L1").copy(hashPages = true)) } }
      case("repair extensions on") { run("L1") { lifecycle.updateLibrary(current("L1").copy(repairExtensions = true)) } }
      case("convert to cbz on") { run("L1") { lifecycle.updateLibrary(current("L1").copy(convertToCbz = true)) } }
      case("all off") {
        run("L1") { lifecycle.updateLibrary(current("L1").copy(hashFiles = false, hashKoreader = false, hashPages = false, repairExtensions = false, convertToCbz = false)) }
      }
      case("everything at once") {
        run("L1") {
          lifecycle.updateLibrary(
            current("L1").copy(
              root = url("other"),
              scanInterval = Library.ScanInterval.WEEKLY,
              hashFiles = true,
              hashKoreader = true,
              hashPages = true,
              repairExtensions = true,
              convertToCbz = true,
            ),
          )
        }
      }
      case("import flags do not rescan") { run("L1") { lifecycle.updateLibrary(current("L1").copy(importComicInfoBook = false, importEpubSeries = false, emptyTrashAfterScan = true)) } }
    }
    func("checkLibraryShouldRescan") {
      case("root") { run("L1") { lifecycle.updateLibrary(current("L1").copy(root = url("lib1"))) } }
      case("oneshots directory") { run("L1") { lifecycle.updateLibrary(current("L1").copy(oneshotsDirectory = "_oneshots")) } }
      case("oneshots directory to null") { run("L1") { lifecycle.updateLibrary(current("L1").copy(oneshotsDirectory = null)) } }
      case("scan cbx") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanCbx = false)) } }
      case("scan pdf") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanPdf = false)) } }
      case("scan epub") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanEpub = false)) } }
      case("force modified time") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanForceModifiedTime = true)) } }
      case("directory exclusions") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanDirectoryExclusions = setOf("x"))) } }
      case("directory exclusions same set other order") {
        lifecycle.updateLibrary(current("L1").copy(scanDirectoryExclusions = setOf("a", "b")))
        graph.takeTasks()
        graph.takeEvents()
        run("L1") { lifecycle.updateLibrary(current("L1").copy(scanDirectoryExclusions = setOf("b", "a"))) }
      }
      case("scan on startup does not rescan") { run("L1") { lifecycle.updateLibrary(current("L1").copy(scanOnStartup = true)) } }
    }
    func("deleteLibrary") {
      case("with series, books and sidecars") {
        db.seriesDao.insert(series("S2", "L1"))
        db.bookDao.insert(book("B3", "S2", "L1"))
        db.sidecarDao.save("L1", Sidecar(url("lib1/cover.jpg"), url("lib1"), date, Sidecar.Type.ARTWORK, Sidecar.Source.SERIES))
        run("L1") {
          lifecycle.deleteLibrary(current("L1"))
          listOf(db.seriesDao.findAll().map { it.id }, db.bookDao.findAll().map { it.id }, db.sidecarDao.findAll().map { it.url })
        }
      }
      case("unknown library") { run(null) { lifecycle.deleteLibrary(lib("L9", "x")) } }
      case("remaining") { db.libraryDao.findAll().map { it.id }.sorted() }
    }
  }
}
