package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.DirectoryNotFoundException
import org.gotson.komga.domain.model.DuplicateNameException
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.PathContainedInPath
import org.gotson.komga.domain.service.LibraryLifecycle
import org.gotson.komga.interfaces.api.rest.LibraryController
import org.gotson.komga.interfaces.api.rest.dto.LibraryCreationDto
import org.gotson.komga.interfaces.api.rest.dto.LibraryUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import java.io.FileNotFoundException

class LibraryControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls; fails depending on the library root (same fake in the TypeScript twin) */
  private fun fail(library: Library) {
    val root = library.root.toString()
    when {
      root.endsWith("/dup") -> throw DuplicateNameException("Library name already exists", "ERR_1015")
      root.endsWith("/missing") -> throw DirectoryNotFoundException("Library root folder does not exist", "ERR_1016")
      root.endsWith("/nofile") -> throw FileNotFoundException("no file")
      root.endsWith("/contained") -> throw PathContainedInPath("Library path is a child of another", "ERR_1017")
      root.endsWith("/boom") -> throw IllegalStateException("boom")
    }
  }

  private val lifecycle =
    mockk<LibraryLifecycle> {
      every { addLibrary(any()) } answers {
        val l = firstArg<Library>()
        calls.add("addLibrary", l)
        fail(l)
        l
      }
      every { updateLibrary(any()) } answers {
        val l = firstArg<Library>()
        calls.add("updateLibrary", l)
        fail(l)
      }
      every { deleteLibrary(any()) } answers { calls.add("deleteLibrary", firstArg<Library>().id) }
    }
  private val c = LibraryController(taskEmitter(db, calls), lifecycle, db.libraryDao, db.bookDao, db.seriesDao)
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)
  private val l1 = principal(RestSamples.l1Only)

  private fun create(json: String) = read<LibraryCreationDto>(json)

  private fun update(json: String) = read<LibraryUpdateDto>(json)

  override fun cases() {
    func("getLibraries") {
      case("empty") { c.getLibraries(admin) }
      case("admin sees roots, sorted by lowercase name") {
        RestSamples.seed(db)
        db.libraryDao.insert(Library(name = "apple", root = java.net.URL("file:/lib3"), id = "L3", createdDate = RestOracle.FIXED))
        c.getLibraries(admin)
      }
      case("user without root") { c.getLibraries(all) }
      case("restricted user") { c.getLibraries(l1) }
    }
    func("getLibraryById") {
      case("admin") { c.getLibraryById(admin, "L1") }
      case("user") { c.getLibraryById(all, "L2") }
      case("restricted, allowed") { c.getLibraryById(l1, "L1") }
      case("restricted, forbidden") { c.getLibraryById(l1, "L2") }
      case("not found") { c.getLibraryById(admin, "LX") }
    }
    func("addLibrary") {
      case("defaults") { listOf(stable(c.addLibrary(admin, create("""{"name":"New","root":"/data/new"}"""))), calls.take()) }
      case("all fields, non admin result") {
        listOf(
          stable(
            c.addLibrary(
              all,
              create(
                """{"name":"Full","root":"/data/My Comics/é","importComicInfoBook":false,"importComicInfoSeries":false,"importComicInfoCollection":false,
                  |"importComicInfoReadList":false,"importComicInfoSeriesAppendVolume":false,"importEpubBook":false,"importEpubSeries":false,"importMylarSeries":false,
                  |"importLocalArtwork":false,"importBarcodeIsbn":false,"scanForceModifiedTime":true,"scanInterval":"DAILY","scanOnStartup":true,"scanCbx":false,
                  |"scanPdf":false,"scanEpub":false,"scanDirectoryExclusions":["#recycle"],"repairExtensions":true,"convertToCbz":true,"emptyTrashAfterScan":true,
                  |"seriesCover":"LAST","hashFiles":false,"hashPages":true,"hashKoreader":true,"analyzeDimensions":false,"oneshotsDirectory":"_one"}
                """.trimMargin(),
              ),
            ),
          ),
          calls.take(),
        )
      }
      case("blank oneshots directory") { stable(c.addLibrary(admin, create("""{"name":"B","root":"/b","oneshotsDirectory":"  "}"""))).also { calls.take() } }
      case("duplicate") { c.addLibrary(admin, create("""{"name":"D","root":"/x/dup"}""")).also { calls.take() } }
      case("directory not found") { c.addLibrary(admin, create("""{"name":"D","root":"/x/missing"}""")).also { calls.take() } }
      case("file not found") { c.addLibrary(admin, create("""{"name":"D","root":"/x/nofile"}""")).also { calls.take() } }
      case("contained") { c.addLibrary(admin, create("""{"name":"D","root":"/x/contained"}""")).also { calls.take() } }
      case("other error") { listOf(exceptionType { c.addLibrary(admin, create("""{"name":"D","root":"/x/boom"}""")) }, calls.take()) }
    }
    func("updateLibraryByIdDeprecated") {
      case("empty patch keeps everything") {
        @Suppress("DEPRECATION")
        c.updateLibraryByIdDeprecated("L1", update("{}"))
        calls.take()
      }
      case("patch") {
        @Suppress("DEPRECATION")
        c.updateLibraryByIdDeprecated(
          "L1",
          update("""{"name":"Renamed","root":"/new/root","scanInterval":"WEEKLY","seriesCover":"FIRST_UNREAD_OR_FIRST","hashPages":true,"scanDirectoryExclusions":["a"],"oneshotsDirectory":"os"}"""),
        )
        calls.take()
      }
      case("null exclusions and oneshots") {
        @Suppress("DEPRECATION")
        c.updateLibraryByIdDeprecated("L1", update("""{"scanDirectoryExclusions":null,"oneshotsDirectory":null}"""))
        calls.take()
      }
      case("blank oneshots") {
        @Suppress("DEPRECATION")
        c.updateLibraryByIdDeprecated("L1", update("""{"oneshotsDirectory":" "}"""))
        calls.take()
      }
      case("not found") {
        @Suppress("DEPRECATION")
        listOf(c.updateLibraryByIdDeprecated("LX", update("{}")), calls.take())
      }
      case("duplicate") {
        @Suppress("DEPRECATION")
        c.updateLibraryByIdDeprecated("L1", update("""{"root":"/x/dup"}""")).also { calls.take() }
      }
      case("other error") {
        @Suppress("DEPRECATION")
        listOf(exceptionType { c.updateLibraryByIdDeprecated("L1", update("""{"root":"/x/boom"}""")) }, calls.take())
      }
    }
    func("deleteLibraryById") {
      case("existing") {
        c.deleteLibraryById("L2")
        calls.take()
      }
      case("not found") { c.deleteLibraryById("LX") }
    }
    func("libraryScan") {
      case("default") {
        c.libraryScan("L1")
        listOf(tasks(db), calls.take())
      }
      case("deep") {
        c.libraryScan("L1", true)
        tasks(db)
      }
      case("not found") { listOf(exceptionType { c.libraryScan("LX") }, tasks(db)) }
    }
    func("libraryAnalyze") {
      case("L1") {
        c.libraryAnalyze("L1")
        listOf(tasks(db), calls.take())
      }
      case("unknown library: no book") {
        c.libraryAnalyze("LX")
        listOf(tasks(db), calls.take())
      }
    }
    func("libraryRefreshMetadata") {
      case("L1") {
        c.libraryRefreshMetadata("L1")
        listOf(tasks(db), calls.take())
      }
      case("L2") {
        c.libraryRefreshMetadata("L2")
        tasks(db)
      }
      case("unknown") {
        c.libraryRefreshMetadata("LX")
        listOf(tasks(db), calls.take())
      }
    }
    func("libraryEmptyTrash") {
      case("L1") {
        c.libraryEmptyTrash("L1")
        listOf(tasks(db), calls.take())
      }
      case("not found") { listOf(exceptionType { c.libraryEmptyTrash("LX") }, tasks(db)) }
    }
  }
}
