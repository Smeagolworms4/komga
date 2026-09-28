package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.domain.service.PageHashLifecycle
import org.gotson.komga.interfaces.api.rest.PageHashController
import org.gotson.komga.interfaces.api.rest.dto.PageHashCreationDto
import org.gotson.komga.interfaces.api.rest.dto.PageHashMatchDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.entity
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class PageHashControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls, answers depending on the hash (same fake in the TypeScript twin) */
  private val lifecycle =
    mockk<PageHashLifecycle> {
      every { getPage(any(), any()) } answers {
        calls.add("getPage", firstArg<String>(), secondArg<Int?>())
        when (firstArg<String>()) {
          "ph1" -> TypedBytes(byteArrayOf(1, 2, 3), "image/jpeg")
          "ph2" -> TypedBytes(byteArrayOf(4), "not a media type")
          else -> null
        }
      }
      every { createOrUpdate(any()) } answers {
        val p = firstArg<PageHashKnown>()
        calls.add("createOrUpdate", p.hash, p.size, p.action, p.deleteCount, p.matchCount)
        if (p.hash == "bad") throw IllegalArgumentException("bad hash")
      }
    }
  private val c = PageHashController(db.pageHashDao, lifecycle, taskEmitter(db, calls))
  private val p20 = PageRequest.of(0, 20)

  override fun cases() {
    func("getKnownPageHashes") {
      case("empty") { c.getKnownPageHashes(null, p20) }
      case("all") {
        RestSamples.seed(db)
        db.pageHashDao.insert(PageHashKnown("ph1", 100, PageHashKnown.Action.DELETE_AUTO, createdDate = RestOracle.FIXED), byteArrayOf(9, 8))
        db.pageHashDao.insert(PageHashKnown("zz", null, PageHashKnown.Action.IGNORE, createdDate = RestOracle.FIXED), null)
        db.pageHashDao.insert(PageHashKnown("ph3", 300, PageHashKnown.Action.DELETE_MANUAL, createdDate = RestOracle.FIXED), null)
        stable(c.getKnownPageHashes(null, p20))
      }
      case("by actions") { stable(c.getKnownPageHashes(listOf(PageHashKnown.Action.IGNORE, PageHashKnown.Action.DELETE_MANUAL), p20)) }
      case("empty actions") { stable(c.getKnownPageHashes(emptyList(), p20)) }
      case("sorted, paged") { stable(c.getKnownPageHashes(null, PageRequest.of(0, 2, Sort.by(Sort.Order.desc("hash"))))) }
      case("unpaged") { stable(c.getKnownPageHashes(null, Pageable.unpaged())) }
    }
    func("getKnownPageHashThumbnail") {
      case("with thumbnail") { c.getKnownPageHashThumbnail("ph1") }
      case("without thumbnail") { c.getKnownPageHashThumbnail("zz") }
      case("unknown") { c.getKnownPageHashThumbnail("nope") }
    }
    func("getUnknownPageHashes") {
      case("default") { c.getUnknownPageHashes(p20) }
      case("page 1 of 1") { c.getUnknownPageHashes(PageRequest.of(1, 1)) }
      case("unpaged") { c.getUnknownPageHashes(Pageable.unpaged()) }
    }
    func("getPageHashMatches") {
      case("ph2") { c.getPageHashMatches("ph2", p20) }
      case("paged") { c.getPageHashMatches("ph1", PageRequest.of(1, 2)) }
      case("sorted by url desc") { c.getPageHashMatches("ph1", PageRequest.of(0, 20, Sort.by(Sort.Order.desc("url")))) }
      case("unknown") { c.getPageHashMatches("nope", p20) }
    }
    func("getUnknownPageHashThumbnail") {
      case("jpeg") { listOf(entity(c.getUnknownPageHashThumbnail("ph1", 200)), calls.take()) }
      case("invalid media type") { listOf(entity(c.getUnknownPageHashThumbnail("ph2")), calls.take()) }
      case("not found") { listOf(exceptionType { c.getUnknownPageHashThumbnail("nope") }, calls.take()) }
    }
    func("createOrUpdateKnownPageHash") {
      case("ok") {
        c.createOrUpdateKnownPageHash(PageHashCreationDto("abc", 12, PageHashKnown.Action.DELETE_AUTO))
        calls.take()
      }
      case("no size") {
        c.createOrUpdateKnownPageHash(PageHashCreationDto("abc", null, PageHashKnown.Action.IGNORE))
        calls.take()
      }
      case("illegal argument") { listOf(exceptionType { c.createOrUpdateKnownPageHash(PageHashCreationDto("bad", 1, PageHashKnown.Action.IGNORE)) }, calls.take()) }
      case("illegal argument message") {
        try {
          c.createOrUpdateKnownPageHash(PageHashCreationDto("bad", 1, PageHashKnown.Action.IGNORE))
          null
        } catch (e: Exception) {
          listOf(e.message, calls.take())
        }
      }
    }
    func("deleteDuplicatePagesByPageHash") {
      case("matches in several books") {
        c.deleteDuplicatePagesByPageHash("ph1")
        listOf(tasks(db), calls.take())
      }
      case("unknown hash") {
        c.deleteDuplicatePagesByPageHash("nope")
        listOf(tasks(db), calls.take())
      }
    }
    func("deleteSingleMatchByPageHash") {
      case("match") {
        c.deleteSingleMatchByPageHash("ph3", PageHashMatchDto("B2", "/lib1/Alpha/Alpha-2.cbz", 3, "p3.jpg", 300, "image/jpeg"))
        listOf(tasks(db), calls.take())
      }
    }
  }
}
