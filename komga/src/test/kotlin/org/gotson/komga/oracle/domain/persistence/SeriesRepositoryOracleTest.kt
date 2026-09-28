package org.gotson.komga.oracle.domain.persistence

import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class SeriesRepositoryOracleTest : OracleTest() {
  private val db = OracleDb()
  private val repo: SeriesRepository = db.seriesDao
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val series = Series("Série", URL("file:/lib/s"), date, "S1", "L1", 3, createdDate = date)

  override fun cases() {
    func("update") {
      case("setup") {
        db.libraryDao.insert(Library("lib", URL("file:/lib"), id = "L1"))
        db.libraryDao.insert(Library("lib2", URL("file:/lib2"), id = "L2"))
        repo.insert(series)
        stable(repo.findByIdOrNull("S1"))
      }
      case("default updates modified time") {
        repo.update(series.copy(name = "renamed", bookCount = 5, oneshot = true))
        stable(repo.findByIdOrNull("S1"))
      }
      case("without modified time") {
        repo.update(repo.findByIdOrNull("S1")!!.copy(name = "again", deletedDate = LocalDateTime.of(2021, 6, 1, 12, 0), libraryId = "L2"), false)
        stable(repo.findByIdOrNull("S1"))
      }
      case("explicit true") {
        repo.update(series.copy(url = URL("file:/lib/s%20b")), true)
        stable(repo.findByIdOrNull("S1"))
      }
      case("missing series") {
        repo.update(series.copy(id = "NOPE"))
        repo.count()
      }
      case("unknown library") { exceptionType { repo.update(series.copy(libraryId = "L9")) } }
    }
  }
}
