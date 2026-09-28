package org.gotson.komga.oracle.interfaces.api.persistence

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.interfaces.api.persistence.BookDtoRepository
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class BookDtoRepositoryOracleTest : OracleTest() {
  private val db = OracleDb()
  private val repo: BookDtoRepository = db.bookDtoDao

  private fun ids(p: org.springframework.data.domain.Page<org.gotson.komga.interfaces.api.rest.dto.BookDto>) = listOf(p.content.map { it.id }, p.totalElements, p.number, p.size)

  override fun cases() {
    func("findAllOnDeck") {
      case("setup") { InterfacesData.setup(db) }
      case("admin, default restrictions") { ids(repo.findAllOnDeck("U1", null, Pageable.unpaged())) }
      case("admin, library filter") { ids(repo.findAllOnDeck("U1", listOf("L2"), PageRequest.of(0, 10))) }
      case("admin, restricted") { ids(repo.findAllOnDeck("U1", null, Pageable.unpaged(), ContentRestrictions(AgeRestriction(10, AllowExclude.ALLOW_ONLY)))) }
      case("other user") { ids(repo.findAllOnDeck("U2", null, Pageable.unpaged())) }
    }
    func("findNextInReadListOrNull") {
      case("next of first") { db.readListDao.findByIdOrNull("R1", SearchContext.empty())?.let { repo.findNextInReadListOrNull(it, "B2", SearchContext(InterfacesData.admin))?.id } }
      case("next of last") { db.readListDao.findByIdOrNull("R1", SearchContext.empty())?.let { repo.findNextInReadListOrNull(it, "B4", SearchContext(InterfacesData.admin))?.id } }
      case("limited user") { db.readListDao.findByIdOrNull("R1", SearchContext.empty())?.let { repo.findNextInReadListOrNull(it, "B2", SearchContext(InterfacesData.limited))?.id } }
      case("not in read list") { db.readListDao.findByIdOrNull("R1", SearchContext.empty())?.let { repo.findNextInReadListOrNull(it, "B1", SearchContext(InterfacesData.admin))?.id } }
    }
  }
}
