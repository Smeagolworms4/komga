package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadataAggregation
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import java.time.LocalDate

class BookMetadataAggregationDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.bookMetadataAggregationDao

  private val full =
    BookMetadataAggregation(
      authors =
        listOf(
          Author("  Jean Dupont ", " WRITER "),
          Author("Émilie Ünïcode", "penciller"),
          Author("漫画家", "writer"),
          Author("Jean Dupont", "writer"),
          Author("", ""),
        ),
      tags = setOf("zeta", "Alpha", "ünïcode", "漫画", ""),
      releaseDate = LocalDate.of(1999, 12, 31),
      summary = "Un résumé\nsur deux lignes",
      summaryNumber = "3",
      seriesId = "S2",
    )

  private fun rows(table: String) = db.rawQuery("select * from $table order by SERIES_ID, rowid")

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("insert") {
      case("defaults") {
        db.libraryDao.insert(library("L1"))
        (1..6).forEach { db.seriesDao.insert(series("S$it", "L1")) }
        db.seriesDao.insert(series("BIG", "L1"))
        dao.insert(BookMetadataAggregation(seriesId = "S1"))
        stable(dao.findById("S1"))
      }
      case("all fields") {
        dao.insert(full)
        stable(dao.findById("S2"))
      }
      case("duplicate series") { exceptionType { dao.insert(BookMetadataAggregation(seriesId = "S1")) } }
      case("unknown series") { exceptionType { dao.insert(BookMetadataAggregation(seriesId = "NOPE")) } }
      case("more authors and tags than batch size") {
        dao.insert(
          BookMetadataAggregation(
            authors = (1..1234).map { Author("author $it", if (it % 2 == 0) "writer" else "colorist") },
            tags = (1..1100).map { "tag $it" }.toSet(),
            seriesId = "BIG",
          ),
        )
        dao.findById("BIG").let { listOf(it.authors.size, it.tags.size, it.authors.last().name, it.tags.first()) }
      }
      case("stored values") {
        sql(db, "update BOOK_METADATA_AGGREGATION set CREATED_DATE = '2020-05-01 10:00:00', LAST_MODIFIED_DATE = '2020-05-02 11:30:00'")
        db.rawQuery("select * from BOOK_METADATA_AGGREGATION where SERIES_ID <> 'BIG' order by SERIES_ID")
      }
    }

    func("insertAuthors") {
      case("rows") { rows("BOOK_METADATA_AGGREGATION_AUTHOR where SERIES_ID <> 'BIG'") }
      case("no author") { db.rawQuery("select count(*) from BOOK_METADATA_AGGREGATION_AUTHOR where SERIES_ID = 'S1'") }
    }

    func("insertTags") {
      case("rows") { rows("BOOK_METADATA_AGGREGATION_TAG where SERIES_ID <> 'BIG'") }
    }

    func("findById") {
      case("existing") { dao.findById("S2") }
      case("missing") { dao.findById("NOPE") }
    }

    func("findByIdOrNull") {
      case("existing") { dao.findByIdOrNull("S1") }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("series without aggregation") { dao.findByIdOrNull("S3") }
      case("empty id") { dao.findByIdOrNull("") }
    }

    func("findOne") {
      case("authors keep insertion order and duplicates") { dao.findById("S2").authors.map { listOf(it.name, it.role) } }
      case("authors orphan rows are ignored") {
        sql(db, "insert into BOOK_METADATA_AGGREGATION_AUTHOR (NAME, ROLE, SERIES_ID) values ('orphan', 'writer', 'S3')")
        dao.findByIdOrNull("S3")
      }
    }

    func("findTags") {
      case("tags") { dao.findById("S2").tags }
      case("no tag") { dao.findById("S1").tags }
      case("orphan tags are read") {
        sql(db, "insert into BOOK_METADATA_AGGREGATION_TAG (TAG, SERIES_ID) values ('Mixed CASE', 'S1')")
        dao.findById("S1").tags
      }
    }

    func("toDomain@147") {
      case("dates in current time zone") { dao.findById("S2").let { listOf(it.createdDate, it.lastModifiedDate, it.releaseDate) } }
    }

    func("toDomain@161") {
      case("author trimmed and lowercased") { dao.findById("S2").authors.first().let { listOf(it.name, it.role) } }
      case("stored raw author") {
        sql(db, "insert into BOOK_METADATA_AGGREGATION_AUTHOR (NAME, ROLE, SERIES_ID) values ('  Raw  ', ' INKER ', 'S1')")
        dao.findById("S1").authors.map { listOf(it.name, it.role) }
      }
    }

    func("update") {
      case("all fields") {
        dao.update(
          full.copy(
            authors = listOf(Author("Nouvel Auteur", "Editor")),
            tags = setOf("new"),
            releaseDate = null,
            summary = "",
            summaryNumber = "",
          ),
        )
        stable(dao.findById("S2"))
      }
      case("remove authors and tags") {
        dao.update(dao.findById("S2").copy(authors = emptyList(), tags = emptySet(), releaseDate = LocalDate.of(2024, 2, 29)))
        stable(dao.findById("S2"))
      }
      case("missing aggregation") {
        dao.update(BookMetadataAggregation(seriesId = "S4", summary = "x"))
        dao.findByIdOrNull("S4")
      }
      case("missing aggregation with authors") {
        exceptionType { dao.update(BookMetadataAggregation(authors = listOf(Author("a", "b")), seriesId = "NOPE")) }
      }
      case("stored values") { db.rawQuery("select SERIES_ID, RELEASE_DATE, SUMMARY, SUMMARY_NUMBER, CREATED_DATE, LAST_MODIFIED_DATE > '2021' from BOOK_METADATA_AGGREGATION where SERIES_ID = 'S2'") }
    }

    func("delete@130") {
      case("existing") {
        dao.insert(full.copy(seriesId = "S5"))
        dao.delete("S5")
        listOf(dao.findByIdOrNull("S5"), db.rawQuery("select count(*) from BOOK_METADATA_AGGREGATION_AUTHOR where SERIES_ID = 'S5'"))
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("delete@137") {
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
      case("several") {
        dao.insert(full.copy(seriesId = "S5"))
        dao.insert(full.copy(seriesId = "S6"))
        dao.delete(listOf("S5", "NOPE", "S6", "S5"))
        listOf(dao.count(), db.rawQuery("select SERIES_ID, count(*) from BOOK_METADATA_AGGREGATION_TAG group by SERIES_ID order by SERIES_ID"))
      }
      case("more than batch size") {
        dao.delete((1..1500).map { "X$it" } + listOf("BIG", "S1"))
        listOf(
          dao.count(),
          db.rawQuery("select SERIES_ID, count(*) from BOOK_METADATA_AGGREGATION_AUTHOR group by SERIES_ID order by SERIES_ID"),
          db.rawQuery("select SERIES_ID, count(*) from BOOK_METADATA_AGGREGATION_TAG group by SERIES_ID order by SERIES_ID"),
        )
      }
    }

    func("count") {
      case("after deletions") { dao.count() }
    }
  }
}
