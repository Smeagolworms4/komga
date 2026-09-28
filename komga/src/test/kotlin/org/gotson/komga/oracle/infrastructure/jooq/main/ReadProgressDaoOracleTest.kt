package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class ReadProgressDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.readProgressDao

  private fun keys(l: Collection<ReadProgress>) = l.map { "${it.bookId}/${it.userId}/${it.page}/${it.completed}" }.sorted()

  private fun series() = db.rawQuery("select SERIES_ID, USER_ID, READ_COUNT, IN_PROGRESS_COUNT, MOST_RECENT_READ_DATE from READ_PROGRESS_SERIES order by SERIES_ID, USER_ID")

  private val locator =
    R2Locator(
      href = "chapter1.xhtml",
      type = "application/xhtml+xml",
      title = "Chapitre ünï",
      locations = R2Locator.Location(fragments = listOf("#p1"), progression = 0.25f, position = 12, totalProgression = 0.1f),
      text = R2Locator.Text(before = "a", highlight = "b"),
      koboSpan = "kobo.1.1",
    )

  override fun cases() {
    func("findAll") {
      case("empty") { dao.findAll() }
      case("seeded") {
        NzDaoSeed.seed(db)
        keys(dao.findAll())
      }
    }

    func("findByBookIdAndUserIdOrNull") {
      case("all fields") { dao.findByBookIdAndUserIdOrNull("B2", "U1") }
      case("missing") { dao.findByBookIdAndUserIdOrNull("B2", "U2") }
    }

    func("toDomain") {
      case("dates converted to current time zone") { dao.findByBookIdAndUserIdOrNull("B1", "U2")!!.let { listOf(it.readDate, it.createdDate, it.lastModifiedDate) } }
      case("stored values") { db.rawQuery("select BOOK_ID, USER_ID, PAGE, COMPLETED, READ_DATE, DEVICE_ID, DEVICE_NAME, LOCATOR from READ_PROGRESS order by BOOK_ID, USER_ID") }
    }

    func("findAllByUserId") {
      case("U2") { keys(dao.findAllByUserId("U2")) }
      case("unknown") { dao.findAllByUserId("NOPE") }
    }

    func("findAllByBookId") {
      case("B1") { keys(dao.findAllByBookId("B1")) }
      case("unread") { dao.findAllByBookId("B9") }
    }

    func("findAllByBookIdsAndUserId") {
      case("some") { keys(dao.findAllByBookIdsAndUserId(listOf("B1", "B2", "B6", "NOPE"), "U1")) }
      case("empty") { dao.findAllByBookIdsAndUserId(emptyList(), "U1") }
    }

    func("aggregateSeriesProgress@185") {
      case("seeded aggregation") { series() }
    }

    func("save@79") {
      case("new progress with locator") {
        dao.save(ReadProgress("B3", "U1", 2, false, LocalDateTime.of(2021, 6, 15, 23, 30, 0, 123000000), "d2", "Tablette", locator))
        listOf(stable(dao.findByBookIdAndUserIdOrNull("B3", "U1")), series())
      }
      case("update existing") {
        dao.save(ReadProgress("B3", "U1", 3, true, LocalDateTime.of(2021, 7, 1, 8, 0), "d3", "Phone"))
        listOf(stable(dao.findByBookIdAndUserIdOrNull("B3", "U1")), series())
      }
      case("unknown book") { exceptionType { dao.save(ReadProgress("NOPE", "U1", 1, false, LocalDateTime.of(2021, 1, 1, 0, 0))) } }
      case("unknown user") { exceptionType { dao.save(ReadProgress("B1", "NOPE", 1, false, LocalDateTime.of(2021, 1, 1, 0, 0))) } }
    }

    func("toQuery") {
      case("locator is stored compressed") { db.rawQuery("select LOCATOR is not null, DEVICE_NAME from READ_PROGRESS where BOOK_ID = 'B3' order by USER_ID") }
    }

    func("save@85") {
      case("several users") {
        dao.save(
          listOf(
            ReadProgress("B5", "U2", 1, true, LocalDateTime.of(2021, 8, 1, 0, 0)),
            ReadProgress("B6", "U3", 1, false, LocalDateTime.of(2021, 8, 2, 0, 0), locator = locator),
            ReadProgress("B6", "U2", 4, true, LocalDateTime.of(2021, 8, 3, 0, 0)),
          ),
        )
        listOf(keys(dao.findAll()), series())
      }
      case("empty") {
        dao.save(emptyList())
        dao.findAll().size
      }
      case("locator read back") { dao.findByBookIdAndUserIdOrNull("B6", "U3")!!.locator }
    }

    func("aggregateSeriesProgress@197") {
      case("after saves") { series() }
    }

    func("delete") {
      case("existing") {
        dao.delete("B1", "U1")
        listOf(keys(dao.findAllByUserId("U1")), series())
      }
      case("missing") {
        dao.delete("B9", "U1")
        series()
      }
    }

    func("deleteByBookId") {
      case("existing") {
        dao.deleteByBookId("B6")
        listOf(keys(dao.findAll()), series())
      }
    }

    func("deleteByBookIds") {
      case("empty") {
        dao.deleteByBookIds(emptyList())
        dao.findAll().size
      }
      case("large list") {
        dao.deleteByBookIds((1..1300).map { "X$it" } + listOf("B4", "B7"))
        listOf(keys(dao.findAll()), series())
      }
    }

    func("deleteByBookIdsAndUserId") {
      case("only for user") {
        dao.save(ReadProgress("B2", "U2", 1, false, LocalDateTime.of(2021, 9, 1, 0, 0)))
        dao.deleteByBookIdsAndUserId(listOf("B2", "B3"), "U1")
        listOf(keys(dao.findAll()), series())
      }
    }

    func("deleteBySeriesIds") {
      case("only series aggregation") {
        dao.deleteBySeriesIds(listOf("S1", "NOPE"))
        listOf(keys(dao.findAll()), series())
      }
    }

    func("deleteByUserId") {
      case("existing") {
        dao.deleteByUserId("U2")
        listOf(keys(dao.findAll()), series())
      }
    }

    func("deleteAll") {
      case("all") {
        dao.deleteAll()
        listOf(dao.findAll(), series())
      }
    }
  }
}
