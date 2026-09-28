package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.AlternateTitle
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URI
import java.net.URL
import java.time.LocalDateTime

class SeriesMetadataDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.seriesMetadataDao

  private fun series(id: String) = Series(id, URL("file:/lib1/$id"), LocalDateTime.of(2020, 1, 1, 0, 0), id = id, libraryId = "L1")

  private val big =
    SeriesMetadata(
      status = SeriesMetadata.Status.HIATUS,
      title = "  Big ünïcode  ",
      titleSort = " big ",
      summary = "line1\nline2",
      readingDirection = SeriesMetadata.ReadingDirection.WEBTOON,
      publisher = " Pub ",
      ageRating = 0,
      language = "EN-us",
      genres = (1..1200).map { "Genre $it" }.toSet() + setOf("", "  ", "ÉPIQUE"),
      tags = setOf("Tag B", "tag a", "tag a "),
      totalBookCount = 0,
      sharingLabels = (1..3).map { "Label$it" }.toSet(),
      links = (1..1100).map { WebLink("link $it", URI("https://example.org/$it?q=%C3%A9")) },
      alternateTitles = listOf(AlternateTitle("ja", "ビッグ"), AlternateTitle("", ""), AlternateTitle("ja", "ビッグ")),
      statusLock = true,
      titleLock = true,
      titleSortLock = true,
      summaryLock = true,
      readingDirectionLock = true,
      publisherLock = true,
      ageRatingLock = true,
      languageLock = true,
      genresLock = true,
      tagsLock = true,
      totalBookCountLock = true,
      sharingLabelsLock = true,
      linksLock = true,
      alternateTitlesLock = true,
      seriesId = "TMP",
    )

  private fun summary(m: SeriesMetadata) = listOf(m.title, m.titleSort, m.language, m.genres.size, m.genres.take(3), m.tags, m.sharingLabels, m.links.size, m.links.takeLast(1), m.alternateTitles)

  override fun cases() {
    func("count") {
      case("seeded") {
        NzDaoSeed.seed(db)
        dao.count()
      }
    }

    func("findById") {
      case("all fields") { dao.findById("S1") }
      case("japanese title") { dao.findById("S3") }
      case("missing") { exceptionType { dao.findById("NOPE") } }
    }

    func("findByIdOrNull") {
      case("no optional values") { dao.findByIdOrNull("S4") }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("findOne") {
      case("dates converted to current time zone") { dao.findById("S2").let { listOf(it.createdDate, it.lastModifiedDate) } }
    }

    func("findGenres") {
      case("several") { dao.findById("S3").genres }
      case("none") { dao.findById("S4").genres }
    }

    func("findTags") {
      case("several") { dao.findById("S1").tags }
    }

    func("findSharingLabels") {
      case("several") { dao.findById("S6").sharingLabels }
    }

    func("findLinks") {
      case("one") { dao.findById("S1").links }
      case("none") { dao.findById("S2").links }
    }

    func("findAlternateTitles") {
      case("duplicated labels") { dao.findById("S3").alternateTitles }
    }

    func("insert") {
      case("large collections over batch size") {
        db.seriesDao.insert(series("TMP"))
        dao.insert(big)
        summary(dao.findById("TMP"))
      }
      case("stored values") {
        db.rawQuery(
          "select STATUS, TITLE, TITLE_SORT, SUMMARY, READING_DIRECTION, PUBLISHER, AGE_RATING, LANGUAGE, TOTAL_BOOK_COUNT, STATUS_LOCK, ALTERNATE_TITLES_LOCK from SERIES_METADATA where SERIES_ID = 'TMP'",
        )
      }
      case("child rows") {
        listOf("SERIES_METADATA_GENRE", "SERIES_METADATA_TAG", "SERIES_METADATA_SHARING", "SERIES_METADATA_LINK", "SERIES_METADATA_ALTERNATE_TITLE").map {
          db.rawQuery("select count(*) from $it where SERIES_ID = 'TMP'")
        }
      }
      case("duplicate") { exceptionType { dao.insert(big) } }
      case("unknown series") { exceptionType { dao.insert(SeriesMetadata(title = "x", seriesId = "NOPE")) } }
    }

    func("insertGenres") {
      case("lower cased and not blank") { dao.findById("TMP").genres.filter { !it.startsWith("genre ") } }
    }

    func("insertTags") {
      case("deduplicated after trim") { dao.findById("TMP").tags }
    }

    func("insertSharingLabels") {
      case("lower cased") { dao.findById("TMP").sharingLabels }
    }

    func("insertLinks") {
      case("first links") { dao.findById("TMP").links.take(2) }
    }

    func("insertAlternateTitles") {
      case("duplicates kept") { dao.findById("TMP").alternateTitles }
    }

    func("update") {
      case("all fields") {
        dao.update(
          dao.findById("S2").copy(
            status = SeriesMetadata.Status.ONGOING,
            title = "Élan II",
            titleSort = "Elan II",
            summary = "new",
            readingDirection = SeriesMetadata.ReadingDirection.VERTICAL,
            publisher = "Casterman",
            ageRating = 10,
            language = "fr-BE",
            genres = setOf("comedy"),
            tags = emptySet(),
            totalBookCount = null,
            sharingLabels = setOf("kids"),
            links = listOf(WebLink("site", URI("http://casterman.com"))),
            alternateTitles = listOf(AlternateTitle("en", "Momentum")),
            summaryLock = true,
          ),
        )
        stable(dao.findById("S2"))
      }
      case("clear collections of large metadata") {
        dao.update(dao.findById("TMP").copy(genres = emptySet(), tags = emptySet(), sharingLabels = emptySet(), links = emptyList(), alternateTitles = emptyList(), readingDirection = null, ageRating = null))
        summary(dao.findById("TMP"))
      }
      case("missing") {
        val e = exceptionType { dao.update(SeriesMetadata(title = "x", genres = setOf("g"), seriesId = "NOPE")) }
        listOf(e, dao.findByIdOrNull("NOPE"), db.rawQuery("select count(*) from SERIES_METADATA_GENRE where SERIES_ID = 'NOPE'"))
      }
    }

    func("toDomain") {
      case("trimmed and normalized values") { dao.findById("TMP").let { listOf(it.title, it.titleSort, it.publisher, it.language, it.readingDirection, it.ageRating, it.totalBookCount) } }
    }

    func("delete@268") {
      case("existing") {
        dao.delete("TMP")
        listOf(dao.findByIdOrNull("TMP"), dao.count())
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("delete@278") {
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
      case("several with large list") {
        dao.delete((1..1500).map { "X$it" } + listOf("S1", "S3"))
        listOf(dao.count(), dao.findByIdOrNull("S1"), db.rawQuery("select count(*) from SERIES_METADATA_GENRE where SERIES_ID in ('S1', 'S3')"))
      }
    }
  }
}
