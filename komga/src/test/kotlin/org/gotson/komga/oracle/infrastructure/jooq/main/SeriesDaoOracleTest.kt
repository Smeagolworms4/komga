package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.Series
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.net.URL
import java.time.LocalDateTime

class SeriesDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.seriesDao

  /** ids sorted (the query has no ORDER BY), then the page metadata */
  private fun res(p: Page<Series>) = listOf(p.content.map { it.id }.sorted(), p.totalElements, p.number, p.size)

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("insert") {
      case("seed") {
        NzDaoSeed.seed(db)
        dao.count()
      }
      case("stored values") { db.rawQuery("select ID, NAME, URL, FILE_LAST_MODIFIED, LIBRARY_ID, BOOK_COUNT, DELETED_DATE, ONESHOT from SERIES order by ID") }
      case("duplicate id") { exceptionType { dao.insert(NzDaoSeed.allSeries[0]) } }
      case("unknown library") { exceptionType { dao.insert(Series("x", URL("file:/x"), LocalDateTime.of(2020, 1, 1, 0, 0), id = "SX", libraryId = "NOPE")) } }
    }

    func("findAll@42") {
      case("all") { dao.findAll().map { it.id }.sorted() }
    }

    func("findByIdOrNull") {
      case("existing") { dao.findByIdOrNull("S1") }
      case("deleted oneshot") { listOf(dao.findByIdOrNull("S4"), dao.findByIdOrNull("S5")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("toDomain") {
      case("dates converted to current time zone") { dao.findByIdOrNull("S2")!!.let { listOf(it.createdDate, it.lastModifiedDate, it.fileLastModified, it.path) } }
    }

    func("findAllByLibraryId") {
      case("L1") { dao.findAllByLibraryId("L1").map { it.id }.sorted() }
      case("unknown") { dao.findAllByLibraryId("NOPE") }
    }

    func("findAllNotDeletedByLibraryIdAndUrlNotIn") {
      case("empty list") { dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L2", emptyList()).map { it.id }.sorted() }
      case("some urls") { dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L2", listOf(URL("file:/lib2/Naruto"))).map { it.id } }
      case("url of another library") { dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L1", listOf(URL("file:/lib2/Naruto"))).map { it.id }.sorted() }
      case("large list") {
        dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L1", (1..2100).map { URL("file:/lib1/x$it") } + URL("file:/lib1/%C3%89lan") + URL("file:/lib1/Élan")).map { it.id }.sorted()
      }
    }

    func("findNotDeletedByLibraryIdAndUrlOrNull") {
      case("existing") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/lib1/Batman"))?.id }
      case("deleted") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L2", URL("file:/lib2/zorro")) }
      case("wrong library") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L2", URL("file:/lib1/Batman")) }
      case("unicode url") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L2", URL("file:/lib2/Ångström"))?.id }
    }

    func("findAllByTitleContaining") {
      case("ascii ignoring case") { dao.findAllByTitleContaining("AN").map { it.id }.sorted() }
      case("accented") { dao.findAllByTitleContaining("élan").map { it.id } }
      case("accented upper") { dao.findAllByTitleContaining("Élan").map { it.id } }
      case("japanese") { dao.findAllByTitleContaining("ナル").map { it.id } }
      case("percent") { dao.findAllByTitleContaining("%") }
      case("underscore") { dao.findAllByTitleContaining("_") }
      case("empty") { dao.findAllByTitleContaining("").map { it.id }.sorted() }
    }

    func("getLibraryId") {
      case("existing") { dao.getLibraryId("S3") }
      case("missing") { dao.getLibraryId("NOPE") }
    }

    func("findAllIdsByLibraryId") {
      case("L2") { dao.findAllIdsByLibraryId("L2").sorted() }
      case("unknown") { dao.findAllIdsByLibraryId("NOPE") }
    }

    func("countGroupedByLibraryId") {
      case("all") { dao.countGroupedByLibraryId().toSortedMap() }
    }

    func("findAll@115") {
      val unpaged = Pageable.unpaged()
      NzDaoSeed.seriesConditions.forEach { (name, c) ->
        case("admin: $name") { res(dao.findAll(c, SearchContext(NzDaoSeed.u1), unpaged)) }
      }
      case("anonymous with read status") { res(dao.findAll(NzDaoSeed.seriesConditions.first { it.first == "read status is unread" }.second, SearchContext.empty(), unpaged)) }
      case("age restricted user") { res(dao.findAll(null, SearchContext(NzDaoSeed.u2), unpaged)) }
      case("label excluded user in one library") { res(dao.findAll(null, SearchContext(NzDaoSeed.u3), unpaged)) }
      case("age excluded or label allowed user") { res(dao.findAll(null, SearchContext(NzDaoSeed.u4), unpaged)) }
      case("restricted user with condition") { res(dao.findAll(NzDaoSeed.seriesConditions.first { it.first == "library is not" }.second, SearchContext(NzDaoSeed.u4), unpaged)) }
      case("paged first page") { dao.findAll(null, SearchContext(NzDaoSeed.u1), PageRequest.of(0, 4)).let { listOf(it.content.size, it.totalElements, it.number, it.size, it.totalPages) } }
      case("paged last page") { dao.findAll(null, SearchContext(NzDaoSeed.u1), PageRequest.of(1, 4, Sort.by("name"))).let { listOf(it.content.size, it.totalElements, it.number, it.size, it.sort.isSorted) } }
      case("page after the end") { res(dao.findAll(null, SearchContext(NzDaoSeed.u1), PageRequest.of(5, 4))) }
    }

    func("update") {
      case("default updates modified time") {
        dao.update(NzDaoSeed.allSeries[1].copy(name = "Élan renamed", bookCount = 9, oneshot = true))
        stable(dao.findByIdOrNull("S2")).let { listOf(it, db.rawQuery("select NAME, BOOK_COUNT, ONESHOT from SERIES where ID = 'S2'")) }
      }
      case("without modified time") {
        dao.update(dao.findByIdOrNull("S6")!!.copy(deletedDate = LocalDateTime.of(2022, 2, 2, 2, 2), url = URL("file:/lib2/moved")), false)
        dao.findByIdOrNull("S6")
      }
      case("missing") {
        dao.update(NzDaoSeed.allSeries[0].copy(id = "NOPE"))
        dao.count()
      }
    }

    func("delete@193") {
      case("series without books") {
        dao.insert(Series("tmp", URL("file:/tmp"), LocalDateTime.of(2020, 1, 1, 0, 0), id = "TMP", libraryId = "L1"))
        dao.delete("TMP")
        dao.findByIdOrNull("TMP")
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
      case("series with books") { exceptionType { dao.delete("S1") } }
    }

    func("delete@202") {
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
      case("several with large list") {
        (1..3).forEach { dao.insert(Series("tmp$it", URL("file:/tmp$it"), LocalDateTime.of(2020, 1, 1, 0, 0), id = "TMP$it", libraryId = "L2")) }
        dao.delete((1..1200).map { "X$it" } + listOf("TMP1", "TMP3"))
        dao.findAllIdsByLibraryId("L2").sorted()
      }
    }

    func("deleteAll") {
      case("with books") { exceptionType { dao.deleteAll() } }
      case("after removing dependants") {
        listOf("READ_PROGRESS_SERIES", "READ_PROGRESS", "READLIST_BOOK", "THUMBNAIL_BOOK", "MEDIA_PAGE", "MEDIA", "BOOK_METADATA_AUTHOR", "BOOK_METADATA_TAG", "BOOK_METADATA", "BOOK", "COLLECTION_SERIES")
          .forEach { db.dsl.execute("delete from $it") }
        listOf("SERIES_METADATA_GENRE", "SERIES_METADATA_TAG", "SERIES_METADATA_SHARING", "SERIES_METADATA_LINK", "SERIES_METADATA_ALTERNATE_TITLE", "SERIES_METADATA")
          .forEach { db.dsl.execute("delete from $it") }
        listOf("BOOK_METADATA_AGGREGATION_AUTHOR", "BOOK_METADATA_AGGREGATION_TAG", "BOOK_METADATA_AGGREGATION").forEach { db.dsl.execute("delete from $it") }
        dao.deleteAll()
        listOf(dao.count(), dao.countGroupedByLibraryId())
      }
    }
  }
}
