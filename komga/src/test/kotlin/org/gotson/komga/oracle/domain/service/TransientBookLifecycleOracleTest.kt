package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.TransientBook
import org.gotson.komga.domain.service.TransientBookLifecycle
import org.gotson.komga.infrastructure.cache.TransientBookCache
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class TransientBookLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val cache = TransientBookCache()
  private val lifecycle =
    TransientBookLifecycle(
      cache,
      graph.bookAnalyzer,
      graph.fileSystemScanner,
      db.libraryDao,
      ImageType.JPEG,
      db.seriesDao,
      listOf(graph.comicInfoProvider, graph.epubMetadataProvider),
      listOf(graph.isbnBarcodeProvider, graph.comicInfoProvider, graph.epubMetadataProvider),
    )

  private val dir by lazy { tempDir.resolve("root").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun comicInfo(body: String) = t("ComicInfo.xml", "<?xml version=\"1.0\"?><ComicInfo>$body</ComicInfo>")

  private val scanned = linkedMapOf<String, TransientBook>()

  private fun tb(name: String) = scanned.getValue(name)

  private fun brief(t: TransientBook) = listOf(t.book.name, t.book.url, t.book.fileSize, t.media.status, t.media.mediaType, t.media.pageCount, t.metadata)

  override fun cases() {
    func("scanAndPersist") {
      case("setup") {
        val lib = dir.resolve("library").createDirectories()
        db.libraryDao.insert(library("L1", URL("file:$lib")))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Batman", seriesId = "S1", createdDate = date))
        db.seriesDao.insert(series("S2", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Batman Beyond", seriesId = "S2", createdDate = date))
        db.seriesDao.insert(series("S3", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Superman (2016)", seriesId = "S3", createdDate = date))
        val import = dir.resolve("import").createDirectories()
        zipFile(import, "batman.cbz", listOf("p1.png" to png, comicInfo("<Series>Batman</Series><Number>3</Number>")))
        zipFile(import, "bat.cbz", listOf("p1.png" to png, comicInfo("<Series>Beyond</Series><Number>2.5</Number>")))
        zipFile(import, "superman.cbz", listOf("p1.png" to png, comicInfo("<Series>Superman</Series><Volume>2016</Volume><Number>x</Number>")))
        zipFile(import, "blank.cbz", listOf("p1.png" to png, comicInfo("<Series> </Series>")))
        zipFile(import, "nometa.cbz", listOf("p1.png" to png))
        import.resolve("notes.txt").writeBytes(oracleBytes(3))
        Files.copy(Samples.fixture("pdf/komga.pdf"), import.resolve("doc.pdf"))
        Files.copy(Samples.fixture("epub/reflow.epub"), import.resolve("reflow.epub"))
        zipFile(import.resolve("sub").createDirectories(), "deep.cbz", listOf("p1.png" to png))
        zipFile(lib, "inlib.cbz", listOf("p1.png" to png))
        true
      }
      case("folder outside libraries") {
        attempt(dir) {
          lifecycle.scanAndPersist(dir.resolve("import").toString()).also { list -> list.forEach { scanned[it.book.name] = it } }.map { brief(it) }.sortedBy { it[0] as String }
        }
      }
      case("persisted in cache") { attempt(dir) { scanned.values.map { cache.findByIdOrNull(it.book.id)?.let { t -> brief(t) } }.sortedBy { it?.get(0) as String? } } }
      case("library folder") { attempt(dir) { lifecycle.scanAndPersist(dir.resolve("library").toString()) } }
      case("library subfolder") { attempt(dir) { lifecycle.scanAndPersist(dir.resolve("library/sub").toString()) } }
      case("parent of library") { attempt(dir) { lifecycle.scanAndPersist(dir.toString()).map { brief(it) }.sortedBy { it[0] as String } } }
      case("missing folder") { attempt(dir) { lifecycle.scanAndPersist(dir.resolve("nope").toString()) } }
      case("relative path") { attempt(dir) { lifecycle.scanAndPersist("does/not/exist") } }
    }
    func("analyzeAndPersist") {
      for (name in listOf("batman", "bat", "superman", "blank", "nometa", "doc", "reflow", "deep")) {
        case(name) {
          attempt(dir) {
            val updated = lifecycle.analyzeAndPersist(tb(name))
            scanned[name] = updated
            listOf(brief(updated), cache.findByIdOrNull(updated.book.id)?.metadata)
          }
        }
      }
    }
    func("getMetadata") {
      case("comic info series exact") { lifecycle.getMetadata(tb("batman")) }
      case("contains search") { lifecycle.getMetadata(tb("bat")) }
      case("append volume title") { lifecycle.getMetadata(tb("superman")) }
      case("blank series") { lifecycle.getMetadata(tb("blank")) }
      case("no metadata") { lifecycle.getMetadata(tb("nometa")) }
      case("epub") { lifecycle.getMetadata(tb("reflow")) }
      case("not analyzed") { attempt(dir) { lifecycle.getMetadata(TransientBook(book("X", "", "", url = URL("file:$dir/import/batman.cbz")), Media())) } }
    }
    func("getBookPage") {
      case("zip page") { attempt(dir) { lifecycle.getBookPage(tb("batman"), 1).let { listOf(Samples.digest(it.bytes), it.mediaType) } } }
      case("page 2") { attempt(dir) { lifecycle.getBookPage(tb("batman"), 2) } }
      case("pdf page") { attempt(dir) { lifecycle.getBookPage(tb("doc"), 1).let { listOf(graph.describeImage(it.bytes), it.mediaType) } } }
      case("epub reflow") { attempt(dir) { lifecycle.getBookPage(tb("reflow"), 1) } }
      case("not analyzed") { attempt(dir) { lifecycle.getBookPage(TransientBook(book("X", "", ""), Media()), 1) } }
    }
  }
}
