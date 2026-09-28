package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.service.MetadataAggregator
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDate

class MetadataAggregatorOracleTest : OracleTest() {
  private val aggregator = MetadataAggregator()

  private fun m(
    number: String,
    numberSort: Float,
    summary: String = "",
    releaseDate: LocalDate? = null,
    authors: List<Author> = emptyList(),
    tags: Set<String> = emptySet(),
  ) = BookMetadata(
    title = "t$number",
    summary = summary,
    number = number,
    numberSort = numberSort,
    releaseDate = releaseDate,
    authors = authors,
    tags = tags,
    bookId = "B$number",
    createdDate = ServiceGraph.date,
  )

  private fun agg(l: List<BookMetadata>) = stable(aggregator.aggregate(l))

  override fun cases() {
    func("aggregate") {
      case("empty") { agg(emptyList()) }
      case("single without data") { agg(listOf(m("1", 1F))) }
      case("authors distinct by role and name") {
        agg(
          listOf(
            m("1", 1F, authors = listOf(Author("John", "writer"), Author("Jane", "penciller"))),
            m("2", 2F, authors = listOf(Author("john", "writer"), Author("John", "Writer"), Author("John", "penciller"))),
            m("3", 3F, authors = listOf(Author(" Jane ", "PENCILLER"), Author("Zed", "colorist"))),
          ),
        )
      }
      case("tags union in order") {
        agg(listOf(m("1", 1F, tags = setOf("b", "a")), m("2", 2F, tags = setOf("a", "c", "B"))))
      }
      case("summary of lowest numberSort") {
        agg(listOf(m("3", 3F, summary = "third"), m("1", 1F, summary = "first"), m("2", 2F, summary = "second")))
      }
      case("summary skips blank") {
        agg(listOf(m("1", 1F, summary = "  "), m("2", 2F, summary = "\t\n"), m("3", 3F, summary = "real")))
      }
      case("summary all blank") { agg(listOf(m("1", 1F, summary = " "), m("2", 2F))) }
      case("summary same numberSort keeps input order") {
        agg(listOf(m("b", 1F, summary = "B"), m("a", 1F, summary = "A")))
      }
      case("negative and fractional numberSort") {
        agg(listOf(m("1.5", 1.5F, summary = "one and half"), m("-1", -1F, summary = "minus one"), m("0", 0F)))
      }
      case("release date minimum") {
        agg(
          listOf(
            m("1", 1F, releaseDate = LocalDate.of(2020, 5, 1)),
            m("2", 2F),
            m("3", 3F, releaseDate = LocalDate.of(1999, 12, 31)),
            m("4", 4F, releaseDate = LocalDate.of(2021, 1, 1)),
          ),
        )
      }
      case("release date all null") { agg(listOf(m("1", 1F), m("2", 2F))) }
      case("summary number is the number string") { aggregator.aggregate(listOf(m("Vol. 7", 7F, summary = "s"))).summaryNumber }
      case("NaN numberSort") {
        agg(listOf(m("n", Float.NaN, summary = "nan"), m("1", 1F, summary = "one")))
      }
    }
  }
}
