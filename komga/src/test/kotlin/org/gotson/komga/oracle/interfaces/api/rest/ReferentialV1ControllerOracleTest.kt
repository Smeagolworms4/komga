package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.ReferentialV1Controller
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal

@Suppress("DEPRECATION")
class ReferentialV1ControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val c = ReferentialV1Controller(db.referentialDao)
  private val admin = principal(RestSamples.admin)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)

  override fun cases() {
    func("getAuthorsNames") {
      case("empty database") { c.getAuthorsNames(admin, "") }
      case("admin, all") {
        RestSamples.seed(db)
        c.getAuthorsNames(admin, "")
      }
      case("search") { c.getAuthorsNames(admin, "pen") }
      case("search accent and case") { c.getAuthorsNames(admin, "AUTHOR s") }
      case("library restricted") { c.getAuthorsNames(l1, "") }
      case("age restricted user") { c.getAuthorsNames(kids, "") }
    }
    func("getAuthorsRoles") {
      case("admin") { c.getAuthorsRoles(admin) }
      case("library restricted") { c.getAuthorsRoles(l1) }
    }
    func("getGenres") {
      case("admin") { c.getGenres(admin, emptySet(), null) }
      case("library L2") { c.getGenres(admin, setOf("L2"), null) }
      case("library L1 and unknown") { c.getGenres(admin, setOf("L1", "LX"), null) }
      case("collection C2") { c.getGenres(admin, emptySet(), "C2") }
      case("collection C1, library restricted") { c.getGenres(l1, emptySet(), "C1") }
      case("library restricted asks L2") { c.getGenres(l1, setOf("L2"), null) }
    }
    func("getSharingLabels") {
      case("admin") { c.getSharingLabels(admin, emptySet(), null) }
      case("library L1") { c.getSharingLabels(admin, setOf("L1"), null) }
      case("collection C1") { c.getSharingLabels(admin, emptySet(), "C1") }
      case("library restricted") { c.getSharingLabels(l1, emptySet(), null) }
    }
    func("getTags") {
      case("admin") { c.getTags(admin, emptySet(), null) }
      case("library L2") { c.getTags(admin, setOf("L2"), null) }
      case("collection C1") { c.getTags(admin, emptySet(), "C1") }
      case("library restricted") { c.getTags(l1, emptySet(), null) }
    }
    func("getBookTags") {
      case("admin") { c.getBookTags(admin, null, null, emptySet()) }
      case("series S1") { c.getBookTags(admin, "S1", null, emptySet()) }
      case("read list R1") { c.getBookTags(admin, null, "R1", emptySet()) }
      case("library L2") { c.getBookTags(admin, null, null, setOf("L2")) }
      case("library restricted asks L2") { c.getBookTags(l1, null, null, setOf("L2")) }
      case("series takes precedence") { c.getBookTags(admin, "S3", "R1", setOf("L1")) }
    }
    func("getSeriesTags") {
      case("admin") { c.getSeriesTags(admin, null, null) }
      case("library L1") { c.getSeriesTags(admin, "L1", null) }
      case("collection C2") { c.getSeriesTags(admin, null, "C2") }
      case("library restricted") { c.getSeriesTags(l1, null, null) }
    }
    func("getLanguages") {
      case("admin") { c.getLanguages(admin, emptySet(), null) }
      case("library L2") { c.getLanguages(admin, setOf("L2"), null) }
      case("collection C1") { c.getLanguages(admin, emptySet(), "C1") }
      case("library restricted") { c.getLanguages(l1, emptySet(), null) }
    }
    func("getPublishers") {
      case("admin") { c.getPublishers(admin, emptySet(), null) }
      case("library L1") { c.getPublishers(admin, setOf("L1"), null) }
      case("collection C2") { c.getPublishers(admin, emptySet(), "C2") }
      case("library restricted") { c.getPublishers(l1, emptySet(), null) }
    }
    func("getAgeRatings") {
      case("admin, null is None") { c.getAgeRatings(admin, emptySet(), null) }
      case("library L1") { c.getAgeRatings(admin, setOf("L1"), null) }
      case("collection C2") { c.getAgeRatings(admin, emptySet(), "C2") }
      case("library restricted") { c.getAgeRatings(l1, emptySet(), null) }
    }
    func("getSeriesReleaseDates") {
      case("admin") { c.getSeriesReleaseDates(admin, emptySet(), null) }
      case("library L2") { c.getSeriesReleaseDates(admin, setOf("L2"), null) }
      case("collection C1") { c.getSeriesReleaseDates(admin, emptySet(), "C1") }
      case("library restricted") { c.getSeriesReleaseDates(l1, emptySet(), null) }
    }
  }
}
