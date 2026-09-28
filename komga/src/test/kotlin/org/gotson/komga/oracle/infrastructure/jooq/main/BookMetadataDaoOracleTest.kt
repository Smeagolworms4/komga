package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.book
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.transactional
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

class BookMetadataDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.bookMetadataDao

  private fun meta(
    bookId: String,
    numberSort: Float = 1F,
    authors: List<Author> = emptyList(),
    tags: Set<String> = emptySet(),
    links: List<WebLink> = emptyList(),
  ) = BookMetadata(title = "title $bookId", number = "n$bookId", numberSort = numberSort, authors = authors, tags = tags, links = links, bookId = bookId)

  private val full =
    BookMetadata(
      title = "  Le Tïtre 漫画  ",
      summary = " Résumé\r\nligne 2 ",
      number = " 1.5 ",
      numberSort = 0.1F,
      releaseDate = LocalDate.of(2004, 2, 29),
      authors =
        listOf(
          Author("  Jean Dupont ", " WRITER "),
          Author("Émilie Ünïcode", "penciller"),
          Author("Jean Dupont", "writer"),
          Author("漫画家", "Translator"),
        ),
      tags = setOf("Zeta", " alpha ", "ÜNÏCODE", "", "  ", "漫画"),
      isbn = "9782205054774",
      links =
        listOf(
          WebLink("Site", URI("https://example.org/%C3%A9?q=1#frag")),
          WebLink("Ünïcode label", URI("https://漫画.example/path")),
          WebLink("Site", URI("file:/local/path")),
        ),
      titleLock = true,
      summaryLock = true,
      numberLock = true,
      numberSortLock = true,
      releaseDateLock = true,
      authorsLock = true,
      tagsLock = true,
      isbnLock = true,
      linksLock = true,
      bookId = "B2",
      createdDate = LocalDateTime.of(2001, 1, 1, 0, 0),
    )

  private fun ids(c: Collection<BookMetadata>) = c.map { it.bookId }

  private fun counts() =
    db.rawQuery(
      "select (select count(*) from BOOK_METADATA), (select count(*) from BOOK_METADATA_AUTHOR), (select count(*) from BOOK_METADATA_TAG), (select count(*) from BOOK_METADATA_LINK)",
    )

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("insert@74") {
      case("minimal") {
        db.libraryDao.insert(library("L1"))
        db.seriesDao.insert(series("S1", "L1"))
        db.bookDao.insert((1..9).map { book("B$it", "S1", "L1") })
        db.bookDao.insert((1..2200).map { book("M" + "$it".padStart(4, '0'), "S1", "L1") })
        dao.insert(meta("B1"))
        stable(dao.findById("B1"))
      }
      case("all fields") {
        dao.insert(full)
        stable(dao.findById("B2"))
      }
      case("duplicate book") { exceptionType { dao.insert(meta("B1")) } }
      case("unknown book") { exceptionType { dao.insert(meta("NOPE")) } }
      case("NaN number sort") {
        listOf(exceptionType { dao.insert(meta("B9", Float.NaN)) }, stable(dao.findByIdOrNull("B9")))
      }
    }

    func("insert@79") {
      case("empty") {
        dao.insert(emptyList())
        dao.count()
      }
      case("several") {
        dao.insert(
          listOf(
            meta("B5", -3.25F, authors = listOf(Author("b", "writer"))),
            meta("B3", 1e10F, tags = setOf("t")),
            meta("B4", Float.MAX_VALUE, links = listOf(WebLink("l", URI("https://example.org")))),
          ),
        )
        stable(dao.findAllByIds(listOf("B3", "B4", "B5")))
      }
      case("more than batch size with authors in every chunk") {
        dao.insert(
          (1..1100).map {
            val id = "M" + "$it".padStart(4, '0')
            meta(id, it.toFloat(), authors = if (it == 1 || it == 1100) listOf(Author("a$it", "writer")) else emptyList(), tags = setOf("tag$it"))
          },
        )
        counts()
      }
      case("more than batch size with authors only in the first chunk") {
        listOf(
          exceptionType {
            transactional(db) {
              dao.insert(
                (1101..2200).map {
                val id = "M" + "$it".padStart(4, '0')
                  meta(id, it.toFloat(), authors = if (it == 1101) listOf(Author("a$it", "writer")) else emptyList())
                },
              )
            }
          },
          counts(),
        )
      }
    }

    func("insertAuthors") {
      case("rows") { db.rawQuery("select * from BOOK_METADATA_AUTHOR where BOOK_ID like 'B%' order by BOOK_ID, rowid") }
    }

    func("insertTags") {
      case("rows") { db.rawQuery("select * from BOOK_METADATA_TAG where BOOK_ID like 'B%' order by BOOK_ID, rowid") }
    }

    func("insertLinks") {
      case("rows") { db.rawQuery("select * from BOOK_METADATA_LINK order by BOOK_ID, rowid") }
    }

    func("findById") {
      case("existing") { stable(dao.findById("B2")) }
      case("missing") { dao.findById("NOPE") }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("B1")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("book without metadata") { dao.findByIdOrNull("B6") }
    }

    func("findAllByIds") {
      case("empty") { dao.findAllByIds(emptyList()) }
      case("order of the result") { ids(dao.findAllByIds(listOf("B5", "B2", "B1", "B3", "NOPE"))) }
      case("duplicates") { ids(dao.findAllByIds(listOf("B1", "B1"))) }
      case("missing only") { dao.findAllByIds(setOf("X", "Y")) }
      case("more than batch size") {
        val all = dao.findAllByIds((1..2200).map { "M" + "$it".padStart(4, '0') } + listOf("B1"))
        listOf(all.size, ids(all).take(3), ids(all).takeLast(3), all.sumOf { it.authors.size }, all.sumOf { it.tags.size })
      }
    }

    func("find") {
      case("identical authors are merged") {
        sql(db, "insert into BOOK_METADATA_AUTHOR (NAME, ROLE, BOOK_ID) values ('b', 'writer', 'B5'), ('a', 'writer', 'B5')")
        dao.findById("B5").authors.map { listOf(it.name, it.role) }
      }
      case("orphan authors are ignored") {
        sql(db, "insert into BOOK_METADATA_AUTHOR (NAME, ROLE, BOOK_ID) values ('orphan', 'writer', 'B6')")
        dao.findByIdOrNull("B6")
      }
      case("groups ordered by metadata columns") {
        sql(db, "update BOOK_METADATA set CREATED_DATE = '2020-01-0' || substr(BOOK_ID, 2) || ' 00:00:00' where BOOK_ID like 'B%'")
        sql(db, "update BOOK_METADATA set CREATED_DATE = '2019-12-31 00:00:00' where BOOK_ID = 'B4'")
        ids(dao.findAllByIds(listOf("B1", "B2", "B3", "B4", "B5")))
      }
    }

    func("findTags") {
      case("normalized tags") { dao.findById("B2").tags }
      case("stored tags are not normalized") {
        sql(db, "insert into BOOK_METADATA_TAG (TAG, BOOK_ID) values ('Mixed CASE', 'B1')")
        dao.findById("B1").tags
      }
    }

    func("findLinks") {
      case("links") { dao.findById("B2").links }
      case("no link") { dao.findById("B1").links }
      case("invalid stored uri") {
        sql(db, "insert into BOOK_METADATA_LINK (LABEL, URL, BOOK_ID) values ('bad', 'https://exa mple.org', 'B1')")
        exceptionType { dao.findById("B1") }
      }
    }

    func("toDomain@261") {
      case("dates and number sort") { dao.findById("B2").let { listOf(it.createdDate, it.numberSort, it.releaseDate, it.number, it.title) } }
      case("large number sort") { dao.findAllByIds(listOf("B3", "B4", "B5")).map { it.numberSort } }
    }

    func("toDomain@289") {
      case("authors trimmed and lowercased") { dao.findById("B2").authors.map { listOf(it.name, it.role) } }
    }

    func("update@135") {
      case("all fields") {
        sql(db, "delete from BOOK_METADATA_LINK where LABEL = 'bad'")
        dao.update(
          full.copy(
            title = "Nouveau",
            summary = "",
            number = "2",
            numberSort = 2F,
            releaseDate = null,
            authors = listOf(Author("Solo", "Writer")),
            tags = setOf("new"),
            isbn = "",
            links = emptyList(),
            titleLock = false,
            linksLock = false,
          ),
        )
        stable(dao.findById("B2"))
      }
      case("created date is kept") { dao.findById("B2").createdDate }
      case("missing book") {
        dao.update(meta("B7"))
        dao.findByIdOrNull("B7")
      }
      case("missing book with authors") { exceptionType { dao.update(meta("NOPE", authors = listOf(Author("a", "b")))) } }
    }

    func("update@140") {
      case("empty") {
        dao.update(emptyList())
        dao.count()
      }
      case("several") {
        dao.update(listOf(meta("B1", 10F, tags = setOf("x", "y")), meta("B3", 30F, authors = listOf(Author("c", "inker")))))
        stable(dao.findAllByIds(listOf("B1", "B3")))
      }
    }

    func("updateMetadata") {
      case("removes previous authors, tags and links") {
        dao.update(meta("B4"))
        listOf(dao.findById("B4").let { listOf(it.authors, it.tags, it.links) }, db.rawQuery("select count(*) from BOOK_METADATA_LINK where BOOK_ID = 'B4'"))
      }
      case("last modified date") { stable(dao.findById("B4").lastModifiedDate) }
    }

    func("delete@242") {
      case("existing") {
        dao.delete("B2")
        listOf(dao.findByIdOrNull("B2"), counts())
      }
      case("missing") {
        dao.delete("NOPE")
        counts()
      }
    }

    func("delete@250") {
      case("empty") {
        dao.delete(emptyList())
        counts()
      }
      case("several") {
        dao.delete(listOf("B3", "NOPE", "B3"))
        counts()
      }
      case("more than batch size") {
        dao.delete((1..2200).map { "M" + "$it".padStart(4, '0') })
        listOf(counts(), ids(dao.findAllByIds((1..9).map { "B$it" })))
      }
    }

    func("count") {
      case("after deletions") { dao.count() }
    }
  }
}
