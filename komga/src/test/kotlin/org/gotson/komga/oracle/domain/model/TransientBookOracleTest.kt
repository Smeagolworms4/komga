package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.TransientBook
import org.gotson.komga.domain.model.toBookWithMedia
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class TransientBookOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val book = Book("b.cbz", URL("file:/lib/b.cbz"), date, 10, id = "B1", seriesId = "S1", libraryId = "L1", createdDate = date)
  private val media = Media(Media.Status.READY, "application/zip", listOf(BookPage("1.jpg", "image/jpeg")), bookId = "B1", createdDate = date)

  override fun cases() {
    func("toBookWithMedia") {
      case("defaults") { TransientBook(book, media).toBookWithMedia() }
      case("metadata ignored") { TransientBook(book, media, TransientBook.Metadata(1.5f, "S9")).toBookWithMedia() }
      case("same instances") {
        val bwm = TransientBook(book, media).toBookWithMedia()
        listOf(bwm.book === book, bwm.media === media)
      }
      case("empty media") { TransientBook(book, Media(bookId = "B1", createdDate = date)).toBookWithMedia().media }
    }
  }
}
