package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.EpubTocEntry
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.MediaFile
import org.gotson.komga.domain.model.ProxyExtension
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.book
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.transactional

class MediaDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.mediaDao

  private fun mid(it: Int) = "M" + "$it".padStart(4, '0')

  private fun page(
    n: Int,
    hash: String = "",
  ) = BookPage(fileName = "p$n.jpg", mediaType = "image/jpeg", dimension = Dimension(100 + n, 200 + n), fileHash = hash, fileSize = 1000L + n)

  private val epub =
    MediaExtensionEpub(
      toc = listOf(EpubTocEntry("Chapitre 1 — ünï", "ch1.xhtml", listOf(EpubTocEntry("1.1", null)))),
      landmarks = listOf(EpubTocEntry("Cover", "cover.xhtml")),
      isFixedLayout = true,
      positions =
        listOf(
          R2Locator(
            href = "ch1.xhtml",
            type = "application/xhtml+xml",
            title = "Chap",
            locations = R2Locator.Location(fragments = listOf("f1", "f2"), progression = 0.5F, position = 1, totalProgression = 0.25F),
            text = R2Locator.Text(highlight = "hi"),
            koboSpan = "kobo.1.1",
          ),
        ),
    )

  private val full =
    Media(
      status = Media.Status.READY,
      mediaType = "application/epub+zip",
      pages =
        listOf(
          BookPage("cover.jpg", "image/jpeg", Dimension(600, 800), "hash0", 12345),
          BookPage("Ünïcode 漫画.png", "image/png", null, "", null),
          BookPage("p2.webp", "image/webp", Dimension(0, 0), "", 0),
        ),
      pageCount = 7,
      files =
        listOf(
          MediaFile("OEBPS/ch1.xhtml", "application/xhtml+xml", MediaFile.SubType.EPUB_PAGE, 2048),
          MediaFile("OEBPS/style.css", null, null, null),
          MediaFile("OEBPS/font.otf", "font/otf", MediaFile.SubType.EPUB_ASSET, 0),
        ),
      comment = "Commentaire ünïcode",
      extension = epub,
      bookId = "B2",
      epubDivinaCompatible = true,
      epubIsKepub = true,
    )

  private fun counts() = db.rawQuery("select (select count(*) from MEDIA), (select count(*) from MEDIA_PAGE), (select count(*) from MEDIA_FILE)")

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("insert@131") {
      case("minimal") {
        db.libraryDao.insert(library("L1"))
        db.libraryDao.insert(library("L2"))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesDao.insert(series("S2", "L2"))
        db.bookDao.insert((1..9).map { book("B$it", "S1", "L1") } + listOf(book("C1", "S2", "L2")))
        db.bookDao.insert((1..2200).map { book(mid(it), "S1", "L1") })
        dao.insert(Media(bookId = "B1"))
        stable(dao.findById("B1"))
      }
      case("all fields") {
        dao.insert(full)
        stable(dao.findById("B2"))
      }
      case("stored values") { db.rawQuery("select BOOK_ID, MEDIA_TYPE, STATUS, COMMENT, PAGE_COUNT, EXTENSION_CLASS, EXTENSION_VALUE_BLOB is not null, EPUB_DIVINA_COMPATIBLE, EPUB_IS_KEPUB from MEDIA order by BOOK_ID") }
      case("proxy extension is not stored") {
        val proxy = dao.findById("B2").extension
        dao.insert(Media(bookId = "B3", extension = proxy, status = Media.Status.ERROR, comment = "ERR_1234"))
        db.rawQuery("select STATUS, COMMENT, EXTENSION_CLASS, EXTENSION_VALUE_BLOB from MEDIA where BOOK_ID = 'B3'")
      }
      case("duplicate book") { exceptionType { dao.insert(Media(bookId = "B1")) } }
      case("unknown book") { exceptionType { dao.insert(Media(bookId = "NOPE")) } }
    }

    func("insert@136") {
      case("empty") {
        dao.insert(emptyList())
        counts()
      }
      case("several") {
        dao.insert(
          listOf(
            Media(Media.Status.READY, "application/zip", pages = listOf(page(1, "h1"), page(2), page(3, "h3")), bookId = "B4"),
            Media(Media.Status.OUTDATED, "application/pdf", pages = listOf(page(1)), files = listOf(MediaFile("x")), bookId = "B5"),
            Media(Media.Status.UNSUPPORTED, "application/x-rar-compressed; version=5", bookId = "B6"),
            Media(Media.Status.READY, "application/zip", pages = (1..6).map { page(it, if (it <= 2) "h$it" else "") }, bookId = "C1"),
          ),
        )
        stable(listOf(dao.findById("B4"), dao.findById("B5"), dao.findById("B6")))
      }
      case("more than batch size with pages in every chunk") {
        dao.insert((1..1100).map { Media(Media.Status.READY, "application/zip", pages = if (it % 1000 == 1) listOf(page(it)) else emptyList(), files = listOf(MediaFile("f$it")), bookId = mid(it)) })
        counts()
      }
      case("more than batch size with pages only in the first chunk") {
        listOf(
          exceptionType { transactional(db) { dao.insert((1101..2200).map { Media(Media.Status.READY, "application/zip", pages = if (it == 1101) listOf(page(it)) else emptyList(), bookId = mid(it)) }) } },
          counts(),
        )
      }
    }

    func("insertPages") {
      case("rows") { db.rawQuery("select BOOK_ID, FILE_NAME, MEDIA_TYPE, NUMBER, WIDTH, HEIGHT, FILE_HASH, FILE_SIZE from MEDIA_PAGE where BOOK_ID not like 'M%' order by BOOK_ID, NUMBER") }
    }

    func("insertFiles") {
      case("rows") { db.rawQuery("select BOOK_ID, FILE_NAME, MEDIA_TYPE, SUB_TYPE, FILE_SIZE from MEDIA_FILE where BOOK_ID not like 'M%' order by BOOK_ID, rowid") }
    }

    func("findById") {
      case("existing") { stable(dao.findById("B4")) }
      case("missing") { dao.findById("NOPE") }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("B5")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("book without media") { dao.findByIdOrNull("B7") }
    }

    func("find") {
      case("pages ordered by number") {
        sql(db, "update MEDIA_PAGE set NUMBER = 10 - NUMBER where BOOK_ID = 'B4'")
        dao.findById("B4").pages.map { it.fileName }
      }
      case("identical pages are merged") {
        sql(db, "insert into MEDIA_PAGE (BOOK_ID, FILE_NAME, MEDIA_TYPE, NUMBER, FILE_HASH) values ('B6', 'same.jpg', 'image/jpeg', 1, ''), ('B6', 'same.jpg', 'image/jpeg', 2, '')")
        dao.findById("B6").pages.map { it.fileName }
      }
      case("orphan pages and files are ignored") {
        sql(db, "insert into MEDIA_PAGE (BOOK_ID, FILE_NAME, MEDIA_TYPE, NUMBER, FILE_HASH) values ('B7', 'orphan.jpg', 'image/jpeg', 0, '')")
        sql(db, "insert into MEDIA_FILE (BOOK_ID, FILE_NAME) values ('B7', 'orphan.xml')")
        dao.findByIdOrNull("B7")
      }
      case("files of the media") { dao.findById("B2").files }
    }

    func("findExtensionByIdOrNull") {
      case("epub extension") { dao.findExtensionByIdOrNull("B2") }
      case("no extension") { dao.findExtensionByIdOrNull("B1") }
      case("missing book") { dao.findExtensionByIdOrNull("NOPE") }
      case("book without media") { dao.findExtensionByIdOrNull("B7") }
      case("proxy extension in toDomain") { dao.findById("B2").extension }
      case("invalid blob") {
        sql(db, "update MEDIA set EXTENSION_CLASS = 'org.gotson.komga.domain.model.MediaExtensionEpub', EXTENSION_VALUE_BLOB = X'00010203' where BOOK_ID = 'B5'")
        listOf(dao.findExtensionByIdOrNull("B5"), dao.findById("B5").extension)
      }
      case("unknown class") {
        sql(db, "update MEDIA set EXTENSION_CLASS = 'org.gotson.komga.domain.model.Nope' where BOOK_ID = 'B5'")
        listOf(dao.findExtensionByIdOrNull("B5"), exceptionType { dao.findById("B5") })
      }
      case("class that is not an extension") {
        sql(db, "update MEDIA set EXTENSION_CLASS = 'org.gotson.komga.domain.model.MediaExtension' where BOOK_ID = 'B5'")
        listOf(dao.findExtensionByIdOrNull("B5"), dao.findById("B5").extension)
      }
    }

    func("getPagesSizes") {
      case("empty") { dao.getPagesSizes(emptyList()) }
      case("some") { dao.getPagesSizes(listOf("B4", "B1", "NOPE", "B2", "B7")) }
      case("duplicates") { dao.getPagesSizes(listOf("B2", "B2")) }
      case("more than batch size") { dao.getPagesSizes((1..2200).map { mid(it) }).let { listOf(it.size, it.take(2)) } }
    }

    func("findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash") {
      case("no hashing required") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L1", listOf("application/zip"), 0) }
      case("one page each side") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L1", listOf("application/zip", "application/epub+zip"), 1).sorted().take(5) }
      case("two pages each side") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L2", listOf("application/zip"), 2) }
      case("one page each side in other library") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L2", listOf("application/zip"), 1) }
      case("more pages than the book has") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L2", listOf("application/zip"), 10) }
      case("epub") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L1", listOf("application/epub+zip"), 5) }
      case("no media type") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L1", emptyList(), 5) }
      case("not ready") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L1", listOf("application/pdf"), 5) }
      case("unknown library") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("NOPE", listOf("application/zip"), 5) }
      case("negative page hashing") { dao.findAllBookIdsByLibraryIdAndMediaTypeAndWithMissingPageHash("L2", listOf("application/zip"), -1) }
    }

    func("toDomain@318") {
      case("dates") {
        sql(db, "update MEDIA set CREATED_DATE = '2020-03-29 00:59:59', LAST_MODIFIED_DATE = '2020-03-29 01:00:00' where BOOK_ID = 'B1'")
        dao.findById("B1").let { listOf(it.createdDate, it.lastModifiedDate) }
      }
      case("unknown status") {
        sql(db, "update MEDIA set STATUS = 'BOGUS' where BOOK_ID = 'B1'")
        exceptionType { dao.findById("B1") }
      }
      case("lowercase status") {
        sql(db, "update MEDIA set STATUS = 'ready' where BOOK_ID = 'B1'")
        exceptionType { dao.findById("B1") }
      }
    }

    func("toDomain@336") {
      case("page with only a width") {
        sql(db, "update MEDIA set STATUS = 'READY' where BOOK_ID = 'B1'")
        sql(db, "insert into MEDIA_PAGE (BOOK_ID, FILE_NAME, MEDIA_TYPE, NUMBER, WIDTH, FILE_HASH) values ('B1', 'w.jpg', 'image/jpeg', 0, 50, '')")
        dao.findById("B1").pages
      }
    }

    func("toDomain@345") {
      case("file sub types") { dao.findById("B2").files.map { it.subType } }
      case("unknown sub type") {
        sql(db, "insert into MEDIA_FILE (BOOK_ID, FILE_NAME, SUB_TYPE) values ('B1', 'bad', 'EPUB_BOGUS')")
        exceptionType { dao.findById("B1") }
      }
    }

    func("update") {
      case("all fields") {
        sql(db, "delete from MEDIA_FILE where BOOK_ID = 'B1'")
        dao.update(
          Media(
            status = Media.Status.READY,
            mediaType = "application/zip",
            pages = listOf(page(7, "h7")),
            files = listOf(MediaFile("ComicInfo.xml", "application/xml")),
            comment = null,
            extension = MediaExtensionEpub(isFixedLayout = false),
            bookId = "B1",
            epubDivinaCompatible = true,
          ),
        )
        listOf(stable(dao.findById("B1")), dao.findExtensionByIdOrNull("B1"))
      }
      case("null extension keeps the stored one") {
        dao.update(dao.findById("B1").copy(extension = null, comment = "kept"))
        listOf(dao.findExtensionByIdOrNull("B1"), dao.findById("B1").comment)
      }
      case("proxy extension keeps the stored one") {
        dao.update(dao.findById("B2").copy(bookId = "B1", pages = emptyList(), files = emptyList()))
        listOf(dao.findExtensionByIdOrNull("B1"), stable(dao.findById("B1")))
      }
      case("missing media") {
        dao.update(Media(bookId = "B8", status = Media.Status.READY))
        listOf(dao.findByIdOrNull("B8"), counts())
      }
      case("missing media with pages") {
        dao.update(Media(bookId = "B8", pages = listOf(page(1))))
        listOf(dao.findByIdOrNull("B8"), db.rawQuery("select count(*) from MEDIA_PAGE where BOOK_ID = 'B8'"))
      }
      case("missing book with pages") { exceptionType { dao.update(Media(bookId = "NOPE", pages = listOf(page(1)))) } }
    }

    func("copy") {
      case("to a book with media") {
        dao.insert(Media(bookId = "B9", comment = "to be replaced"))
        dao.copy("B2", "B9")
        listOf(stable(dao.findById("B9")), dao.findExtensionByIdOrNull("B9"))
      }
      case("to a book without media") {
        dao.copy("B2", "B7")
        listOf(dao.findByIdOrNull("B7"), db.rawQuery("select count(*) from MEDIA_PAGE where BOOK_ID = 'B7'"))
      }
      case("from a missing media") { exceptionType { dao.copy("NOPE", "B9") } }
      case("to itself") {
        dao.copy("B2", "B2")
        stable(dao.findById("B2"))
      }
    }

    func("delete@301") {
      case("existing") {
        dao.delete("B2")
        listOf(dao.findByIdOrNull("B2"), counts())
      }
      case("missing") {
        dao.delete("NOPE")
        counts()
      }
    }

    func("delete@308") {
      case("empty") {
        dao.delete(emptyList())
        counts()
      }
      case("several") {
        dao.delete(listOf("B4", "NOPE", "B4", "B7", "B8"))
        counts()
      }
      case("more than batch size") {
        dao.delete((1..2200).map { mid(it) })
        listOf(counts(), db.rawQuery("select BOOK_ID from MEDIA order by BOOK_ID"))
      }
    }

    func("count") {
      case("after deletions") { dao.count() }
    }
  }
}
