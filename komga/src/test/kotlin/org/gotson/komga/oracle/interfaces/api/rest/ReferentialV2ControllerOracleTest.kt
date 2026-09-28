package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.domain.model.FilterTags
import org.gotson.komga.interfaces.api.rest.ReferentialV2Controller
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class ReferentialV2ControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val c = ReferentialV2Controller(db.referentialDao)
  private val admin = principal(RestSamples.admin)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val noAdult = principal(RestSamples.noAdult)
  private val p20 = PageRequest.of(0, 20)
  private val p1 = PageRequest.of(1, 1)

  override fun cases() {
    func("getAuthors") {
      case("empty database") { c.getAuthors(admin, null, null, page = p20) }
      case("admin") {
        RestSamples.seed(db)
        c.getAuthors(admin, null, null, page = p20)
      }
      case("search and role") { c.getAuthors(admin, "author", "writer", page = p20) }
      case("unpaged") { c.getAuthors(admin, null, null, unpaged = true, page = p1) }
      case("second page of 1") { c.getAuthors(admin, null, null, page = p1) }
      case("sort ignored") { c.getAuthors(admin, null, null, page = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("name")))) }
      case("library L2") { c.getAuthors(admin, null, null, libraryIds = setOf("L2"), page = p20) }
      case("collection C2") { c.getAuthors(admin, null, null, collectionIds = setOf("C2"), page = p20) }
      case("series S2") { c.getAuthors(admin, null, null, seriesIds = setOf("S2"), page = p20) }
      case("read list R1") { c.getAuthors(admin, null, null, readListIds = setOf("R1"), page = p20) }
      case("library wins over series") { c.getAuthors(admin, null, null, libraryIds = setOf("L2"), seriesIds = setOf("S1"), page = p20) }
      case("library restricted") { c.getAuthors(l1, null, null, page = p20) }
      case("age restricted") { c.getAuthors(kids, null, null, page = p20) }
      case("label excluded") { c.getAuthors(noAdult, null, null, page = p20) }
    }
    func("getAuthorsRoles") {
      case("admin") { c.getAuthorsRoles(admin, page = p20) }
      case("series S3") { c.getAuthorsRoles(admin, seriesIds = setOf("S3"), page = p20) }
      case("unpaged") { c.getAuthorsRoles(kids, unpaged = true, page = p1) }
      case("page 1") { c.getAuthorsRoles(admin, page = p1) }
    }
    func("getAuthorsNames") {
      case("admin") { c.getAuthorsNames(admin, null, null, page = p20) }
      case("search") { c.getAuthorsNames(admin, "pen b", null, page = p20) }
      case("role") { c.getAuthorsNames(admin, null, "penciller", readListIds = setOf("R1"), page = p20) }
      case("restricted") { c.getAuthorsNames(l1, null, null, page = p1) }
    }
    func("getGenres") {
      case("admin") { c.getGenres(admin, null, page = p20) }
      case("search") { c.getGenres(admin, "DRA", page = p20) }
      case("library L2") { c.getGenres(admin, null, libraryIds = setOf("L2"), page = p20) }
      case("collection C1") { c.getGenres(admin, null, collectionIds = setOf("C1"), page = p20) }
      case("kids") { c.getGenres(kids, null, page = p20) }
      case("unpaged") { c.getGenres(noAdult, null, unpaged = true, page = p1) }
    }
    func("getSharingLabels") {
      case("admin") { c.getSharingLabels(admin, null, page = p20) }
      case("search") { c.getSharingLabels(admin, "ki", page = p20) }
      case("collection C1") { c.getSharingLabels(admin, null, collectionIds = setOf("C1"), page = p1) }
      case("restricted") { c.getSharingLabels(noAdult, null, page = p20) }
    }
    func("getLanguages") {
      case("admin") { c.getLanguages(admin, null, page = p20) }
      case("search") { c.getLanguages(admin, "f", page = p20) }
      case("library L1") { c.getLanguages(admin, null, libraryIds = setOf("L1"), page = p20) }
      case("kids unpaged") { c.getLanguages(kids, null, unpaged = true, page = p20) }
    }
    func("getPublishers") {
      case("admin") { c.getPublishers(admin, null, page = p20) }
      case("search") { c.getPublishers(admin, "pub2", page = p20) }
      case("collection C2") { c.getPublishers(admin, null, collectionIds = setOf("C2"), page = p20) }
      case("l1") { c.getPublishers(l1, null, page = p1) }
    }
    func("getTags") {
      case("admin both") { c.getTags(admin, null, page = p20) }
      case("series tags only") { c.getTags(admin, null, includeTags = FilterTags.SERIES, page = p20) }
      case("book tags only") { c.getTags(admin, null, includeTags = FilterTags.BOOK, page = p20) }
      case("search") { c.getTags(admin, "BT", page = p20) }
      case("series S3") { c.getTags(admin, null, seriesIds = setOf("S3"), page = p20) }
      case("read list R1, book") { c.getTags(admin, null, readListIds = setOf("R1"), includeTags = FilterTags.BOOK, page = p20) }
      case("library L1, series") { c.getTags(admin, null, libraryIds = setOf("L1"), includeTags = FilterTags.SERIES, page = p20) }
      case("collection C1") { c.getTags(admin, null, collectionIds = setOf("C1"), page = p20) }
      case("kids") { c.getTags(kids, null, page = p20) }
      case("unpaged page 1") { c.getTags(admin, null, unpaged = true, page = p1) }
    }
    func("getSeriesReleaseYears") {
      case("admin") { c.getSeriesReleaseYears(admin, page = p20) }
      case("library L2") { c.getSeriesReleaseYears(admin, libraryIds = setOf("L2"), page = p20) }
      case("collection C1") { c.getSeriesReleaseYears(admin, collectionIds = setOf("C1"), page = p1) }
      case("kids") { c.getSeriesReleaseYears(kids, page = p20) }
    }
    func("getAgeRatings") {
      case("admin") { c.getAgeRatings(admin, page = p20) }
      case("library L1") { c.getAgeRatings(admin, libraryIds = setOf("L1"), page = p20) }
      case("collection C2") { c.getAgeRatings(admin, collectionIds = setOf("C2"), page = p20) }
      case("restricted unpaged") { c.getAgeRatings(noAdult, unpaged = true, page = p20) }
    }
  }
}
