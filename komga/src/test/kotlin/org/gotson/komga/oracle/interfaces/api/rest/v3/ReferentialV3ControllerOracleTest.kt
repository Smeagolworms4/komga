package org.gotson.komga.oracle.interfaces.api.rest.v3

import org.gotson.komga.domain.model.NotableEntity
import org.gotson.komga.interfaces.api.rest.v3.ReferentialV3Controller
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestSamples
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class ReferentialV3ControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val c = ReferentialV3Controller(db.referentialDao)
  private val admin = principal(RestSamples.admin)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val noAdult = principal(RestSamples.noAdult)
  private val p20 = PageRequest.of(0, 20)
  private val p1 = PageRequest.of(1, 1)

  override fun cases() {
    func("getNotableEntities") {
      case("empty database") { c.getNotableEntities(admin, null, null, page = p20) }
      case("admin") {
        RestSamples.seed(db)
        mapOf(
          "B1" to listOf(NotableEntity("team", "Avengers"), NotableEntity("location", "New York")),
          "B2" to listOf(NotableEntity("character", "Thor"), NotableEntity("team", "Avengers")),
          "B3" to listOf(NotableEntity("character", "Beta Girl"), NotableEntity("location", "Paris")),
          "B4" to listOf(NotableEntity("character", "One")),
        ).forEach { (id, ne) -> db.bookMetadataDao.update(db.bookMetadataDao.findById(id).copy(notableEntities = ne)) }
        c.getNotableEntities(admin, null, null, page = p20)
      }
      case("search and kind") { c.getNotableEntities(admin, "e", "character", page = p20) }
      case("unpaged") { c.getNotableEntities(admin, null, null, unpaged = true, page = p1) }
      case("second page of 1") { c.getNotableEntities(admin, null, null, page = p1) }
      case("sort ignored") { c.getNotableEntities(admin, null, null, page = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("name")))) }
      case("library L2") { c.getNotableEntities(admin, null, null, libraryIds = setOf("L2"), page = p20) }
      case("collection C2") { c.getNotableEntities(admin, null, null, collectionIds = setOf("C2"), page = p20) }
      case("series S2") { c.getNotableEntities(admin, null, null, seriesIds = setOf("S2"), page = p20) }
      case("read list R1") { c.getNotableEntities(admin, null, null, readListIds = setOf("R1"), page = p20) }
      case("library wins over series") { c.getNotableEntities(admin, null, null, libraryIds = setOf("L2"), seriesIds = setOf("S1"), page = p20) }
      case("library restricted") { c.getNotableEntities(l1, null, null, page = p20) }
      case("age restricted") { c.getNotableEntities(kids, null, null, page = p20) }
      case("label excluded") { c.getNotableEntities(noAdult, null, null, page = p20) }
    }
    func("getNotableEntitiesKinds") {
      case("admin") { c.getNotableEntitiesKinds(admin, page = p20) }
      case("series S2") { c.getNotableEntitiesKinds(admin, seriesIds = setOf("S2"), page = p20) }
      case("unpaged") { c.getNotableEntitiesKinds(kids, unpaged = true, page = p1) }
      case("page 1") { c.getNotableEntitiesKinds(admin, page = p1) }
    }
    func("getNotableEntitiesNames") {
      case("admin") { c.getNotableEntitiesNames(admin, null, null, page = p20) }
      case("search") { c.getNotableEntitiesNames(admin, "ar", null, page = p20) }
      case("kind") { c.getNotableEntitiesNames(admin, null, "team", readListIds = setOf("R1"), page = p20) }
      case("restricted") { c.getNotableEntitiesNames(l1, null, null, page = p1) }
    }
  }
}
