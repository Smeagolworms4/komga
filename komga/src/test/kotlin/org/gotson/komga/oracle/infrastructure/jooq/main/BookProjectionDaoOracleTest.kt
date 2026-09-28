package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.BookProjection
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.book
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series

class BookProjectionDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.bookProjectionDao

  private fun rows() = db.rawQuery("select BOOK_ID, PROFILE, FILE_SIZE, CREATED_DATE is not null, LAST_MODIFIED_DATE is not null from BOOK_PROJECTION order by BOOK_ID, PROFILE")

  override fun cases() {
    func("save") {
      case("new projection") {
        db.libraryDao.insert(library("L1"))
        db.seriesDao.insert(series("S1", "L1"))
        db.bookDao.insert((1..5).map { book("B$it", "S1", "L1") })
        dao.save(BookProjection("B1", "epub-kepub", 1234))
        rows()
      }
      case("same key keeps the stored file size") {
        dao.save(BookProjection("B1", "epub-kepub", 999))
        rows()
      }
      case("other profile") {
        dao.save(BookProjection("B1", "Ünïcode 漫画", 0))
        rows()
      }
      case("large file size") {
        dao.save(BookProjection("B2", "epub-kepub", 5_000_000_000L))
        rows()
      }
      case("negative file size") {
        dao.save(BookProjection("B3", "", -1))
        rows()
      }
      case("unknown book") { exceptionType { dao.save(BookProjection("NOPE", "epub-kepub", 1)) } }
    }

    func("delete@35") {
      case("existing") {
        dao.delete("B3")
        rows()
      }
      case("missing") {
        dao.delete("NOPE")
        rows().size
      }
      case("all profiles of the book") {
        dao.delete("B1")
        rows()
      }
    }

    func("delete@40") {
      case("empty") {
        dao.save(BookProjection("B1", "a", 1))
        dao.save(BookProjection("B4", "a", 4))
        dao.save(BookProjection("B5", "a", 5))
        dao.delete(emptyList())
        rows().size
      }
      case("several with missing") {
        dao.delete(listOf("B2", "NOPE", "B2"))
        rows()
      }
      case("more than batch size") {
        dao.delete((1..2500).map { "X$it" } + listOf("B4") + (2501..3000).map { "X$it" })
        rows()
      }
      case("set") {
        dao.delete(setOf("B1", "B5"))
        rows()
      }
    }
  }
}
