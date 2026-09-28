package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.BookSearch
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class BookDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.bookDtoDao

  private fun ctx(u: KomgaUser) = SearchContext(u)

  private val byName = Sort.by("name")

  private fun ids(p: Page<BookDto>) = listOf(p.content.map { it.id }, p.totalElements, p.number, p.size, p.sort.toString())

  private fun attempt(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      "throws ${e::class.java.simpleName}: ${e.message}"
    }

  private fun rl(id: String) = db.readListDao.findByIdOrNull(id, SearchContext.empty())!!

  override fun cases() {
    func("findAll@98") {
      case("anonymous sorted by name") {
        NzDaoSeed.seed(db, lucene = true)
        dao.findAll(Pageable.unpaged(byName))
      }
      case("paged") { ids(dao.findAll(PageRequest.of(2, 4, byName))) }
    }

    func("findAll@100") {
      case("admin read progress") { dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged(byName)).content.map { listOf(it.id, it.readProgress?.page, it.readProgress?.completed) } }
      case("age restricted user") { ids(dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(byName))) }
      case("label excluded user") { ids(dao.findAll(ctx(NzDaoSeed.u3), Pageable.unpaged(byName))) }
      case("age excluded or label allowed user") { ids(dao.findAll(ctx(NzDaoSeed.u4), Pageable.unpaged(byName))) }
      case("without user") { attempt { dao.findAll(SearchContext.empty(), Pageable.unpaged()) } }
    }

    func("findAll@105") {
      NzDaoSeed.bookConditions.forEach { (name, c) ->
        case("condition: $name") { ids(dao.findAll(BookSearch(c), ctx(NzDaoSeed.u1), Pageable.unpaged(byName))) }
      }
      listOf("year", "joke", "batman", "naruto", "うずまき", "miller", "classic", "flux", "zzz", "9781401207526").forEach { s ->
        case("full text: $s") { dao.findAll(BookSearch(fullTextSearch = s), ctx(NzDaoSeed.u1), Pageable.unpaged(byName)).content.map { it.id } }
      }
      case("full text relevance") { dao.findAll(BookSearch(fullTextSearch = "killing"), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by("relevance"))).content.map { it.id } }
      case("full text relevance several") { dao.findAll(BookSearch(fullTextSearch = "year"), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by(Sort.Direction.DESC, "relevance"))).content.map { it.id }.sorted() }
      case("full text and condition paged") {
        ids(dao.findAll(BookSearch(SearchCondition.SeriesId(SearchOperator.Is("S1")), fullTextSearch = "year"), ctx(NzDaoSeed.u1), PageRequest.of(0, 1, byName)))
      }
      listOf(
        "name",
        "series",
        "created",
        "createdDate",
        "lastModified",
        "lastModifiedDate",
        "fileSize",
        "size",
        "fileHash",
        "url",
        "media.status",
        "media.comment",
        "media.mediaType",
        "media.pagesCount",
        "metadata.title",
        "metadata.numberSort",
        "metadata.releaseDate",
        "readProgress.lastModified",
        "readProgress.readDate",
      ).forEach { p ->
        listOf(Sort.Direction.ASC, Sort.Direction.DESC).forEach { dir ->
          case("sort $p $dir") { ids(dao.findAll(BookSearch(), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by(Sort.Order(dir, p), Sort.Order.asc("name"))))) }
        }
      }
      case("sort by read list number") {
        ids(dao.findAll(BookSearch(SearchCondition.ReadListId(SearchOperator.Is("RL1"))), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by("readList.number"))))
      }
      case("sort by read list number desc paged") {
        ids(dao.findAll(BookSearch(SearchCondition.ReadListId(SearchOperator.Is("RL2"))), ctx(NzDaoSeed.u1), PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "readList.number"))))
      }
      case("sort by read list number without read list") { ids(dao.findAll(BookSearch(), ctx(NzDaoSeed.u1), PageRequest.of(0, 20, Sort.by("readList.number")))).drop(1) }
      case("unknown sort") { ids(dao.findAll(BookSearch(), ctx(NzDaoSeed.u1), PageRequest.of(0, 20, Sort.by("nope")))).drop(1) }
      case("page after the end") { ids(dao.findAll(BookSearch(), ctx(NzDaoSeed.u1), PageRequest.of(5, 4, byName))) }
      case("restricted user with condition") {
        ids(dao.findAll(BookSearch(SearchCondition.Deleted(SearchOperator.IsFalse)), ctx(NzDaoSeed.u2), Pageable.unpaged(byName)))
      }
      case("read status for other user") {
        ids(dao.findAll(BookSearch(SearchCondition.ReadStatus(SearchOperator.Is(org.gotson.komga.domain.model.ReadStatus.IN_PROGRESS))), ctx(NzDaoSeed.u2), Pageable.unpaged(byName)))
      }
      case("without user") { attempt { dao.findAll(BookSearch(), SearchContext.empty(), Pageable.unpaged()) } }
    }

    func("findByIdOrNull") {
      case("all fields for admin") { dao.findByIdOrNull("B1", "U1") }
      case("read progress of other user") { dao.findByIdOrNull("B1", "U2")?.readProgress }
      case("error media without release date") { dao.findByIdOrNull("B5", "U1") }
      case("oneshot epub") { dao.findByIdOrNull("B10", "U1") }
      case("missing") { dao.findByIdOrNull("NOPE", "U1") }
    }

    func("findPreviousInSeriesOrNull") {
      case("middle") { dao.findPreviousInSeriesOrNull("B2", "U1")?.id }
      case("first") { dao.findPreviousInSeriesOrNull("B1", "U1") }
      case("decimal number") { dao.findPreviousInSeriesOrNull("B7", "U1")?.id }
      case("unknown book") { exceptionType { dao.findPreviousInSeriesOrNull("NOPE", "U1") } }
    }

    func("findNextInSeriesOrNull") {
      case("middle") { dao.findNextInSeriesOrNull("B2", "U1")?.id }
      case("last") { dao.findNextInSeriesOrNull("B3", "U1") }
      case("decimal number to deleted book") { dao.findNextInSeriesOrNull("B7", "U1")?.id }
      case("oneshot") { dao.findNextInSeriesOrNull("B10", "U1") }
    }

    func("findSiblingSeries") {
      case("dto of the sibling") { dao.findNextInSeriesOrNull("B6", "U1")?.let { listOf(it.id, it.metadata.numberSort, it.readProgress?.completed) } }
    }

    func("findPreviousInReadListOrNull") {
      case("ordered list middle") { dao.findPreviousInReadListOrNull(rl("RL1"), "B1", ctx(NzDaoSeed.u1))?.id }
      case("ordered list first") { dao.findPreviousInReadListOrNull(rl("RL1"), "B3", ctx(NzDaoSeed.u1)) }
      case("unordered list by release date") { dao.findPreviousInReadListOrNull(rl("RL2"), "B7", ctx(NzDaoSeed.u1))?.id }
      case("unordered list first") { dao.findPreviousInReadListOrNull(rl("RL2"), "B5", ctx(NzDaoSeed.u1)) }
      case("book not in list") { dao.findPreviousInReadListOrNull(rl("RL2"), "B1", ctx(NzDaoSeed.u1)) }
      case("restricted user") { dao.findPreviousInReadListOrNull(rl("RL1"), "B6", ctx(NzDaoSeed.u2))?.id }
      case("without user") { attempt { dao.findPreviousInReadListOrNull(rl("RL1"), "B1", SearchContext.empty()) } }
    }

    func("findNextInReadListOrNull") {
      case("ordered list middle") { dao.findNextInReadListOrNull(rl("RL1"), "B1", ctx(NzDaoSeed.u1))?.id }
      case("ordered list last") { dao.findNextInReadListOrNull(rl("RL1"), "B6", ctx(NzDaoSeed.u1)) }
      case("unordered list by release date") { dao.findNextInReadListOrNull(rl("RL2"), "B4", ctx(NzDaoSeed.u1))?.id }
      case("unordered list last") { dao.findNextInReadListOrNull(rl("RL2"), "B11", ctx(NzDaoSeed.u1)) }
      case("restricted user skips forbidden books") { dao.findNextInReadListOrNull(rl("RL1"), "B1", ctx(NzDaoSeed.u4))?.id }
      case("library restricted user in unordered list") { dao.findNextInReadListOrNull(rl("RL2"), "B7", ctx(NzDaoSeed.u3))?.id }
    }

    func("findSiblingReadList") {
      case("book not in ordered list") { dao.findNextInReadListOrNull(rl("RL1"), "B9", ctx(NzDaoSeed.u1))?.id }
    }

    func("findAllOnDeck") {
      case("admin") { ids(dao.findAllOnDeck("U1", null, Pageable.unpaged(), ContentRestrictions())) }
      case("paged") { ids(dao.findAllOnDeck("U1", null, PageRequest.of(0, 1), ContentRestrictions())) }
      case("library filter") { ids(dao.findAllOnDeck("U1", listOf("L2"), Pageable.unpaged(), ContentRestrictions())) }
      case("restrictions") { ids(dao.findAllOnDeck("U1", null, Pageable.unpaged(), NzDaoSeed.u2.restrictions)) }
      case("other user") { ids(dao.findAllOnDeck("U2", null, Pageable.unpaged(), ContentRestrictions())) }
      case("user without progress") { ids(dao.findAllOnDeck("U4", null, Pageable.unpaged(), ContentRestrictions())) }
    }

    func("findAllDuplicates") {
      case("same hash and size") { ids(dao.findAllDuplicates("U1", Pageable.unpaged(byName))) }
      case("paged") { ids(dao.findAllDuplicates("U1", PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "name")))) }
      case("same hash different size") {
        db.dsl.execute("update BOOK set FILE_HASH = 'H2' where ID = 'B5'")
        ids(dao.findAllDuplicates("U1", Pageable.unpaged(byName)))
      }
      case("unsorted") { dao.findAllDuplicates("U2", Pageable.unpaged()).let { listOf(it.content.map { b -> b.id }.sorted(), it.totalElements, it.sort.toString()) } }
    }

    func("readProgressCondition") {
      case("only the user progress") { dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(byName)).content.mapNotNull { b -> b.readProgress?.let { b.id } } }
    }

    func("selectBase") {
      case("series title joined") { dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged(byName)).content.map { it.seriesTitle } }
    }

    func("fetchAndMap") {
      case("authors tags and links") {
        db.bookMetadataDao.update(NzDaoSeed.bookMetadata.first { it.bookId == "B2" }.copy(links = listOf(org.gotson.komga.domain.model.WebLink("wiki", java.net.URI("https://example.org/b2")))))
        dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged(byName)).content.map { listOf(it.id, it.metadata.authors, it.metadata.tags, it.metadata.links) }
      }
    }

    func("toDto@478") {
      case("book fields") { dao.findByIdOrNull("B11", "U1")!!.let { listOf(it.url, it.fileLastModified, it.created, it.lastModified, it.sizeBytes, it.fileHash, it.deleted, it.oneshot, it.number) } }
      case("deleted book") { dao.findByIdOrNull("B8", "U1")!!.deleted }
    }

    func("toDto@503") {
      case("media") { listOf("B4", "B5", "B9", "B11").map { dao.findByIdOrNull(it, "U1")!!.media } }
      case("book without media nor metadata") {
        db.dsl.execute("insert into BOOK (ID, NAME, URL, NUMBER, FILE_LAST_MODIFIED, FILE_SIZE, LIBRARY_ID, SERIES_ID) values ('B99', 'bare', 'file:/lib1/S1/bare.cbz', 9, '2020-01-01 00:00:00', 1, 'L1', 'S1')")
        exceptionType { dao.findByIdOrNull("B99", "U1") }
      }
    }

    func("toDto@513") {
      case("metadata") { dao.findByIdOrNull("B7", "U1")!!.metadata }
    }

    func("toDto@540") {
      case("read progress") { dao.findByIdOrNull("B2", "U1")!!.readProgress }
    }
  }
}
