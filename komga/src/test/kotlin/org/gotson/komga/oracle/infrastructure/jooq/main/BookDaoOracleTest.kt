package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.book
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.transactional
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.user
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.net.URL
import java.time.LocalDateTime

class BookDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.bookDao

  private fun mid(it: Int) = "M" + "$it".padStart(4, '0')

  private val deletedDate = LocalDateTime.of(2021, 1, 1, 0, 0)
  private val unicodeUrl = URL("file:/libraries/L1/S2/Ünï côde 漫画.cbz")

  private fun ids(c: Collection<Book>) = c.map { it.id }.sorted()

  private fun page(
    condition: SearchCondition.Book?,
    context: SearchContext = SearchContext.empty(),
    pageable: Pageable = Pageable.unpaged(),
  ) = dao.findAll(condition, context, pageable).let { p -> listOf(p.content.map { it.id }, p.totalElements, p.number, p.size) }

  private fun meta(
    bookId: String,
    numberSort: Float,
    title: String = "title $bookId",
  ) = BookMetadata(title = title, number = "$bookId", numberSort = numberSort, bookId = bookId)

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("findAll@114") {
      case("empty") { dao.findAll() }
    }

    func("insert@313") {
      case("single book") {
        db.libraryDao.insert(library("L1"))
        db.libraryDao.insert(library("L2"))
        db.libraryDao.insert(library("L3"))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesDao.insert(series("S2", "L1"))
        db.seriesDao.insert(series("S3", "L2"))
        db.komgaUserDao.insert(user("U1"))
        db.komgaUserDao.insert(user("U2"))
        dao.insert(book("B1", "S1", "L1", number = 1, fileSize = 100))
        stable(dao.findByIdOrNull("B1"))
      }
      case("all fields") {
        dao.insert(
          Book(
            name = "Ünïcode 漫画 book",
            url = unicodeUrl,
            fileLastModified = LocalDateTime.of(2021, 3, 28, 2, 30, 15, 123456789),
            fileSize = 5_000_000_000L,
            fileHash = "abcdef",
            fileHashKoreader = "kor1",
            number = -3,
            id = "BU",
            seriesId = "S2",
            libraryId = "L1",
            deletedDate = null,
            oneshot = true,
          ),
        )
        stable(dao.findByIdOrNull("BU"))
      }
      case("duplicate id") { exceptionType { dao.insert(book("B1", "S1", "L1")) } }
      case("unknown series") { exceptionType { dao.insert(book("BN", "NOPE", "L1")) } }
      case("series of another library") {
        dao.insert(book("BW", "S3", "L1", number = 99))
        dao.getLibraryIdOrNull("BW")
      }
    }

    func("insert@318") {
      case("empty") {
        dao.insert(emptyList())
        dao.count()
      }
      case("several") {
        dao.insert(
          listOf(
            book("B2", "S1", "L1", number = 2, fileSize = 200),
            book("B3", "S1", "L1", number = 3, fileSize = 300, ext = "cbr"),
            book("B4", "S1", "L1", number = 4, fileSize = 400, ext = "pdf"),
            book("B5", "S1", "L1", number = 5, fileSize = 500),
            book("B6", "S1", "L1", number = 6, fileSize = 600),
            book("BX", "S1", "L1", number = 8),
            book("B7", "S2", "L1", number = 7, fileSize = 500),
            book("C1", "S3", "L2", number = 11, fileSize = 1100),
            book("C2", "S3", "L2", number = 12, fileSize = 1200, ext = "CBZ"),
            book("D1", "S1", "L1", number = 20, fileSize = 500).copy(deletedDate = deletedDate),
            book("D2", "S2", "L1", number = 21, fileSize = 500).copy(deletedDate = deletedDate, url = unicodeUrl),
          ),
        )
        dao.count()
      }
      case("more than batch size") {
        dao.insert((1..1100).map { book(mid(it), "S2", "L2", number = 1000 + it, fileSize = it.toLong()) })
        listOf(dao.count(), dao.findAllIdsBySeriesId("S2").size)
      }
      case("stored values") {
        // fixed, distinct dates
        sql(
          db,
          "update BOOK set CREATED_DATE = datetime('2020-02-01 00:00:00', '+' || abs(NUMBER) || ' hours'), LAST_MODIFIED_DATE = datetime('2020-03-01 00:00:00', '+' || abs(NUMBER) || ' hours')",
          "update BOOK set LAST_MODIFIED_DATE = '2019-01-01 00:00:00' where ID = 'D2'",
        )
        db.rawQuery("select ID, NAME, URL, NUMBER, FILE_LAST_MODIFIED, FILE_SIZE, FILE_HASH, FILE_HASH_KOREADER, LIBRARY_ID, SERIES_ID, DELETED_DATE, ONESHOT, CREATED_DATE from BOOK where ID not like 'M%' order by ID")
      }
    }

    func("findByIdOrNull") {
      case("existing") { dao.findByIdOrNull("B3") }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("deleted book") { dao.findByIdOrNull("D1")?.deletedDate }
    }

    func("toDomain") {
      case("file last modified is not converted") { dao.findByIdOrNull("BU")!!.fileLastModified }
      case("dates in current time zone") { dao.findByIdOrNull("B2")!!.let { listOf(it.createdDate, it.lastModifiedDate) } }
      case("url and path") { dao.findByIdOrNull("BU")!!.let { listOf(it.url, it.path) } }
    }

    func("findNotDeletedByLibraryIdAndUrlOrNull") {
      case("existing") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/libraries/L1/S1/B2.cbz"))?.id }
      case("unicode url, deleted duplicate ignored") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", unicodeUrl)?.id }
      case("deleted book") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/libraries/L1/S1/D1.cbz")) }
      case("other library") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L2", URL("file:/libraries/L1/S1/B2.cbz")) }
      case("url is case sensitive") { dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/libraries/L1/S1/b2.cbz")) }
      case("most recently modified first") {
        dao.insert(book("B2BIS", "S2", "L1", number = 30).copy(url = URL("file:/libraries/L1/S1/B2.cbz")))
        sql(db, "update BOOK set LAST_MODIFIED_DATE = '2025-01-01 00:00:00' where ID = 'B2BIS'")
        dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/libraries/L1/S1/B2.cbz"))?.id
      }
      case("most recently modified first, other order") {
        sql(db, "update BOOK set LAST_MODIFIED_DATE = '2015-01-01 00:00:00' where ID = 'B2BIS'")
        dao.findNotDeletedByLibraryIdAndUrlOrNull("L1", URL("file:/libraries/L1/S1/B2.cbz"))?.id
      }
    }

    func("findAllBySeriesId") {
      case("series") { ids(dao.findAllBySeriesId("S1")) }
      case("missing") { dao.findAllBySeriesId("NOPE") }
    }

    func("findAllBySeriesIds") {
      case("empty") { dao.findAllBySeriesIds(emptyList()) }
      case("several") { ids(dao.findAllBySeriesIds(listOf("S1", "S3", "NOPE", "S1"))) }
      case("more than batch size") { ids(dao.findAllBySeriesIds((1..1500).map { "X$it" } + listOf("S3", "S1"))) }
    }

    func("findAllNotDeletedByLibraryIdAndUrlNotIn") {
      case("no url") { ids(dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L1", emptyList())) }
      case("some urls") { ids(dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L1", listOf(URL("file:/libraries/L1/S1/B2.cbz"), unicodeUrl, URL("file:/nope")))) }
      case("more than batch size") {
        ids(dao.findAllNotDeletedByLibraryIdAndUrlNotIn("L2", (1..1090).map { URL("file:/libraries/L2/S2/${mid(it)}.cbz") } + (1..500).map { URL("file:/x$it") }))
      }
      case("unknown library") { dao.findAllNotDeletedByLibraryIdAndUrlNotIn("NOPE", emptyList()) }
    }

    func("findAllDeletedByFileSize") {
      case("deleted") { ids(dao.findAllDeletedByFileSize(500)) }
      case("none") { dao.findAllDeletedByFileSize(100) }
      case("large size") { dao.findAllDeletedByFileSize(5_000_000_000L) }
    }

    func("findAll@114") {
      case("all") { dao.findAll().size }
      case("first books") { ids(dao.findAll()).take(20) }
    }

    func("getLibraryIdOrNull") {
      case("existing") { dao.getLibraryIdOrNull("C1") }
      case("missing") { dao.getLibraryIdOrNull("NOPE") }
    }

    func("getSeriesIdOrNull") {
      case("existing") { dao.getSeriesIdOrNull("B7") }
      case("missing") { dao.getSeriesIdOrNull("NOPE") }
    }

    func("existsById") {
      case("existing") { dao.existsById("D2") }
      case("missing") { dao.existsById("NOPE") }
      case("case sensitive") { dao.existsById("b1") }
    }

    func("findAllIdsBySeriesId") {
      case("series") { dao.findAllIdsBySeriesId("S1").sorted() }
      case("missing") { dao.findAllIdsBySeriesId("NOPE") }
    }

    func("findAllIdsByLibraryId") {
      case("library") { dao.findAllIdsByLibraryId("L1").sorted() }
      case("empty library") { dao.findAllIdsByLibraryId("L3") }
    }

    func("findFirstIdInSeriesOrNull") {
      case("without metadata") { dao.findFirstIdInSeriesOrNull("S3") }
      case("missing series") { dao.findFirstIdInSeriesOrNull("NOPE") }
      case("with metadata") {
        db.bookMetadataDao.insert(
          listOf(
            meta("B1", 1F, "Ünïcode é"),
            meta("B2", 2F, "Deuxième"),
            meta("B3", 2.5F),
            meta("B4", 3F),
            meta("B5", 10F, "cinq é"),
            meta("B6", -1F),
            meta("D1", -5F),
            meta("B7", 1F),
            meta("C1", 2F),
            meta("C2", 1F),
          ),
        )
        listOf(dao.findFirstIdInSeriesOrNull("S1"), dao.findFirstIdInSeriesOrNull("S3"))
      }
      case("book without metadata sorts first") {
        sql(db, "delete from BOOK_METADATA where BOOK_ID = 'D1'")
        dao.findFirstIdInSeriesOrNull("S1")
      }
    }

    func("findLastIdInSeriesOrNull") {
      case("with metadata") { listOf(dao.findLastIdInSeriesOrNull("S1"), dao.findLastIdInSeriesOrNull("S3")) }
      case("missing series") { dao.findLastIdInSeriesOrNull("NOPE") }
    }

    func("findFirstUnreadIdInSeriesOrNull") {
      case("no progress") { dao.findFirstUnreadIdInSeriesOrNull("S3", "U1") }
      case("first books read") {
        sql(
          db,
          "insert into READ_PROGRESS (BOOK_ID, USER_ID, PAGE, COMPLETED) values ('C2', 'U1', 10, 1), ('C1', 'U2', 1, 0), ('BX', 'U1', 3, 1), ('D1', 'U1', 3, 1), ('B6', 'U1', 5, 0), ('B1', 'U2', 2, 1)",
        )
        listOf(dao.findFirstUnreadIdInSeriesOrNull("S3", "U1"), dao.findFirstUnreadIdInSeriesOrNull("S3", "U2"))
      }
      case("in progress counts as unread") { dao.findFirstUnreadIdInSeriesOrNull("S1", "U1") }
      case("other user") { dao.findFirstUnreadIdInSeriesOrNull("S1", "U2") }
      case("all read") {
        sql(db, "insert into READ_PROGRESS (BOOK_ID, USER_ID, PAGE, COMPLETED) values ('C1', 'U1', 10, 1)")
        dao.findFirstUnreadIdInSeriesOrNull("S3", "U1")
      }
      case("unknown user") { dao.findFirstUnreadIdInSeriesOrNull("S1", "NOPE") }
    }

    func("findAllByLibraryIdAndMediaTypes") {
      case("setup") {
        db.mediaDao.insert(
          listOf(
            Media(Media.Status.READY, "application/zip", bookId = "B1"),
            Media(Media.Status.READY, "application/zip", bookId = "B2"),
            Media(Media.Status.READY, "application/zip", bookId = "B3"),
            Media(Media.Status.READY, "application/pdf", bookId = "B4"),
            Media(Media.Status.ERROR, null, bookId = "B5"),
            Media(Media.Status.READY, "application/zip", bookId = "C1"),
            Media(Media.Status.READY, "application/zip", bookId = "C2"),
            Media(Media.Status.READY, "application/epub+zip", bookId = "BU"),
          ),
        )
        db.mediaDao.count()
      }
      case("one type") { ids(dao.findAllByLibraryIdAndMediaTypes("L1", listOf("application/zip"))) }
      case("several types") { ids(dao.findAllByLibraryIdAndMediaTypes("L1", setOf("application/pdf", "application/epub+zip", "nope"))) }
      case("no type") { dao.findAllByLibraryIdAndMediaTypes("L1", emptyList()) }
      case("media type is case sensitive") { dao.findAllByLibraryIdAndMediaTypes("L1", listOf("APPLICATION/ZIP")) }
      case("other library") { ids(dao.findAllByLibraryIdAndMediaTypes("L2", listOf("application/zip"))) }
    }

    func("findAllByLibraryIdAndMismatchedExtension") {
      case("mismatched") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L1", "application/zip", "cbz")) }
      case("like is case insensitive") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L2", "application/zip", "cbz")) }
      case("uppercase extension") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L1", "application/zip", "CBZ")) }
      case("wildcard in extension") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L1", "application/zip", "c_r")) }
      case("percent extension") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L1", "application/zip", "%")) }
      case("empty extension") { ids(dao.findAllByLibraryIdAndMismatchedExtension("L1", "application/pdf", "")) }
      case("unknown media type") { dao.findAllByLibraryIdAndMismatchedExtension("L1", "nope", "cbz") }
    }

    func("update@362") {
      case("all fields") {
        dao.update(
          dao.findByIdOrNull("B1")!!.copy(
            name = "Renamed ünï",
            url = URL("file:/libraries/L1/S1/renamed.cbz"),
            number = 100,
            fileLastModified = LocalDateTime.of(2022, 2, 2, 2, 2, 2),
            fileSize = 12345,
            fileHash = "hash1",
            fileHashKoreader = "kor1",
            deletedDate = LocalDateTime.of(2023, 3, 3, 3, 3, 3),
            oneshot = true,
          ),
        )
        stable(dao.findByIdOrNull("B1"))
      }
      case("restore") {
        dao.update(dao.findByIdOrNull("B1")!!.copy(deletedDate = null, oneshot = false, fileHash = ""))
        stable(dao.findByIdOrNull("B1"))
      }
      case("move to another series") {
        dao.update(dao.findByIdOrNull("B7")!!.copy(seriesId = "S3", libraryId = "L2"))
        listOf(dao.getSeriesIdOrNull("B7"), dao.getLibraryIdOrNull("B7"))
      }
      case("unknown series") { exceptionType { dao.update(dao.findByIdOrNull("B7")!!.copy(seriesId = "NOPE")) } }
      case("missing book") {
        dao.update(book("NOPE", "S1", "L1"))
        dao.existsById("NOPE")
      }
      case("created date is kept") { dao.findByIdOrNull("B1")!!.createdDate }
    }

    func("update@367") {
      case("empty") {
        dao.update(emptyList())
        dao.count()
      }
      case("several") {
        dao.update(listOf(dao.findByIdOrNull("B2")!!.copy(fileHashKoreader = "kor1"), dao.findByIdOrNull("C1")!!.copy(fileHash = "", fileHashKoreader = "")))
        stable(listOf(dao.findByIdOrNull("B2"), dao.findByIdOrNull("C1")))
      }
      case("failure in the middle") {
        listOf(
          exceptionType { transactional(db) { dao.update(listOf(dao.findByIdOrNull("B3")!!.copy(name = "first"), dao.findByIdOrNull("B4")!!.copy(seriesId = "NOPE"))) } },
          dao.findByIdOrNull("B3")!!.name,
        )
      }
    }

    func("updateBook") {
      case("last modified date") { stable(dao.findByIdOrNull("B2")!!.lastModifiedDate) }
    }

    func("findAllByLibraryIdAndWithEmptyHash") {
      case("library") { ids(dao.findAllByLibraryIdAndWithEmptyHash("L1")) }
      case("unknown library") { dao.findAllByLibraryIdAndWithEmptyHash("NOPE") }
    }

    func("findAllByLibraryIdAndWithEmptyHashKoreader") {
      case("library") { ids(dao.findAllByLibraryIdAndWithEmptyHashKoreader("L1")) }
      case("other library") { ids(dao.findAllByLibraryIdAndWithEmptyHashKoreader("L2")).size }
    }

    func("findAllByHashKoreader") {
      case("several") { ids(dao.findAllByHashKoreader("kor1")) }
      case("empty hash") { ids(dao.findAllByHashKoreader("")).size }
      case("case sensitive") { dao.findAllByHashKoreader("KOR1") }
    }

    func("findAll@120") {
      val u1 = user("U1")
      val limited = user("U9").copy(sharedAllLibraries = false, sharedLibrariesIds = setOf("L2"))
      case("no condition") { page(null) }
      case("no condition, paged and sorted") { page(null, pageable = PageRequest.of(1, 5, Sort.by("number"))) }
      case("library") { page(SearchCondition.LibraryId(SearchOperator.Is("L2")), pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "number"))) }
      case("library is not") { page(SearchCondition.LibraryId(SearchOperator.IsNot("L2")), pageable = PageRequest.of(0, 50, Sort.by("number"))) }
      case("all of") {
        page(
          SearchCondition.AllOfBook(SearchCondition.LibraryId(SearchOperator.Is("L1")), SearchCondition.Deleted(SearchOperator.IsFalse)),
          pageable = PageRequest.of(0, 4, Sort.by("number")),
        )
      }
      case("any of") {
        page(
          SearchCondition.AnyOfBook(SearchCondition.SeriesId(SearchOperator.Is("S3")), SearchCondition.SeriesId(SearchOperator.Is("S1"))),
          pageable = PageRequest.of(0, 5, Sort.by(Sort.Order.desc("seriesId"), Sort.Order.asc("number"))),
        )
      }
      case("deleted") { page(SearchCondition.Deleted(SearchOperator.IsTrue), pageable = PageRequest.of(0, 10, Sort.by("createdDate"))) }
      case("oneshot") { page(SearchCondition.OneShot(SearchOperator.IsTrue)) }
      case("title contains") { page(SearchCondition.Title(SearchOperator.Contains("é")), pageable = PageRequest.of(0, 10, Sort.by("number"))) }
      case("title begins with, case insensitive") { page(SearchCondition.Title(SearchOperator.BeginsWith("DEUX"))) }
      case("number sort") { page(SearchCondition.NumberSort(SearchOperator.GreaterThan(1.5F)), pageable = PageRequest.of(0, 10, Sort.by("number"))) }
      case("media status") { page(SearchCondition.MediaStatus(SearchOperator.Is(Media.Status.READY)), pageable = PageRequest.of(0, 10, Sort.by("number"))) }
      case("read status read") { page(SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.READ)), SearchContext(u1), PageRequest.of(0, 10, Sort.by("number"))) }
      case("read status in progress") { page(SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.IN_PROGRESS)), SearchContext(u1)) }
      case("read status unread in series") {
        page(
          SearchCondition.AllOfBook(SearchCondition.SeriesId(SearchOperator.Is("S1")), SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.UNREAD))),
          SearchContext(u1),
          PageRequest.of(0, 20, Sort.by("number")),
        )
      }
      case("read status without user") { page(SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.READ))) }
      case("read list") { page(SearchCondition.ReadListId(SearchOperator.Is("RL1"))) }
      case("not in read list") { page(SearchCondition.AllOfBook(SearchCondition.ReadListId(SearchOperator.IsNot("RL1")), SearchCondition.LibraryId(SearchOperator.Is("L1"))), pageable = PageRequest.of(0, 3, Sort.by("number"))) }
      case("limited user") { page(null, SearchContext(limited), PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "number"))) }
      case("restricted user") {
        val r = user("U8").copy(restrictions = ContentRestrictions(AgeRestriction(10, AllowExclude.ALLOW_ONLY)))
        page(SearchCondition.SeriesId(SearchOperator.Is("S3")), SearchContext(r))
      }
      case("unknown sort") { page(null, pageable = PageRequest.of(0, 2, Sort.by("name"))) }
      case("page beyond the end") { page(SearchCondition.SeriesId(SearchOperator.Is("S1")), pageable = PageRequest.of(5, 10)) }
      case("unpaged sorted") { page(SearchCondition.SeriesId(SearchOperator.Is("S1")), pageable = Pageable.unpaged(Sort.by(Sort.Direction.DESC, "number"))) }
      case("anonymous user") { page(SearchCondition.SeriesId(SearchOperator.Is("S3")), SearchContext.ofAnonymousUser()) }
      case("page content") { stable(dao.findAll(SearchCondition.SeriesId(SearchOperator.Is("S3")), SearchContext.empty(), PageRequest.of(0, 1, Sort.by("number")))) }
    }

    func("countGroupedByLibraryId") {
      case("counts") { dao.countGroupedByLibraryId() }
    }

    func("getFilesizeGroupedByLibraryId") {
      case("sizes") { dao.getFilesizeGroupedByLibraryId() }
    }

    func("delete@390") {
      case("book with dependencies") { exceptionType { dao.delete("C1") } }
      case("existing") {
        dao.delete("BW")
        dao.existsById("BW")
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("delete@395") {
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
      case("several") {
        dao.delete(listOf("B2BIS", "NOPE", "B2BIS"))
        dao.count()
      }
      case("more than batch size") {
        dao.delete((1..1100).map { mid(it) } + (1..500).map { "X$it" })
        listOf(dao.count(), dao.countGroupedByLibraryId())
      }
    }

    func("deleteAll") {
      case("with dependencies") { exceptionType { dao.deleteAll() } }
      case("all") {
        sql(db, "delete from READ_PROGRESS", "delete from BOOK_METADATA", "delete from MEDIA", "delete from READ_PROGRESS_SERIES")
        dao.deleteAll()
        listOf(dao.count(), dao.findAll(), dao.countGroupedByLibraryId(), dao.getFilesizeGroupedByLibraryId())
      }
    }
  }
}
