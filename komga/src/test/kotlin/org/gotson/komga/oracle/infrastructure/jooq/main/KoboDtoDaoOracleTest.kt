package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.BookProjection
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class KoboDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.koboDtoDao

  private fun find(vararg ids: String) = dao.findBookMetadataByIds(ids.toList()).sortedBy { it.entitlementId }

  override fun cases() {
    func("findBookMetadataByIds") {
      case("empty database") { dao.findBookMetadataByIds(listOf("B1")) }
      case("series book with thumbnail and authors") {
        NzDaoSeed.seed(db)
        find("B1")
      }
      case("several books") { find("B6", "B2", "B4", "NOPE") }
      case("book without release date nor publisher") { find("B5", "B9") }
      case("oneshot fixed layout kepub with projections") {
        db.mediaDao.update(NzDaoSeed.media.first { it.bookId == "B10" }.copy(extension = MediaExtensionEpub(isFixedLayout = true), epubIsKepub = true))
        db.bookProjectionDao.save(BookProjection("B10", "kepub", 650))
        db.bookProjectionDao.save(BookProjection("B10", "epub3", 710))
        find("B10")
      }
      case("epub not fixed layout") {
        db.mediaDao.update(NzDaoSeed.media.first { it.bookId == "B11" }.copy(mediaType = "application/epub+zip", extension = MediaExtensionEpub()))
        find("B11").map { listOf(it.isPrePaginated, it.isKepub, it.language, it.extraFileSizes) }
      }
      case("empty ids") { dao.findBookMetadataByIds(emptyList()) }
      case("all books") { find(*NzDaoSeed.books.map { it.id }.toTypedArray()).map { listOf(it.entitlementId, it.series?.name, it.language, it.coverImageId) } }
    }
  }
}
