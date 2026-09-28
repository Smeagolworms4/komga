package org.gotson.komga.oracle.interfaces.api.opds

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.opds.OpdsCommonController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.gotson.komga.oracle.interfaces.OpdsSupport

class OpdsCommonControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val controller by lazy { OpdsCommonController(services.contentRestrictionChecker, services.bookLifecycle, services.imageConverter) }

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, e.message)
    }

  override fun cases() {
    func("getBookThumbnail") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
        OpdsSupport.thumbnails(db)
      }
      case("jpeg thumbnail") { attempt { controller.getBookThumbnail(KomgaPrincipal(InterfacesData.admin), "B7") } }
      case("png thumbnail converted") {
        db.thumbnailBookDao.insert(
          ThumbnailBook(thumbnail = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAYAAACddGYaAAAAH0lEQVR4nGP4z8DwHwwZ/v9n4BKR+69hZPPfLSDqPwCJ2wq6OEinjgAAAABJRU5ErkJggg=="), type = ThumbnailBook.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = Dimension(3, 2), selected = true, id = "TB8", bookId = "B8"),
        )
        attempt { controller.getBookThumbnail(KomgaPrincipal(InterfacesData.admin), "B8").let { listOf(it.size > 0, it[0].toInt() and 0xff, it[1].toInt() and 0xff) } }
      }
      case("no thumbnail") { attempt { controller.getBookThumbnail(KomgaPrincipal(InterfacesData.admin), "B1") } }
      case("restricted") { attempt { controller.getBookThumbnail(KomgaPrincipal(InterfacesData.restricted), "B4") } }
      case("unknown book") { attempt { controller.getBookThumbnail(KomgaPrincipal(InterfacesData.limited), "BX") } }
    }
  }
}
