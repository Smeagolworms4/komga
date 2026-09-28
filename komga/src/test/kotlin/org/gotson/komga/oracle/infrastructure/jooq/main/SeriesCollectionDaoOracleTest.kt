package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class SeriesCollectionDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.seriesCollectionDao

  private fun ctx(u: KomgaUser) = SearchContext(u)

  private fun names(p: Page<SeriesCollection>) = listOf(p.content.map { it.name }, p.totalElements, p.number, p.size, p.sort.toString())

  private fun series(c: SeriesCollection?) = c?.let { listOf(it.id, it.seriesIds, it.filtered) }

  override fun cases() {
    func("count") {
      case("seeded") {
        NzDaoSeed.seed(db, lucene = true)
        dao.count()
      }
    }

    func("findByIdOrNull") {
      case("admin") { dao.findByIdOrNull("C1", ctx(NzDaoSeed.u1)) }
      case("ordered") { dao.findByIdOrNull("C2", SearchContext.empty()) }
      case("age restricted user sees filtered collection") { series(dao.findByIdOrNull("C1", ctx(NzDaoSeed.u2))) }
      case("user of one library") { series(dao.findByIdOrNull("C1", ctx(NzDaoSeed.u3))) }
      case("user of one library without allowed series") { series(dao.findByIdOrNull("C2", ctx(NzDaoSeed.u3))) }
      case("label allowed user") { series(dao.findByIdOrNull("C2", ctx(NzDaoSeed.u4))) }
      case("empty collection for admin") { series(dao.findByIdOrNull("C3", ctx(NzDaoSeed.u1))) }
      case("empty collection for limited user") { dao.findByIdOrNull("C3", ctx(NzDaoSeed.u2)) }
      case("missing") { dao.findByIdOrNull("NOPE", ctx(NzDaoSeed.u1)) }
    }

    func("selectBase") {
      case("one row per collection") { dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged()).content.map { it.id }.sorted() }
    }

    func("fetchAndMap") {
      case("series ids sorted by number") { dao.findAll(SearchContext.empty(), Pageable.unpaged()).content.sortedBy { it.id }.map { it.seriesIds } }
    }

    func("toDomain") {
      case("dates converted to current time zone") { dao.findByIdOrNull("C2", SearchContext.empty())!!.let { listOf(it.createdDate, it.lastModifiedDate, it.ordered) } }
    }

    func("insert") {
      case("unicode name") {
        dao.insert(SeriesCollection("Élan collection", ordered = true, seriesIds = listOf("S5", "S2"), id = "C4"))
        stable(dao.findByIdOrNull("C4", SearchContext.empty()))
      }
      case("more collections for sorting") {
        dao.insert(SeriesCollection("zebra", seriesIds = listOf("S6"), id = "C5"))
        dao.insert(SeriesCollection("Ångström collection", seriesIds = listOf("S4", "S1"), id = "C6"))
        dao.insert(SeriesCollection("heroes 2", id = "C7"))
        dao.count()
      }
      case("stored values") { db.rawQuery("select ID, NAME, ORDERED, SERIES_COUNT from COLLECTION order by ID") }
      case("duplicate id") { exceptionType { dao.insert(SeriesCollection("dup", id = "C1")) } }
      case("unknown series") {
        val e = exceptionType { dao.insert(SeriesCollection("bad", seriesIds = listOf("NOPE"), id = "CX")) }
        db.dsl.execute("delete from COLLECTION where ID = 'CX'")
        e
      }
    }

    func("insertSeries") {
      case("stored series rows") { db.rawQuery("select COLLECTION_ID, SERIES_ID, NUMBER from COLLECTION_SERIES order by COLLECTION_ID, NUMBER") }
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
      case("age restricted user") { dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.seriesIds, it.filtered) } }
      case("label excluded user") { dao.findAll(ctx(NzDaoSeed.u3), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.seriesIds, it.filtered) } }
      case("label allowed user") { dao.findAll(ctx(NzDaoSeed.u4), Pageable.unpaged(Sort.by("name"))).content.map { listOf(it.name, it.seriesIds, it.filtered) } }
      case("search") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), null, "heroes")) }
      case("search with accent") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), null, "ordered ae")) }
      case("search sorted by relevance") { dao.findAll(u1, Pageable.unpaged(Sort.by("relevance")), null, "e").content.map { it.name }.sorted() }
      case("search without match") { names(dao.findAll(u1, Pageable.unpaged(), null, "zzzz")) }
      case("search and library") { names(dao.findAll(u1, Pageable.unpaged(Sort.by("name")), listOf("L1"), "heroes")) }
    }

    func("findAllContainingSeriesId") {
      case("admin") { dao.findAllContainingSeriesId("S1", ctx(NzDaoSeed.u1)).map { it.id }.sorted() }
      case("restricted user") { dao.findAllContainingSeriesId("S1", ctx(NzDaoSeed.u2)).map { series(it) } }
      case("other library") { stable(dao.findAllContainingSeriesId("S1", ctx(NzDaoSeed.u3))) }
      case("unknown") { dao.findAllContainingSeriesId("NOPE", SearchContext.empty()) }
    }

    func("findAllEmpty") {
      case("empty collections") { dao.findAllEmpty().map { it.id }.sorted() }
    }

    func("findByNameOrNull") {
      case("ignoring case") { dao.findByNameOrNull("HEROES")?.id }
      case("non ascii ignoring case") { dao.findByNameOrNull("élan COLLECTION")?.id }
      case("non ascii other case") { dao.findByNameOrNull("ÉLAN COLLECTION")?.id }
      case("missing") { dao.findByNameOrNull("nope") }
    }

    func("existsByName") {
      case("existing") { listOf(dao.existsByName("zebra"), dao.existsByName("ZEBRA"), dao.existsByName("ordered æ")) }
      case("missing") { dao.existsByName("zebr") }
    }

    func("update") {
      case("name, order and series") {
        dao.update(dao.findByIdOrNull("C1", SearchContext.empty())!!.copy(name = "Heroes renamed", ordered = true, seriesIds = listOf("S2", "S1")))
        listOf(stable(dao.findByIdOrNull("C1", SearchContext.empty())), db.rawQuery("select SERIES_COUNT from COLLECTION where ID = 'C1'"))
      }
      case("remove all series") {
        dao.update(dao.findByIdOrNull("C5", SearchContext.empty())!!.copy(seriesIds = emptyList()))
        listOf(series(dao.findByIdOrNull("C5", SearchContext.empty())), dao.findAllEmpty().map { it.id }.sorted())
      }
      case("missing") {
        dao.update(SeriesCollection("x", id = "NOPE"))
        dao.count()
      }
    }

    func("removeSeriesFromAll@248") {
      case("series in several collections") {
        dao.removeSeriesFromAll("S1")
        dao.findAllContainingSeriesId("S1", SearchContext.empty())
      }
      case("filtered flag after removal") { series(dao.findByIdOrNull("C2", SearchContext.empty())) }
    }

    func("removeSeriesFromAll@256") {
      case("empty") {
        dao.removeSeriesFromAll(emptyList())
        db.rawQuery("select count(*) from COLLECTION_SERIES")
      }
      case("large list") {
        dao.removeSeriesFromAll((1..1100).map { "X$it" } + listOf("S6", "S5"))
        db.rawQuery("select COLLECTION_ID, SERIES_ID from COLLECTION_SERIES order by COLLECTION_ID, NUMBER")
      }
    }

    func("delete@266") {
      case("existing") {
        dao.delete("C4")
        listOf(dao.findByIdOrNull("C4", SearchContext.empty()), dao.count())
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("delete@272") {
      case("several") {
        dao.delete(listOf("C5", "C6", "NOPE"))
        listOf(dao.count(), db.rawQuery("select distinct COLLECTION_ID from COLLECTION_SERIES order by COLLECTION_ID"))
      }
      case("empty") {
        dao.delete(emptyList())
        dao.count()
      }
    }

    func("deleteAll") {
      case("all") {
        dao.deleteAll()
        listOf(dao.count(), db.rawQuery("select count(*) from COLLECTION_SERIES"))
      }
    }
  }
}
