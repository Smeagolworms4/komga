package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.AuthorDto
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataDto
import org.gotson.komga.interfaces.api.rest.dto.MediaDto
import org.gotson.komga.interfaces.api.rest.dto.ReadProgressDto
import org.gotson.komga.interfaces.api.rest.dto.WebLinkDto
import org.gotson.komga.interfaces.api.rest.dto.restrictUrl
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDate
import java.time.LocalDateTime

class BookDtoOracleTest : OracleTest() {
  private val d = LocalDateTime.of(2020, 3, 4, 5, 6, 7)

  private fun book(
    url: String,
    sizeBytes: Long = 1536,
  ) = BookDto(
    id = "B1",
    seriesId = "S1",
    seriesTitle = "Series",
    libraryId = "L1",
    name = "Book",
    url = url,
    number = 1,
    created = d,
    lastModified = d,
    fileLastModified = d,
    sizeBytes = sizeBytes,
    media = MediaDto("READY", "application/zip", 10, "", false, false),
    metadata =
      BookMetadataDto(
        "Title", false, "Summary", true, "1", false, 1.5f, false, LocalDate.of(2020, 1, 1), false,
        listOf(AuthorDto("a", "writer")), false, setOf("t2", "t1"), false, "9781234567897", false,
        listOf(WebLinkDto("l", "https://l")), false, d, d,
      ),
    readProgress = ReadProgressDto(3, false, d, d, d, "dev", "Device"),
    deleted = false,
    fileHash = "hash",
    oneshot = false,
  )

  override fun cases() {
    func("restrictUrl") {
      case("not restricted") { book("/data/lib/series/book.cbz").restrictUrl(false) }
      case("restricted") { book("/data/lib/series/book.cbz").restrictUrl(true).url }
      case("restricted windows path") { book("C:\\data\\lib\\book.cbz").restrictUrl(true).url }
      case("restricted no directory") { book("book.cbz").restrictUrl(true).url }
      case("restricted trailing slash") { book("/data/lib/").restrictUrl(true).url }
      case("restricted empty") { book("").restrictUrl(true).url }
      case("size formats") { listOf(0L, 1L, 1023L, 1024L, 1536L, 1048576L, 123456789L, 1099511627776L).map { book("x", it).size } }
      case("json") { json(book("/a/b.cbz").restrictUrl(true)) }
    }
  }
}
