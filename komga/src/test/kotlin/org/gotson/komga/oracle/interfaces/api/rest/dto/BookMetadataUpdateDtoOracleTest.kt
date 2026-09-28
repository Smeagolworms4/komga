package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataUpdateDto
import org.gotson.komga.interfaces.api.rest.dto.patch
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

class BookMetadataUpdateDtoOracleTest : OracleTest() {
  private val props = listOf("summary", "releaseDate", "authors", "tags", "isbn", "links", "title", "number")

  private val meta =
    BookMetadata(
      title = "Title",
      summary = "Summary",
      number = "1",
      numberSort = 1f,
      releaseDate = LocalDate.of(2020, 1, 1),
      authors = listOf(Author("John", "writer")),
      tags = setOf("t1"),
      isbn = "9781234567897",
      links = listOf(WebLink("site", URI("https://example.org"))),
      bookId = "B1",
      createdDate = LocalDateTime.of(2020, 1, 1, 0, 0),
    )

  private fun dto(json: String) = read<BookMetadataUpdateDto>(json)

  override fun cases() {
    func("isSet") {
      case("empty body") { dto("{}").let { d -> props.map { d.isSet(it) } } }
      case("nulls") { dto("""{"summary":null,"releaseDate":null,"authors":null,"tags":null,"isbn":null,"links":null,"title":null}""").let { d -> props.map { d.isSet(it) } } }
      case("values") { dto("""{"summary":"s","releaseDate":"2021-02-03","authors":[],"tags":["a"],"isbn":"1","links":[]}""").let { d -> props.map { d.isSet(it) } } }
    }
    func("patch") {
      case("empty patch") { meta.patch(dto("{}")) }
      case("all null") {
        meta.patch(dto("""{"title":null,"titleLock":null,"summary":null,"number":null,"numberSort":null,"releaseDate":null,"authors":null,"tags":null,"isbn":null,"links":null}"""))
      }
      case("all values") {
        meta.patch(
          dto(
            """{"title":"T2","titleLock":true,"summary":"S2","summaryLock":true,"number":"2","numberLock":true,"numberSort":2.5,"numberSortLock":true,
            |"releaseDate":"2021-02-03","releaseDateLock":true,"authors":[{"name":"Jane","role":"PENCILLER"},{"name":" x ","role":" Y "}],"authorsLock":true,
            |"tags":["b","a"],"tagsLock":true,"isbn":"978-1-4028-9462-6","isbnLock":true,"links":[{"label":"l","url":"https://x.org/p?q=1"}],"linksLock":true}
            """.trimMargin(),
          ),
        )
      }
      case("isbn with letters") { meta.patch(dto("""{"isbn":"ISBN 12-3x"}""")).isbn }
      case("author with null fields") { meta.patch(dto("""{"authors":[{}]}""")).authors }
      case("link with null label") { exceptionType { meta.patch(dto("""{"links":[{"url":"https://x"}]}""")) } }
      case("link with invalid uri") { exceptionType { meta.patch(dto("""{"links":[{"label":"l","url":"not a uri"}]}""")) } }
    }
  }
}
