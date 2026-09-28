package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.FilterBy
import org.gotson.komga.domain.model.FilterByEntity
import org.gotson.komga.domain.model.FilterTags
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

@Suppress("DEPRECATION")
class ReferentialDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.referentialDao

  private val filters = listOf("all libraries" to null, "library L1" to listOf("L1"), "library L2" to listOf("L2"), "no library" to emptyList<String>())

  private val contexts =
    listOf(
      "admin" to SearchContext(NzDaoSeed.u1),
      "anonymous" to SearchContext.empty(),
      "age restricted" to SearchContext(NzDaoSeed.u2),
      "label excluded" to SearchContext(NzDaoSeed.u3),
      "age excluded or label allowed" to SearchContext(NzDaoSeed.u4),
    )

  private val filterBys =
    listOf(
      "no filter" to null,
      "by library" to FilterBy(FilterByEntity.LIBRARY, setOf("L2")),
      "by collection" to FilterBy(FilterByEntity.COLLECTION, setOf("C1")),
      "by series" to FilterBy(FilterByEntity.SERIES, setOf("S1", "S3")),
      "by read list" to FilterBy(FilterByEntity.READLIST, setOf("RL1")),
    )

  private val paged = PageRequest.of(1, 2)

  private fun <T> withFilters(block: (List<String>?) -> T) {
    filters.forEach { (name, f) -> case(name) { block(f) } }
  }

  private fun attempt(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      "throws ${e::class.java.simpleName}"
    }

  /** every context x filter combination, unpaged, plus a paged admin query and a search */
  private fun <T> generic(
    search: String?,
    block: (SearchContext, String?, FilterBy?, Pageable) -> T,
  ) {
    case("seeded") {
      if (db.seriesDao.count() == 0L) NzDaoSeed.seed(db)
      attempt { block(contexts[0].second, null, null, Pageable.unpaged()) }
    }
    contexts.forEach { (cn, c) ->
      filterBys.forEach { (fn, f) -> case("$cn, $fn") { attempt { block(c, null, f, Pageable.unpaged()) } } }
    }
    case("paged") { attempt { block(contexts[0].second, null, null, paged) } }
    if (search != null) {
      case("search") { attempt { block(contexts[0].second, search, null, Pageable.unpaged()) } }
      case("search restricted and filtered") { attempt { block(contexts[2].second, search, filterBys[1].second, PageRequest.of(0, 1)) } }
    }
  }

  override fun cases() {
    func("findAllAuthorsByName") {
      case("empty database") { dao.findAllAuthorsByName("a", null) }
      case("seed") {
        NzDaoSeed.seed(db)
        dao.findAllAuthorsByName("", null)
      }
      case("accent insensitive") { dao.findAllAuthorsByName("emile", null) }
      case("case sensitive") { dao.findAllAuthorsByName("FRANK", null) }
      withFilters { dao.findAllAuthorsByName("i", it) }
    }

    func("findAllAuthorsByNameAndLibrary") {
      case("L1") { dao.findAllAuthorsByNameAndLibrary("", "L1", null) }
      case("filtered out") { dao.findAllAuthorsByNameAndLibrary("", "L1", listOf("L2")) }
      withFilters { dao.findAllAuthorsByNameAndLibrary("a", "L2", it) }
    }

    func("findAllAuthorsByNameAndCollection") {
      withFilters { dao.findAllAuthorsByNameAndCollection("", "C1", it) }
      case("search") { dao.findAllAuthorsByNameAndCollection("MILLER", "C1", null) }
    }

    func("findAllAuthorsByNameAndSeries") {
      withFilters { dao.findAllAuthorsByNameAndSeries("r", "S1", it) }
      case("unknown series") { dao.findAllAuthorsByNameAndSeries("", "NOPE", null) }
    }

    func("findAllAuthorsNamesByName") {
      withFilters { dao.findAllAuthorsNamesByName("", it) }
      case("accent insensitive") { dao.findAllAuthorsNamesByName("zo", null) }
    }

    func("findAllAuthorsRoles") {
      withFilters { dao.findAllAuthorsRoles(it) }
    }

    func("findAuthors") {
      generic("mill") { c, s, f, p -> dao.findAuthors(c, s, null, f, p) }
      case("role") { dao.findAuthors(contexts[0].second, null, "penciller", null, Pageable.unpaged()) }
      case("role and search") { dao.findAuthors(contexts[0].second, "a", "writer", filterBys[3].second, Pageable.unpaged()) }
    }

    func("findAuthorsRoles") {
      generic(null) { c, _, f, p -> dao.findAuthorsRoles(c, f, p) }
    }

    func("findAuthorsNames") {
      generic("é") { c, s, f, p -> dao.findAuthorsNames(c, s, null, f, p) }
      case("role") { dao.findAuthorsNames(contexts[0].second, null, "writer", null, PageRequest.of(0, 3)) }
    }

    func("findAllGenres") {
      withFilters { dao.findAllGenres(it) }
    }

    func("findAllGenresByLibraries") {
      withFilters { dao.findAllGenresByLibraries(setOf("L1", "L2"), it) }
      case("empty library set") { dao.findAllGenresByLibraries(emptySet(), null) }
    }

    func("findAllGenresByCollection") {
      withFilters { dao.findAllGenresByCollection("C1", it) }
    }

    func("findGenres") {
      generic("ac") { c, s, f, p -> dao.findGenres(c, s, f, p) }
    }

    func("findAllSeriesAndBookTags") {
      withFilters { dao.findAllSeriesAndBookTags(it) }
    }

    func("findAllSeriesAndBookTagsByLibraries") {
      withFilters { dao.findAllSeriesAndBookTagsByLibraries(setOf("L1"), it) }
    }

    func("findAllSeriesAndBookTagsByCollection") {
      withFilters { dao.findAllSeriesAndBookTagsByCollection("C1", it) }
    }

    func("findAllSeriesTags") {
      withFilters { dao.findAllSeriesTags(it) }
    }

    func("findAllSeriesTagsByLibrary") {
      withFilters { dao.findAllSeriesTagsByLibrary("L1", it) }
    }

    func("findAllBookTagsBySeries") {
      withFilters { dao.findAllBookTagsBySeries("S1", it) }
    }

    func("findAllBookTagsByReadList") {
      withFilters { dao.findAllBookTagsByReadList("RL1", it) }
    }

    func("findTags") {
      FilterTags.entries.forEach { t ->
        case("$t seeded") { dao.findTags(contexts[0].second, null, null, t, Pageable.unpaged()) }
        contexts.drop(1).forEach { (cn, c) -> case("$t $cn") { attempt { dao.findTags(c, null, null, t, Pageable.unpaged()) } } }
        filterBys.drop(1).forEach { (fn, f) -> case("$t $fn") { attempt { dao.findTags(contexts[0].second, null, f, t, Pageable.unpaged()) } } }
        case("$t search paged") { dao.findTags(contexts[0].second, "e", null, t, PageRequest.of(0, 2)) }
      }
    }

    func("findAllSeriesTagsByCollection") {
      withFilters { dao.findAllSeriesTagsByCollection("C1", it) }
    }

    func("findAllBookTags") {
      withFilters { dao.findAllBookTags(it) }
    }

    func("findAllLanguages") {
      withFilters { dao.findAllLanguages(it) }
    }

    func("findAllLanguagesByLibraries") {
      withFilters { dao.findAllLanguagesByLibraries(setOf("L2"), it) }
    }

    func("findAllLanguagesByCollection") {
      withFilters { dao.findAllLanguagesByCollection("C2", it) }
    }

    func("findLanguages") {
      generic("E") { c, s, f, p -> dao.findLanguages(c, s, f, p) }
    }

    func("findAllPublishers@458") {
      withFilters { dao.findAllPublishers(it) }
    }

    func("findAllPublishers@469") {
      withFilters { dao.findAllPublishers(it, PageRequest.of(0, 2)) }
      case("unpaged") { dao.findAllPublishers(null, Pageable.unpaged()) }
      case("second page") { dao.findAllPublishers(null, paged) }
    }

    func("findAllPublishersByLibraries") {
      withFilters { dao.findAllPublishersByLibraries(setOf("L1", "L2"), it) }
    }

    func("findAllPublishersByCollection") {
      withFilters { dao.findAllPublishersByCollection("C1", it) }
    }

    func("findPublishers") {
      generic("DC") { c, s, f, p -> dao.findPublishers(c, s, f, p) }
    }

    func("findAllAgeRatings") {
      withFilters { dao.findAllAgeRatings(it) }
    }

    func("findAllAgeRatingsByLibraries") {
      withFilters { dao.findAllAgeRatingsByLibraries(setOf("L1"), it) }
    }

    func("findAllAgeRatingsByCollection") {
      withFilters { dao.findAllAgeRatingsByCollection("C1", it) }
    }

    func("findAgeRatings") {
      generic(null) { c, _, f, p -> dao.findAgeRatings(c, f, p) }
    }

    func("findAllSeriesReleaseDates") {
      withFilters { dao.findAllSeriesReleaseDates(it) }
    }

    func("findAllSeriesReleaseDatesByLibraries") {
      withFilters { dao.findAllSeriesReleaseDatesByLibraries(setOf("L2"), it) }
    }

    func("findAllSeriesReleaseDatesByCollection") {
      withFilters { dao.findAllSeriesReleaseDatesByCollection("C1", it) }
    }

    func("findSeriesReleaseYears") {
      generic(null) { c, _, f, p -> dao.findSeriesReleaseYears(c, f, p) }
    }

    func("findAllSharingLabels") {
      withFilters { dao.findAllSharingLabels(it) }
    }

    func("findAllSharingLabelsByLibraries") {
      withFilters { dao.findAllSharingLabelsByLibraries(setOf("L2"), it) }
    }

    func("findAllSharingLabelsByCollection") {
      withFilters { dao.findAllSharingLabelsByCollection("C2", it) }
    }

    func("findSharingLabels") {
      generic("AD") { c, s, f, p -> dao.findSharingLabels(c, s, f, p) }
    }

    func("toDomain@838") {
      case("book author") { dao.findAllAuthorsByName("Moore", null) }
    }

    func("toDomain@844") {
      case("aggregated author") { dao.findAllAuthorsByNameAndSeries("", "S3", null) }
    }
  }
}
