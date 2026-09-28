package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Media
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeBytes

class BookConverterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val converter = graph.bookConverter

  private val dir by lazy { tempDir.resolve("lib").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  /** Adds the book found at [path] (scanned, then analyzed) */
  private fun addBook(
    id: String,
    path: Path,
    libraryId: String = "L1",
  ): Book {
    val scanned = graph.fileSystemScanner.scanFile(path)!!.copy(id = id, seriesId = "S$libraryId", libraryId = libraryId, createdDate = date, lastModifiedDate = date)
    db.bookDao.insert(scanned)
    db.bookMetadataDao.insert(metadata(scanned))
    db.mediaDao.insert(graph.bookAnalyzer.analyze(scanned, true).copy(createdDate = date))
    return scanned
  }

  private fun copy(
    from: Path,
    name: String,
  ): Path = Files.copy(from, dir.resolve(name))

  private fun b(id: String) = db.bookDao.findByIdOrNull(id)!!

  private fun state(id: String) =
    attempt(dir) {
      val bk = db.bookDao.findByIdOrNull(id)
      val m = db.mediaDao.findByIdOrNull(id)
      listOf(
        bk?.let { listOf(it.name, it.url, it.fileLastModified, it.number, it.seriesId, it.libraryId) },
        m?.let { listOf(it.status, it.mediaType, it.pageCount, it.pages.map { p -> listOf(p.fileName, p.mediaType, p.fileHash, p.dimension) }, it.files.map { f -> f.fileName }) },
        Files.list(dir).use { s -> s.map { it.name }.toList().sorted() },
        db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by TYPE, BOOK_ID"),
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  override fun cases() {
    func("getConvertibleBooks") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$dir")).copy(convertToCbz = true, repairExtensions = true))
        db.libraryDao.insert(library("L2", URL("file:$dir")))
        db.seriesDao.insert(series("SL1", "L1", URL("file:$dir")))
        db.seriesDao.insert(series("SL2", "L2", URL("file:$dir")))
        addBook("R4", copy(Samples.komgaRes("archives/rar4.rar"), "rar4.cbr"))
        addBook("R5", copy(Samples.komgaRes("archives/rar5.rar"), "rar5.rar"))
        addBook("RX", copy(Samples.komgaRes("archives/rar4.rar"), "exists.cbr"))
        dir.resolve("exists.cbz").writeBytes(oracleBytes(3))
        addBook("RE", copy(Samples.komgaRes("archives/rar4-encrypted.rar"), "encrypted.cbr"))
        addBook("RL2", copy(Samples.komgaRes("archives/rar5.rar"), "other.cbr"), "L2")
        addBook("Z1", zipFile(dir, "zip.cbz", listOf("p1.png" to png, t("ComicInfo.xml", "<ComicInfo/>"))).let { Path.of(it.toURI()) })
        addBook("ZR", zipFile(dir, "zipped.cbr", listOf("p1.png" to png)).let { Path.of(it.toURI()) })
        addBook("PE", copy(Samples.fixture("pdf/komga.pdf"), "doc.epub"))
        addBook("EZ", zipFile(dir, "ep.epub", listOf("p1.png" to png)).let { Path.of(it.toURI()) })
        addBook("EP", copy(Samples.fixture("epub/reflow.epub"), "reflow.zip"))
        addBook("OK", copy(Samples.fixture("pdf/komga.pdf"), "good.pdf"))
        db.rawQuery("select BOOK_ID, STATUS, MEDIA_TYPE from MEDIA order by BOOK_ID")
      }
      case("enabled") { converter.getConvertibleBooks(db.libraryDao.findById("L1")).map { it.id }.sorted() }
      case("disabled") { converter.getConvertibleBooks(db.libraryDao.findById("L2")) }
      case("enabled on L2") { converter.getConvertibleBooks(db.libraryDao.findById("L2").copy(convertToCbz = true)).map { it.id } }
      case("unknown library") { converter.getConvertibleBooks(library("L9").copy(convertToCbz = true)) }
    }
    func("getMismatchedExtensionBooks") {
      case("library L1") { converter.getMismatchedExtensionBooks(db.libraryDao.findById("L1")).map { it.id }.sorted() }
      case("library L2") { converter.getMismatchedExtensionBooks(db.libraryDao.findById("L2")).map { it.id } }
    }
    func("convertToCbz") {
      case("disabled library") {
        converter.convertToCbz(b("RL2"))
        state("RL2")
      }
      case("rar4") {
        attempt(dir) { converter.convertToCbz(b("R4")) }
        state("R4")
      }
      case("rar5 with hashes") {
        db.mediaDao.update(db.mediaDao.findById("R5").let { it.copy(pages = it.pages.mapIndexed { i, p -> p.copy(fileHash = "H$i") }) })
        attempt(dir) { converter.convertToCbz(b("R5")) }
        state("R5")
      }
      case("destination exists") { attempt(dir) { converter.convertToCbz(b("RX")) } }
      case("not convertible") { attempt(dir) { converter.convertToCbz(b("Z1")) } }
      case("changed on disk") {
        converter.convertToCbz(b("RE").copy(fileLastModified = date))
        state("RE")
      }
      case("encrypted, not ready") { attempt(dir) { converter.convertToCbz(b("RE")) } }
      case("file not found") { attempt(dir) { converter.convertToCbz(b("R4").copy(url = URL("file:$dir/gone.cbr"))) } }
      case("unknown library") { exceptionType { converter.convertToCbz(b("R4").copy(libraryId = "L9")) } }
      case("failed conversion is remembered") {
        db.mediaDao.update(db.mediaDao.findById("RE").copy(status = Media.Status.READY))
        listOf(attempt(dir) { converter.convertToCbz(b("RE")) }, state("RE"), attempt(dir) { converter.convertToCbz(b("RE")) })
      }
    }
    func("repairExtension") {
      case("disabled library") {
        converter.repairExtension(b("RL2"))
        state("RL2")
      }
      case("zip named cbr") {
        attempt(dir) { converter.repairExtension(b("ZR")) }
        state("ZR")
      }
      case("pdf named epub") {
        attempt(dir) { converter.repairExtension(b("PE")) }
        state("PE")
      }
      case("epub detected as zip is skipped") {
        attempt(dir) { converter.repairExtension(b("EZ")) }
        listOf(state("EZ"), attempt(dir) { converter.repairExtension(b("EZ")) })
      }
      case("epub named zip") {
        attempt(dir) { converter.repairExtension(b("EP")) }
        state("EP")
      }
      case("extension already correct") {
        listOf(attempt(dir) { converter.repairExtension(b("OK")) }, state("OK"), attempt(dir) { converter.repairExtension(b("OK")) })
      }
      case("unsupported media type") {
        db.mediaDao.update(db.mediaDao.findById("Z1").copy(mediaType = "image/png"))
        attempt(dir) { converter.repairExtension(b("Z1")) }
      }
      case("file not found") { attempt(dir) { converter.repairExtension(b("R4").copy(url = URL("file:$dir/gone.cbr"))) } }
      case("destination exists") {
        dir.resolve("clash.cbz").writeBytes(oracleBytes(3))
        addBook("CL", Path.of(zipFile(dir, "clash.cbr", listOf("p1.png" to png)).toURI()))
        attempt(dir) { converter.repairExtension(b("CL")) }
      }
      case("unknown library") { exceptionType { converter.repairExtension(b("ZR").copy(libraryId = "L9")) } }
    }
  }
}
