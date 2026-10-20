package org.gotson.komga.oracle.infrastructure.koreader

import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.infrastructure.koreader.KoreaderProgressConverter
import org.gotson.komga.interfaces.api.kosync.dto.DocumentProgressDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData

class KoreaderProgressConverterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val converter = KoreaderProgressConverter(db.mediaDao)

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, e.message)
    }

  /** the R2Progression without its `modified` date (now) */
  private fun convert(
    bookId: String,
    progress: String,
    percentage: Float = 0.5F,
    media: Media? = null,
  ) = attempt {
    val book = db.bookDao.findByIdOrNull(bookId)!!
    converter
      .convertKoreaderProgressToR2(DocumentProgressDto("doc", percentage, progress, "KOReader dev", "dev-id"), BookWithMedia(book, media ?: db.mediaDao.findById(bookId)))
      ?.let { listOf(it.device, it.locator) }
  }

  override fun cases() {
    func("convertKoreaderProgressToR2") {
      case("setup") {
        InterfacesData.setup(db)
        db.mediaDao.update(
          db.mediaDao.findById("B6").copy(
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
      }
      case("divina page") { convert("B1", "2", 0.66F) }
      case("divina invalid page") { convert("B1", "abc") }
      case("divina negative page") { convert("B2", "-3", 0.1F) }
      case("pdf page") { convert("B5", "4", 1F) }
      case("epub doc fragment") { convert("B6", "/body/DocFragment[2]/body/div/p[1]/text().0", 0.3F) }
      case("epub doc fragment without brackets") { convert("B6", "/body/DocFragment/body/p", 0.01F) }
      case("epub toc fragment") { convert("B6", "#_doc_fragment_2_ c37", 0.95F) }
      case("epub index out of range") { convert("B6", "/body/DocFragment[9].0") }
      case("epub unparseable") { convert("B6", "garbage") }
      case("epub without extension") { convert("B4", "/body/DocFragment[1].0") }
      case("no profile") { convert("B1", "1", media = Media(status = Media.Status.READY, mediaType = "application/octet-stream", bookId = "B1")) }
      case("unknown media") { convert("B1", "1", media = Media(bookId = "B1")) }
    }
  }
}
