package org.gotson.komga.oracle.interfaces.api.kosync

import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.kosync.KoreaderSyncController
import org.gotson.komga.interfaces.api.kosync.dto.DocumentProgressDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import java.time.LocalDateTime

class KoreaderSyncControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val controller by lazy { KoreaderSyncController(db.bookDao, db.mediaDao, db.readProgressDao, services.bookLifecycle) }
  private val admin = KomgaPrincipal(InterfacesData.admin)
  private val limited = KomgaPrincipal(InterfacesData.limited)
  private val date = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

  private fun json(v: Any?) = WebOracle.stableText(WebOracle.mapper.writeValueAsString(v))

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, e.message)
    }

  private fun hash(
    bookId: String,
    h: String,
  ) = db.bookDao.update(db.bookDao.findByIdOrNull(bookId)!!.copy(fileHashKoreader = h))

  private fun update(
    document: String,
    progress: String,
    percentage: Float = 0.5F,
    principal: KomgaPrincipal = admin,
  ) = attempt { controller.updateProgress(principal, DocumentProgressDto(document, percentage, progress, "KOReader dev", "dev-id")) }

  private fun progress(bookId: String) = stable(db.readProgressDao.findByBookIdAndUserIdOrNull(bookId, "U1"))

  override fun cases() {
    func("registerUser") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
        hash("B7", "hash-cbz")
        hash("B8", "hash-epub")
        hash("B5", "hash-pdf")
        hash("B1", "dup")
        hash("B2", "dup")
        hash("B4", "hash-noext")
      }
      case("forbidden") { attempt { controller.registerUser() } }
    }
    func("authorize") {
      case("ok") { json(controller.authorize()) }
    }
    func("getProgress") {
      case("unknown hash") { attempt { controller.getProgress(admin, "nope") } }
      case("duplicate hash") { attempt { controller.getProgress(admin, "dup") } }
      case("no progress") { attempt { controller.getProgress(admin, "hash-cbz") } }
      case("divina progress") {
        db.readProgressDao.save(ReadProgress("B7", "U1", 2, false, date, "d1", "Device 1", createdDate = date))
        attempt { json(controller.getProgress(admin, "hash-cbz")) }
      }
      case("divina progress with locator") {
        db.readProgressDao.save(ReadProgress("B7", "U1", 3, true, date, "d1", "Device 1", R2Locator("", "", locations = R2Locator.Location(totalProgression = 0.75F)), createdDate = date))
        attempt { json(controller.getProgress(admin, "hash-cbz")) }
      }
      case("pdf, page only") {
        db.readProgressDao.save(ReadProgress("B5", "U1", 1, false, date, createdDate = date))
        attempt { json(controller.getProgress(admin, "hash-pdf")) }
      }
      case("epub without extension") {
        db.readProgressDao.save(ReadProgress("B4", "U1", 1, false, date, locator = R2Locator("OEBPS/a.xhtml", "application/xhtml+xml"), createdDate = date))
        attempt { controller.getProgress(admin, "hash-noext") }
      }
      case("epub") {
        db.mediaDao.update(
          db.mediaDao.findById("B8").copy(
            extension =
              MediaExtensionEpub(
                positions =
                  listOf(
                    R2Locator("OEBPS/cover.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0F, position = 1, totalProgression = 0F)),
                    R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0F, position = 2, totalProgression = 0.3F)),
                    R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0.5F, position = 3, totalProgression = 0.6F)),
                    R2Locator("OEBPS/ch2.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0F, position = 4, totalProgression = 0.9F)),
                  ),
              ),
          ),
        )
        db.readProgressDao.save(ReadProgress("B8", "U1", 1, false, date, locator = R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(totalProgression = 0.3F)), createdDate = date))
        attempt { json(controller.getProgress(admin, "hash-epub")) }
      }
      case("epub, unknown href") {
        db.readProgressDao.save(ReadProgress("B8", "U1", 1, false, date, locator = R2Locator("OEBPS/none.xhtml", "application/xhtml+xml"), createdDate = date))
        attempt { json(controller.getProgress(admin, "hash-epub")) }
      }
      case("other user") { attempt { controller.getProgress(limited, "hash-epub") } }
    }
    func("updateProgress") {
      case("unknown hash") { update("nope", "1") }
      case("duplicate hash") { update("dup", "1") }
      case("divina page") {
        update("hash-cbz", "2", 0.66F)
        progress("B7")
      }
      case("divina invalid page") { update("hash-cbz", "abc") }
      case("divina page out of range") { update("hash-cbz", "9") }
      case("epub doc fragment") {
        update("hash-epub", "/body/DocFragment[2]/body/div/p[1]/text().0", 0.3F)
        progress("B8")
      }
      case("epub doc fragment lower case") { update("hash-epub", "/body/docfragment[3].0", 0.95F).let { listOf(it, progress("B8")) } }
      case("epub toc fragment") { update("hash-epub", "#_doc_fragment_1_ c37", 0.31F).let { listOf(it, progress("B8")) } }
      case("epub index out of range") { update("hash-epub", "/body/DocFragment[9].0") }
      case("epub unparseable") { update("hash-epub", "garbage") }
      case("epub without extension") { update("hash-noext", "/body/DocFragment[1].0") }
      case("events") { services.drainEvents() }
    }
  }
}
