package org.gotson.komga.oracle.domain.persistence

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.persistence.ReadListRepository
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.net.URL
import java.time.LocalDateTime

class ReadListRepositoryOracleTest : OracleTest() {
  private val db = OracleDb()
  private val repo: ReadListRepository = db.readListDao
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  override fun cases() {
    func("findAll") {
      case("empty") { repo.findAll(SearchContext.empty(), Pageable.unpaged()) }
      case("setup") {
        db.libraryDao.insert(Library("lib", URL("file:/lib"), id = "L1"))
        db.seriesDao.insert(Series("s1", URL("file:/lib/s1"), date, "S1", "L1", createdDate = date))
        db.bookDao.insert(Book("b1", URL("file:/lib/s1/b1.cbz"), date, id = "B1", seriesId = "S1", libraryId = "L1", createdDate = date))
        db.bookDao.insert(Book("b2", URL("file:/lib/s1/b2.cbz"), date, id = "B2", seriesId = "S1", libraryId = "L1", createdDate = date))
        repo.insert(ReadList("Zed", "summary", bookIds = sortedMapOf(2 to "B1", 1 to "B2"), id = "R1", createdDate = date))
        repo.insert(ReadList("arc", ordered = false, bookIds = sortedMapOf(0 to "B2"), id = "R2", createdDate = date))
        repo.count()
      }
      case("defaults, unpaged") { stable(repo.findAll(SearchContext.empty(), Pageable.unpaged())) }
      case("defaults, sorted by name") { stable(repo.findAll(SearchContext.empty(), PageRequest.of(0, 1, Sort.by("name")))) }
      case("defaults, last page") { stable(repo.findAll(SearchContext.empty(), PageRequest.of(1, 1, Sort.by("name")))) }
      case("anonymous context") { stable(repo.findAll(SearchContext.ofAnonymousUser(), Pageable.unpaged())) }
    }
  }
}
