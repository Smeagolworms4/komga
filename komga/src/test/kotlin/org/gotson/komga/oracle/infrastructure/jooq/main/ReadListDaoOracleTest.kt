package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class ReadListDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.readListDao

  private fun ctx(u: org.gotson.komga.domain.model.KomgaUser) = SearchContext(u)

  private fun names(p: Page<ReadList>) = listOf(p.content.map { it.name }, p.totalElements, p.number, p.size, p.sort.toString())

  private fun books(r: ReadList?) = r?.let { listOf(it.id, it.bookIds, it.filtered) }

  override fun cases() {
    func("count") {
      case("seeded") {
        NzDaoSeed.seed(db, lucene = true)
        dao.count()
      }
    }

    func("findByIdOrNull") {
      case("admin") { dao.findByIdOrNull("RL1", ctx(NzDaoSeed.u1)) }
      case("unordered") { dao.findByIdOrNull("RL2", SearchContext.empty()) }
      case("age restricted user sees filtered list") { books(dao.findByIdOrNull("RL1", ctx(NzDaoSeed.u2))) }
      case("user of other library") { books(dao.findByIdOrNull("RL1", ctx(NzDaoSeed.u3))) }
      case("user of other library with allowed books") { books(dao.findByIdOrNull("RL2", ctx(NzDaoSeed.u3))) }
      case("label allowed user") { books(dao.findByIdOrNull("RL1", ctx(NzDaoSeed.u4))) }
      case("empty list for admin") { books(dao.findByIdOrNull("RL3", ctx(NzDaoSeed.u1))) }
      case("empty list for limited user") { dao.findByIdOrNull("RL3", ctx(NzDaoSeed.u2)) }
      case("missing") { dao.findByIdOrNull("NOPE", ctx(NzDaoSeed.u1)) }
    }

    func("selectBase") {
      case("one row per list") { dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged()).content.map { it.id } .sorted() }
    }

    func("fetchAndMap") {
      case("book ids sorted by number") { dao.findAll(SearchContext.empty(), Pageable.unpaged()).content.sortedBy { it.id }.map { it.bookIds } }
    }

    func("toDomain") {
      case("dates converted to current time zone") { dao.findByIdOrNull("RL2", SearchContext.empty())!!.let { listOf(it.createdDate, it.lastModifiedDate, it.ordered, it.summary) } }
    }

    func("insert") {
      case("sparse numbers and unicode name") {
        dao.insert(ReadList("Élan list", "résumé", ordered = true, bookIds = sortedMapOf(10 to "B9", 5 to "B2"), id = "RL4"))
        stable(dao.findByIdOrNull("RL4", SearchContext.empty()))
      }
      case("more lists for sorting") {
        dao.insert(ReadList("zebra", id = "RL5", bookIds = sortedMapOf(0 to "B11")))
        dao.insert(ReadList("Ångström list", id = "RL6", bookIds = sortedMapOf(0 to "B10", 1 to "B1")))
        dao.insert(ReadList("reading order 2", id = "RL7"))
        // fixed dates (CURRENT_TIMESTAMP, to the second): "sorted by dates" does not depend on a second boundary between two cases
        db.dsl.execute("update READLIST set CREATED_DATE = '2021-01-01 00:00:00', LAST_MODIFIED_DATE = '2021-01-01 00:00:00' where ID in ('RL4', 'RL5', 'RL6', 'RL7')")
        dao.count()
      }
      case("stored values") { db.rawQuery("select ID, NAME, SUMMARY, ORDERED, BOOK_COUNT from READLIST order by ID") }
      case("duplicate id") { exceptionType { dao.insert(ReadList("dup", id = "RL1")) } }
      case("unknown book") {
        val e = exceptionType { dao.insert(ReadList("bad", id = "RLX", bookIds = sortedMapOf(1 to "NOPE"))) }
        db.dsl.execute("delete from READLIST where ID = 'RLX'")
        e
      }
    }

    func("insertBooks") {
      case("stored book rows") { db.rawQuery("select READLIST_ID, BOOK_ID, NUMBER from READLIST_BOOK order by READLIST_ID, NUMBER") }
    }

    func("findAll") {
      val u1 = ctx(NzDaoSeed.u1)
      case("unpaged unsorted") { dao.findAll(u1, Pageable.unpaged()).let { listOf(it.content.map { r -> r.id }.sorted(), it.totalElements, it.size) } }
      case("sorted by name") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")))) }
      case("sorted by name desc paged") { names(dao.findAll(u1, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "name")))) }
      case("second page") { names(dao.findAll(u1, PageRequest.of(1, 3, Sort.by("name")))) }
      case("sorted by dates") { names(dao.findAll(u1, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("lastModifiedDate"), Sort.Order.asc("createdDate"), Sort.Order.asc("name"))))) }
      case("unknown sort") { names(dao.findAll(u1, PageRequest.of(0, 2, Sort.by("nope")))).drop(1) }
      case("belongs to library") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), listOf("L2"))) }
      case("belongs to no library") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), emptyList())) }
      case("age restricted user") { dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.bookIds, it.filtered) } }
      case("label excluded user") { dao.findAll(ctx(NzDaoSeed.u3), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.bookIds, it.filtered) } }
      case("label allowed user") { dao.findAll(ctx(NzDaoSeed.u4), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.bookIds, it.filtered) } }
      case("search") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), null, "reading")) }
      case("search sorted by relevance") { dao.findAll(u1, Pageable.unpaged(Sort.by("relevance")), null, "list").content.map { it.name }.sorted() }
      case("search without match") { names(dao.findAll(u1, Pageable.unpaged(), null, "zzzz")) }
      case("blank search") { dao.findAll(u1, Pageable.unpaged(), null, " ").totalElements }
      case("search and library") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), listOf("L1"), "order")) }
    }

    func("findAllContainingBookId") {
      case("admin") { dao.findAllContainingBookId("B1", ctx(NzDaoSeed.u1)).map { it.id }.sorted() }
      case("restricted user") { dao.findAllContainingBookId("B1", ctx(NzDaoSeed.u2)).map { books(it) } }
      case("other library") { dao.findAllContainingBookId("B1", ctx(NzDaoSeed.u3)) }
      case("unknown") { dao.findAllContainingBookId("NOPE", SearchContext.empty()) }
    }

    func("findAllEmpty") {
      case("empty lists") { dao.findAllEmpty().map { it.id }.sorted() }
    }

    func("findByNameOrNull") {
      case("ignoring case") { dao.findByNameOrNull("READING order")?.id }
      case("non ascii ignoring case") { dao.findByNameOrNull("élan LIST")?.id }
      case("non ascii other case") { dao.findByNameOrNull("ÉLAN LIST")?.id }
      case("missing") { dao.findByNameOrNull("nope") }
    }

    func("existsByName") {
      case("existing") { listOf(dao.existsByName("zebra"), dao.existsByName("ZEBRA"), dao.existsByName("ångström LIST")) }
      case("missing") { dao.existsByName("zebr") }
    }

    func("update") {
      case("name, summary, order and books") {
        dao.update(dao.findByIdOrNull("RL2", SearchContext.empty())!!.copy(name = "Now ordered", summary = "s", ordered = true, bookIds = sortedMapOf(3 to "B1", 1 to "B4")))
        listOf(stable(dao.findByIdOrNull("RL2", SearchContext.empty())), db.rawQuery("select BOOK_COUNT from READLIST where ID = 'RL2'"))
      }
      case("remove all books") {
        dao.update(dao.findByIdOrNull("RL5", SearchContext.empty())!!.copy(bookIds = sortedMapOf()))
        listOf(books(dao.findByIdOrNull("RL5", SearchContext.empty())), dao.findAllEmpty().map { it.id }.sorted())
      }
      case("missing") {
        dao.update(ReadList("x", id = "NOPE"))
        dao.count()
      }
    }

    func("removeBookFromAll") {
      case("book in two lists") {
        dao.removeBookFromAll("B1")
        dao.findAllContainingBookId("B1", SearchContext.empty())
      }
      case("filtered flag after removal") { books(dao.findByIdOrNull("RL1", SearchContext.empty())) }
    }

    func("removeBooksFromAll") {
      case("empty") {
        dao.removeBooksFromAll(emptyList())
        db.rawQuery("select count(*) from READLIST_BOOK")
      }
      case("large list") {
        dao.removeBooksFromAll((1..1100).map { "X$it" } + listOf("B6", "B9"))
        db.rawQuery("select READLIST_ID, BOOK_ID from READLIST_BOOK order by READLIST_ID, NUMBER")
      }
    }

    func("delete@269") {
      case("existing") {
        dao.delete("RL4")
        listOf(dao.findByIdOrNull("RL4", SearchContext.empty()), dao.count())
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("delete@275") {
      case("several") {
        dao.delete(listOf("RL5", "RL6", "NOPE"))
        listOf(dao.count(), db.rawQuery("select distinct READLIST_ID from READLIST_BOOK order by READLIST_ID"))
      }
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
    }

    func("deleteAll") {
      case("all") {
        dao.deleteAll()
        listOf(dao.count(), db.rawQuery("select count(*) from READLIST_BOOK"))
      }
    }
  }
}
