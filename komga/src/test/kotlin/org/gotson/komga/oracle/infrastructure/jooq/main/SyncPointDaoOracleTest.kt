package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.BookSearch
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.domain.model.SyncPoint
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

class SyncPointDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.syncPointDao
  private var a = ""
  private var b = ""
  private var c = ""
  private val all = Pageable.unpaged()

  private fun books(p: Page<SyncPoint.Book>) = stable(listOf(p.content.sortedBy { it.bookId }, p.totalElements, p.number, p.size))

  private fun ids(p: Page<SyncPoint.Book>) = listOf(p.content.map { it.bookId }.sorted(), p.totalElements)

  private fun lists(p: Page<SyncPoint.ReadList>) = stable(listOf(p.content.sortedBy { it.readListId }, p.totalElements, p.number, p.size))

  private fun attempt(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      "throws ${e::class.java.simpleName}: ${e.message}"
    }

  override fun cases() {
    func("create") {
      case("not deleted books") {
        NzDaoSeed.seed(db)
        val sp = dao.create("K1", BookSearch(SearchCondition.Deleted(SearchOperator.IsFalse)), SearchContext(NzDaoSeed.u1))
        a = sp.id
        stable(listOf(sp, sp.userId, sp.apiKeyId))
      }
      case("stored books") { books(dao.findBooksById(a, false, all)) }
      case("restricted user and condition") {
        val sp = dao.create(null, BookSearch(SearchCondition.LibraryId(SearchOperator.Is("L1"))), SearchContext(NzDaoSeed.u2))
        c = sp.id
        listOf(sp.apiKeyId, ids(dao.findBooksById(c, false, all)))
      }
      case("without user") { attempt { dao.create(null, BookSearch(), SearchContext.empty()) } }
      case("after changes") {
        db.dsl.execute("update BOOK set FILE_SIZE = 1111 where ID = 'B2'")
        db.dsl.execute("update BOOK_METADATA set LAST_MODIFIED_DATE = '2022-01-01 00:00:00' where BOOK_ID = 'B3'")
        db.dsl.execute("update READ_PROGRESS set LAST_MODIFIED_DATE = '2022-02-02 00:00:00' where BOOK_ID = 'B7' and USER_ID = 'U1'")
        db.readProgressDao.save(ReadProgress("B10", "U1", 1, false, LocalDateTime.of(2022, 3, 3, 0, 0)))
        db.thumbnailBookDao.markSelected(NzDaoSeed.thumbnails.first { it.id == "TB4" })
        db.dsl.execute("update BOOK set FILE_HASH = 'NEW' where ID = 'B11'")
        val sp = dao.create("K2", BookSearch(SearchCondition.SeriesId(SearchOperator.IsNot("S4"))), SearchContext(NzDaoSeed.u1))
        b = sp.id
        ids(dao.findBooksById(b, false, all))
      }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull(b)) }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("findBooksById") {
      case("paged") { dao.findBooksById(a, false, PageRequest.of(1, 3)).let { listOf(it.content.size, it.totalElements, it.number, it.size) } }
      case("unknown") { dao.findBooksById("NOPE", false, all) }
    }

    func("queryToPageBook") {
      case("dates at UTC") { stable(dao.findBooksById(a, false, all).content.first { it.bookId == "B1" }) }
    }

    func("findBooksAdded") {
      case("added") { books(dao.findBooksAdded(a, b, false, all)) }
      case("reverse") { ids(dao.findBooksAdded(b, a, false, all)) }
      case("same") { ids(dao.findBooksAdded(a, a, false, all)) }
    }

    func("findBooksRemoved") {
      case("removed") { books(dao.findBooksRemoved(a, b, false, all)) }
      case("reverse") { ids(dao.findBooksRemoved(b, a, false, all)) }
    }

    func("findBooksChanged") {
      case("changed") { ids(dao.findBooksChanged(a, b, false, all)) }
      case("values") { books(dao.findBooksChanged(a, b, false, PageRequest.of(0, 20))) }
    }

    func("findBooksReadProgressChanged") {
      case("read progress changed") { ids(dao.findBooksReadProgressChanged(a, b, false, all)) }
      case("same sync point") { ids(dao.findBooksReadProgressChanged(a, a, false, all)) }
    }

    func("findBooksById") {
      case("only not synced after marking") {
        dao.markBooksSynced(b, false, listOf("B2", "B1"))
        dao.markBooksSynced(b, false, emptyList())
        listOf(ids(dao.findBooksById(b, true, all)), ids(dao.findBooksChanged(a, b, true, all)), ids(dao.findBooksAdded(a, b, true, all)))
      }
      case("removed books marked synced") {
        dao.markBooksSynced(b, true, listOf("B9"))
        dao.markBooksSynced(b, true, listOf("B9", "B5"))
        listOf(ids(dao.findBooksRemoved(a, b, true, all)), ids(dao.findBooksRemoved(a, b, false, all)), db.rawQuery("select BOOK_ID from SYNC_POINT_BOOK_REMOVED_SYNCED order by BOOK_ID"))
      }
      case("read progress changed only not synced") {
        dao.markBooksSynced(b, false, listOf("B10"))
        ids(dao.findBooksReadProgressChanged(a, b, true, all))
      }
    }

    func("addOnDeck") {
      case("admin on deck") {
        dao.addOnDeck(b, SearchContext(NzDaoSeed.u1), null)
        listOf(lists(dao.findReadListsById(b, false, all)), dao.findBookIdsByReadListIds(b, listOf(SyncPoint.ReadList.ON_DECK_ID)).map { it.bookId }.sorted())
      }
      case("filtered library without books") {
        dao.addOnDeck(a, SearchContext(NzDaoSeed.u3), listOf("L1"))
        dao.findReadListsById(a, false, all)
      }
      case("without user") { attempt { dao.addOnDeck(a, SearchContext.empty(), null) } }
    }

    func("findReadListsById") {
      case("read lists of sync points") {
        listOf(
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$a', 'RLa', 'Old name', '2020-01-01 00:00:00', '2020-01-02 00:00:00')",
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$a', 'RLc', 'Removed', '2020-01-01 00:00:00', '2020-01-02 00:00:00')",
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$a', 'RLd', 'Same', '2020-01-01 00:00:00', '2020-01-02 00:00:00')",
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$b', 'RLa', 'New name', '2020-01-01 00:00:00', '2020-01-02 00:00:00')",
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$b', 'RLb', 'Added', '2021-01-01 00:00:00', '2021-06-02 10:00:00')",
          "insert into SYNC_POINT_READLIST (SYNC_POINT_ID, READLIST_ID, READLIST_NAME, READLIST_CREATED_DATE, READLIST_LAST_MODIFIED_DATE) values ('$b', 'RLd', 'Same', '2020-01-01 00:00:00', '2020-01-02 00:00:00')",
          "insert into SYNC_POINT_READLIST_BOOK (SYNC_POINT_ID, READLIST_ID, BOOK_ID) values ('$b', 'RLa', 'B3')",
          "insert into SYNC_POINT_READLIST_BOOK (SYNC_POINT_ID, READLIST_ID, BOOK_ID) values ('$b', 'RLb', 'B4')",
        ).forEach { db.dsl.execute(it) }
        listOf(lists(dao.findReadListsById(a, false, all)), dao.findReadListsById(b, false, PageRequest.of(1, 2)).let { listOf(it.content.size, it.totalElements, it.size) })
      }
    }

    func("queryToPageReadList") {
      case("dates at UTC") { stable(dao.findReadListsById(b, false, all).content.first { it.readListId == "RLb" }) }
    }

    func("findReadListsAdded") {
      case("added") { lists(dao.findReadListsAdded(a, b, false, all)) }
      case("reverse") { lists(dao.findReadListsAdded(b, a, false, all)) }
    }

    func("findReadListsChanged") {
      case("changed name") { lists(dao.findReadListsChanged(a, b, false, all)) }
    }

    func("findReadListsRemoved") {
      case("removed") { lists(dao.findReadListsRemoved(a, b, false, all)) }
    }

    func("findReadListsById") {
      case("only not synced after marking") {
        dao.markReadListsSynced(b, false, listOf("RLa", SyncPoint.ReadList.ON_DECK_ID))
        dao.markReadListsSynced(b, true, listOf("RLc"))
        dao.markReadListsSynced(b, true, listOf("RLc"))
        dao.markReadListsSynced(b, false, emptyList())
        listOf(
          lists(dao.findReadListsById(b, true, all)),
          lists(dao.findReadListsChanged(a, b, true, all)),
          lists(dao.findReadListsAdded(a, b, true, all)),
          lists(dao.findReadListsRemoved(a, b, true, all)),
        )
      }
    }

    func("findBookIdsByReadListIds") {
      case("some lists") { dao.findBookIdsByReadListIds(b, listOf("RLa", "RLb", "NOPE")).sortedBy { it.bookId }.let { stable(it) } }
      case("empty") { dao.findBookIdsByReadListIds(b, emptyList()) }
    }

    func("deleteByUserIdAndApiKeyIds") {
      case("one api key") {
        dao.deleteByUserIdAndApiKeyIds("U1", listOf("K1", "NOPE"))
        listOf(dao.findByIdOrNull(a), dao.findByIdOrNull(b) != null, db.rawQuery("select count(*) from SYNC_POINT_BOOK"), db.rawQuery("select count(*) from SYNC_POINT_READLIST"))
      }
      case("other user") {
        dao.deleteByUserIdAndApiKeyIds("U2", listOf("K2"))
        dao.findByIdOrNull(b) != null
      }
    }

    func("deleteSubEntities") {
      case("tables emptied for sync point") {
        listOf("SYNC_POINT_READLIST_REMOVED_SYNCED", "SYNC_POINT_READLIST_BOOK", "SYNC_POINT_READLIST", "SYNC_POINT_BOOK_REMOVED_SYNCED", "SYNC_POINT_BOOK").map {
          db.rawQuery("select count(*) from $it where SYNC_POINT_ID = '$a'")
        }
      }
    }

    func("deleteOne") {
      case("existing") {
        dao.deleteOne(b)
        listOf(dao.findByIdOrNull(b), db.rawQuery("select count(*) from SYNC_POINT_BOOK"), db.rawQuery("select count(*) from SYNC_POINT_READLIST_REMOVED_SYNCED"))
      }
      case("missing") {
        dao.deleteOne("NOPE")
        dao.findByIdOrNull(c) != null
      }
    }

    func("deleteByUserId") {
      case("existing") {
        dao.deleteByUserId("U2")
        listOf(dao.findByIdOrNull(c), db.rawQuery("select count(*) from SYNC_POINT"))
      }
    }

    func("deleteAll") {
      case("all") {
        dao.create(null, BookSearch(), SearchContext(NzDaoSeed.u4))
        dao.deleteAll()
        listOf("SYNC_POINT", "SYNC_POINT_BOOK", "SYNC_POINT_READLIST").map { db.rawQuery("select count(*) from $it") }
      }
    }
  }
}
