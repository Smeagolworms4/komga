package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class ReadProgressDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.readProgressDtoDao

  override fun cases() {
    func("findProgressV2BySeries") {
      case("series without books") {
        NzDaoSeed.seed(db)
        db.dsl.execute("insert into SERIES (ID, NAME, URL, FILE_LAST_MODIFIED, LIBRARY_ID) values ('S9', 'empty', 'file:/lib1/empty', '2020-01-01 00:00:00', 'L1')")
        exceptionType { dao.findProgressV2BySeries("S9", "U1") }
      }
      case("read then in progress") { dao.findProgressV2BySeries("S1", "U1") }
      case("decimal number sorts") { dao.findProgressV2BySeries("S3", "U1") }
      case("no progress for user") { dao.findProgressV2BySeries("S3", "U2") }
      case("all read") { dao.findProgressV2BySeries("S6", "U1") }
      case("unknown user") { dao.findProgressV2BySeries("S1", "NOPE") }
    }

    func("getSeriesBooksCount") {
      case("counts") { dao.findProgressV2BySeries("S2", "U1").let { listOf(it.booksCount, it.booksReadCount, it.booksUnreadCount, it.booksInProgressCount) } }
    }

    func("booksCountToDtoV2") {
      case("max number sort") { dao.findProgressV2BySeries("S3", "U3").let { listOf(it.lastReadContinuousNumberSort, it.maxNumberSort) } }
    }

    func("readProgressCondition") {
      case("other users progress ignored") { dao.findProgressV2BySeries("S1", "U2") }
    }

    func("lastRead") {
      case("first book unread") {
        db.readProgressDao.save(ReadProgress("B3", "U3", 1, true, LocalDateTime.of(2021, 1, 1, 0, 0)))
        dao.findProgressV2BySeries("S1", "U3").lastReadContinuousNumberSort
      }
      case("read list with gap") { dao.findProgressByReadList("RL2", "U1").lastReadContinuousIndex }
    }

    func("findProgressByReadList") {
      case("first book unread") { dao.findProgressByReadList("RL1", "U1") }
      case("continuous reads") { dao.findProgressByReadList("RL2", "U1") }
      case("read by other user") { dao.findProgressByReadList("RL1", "U3") }
      case("empty read list") { exceptionType { dao.findProgressByReadList("RL3", "U1") } }
      case("unknown user") { dao.findProgressByReadList("RL2", "NOPE") }
    }

    func("booksCountToDto") {
      case("counts") { dao.findProgressByReadList("RL2", "U2").let { listOf(it.booksCount, it.booksReadCount, it.booksUnreadCount, it.booksInProgressCount) } }
    }
  }
}
