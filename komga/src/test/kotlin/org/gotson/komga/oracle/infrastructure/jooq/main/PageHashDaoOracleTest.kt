package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class PageHashDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.pageHashDao

  private fun known(
    hash: String,
    size: Long?,
    action: PageHashKnown.Action,
  ) = PageHashKnown(hash, size, action)

  private fun sort(vararg orders: Sort.Order) = Sort.by(*orders)

  override fun cases() {
    func("insert") {
      case("with thumbnail") {
        NzDaoSeed.seed(db)
        dao.insert(known("PH1", 101, PageHashKnown.Action.DELETE_AUTO), oracleBytes(16))
        stable(dao.findKnown("PH1"))
      }
      case("without thumbnail") {
        dao.insert(known("PH2", null, PageHashKnown.Action.IGNORE), null)
        stable(dao.findKnown("PH2"))
      }
      case("negative size is null") {
        dao.insert(known("NEG", -5, PageHashKnown.Action.DELETE_MANUAL), null)
        dao.findKnown("NEG")!!.size
      }
      case("unmatched known hash") {
        dao.insert(known("ZZ", 999, PageHashKnown.Action.DELETE_MANUAL), oracleBytes(3))
        db.rawQuery("select HASH, SIZE, ACTION, DELETE_COUNT from PAGE_HASH order by HASH")
      }
      case("duplicate hash") { exceptionType { dao.insert(known("PH1", 1, PageHashKnown.Action.IGNORE), null) } }
    }

    func("findKnown") {
      case("missing") { dao.findKnown("NOPE") }
      case("case sensitive") { dao.findKnown("ph1") }
    }

    func("toDomain") {
      case("dates converted to current time zone") {
        db.dsl.execute("update PAGE_HASH set CREATED_DATE = '2020-06-01 10:00:00', LAST_MODIFIED_DATE = '2020-06-02 23:30:00'")
        dao.findKnown("PH1")!!.let { listOf(it.createdDate, it.lastModifiedDate, it.matchCount, it.deleteCount) }
      }
    }

    func("getKnownThumbnail") {
      case("with thumbnail") { dao.getKnownThumbnail("PH1") }
      case("without thumbnail") { dao.getKnownThumbnail("PH2") }
      case("missing") { dao.getKnownThumbnail("NOPE") }
    }

    func("findAllKnown") {
      case("unpaged all actions") { dao.findAllKnown(null, Pageable.unpaged()) }
      case("filter actions") { dao.findAllKnown(listOf(PageHashKnown.Action.DELETE_MANUAL, PageHashKnown.Action.IGNORE), Pageable.unpaged()).content.map { it.hash } }
      case("empty action list") { dao.findAllKnown(emptyList(), Pageable.unpaged()) }
      case("sort by match count then hash") {
        dao.findAllKnown(null, PageRequest.of(0, 20, sort(Sort.Order.desc("matchCount"), Sort.Order.asc("hash")))).content.map { it.hash to it.matchCount }
      }
      case("paged") { dao.findAllKnown(null, PageRequest.of(1, 2, Sort.by("hash"))) }
      case("sort by delete size") { dao.findAllKnown(null, PageRequest.of(0, 10, sort(Sort.Order.desc("deleteSize"), Sort.Order.desc("hash")))).content.map { it.hash } }
      case("sort by size aliases") {
        listOf("size", "fileSize").map { p -> dao.findAllKnown(null, PageRequest.of(0, 10, sort(Sort.Order.asc(p), Sort.Order.asc("hash")))).content.map { it.hash } }
      }
      case("unknown sort property") { dao.findAllKnown(null, PageRequest.of(0, 1, Sort.by("nope"))) }
    }

    func("update") {
      case("action, size and delete count") {
        dao.update(PageHashKnown("PH2", 102, PageHashKnown.Action.DELETE_AUTO, deleteCount = 7))
        stable(dao.findKnown("PH2"))
      }
      case("missing") {
        dao.update(known("NOPE", 1, PageHashKnown.Action.IGNORE))
        dao.findKnown("NOPE")
      }
      case("sorted by delete count") {
        dao.findAllKnown(null, PageRequest.of(0, 10, sort(Sort.Order.desc("deleteCount"), Sort.Order.asc("hash")))).content.map { it.hash to it.deleteCount }
      }
    }

    func("findAllUnknown") {
      case("unpaged") { dao.findAllUnknown(Pageable.unpaged()) }
      case("sorted by hash desc") { dao.findAllUnknown(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "hash"))) }
      case("sorted by total size") { dao.findAllUnknown(PageRequest.of(0, 10, sort(Sort.Order.desc("totalSize"), Sort.Order.asc("hash")))).content.map { it.hash } }
      case("paged") { dao.findAllUnknown(PageRequest.of(1, 1, Sort.by("hash"))) }
      case("unknown sort property") { dao.findAllUnknown(PageRequest.of(0, 5, Sort.by("nope"))).totalElements }
    }

    func("findMatchesByHash") {
      case("unpaged sorted by book") { dao.findMatchesByHash("PH1", PageRequest.of(0, 10, Sort.by("bookId"))) }
      case("paged") { dao.findMatchesByHash("PH2", PageRequest.of(1, 2, sort(Sort.Order.asc("url")))) }
      case("unpaged") { dao.findMatchesByHash("PQ1", Pageable.unpaged()) }
      case("no match") { dao.findMatchesByHash("NOPE", Pageable.unpaged()) }
      case("sort by page number desc") { dao.findMatchesByHash("PH3", PageRequest.of(0, 10, sort(Sort.Order.desc("pageNumber"), Sort.Order.desc("bookId")))).content.map { it.bookId } }
    }

    func("findMatchesByKnownHashAction") {
      case("delete auto everywhere") {
        dao.findMatchesByKnownHashAction(listOf(PageHashKnown.Action.DELETE_AUTO), null).toSortedMap().mapValues { (_, v) -> v.sortedBy { it.pageNumber } }
      }
      case("two actions in one library") {
        dao.findMatchesByKnownHashAction(listOf(PageHashKnown.Action.DELETE_AUTO, PageHashKnown.Action.IGNORE), "L2").toSortedMap().mapValues { (_, v) -> v.map { it.fileName to it.pageNumber }.sortedBy { it.second } }
      }
      case("library without matches") { dao.findMatchesByKnownHashAction(listOf(PageHashKnown.Action.DELETE_AUTO), "NOPE") }
      case("empty actions") { dao.findMatchesByKnownHashAction(emptyList(), null) }
      case("null actions") { exceptionType { dao.findMatchesByKnownHashAction(null, null) } }
    }
  }
}
