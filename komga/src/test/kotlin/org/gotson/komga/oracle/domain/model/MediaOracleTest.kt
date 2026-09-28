package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.MediaFile
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class MediaOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  override fun cases() {
    func("toString") {
      case("defaults") { Media(createdDate = date).toString() }
      case("full") {
        Media(
          Media.Status.READY,
          "application/epub+zip",
          listOf(BookPage("1.jpg", "image/jpeg")),
          7,
          listOf(MediaFile("a.css")),
          "ERR_1000 'quoted'",
          MediaExtensionEpub(isFixedLayout = true),
          "B1",
          true,
          true,
          LocalDateTime.of(2021, 2, 3, 4, 5, 6, 7),
          LocalDateTime.of(2021, 2, 3, 4, 5),
        ).toString()
      }
      case("unicode comment") { Media(Media.Status.ERROR, comment = "échec 漫画", bookId = "", createdDate = date).toString() }
      case("empty strings") { Media(Media.Status.OUTDATED, "", comment = "", createdDate = date).toString() }
    }
  }
}
