package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.R2Device
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.R2Progression
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.CommonBookController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CommonBookControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val controller: CommonBookController by lazy { services.commonBookController }
  private val admin = KomgaPrincipal(InterfacesData.admin)
  private val limited = KomgaPrincipal(InterfacesData.limited)
  private val restricted = KomgaPrincipal(InterfacesData.restricted)

  private fun json(v: Any?): String = WebOracle.stableText(WebOracle.mapper.writeValueAsString(v))

  private fun <T> web(block: () -> T): T = WebOracle.withRequest(WebOracle.request(uri = "/api/v1/books", host = "localhost", port = 25600), block)

  private fun describe(e: ResponseEntity<*>): List<Any?> {
    val d = WebOracle.describeEntity(e)
    val body = e.body
    return if (body is StreamingResponseBody) d.take(2) + listOf(ByteArrayOutputStream().also { body.writeTo(it) }.toByteArray()) else d
  }

  private fun lastModified() = "Thu, 02 Jan 2020 03:04:05 GMT"

  private fun page(
    bookId: String,
    page: Int,
    convert: String? = null,
    principal: KomgaPrincipal = admin,
    accept: String? = null,
    headers: List<Pair<String, String>> = emptyList(),
  ) = describe(
    controller.getBookPageInternal(bookId, page, convert, ServletWebRequest(WebOracle.request(uri = "/api/v1/books/$bookId/pages/$page", headers = headers)), principal, accept?.let { MediaType.parseMediaTypes(it).toMutableList() }),
  )

  private fun convertedPage(
    bookId: String,
    page: Int,
    convert: String,
  ) = page(bookId, page, convert).let { listOf(it[0], it[1]) }

  private fun progression(
    position: Int?,
    href: String = "p",
    progression: Float? = null,
    date: ZonedDateTime = ZonedDateTime.of(2021, 5, 6, 7, 8, 9, 0, ZoneOffset.UTC),
  ) = R2Progression(date, R2Device("dev1", "My Device"), R2Locator(href, "image/png", locations = R2Locator.Location(position = position, progression = progression)))

  private fun progress(
    bookId: String,
    user: KomgaUser = InterfacesData.admin,
  ) = stable(db.readProgressDao.findByBookIdAndUserIdOrNull(bookId, user.id))

  override fun cases() {
    func("getWebPubManifestInternal") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
      }
      listOf("B1", "B4", "B5", "B6", "BX").forEach { id -> case(id) { web { json(controller.getWebPubManifestInternal(admin, id, services.webPubGenerator)) } } }
      case("unknown media type") {
        db.mediaDao.update(db.mediaDao.findById("B3").copy(mediaType = "text/plain"))
        web { json(controller.getWebPubManifestInternal(admin, "B3", services.webPubGenerator)) }
      }
      case("opds generator") { web { json(controller.getWebPubManifestInternal(admin, "B2", services.opdsGenerator)) } }
    }
    func("getWebPubManifestEpubInternal") {
      case("epub") { web { json(controller.getWebPubManifestEpubInternal(admin, "B4", services.webPubGenerator)) } }
      case("not epub") { web { json(controller.getWebPubManifestEpubInternal(admin, "B1", services.webPubGenerator)) } }
      case("restricted") { web { json(controller.getWebPubManifestEpubInternal(restricted, "B4", services.webPubGenerator)) } }
      case("limited: other library") { web { json(controller.getWebPubManifestEpubInternal(limited, "B4", services.webPubGenerator)) } }
      case("unknown") { web { json(controller.getWebPubManifestEpubInternal(admin, "BX", services.webPubGenerator)) } }
    }
    func("getWebPubManifestPdfInternal") {
      case("pdf") { web { json(controller.getWebPubManifestPdfInternal(admin, "B5", services.webPubGenerator)) } }
      case("not pdf") { web { json(controller.getWebPubManifestPdfInternal(admin, "B4", services.webPubGenerator)) } }
      case("limited") { web { json(controller.getWebPubManifestPdfInternal(limited, "B5", services.webPubGenerator)) } }
      case("unknown") { web { json(controller.getWebPubManifestPdfInternal(admin, "BX", services.webPubGenerator)) } }
    }
    func("getWebPubManifestDivinaInternal") {
      case("divina") { web { json(controller.getWebPubManifestDivinaInternal(admin, "B7", services.webPubGenerator)) } }
      case("pdf as divina") { web { json(controller.getWebPubManifestDivinaInternal(admin, "B5", services.webPubGenerator)) } }
      case("limited, allowed") { web { json(controller.getWebPubManifestDivinaInternal(limited, "B2", services.webPubGenerator)) } }
      case("unknown") { web { json(controller.getWebPubManifestDivinaInternal(admin, "BX", services.webPubGenerator)) } }
    }
    func("getBookPageInternal") {
      case("page 1") { page("B7", 1) }
      case("page 2") { page("B7", 2) }
      case("page 3") { page("B7", 3) }
      case("page 0") { page("B7", 0) }
      case("page 4") { page("B7", 4) }
      case("invalid conversion") { page("B7", 1, "webp") }
      case("empty conversion") { page("B7", 1, "") }
      case("convert same format") { page("B7", 2, "JPEG") }
      case("convert png to jpeg") { convertedPage("B7", 1, "jpeg") }
      case("convert jpeg to png") { convertedPage("B7", 2, "png") }
      case("not modified") { page("B7", 1, headers = listOf("If-Modified-Since" to lastModified())) }
      case("modified since earlier") { page("B7", 1, headers = listOf("If-Modified-Since" to "Wed, 01 Jan 2020 00:00:00 GMT")).take(2) }
      case("missing file") { page("B1", 1) }
      case("unknown book") { page("BX", 1) }
      case("restricted") { page("B7", 1, principal = restricted) }
      case("limited other library") { page("B4", 1, principal = limited) }
      case("pdf accept pdf: raw, missing file") { page("B5", 1, accept = "application/pdf") }
      case("pdf accept image first") { page("B5", 1, accept = "image/jpeg, application/pdf;q=0.5") }
      case("accept pdf on cbz") { page("B7", 1, accept = "application/pdf") }
    }
    func("getBookPageRawByNumber") {
      case("page 1") { describe(controller.getBookPageRawByNumber(admin, ServletWebRequest(WebOracle.request()), "B7", 1)) }
      case("page 9") { describe(controller.getBookPageRawByNumber(admin, ServletWebRequest(WebOracle.request()), "B7", 9)) }
      case("not modified") { describe(controller.getBookPageRawByNumber(admin, ServletWebRequest(WebOracle.request(headers = listOf("If-Modified-Since" to lastModified()))), "B7", 1)) }
      case("unknown") { describe(controller.getBookPageRawByNumber(admin, ServletWebRequest(WebOracle.request()), "BX", 1)) }
      case("restricted") { describe(controller.getBookPageRawByNumber(restricted, ServletWebRequest(WebOracle.request()), "B7", 1)) }
    }
    func("getBookPageRawInternal") {
      case("page 3") { describe(controller.getBookPageRawInternal(db.bookDao.findByIdOrNull("B7")!!, db.mediaDao.findById("B7"), 3)) }
      case("page -1") { describe(controller.getBookPageRawInternal(db.bookDao.findByIdOrNull("B7")!!, db.mediaDao.findById("B7"), -1)) }
      case("epub") { describe(controller.getBookPageRawInternal(db.bookDao.findByIdOrNull("B8")!!, db.mediaDao.findById("B8"), 1)) }
      case("missing file") { describe(controller.getBookPageRawInternal(db.bookDao.findByIdOrNull("B2")!!, db.mediaDao.findById("B2"), 1)) }
    }
    func("getBookEpubResource") {
      fun res(
        resource: String,
        principal: KomgaPrincipal? = admin,
        bookId: String = "B8",
        headers: List<Pair<String, String>> = emptyList(),
      ) = describe(controller.getBookEpubResource(WebOracle.request(headers = headers), principal, bookId, resource))
      case("page") { res("/OEBPS/ch 1.xhtml") }
      case("asset without leading slash") { res("OEBPS/style.css") }
      case("font anonymous") { res("/OEBPS/fonts/f.woff2", null) }
      case("css anonymous") { res("/OEBPS/style.css", null) }
      case("not in media files") { res("/OEBPS/other.css") }
      case("in media files but not in archive") { res("/OEBPS/missing.css") }
      case("not epub") { res("/p1.png", bookId = "B7") }
      case("unknown book") { res("/x.css", bookId = "BX") }
      case("not modified") { res("/OEBPS/style.css", headers = listOf("If-Modified-Since" to lastModified())) }
      case("restricted") { res("/OEBPS/style.css", KomgaPrincipal(InterfacesData.restricted)) }
    }
    func("downloadBookFile") {
      case("cbz") { describe(controller.downloadBookFile(admin, "B7")) }
      case("unknown") { describe(controller.downloadBookFile(admin, "BX")) }
    }
    func("getBookFileInternal") {
      case("epub") { describe(controller.getBookFileInternal(admin, "B8")) }
      case("missing file") { describe(controller.getBookFileInternal(admin, "B1")) }
      case("restricted") { describe(controller.getBookFileInternal(restricted, "B7")) }
      case("limited other library") { describe(controller.getBookFileInternal(limited, "B4")) }
    }
    func("getBookProgression") {
      case("in progress") { describe(controller.getBookProgression(admin, "B2")).let { listOf(it[0], it[1], json(it[2])) } }
      case("completed") { describe(controller.getBookProgression(admin, "B1")).let { listOf(it[0], it[1], json(it[2])) } }
      case("no progress") { describe(controller.getBookProgression(admin, "B3")) }
      case("unknown") { describe(controller.getBookProgression(admin, "BX")) }
      case("limited") { describe(controller.getBookProgression(limited, "B5")) }
    }
    func("updateBookProgression") {
      case("divina page 2") {
        controller.updateBookProgression(admin, "B7", progression(2))
        progress("B7")
      }
      case("older than existing") { controller.updateBookProgression(admin, "B7", progression(3, date = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))) }
      case("last page completes") {
        controller.updateBookProgression(admin, "B7", progression(3, date = ZonedDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(2))))
        progress("B7")
      }
      case("page out of range") { controller.updateBookProgression(admin, "B3", progression(9)) }
      case("no position") { controller.updateBookProgression(admin, "B3", progression(null)) }
      case("epub resource not found") { controller.updateBookProgression(admin, "B8", progression(null, "OEBPS/nope.xhtml", 0.5F)) }
      case("epub no progression") { controller.updateBookProgression(admin, "B8", progression(null, "OEBPS/ch%201.xhtml#x")) }
      case("epub without extension") { controller.updateBookProgression(admin, "B8", progression(null, "OEBPS/ch%201.xhtml#x", 0.5F)) }
      case("restricted") { controller.updateBookProgression(restricted, "B7", progression(1)) }
      case("unknown") { controller.updateBookProgression(admin, "BX", progression(1)) }
      case("events") { services.drainEvents() }
    }
  }
}
