package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SearchField
import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.domain.model.SeriesSearch
import org.gotson.komga.interfaces.api.rest.dto.SeriesDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class SeriesDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.seriesDtoDao

  private fun ctx(u: KomgaUser) = SearchContext(u)

  private val byTitle = Sort.by("metadata.titleSort")

  /** ids in page order, then the page metadata */
  private fun ids(p: Page<SeriesDto>) = listOf(p.content.map { it.id }, p.totalElements, p.number, p.size, p.sort.toString())

  private fun counts(p: Page<SeriesDto>) = p.content.map { listOf(it.id, it.booksCount, it.booksReadCount, it.booksUnreadCount, it.booksInProgressCount) }

  private fun attempt(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      "throws ${e::class.java.simpleName}: ${e.message}"
    }

  override fun cases() {
    func("findAll@99") {
      case("anonymous sorted by title") {
        NzDaoSeed.seed(db, lucene = true)
        dao.findAll(Pageable.unpaged(byTitle))
      }
      case("paged") { ids(dao.findAll(PageRequest.of(1, 4, byTitle))) }
    }

    func("findAll@101") {
      case("admin read counts") { counts(dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      case("age restricted user") { ids(dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(byTitle))) }
      case("label excluded user") { ids(dao.findAll(ctx(NzDaoSeed.u3), Pageable.unpaged(byTitle))) }
      case("age excluded or label allowed user") { ids(dao.findAll(ctx(NzDaoSeed.u4), Pageable.unpaged(byTitle))) }
      case("without user") { attempt { dao.findAll(SearchContext.empty(), Pageable.unpaged()) } }
    }

    func("findAll@106") {
      NzDaoSeed.seriesConditions.forEach { (name, c) ->
        case("condition: $name") { ids(dao.findAll(SeriesSearch(c), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      }
      listOf("batman", "BAT", "naruto", "flux", "angstrom", "kishimoto", "dc comics", "zzz", "joker", "action").forEach { s ->
        case("full text: $s") { dao.findAll(SeriesSearch(fullTextSearch = s), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle)).content.map { it.id } }
      }
      case("full text relevance") { dao.findAll(SeriesSearch(fullTextSearch = "batman"), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by("relevance"))).content.map { it.id } }
      case("full text relevance several") { dao.findAll(SeriesSearch(fullTextSearch = "a"), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by(Sort.Direction.DESC, "relevance"))).content.map { it.id }.sorted() }
      case("full text and condition") {
        ids(dao.findAll(SeriesSearch(SearchCondition.LibraryId(SearchOperator.Is("L2")), fullTextSearch = "naruto"), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle)))
      }
      case("regex on title") { ids(dao.findAll(SeriesSearch(regexSearch = "^[A-Z]" to SearchField.TITLE), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      case("regex on title sort") { ids(dao.findAll(SeriesSearch(regexSearch = "^a" to SearchField.TITLE_SORT), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      listOf(
        "metadata.titleSort",
        "createdDate",
        "created",
        "lastModifiedDate",
        "lastModified",
        "booksMetadata.releaseDate",
        "readDate",
        "name",
        "booksCount",
      ).forEach { p ->
        listOf(Sort.Direction.ASC, Sort.Direction.DESC).forEach { dir ->
          case("sort $p $dir") { ids(dao.findAll(SeriesSearch(), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by(Sort.Order(dir, p), Sort.Order.asc("name"))))) }
        }
      }
      case("sort by collection number") {
        ids(dao.findAll(SeriesSearch(SearchCondition.CollectionId(SearchOperator.Is("C2"))), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by("collection.number"))))
      }
      case("sort by collection number desc") {
        ids(dao.findAll(SeriesSearch(SearchCondition.CollectionId(SearchOperator.Is("C1"))), ctx(NzDaoSeed.u1), PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "collection.number"))))
      }
      case("sort by collection number without collection") { ids(dao.findAll(SeriesSearch(), ctx(NzDaoSeed.u1), PageRequest.of(0, 20, Sort.by("collection.number")))).drop(1) }
      case("random sort") { dao.findAll(SeriesSearch(), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by("random"))).let { listOf(it.content.map { s -> s.id }.sorted(), it.totalElements) } }
      case("unknown sort") { ids(dao.findAll(SeriesSearch(), ctx(NzDaoSeed.u1), PageRequest.of(0, 20, Sort.by("nope")))).drop(1) }
      case("page after the end") { ids(dao.findAll(SeriesSearch(), ctx(NzDaoSeed.u1), PageRequest.of(3, 4, byTitle))) }
      case("restricted user with condition") {
        ids(dao.findAll(SeriesSearch(SearchCondition.Deleted(SearchOperator.IsFalse)), ctx(NzDaoSeed.u4), Pageable.unpaged(byTitle)))
      }
      case("without user") { attempt { dao.findAll(SeriesSearch(), SearchContext.empty(), Pageable.unpaged()) } }
    }

    func("findAllRecentlyUpdated") {
      case("admin") { ids(dao.findAllRecentlyUpdated(SeriesSearch(), ctx(NzDaoSeed.u1), Pageable.unpaged(Sort.by(Sort.Direction.DESC, "lastModified")))) }
      case("with condition") { ids(dao.findAllRecentlyUpdated(SeriesSearch(SearchCondition.LibraryId(SearchOperator.Is("L1"))), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      case("full text") { ids(dao.findAllRecentlyUpdated(SeriesSearch(fullTextSearch = "naruto"), ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle))) }
      case("restricted") { ids(dao.findAllRecentlyUpdated(SeriesSearch(), ctx(NzDaoSeed.u2), Pageable.unpaged(byTitle))) }
      case("without user") { attempt { dao.findAllRecentlyUpdated(SeriesSearch(), SearchContext.empty(), Pageable.unpaged()) } }
    }

    func("countByFirstCharacter") {
      case("admin") { dao.countByFirstCharacter(SeriesSearch(), ctx(NzDaoSeed.u1)).sortedBy { it.group } }
      case("condition") { dao.countByFirstCharacter(SeriesSearch(SearchCondition.Deleted(SearchOperator.IsFalse)), ctx(NzDaoSeed.u1)).sortedBy { it.group } }
      case("collection condition") { dao.countByFirstCharacter(SeriesSearch(SearchCondition.CollectionId(SearchOperator.Is("C1"))), ctx(NzDaoSeed.u1)).sortedBy { it.group } }
      case("full text") { dao.countByFirstCharacter(SeriesSearch(fullTextSearch = "a"), ctx(NzDaoSeed.u1)).sortedBy { it.group } }
      case("regex") { dao.countByFirstCharacter(SeriesSearch(regexSearch = "^[n-z]" to SearchField.TITLE_SORT), ctx(NzDaoSeed.u1)).sortedBy { it.group } }
      case("restricted") { dao.countByFirstCharacter(SeriesSearch(), ctx(NzDaoSeed.u3)).sortedBy { it.group } }
      case("no match") { dao.countByFirstCharacter(SeriesSearch(fullTextSearch = "zzzz"), ctx(NzDaoSeed.u1)) }
      case("without user") { attempt { dao.countByFirstCharacter(SeriesSearch(), SearchContext.empty()) } }
    }

    func("findByIdOrNull") {
      case("all fields for admin") { dao.findByIdOrNull("S1", "U1") }
      case("other user") { dao.findByIdOrNull("S1", "U2")?.let { listOf(it.booksReadCount, it.booksUnreadCount, it.booksInProgressCount) } }
      case("oneshot") { dao.findByIdOrNull("S5", "U1") }
      case("deleted") { dao.findByIdOrNull("S4", "U1") }
      case("missing") { dao.findByIdOrNull("NOPE", "U1") }
    }

    func("selectBase") {
      case("one row per series") { dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged()).totalElements }
    }

    func("findAll@224") {
      case("count distinct with joins") { ids(dao.findAll(SeriesSearch(SearchCondition.Tag(SearchOperator.Is("classic"))), ctx(NzDaoSeed.u1), PageRequest.of(0, 1))) }
    }

    func("readProgressConditionSeries") {
      case("read progress of the user only") { counts(dao.findAll(ctx(NzDaoSeed.u2), Pageable.unpaged(byTitle))) }
    }

    func("fetchAndMap") {
      case("collections of every series") {
        dao.findAll(ctx(NzDaoSeed.u1), Pageable.unpaged(byTitle)).content.map {
          listOf(it.id, it.metadata.genres, it.metadata.tags, it.metadata.sharingLabels, it.metadata.links, it.metadata.alternateTitles, it.booksMetadata.authors, it.booksMetadata.tags)
        }
      }
    }

    func("toColumn") {
      case("title") { dao.countByFirstCharacter(SeriesSearch(regexSearch = "ルト" to SearchField.TITLE), ctx(NzDaoSeed.u1)) }
      case("title sort") { dao.countByFirstCharacter(SeriesSearch(regexSearch = "ルト" to SearchField.TITLE_SORT), ctx(NzDaoSeed.u1)) }
    }

    func("toDto@412") {
      case("series metadata") { dao.findByIdOrNull("S3", "U1")!!.metadata }
      case("no reading direction") { dao.findByIdOrNull("S2", "U1")!!.metadata.let { listOf(it.readingDirection, it.ageRating, it.totalBookCount, it.created, it.lastModified) } }
    }

    func("toDto@451") {
      case("books metadata") { dao.findByIdOrNull("S1", "U1")!!.booksMetadata }
      case("without aggregation") {
        db.dsl.execute("insert into SERIES (ID, NAME, URL, FILE_LAST_MODIFIED, LIBRARY_ID) values ('S9', 'bare', 'file:/lib1/bare', '2020-01-01 00:00:00', 'L1')")
        exceptionType { dao.findByIdOrNull("S9", "U1") }
      }
    }
  }
}
