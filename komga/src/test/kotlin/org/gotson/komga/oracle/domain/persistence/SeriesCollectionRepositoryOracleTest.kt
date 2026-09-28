package org.gotson.komga.oracle.domain.persistence

import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.persistence.SeriesCollectionRepository
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.net.URL
import java.time.LocalDateTime

class SeriesCollectionRepositoryOracleTest : OracleTest() {
  private val db = OracleDb()
  private val repo: SeriesCollectionRepository = db.seriesCollectionDao
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  override fun cases() {
    func("findAll") {
      case("empty") { repo.findAll(SearchContext.empty(), Pageable.unpaged()) }
      case("setup") {
        db.libraryDao.insert(Library("lib", URL("file:/lib"), id = "L1"))
        db.seriesDao.insert(Series("s1", URL("file:/lib/s1"), date, "S1", "L1", createdDate = date))
        db.seriesDao.insert(Series("s2", URL("file:/lib/s2"), date, "S2", "L1", createdDate = date))
        repo.insert(SeriesCollection("Beta", seriesIds = listOf("S2", "S1"), id = "C1", createdDate = date))
        repo.insert(SeriesCollection("alpha", ordered = true, seriesIds = listOf("S1"), id = "C2", createdDate = date))
        repo.insert(SeriesCollection("Émpty", id = "C3", createdDate = date))
        repo.count()
      }
      case("defaults, unpaged") { stable(repo.findAll(SearchContext.empty(), Pageable.unpaged())) }
      case("defaults, sorted by name") { stable(repo.findAll(SearchContext.empty(), PageRequest.of(0, 2, Sort.by("name")))) }
      case("defaults, second page") { stable(repo.findAll(SearchContext.empty(), PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "name")))) }
      case("anonymous context") { stable(repo.findAll(SearchContext.ofAnonymousUser(), Pageable.unpaged())) }
    }
  }
}
