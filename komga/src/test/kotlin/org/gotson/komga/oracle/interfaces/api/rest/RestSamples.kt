package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.BookMetadataAggregation
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A small library for the controller oracles, written through the real DAOs (same data in
 * test/unit/interfaces/api/rest/rest-samples.ts). Fixed ids and dates.
 *
 * - libraries L1 "Comics" (S1, S2), L2 "Manga" (S3 oneshot)
 * - S1 "Alpha" (age 10, label kids): B1 (read), B2 (in progress); S2 "beta" (age 16, label adult): B3;
 *   S3 "Oneshot" (no age rating): B4 (deleted: false)
 * - collections C1 [S2, S1] (ordered), C2 [S3]; read lists R1 [B3, B1], R2 [B4]
 * - users: ADMIN (admin), ALL (all libraries), L1ONLY (library L1), KIDS (age <= 12 allowed only), NOADULT (excludes label adult)
 */
object RestSamples {
  private fun d(
    y: Int,
    m: Int,
    day: Int,
  ) = LocalDateTime.of(y, m, day, 10, 0, 0)

  val admin = user("ADMIN", roles = setOf(UserRoles.ADMIN, UserRoles.FILE_DOWNLOAD, UserRoles.PAGE_STREAMING))
  val all = user("ALL", roles = setOf(UserRoles.FILE_DOWNLOAD, UserRoles.PAGE_STREAMING))
  val l1Only = user("L1ONLY", sharedAllLibraries = false, sharedLibrariesIds = setOf("L1"))
  val kids = user("KIDS", restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY)))
  val noAdult = user("NOADULT", restrictions = ContentRestrictions(labelsExclude = setOf("adult")))
  val users = listOf(admin, all, l1Only, kids, noAdult)

  val l1 = Library(name = "Comics", root = URL("file:/lib1"), id = "L1", createdDate = d(2020, 1, 1))
  val l2 = Library(name = "Manga", root = URL("file:/lib2"), id = "L2", createdDate = d(2020, 1, 2))

  val s1 = Series(name = "Alpha", url = URL("file:/lib1/Alpha"), fileLastModified = d(2020, 2, 1), id = "S1", libraryId = "L1", createdDate = d(2020, 2, 1))
  val s2 = Series(name = "beta", url = URL("file:/lib1/beta"), fileLastModified = d(2020, 2, 2), id = "S2", libraryId = "L1", createdDate = d(2020, 2, 2))
  val s3 = Series(name = "Oneshot", url = URL("file:/lib2/Oneshot.cbz"), fileLastModified = d(2020, 2, 3), id = "S3", libraryId = "L2", oneshot = true, createdDate = d(2020, 2, 3))

  private fun book(
    id: String,
    series: Series,
    number: Int,
    name: String,
  ) = Book(
    name = name,
    url = URL("${series.url}/$name.cbz"),
    fileLastModified = d(2020, 3, number),
    fileSize = 1000L * number,
    fileHash = "hash$id",
    number = number,
    id = id,
    seriesId = series.id,
    libraryId = series.libraryId,
    oneshot = series.oneshot,
    createdDate = d(2020, 3, number + 10),
  )

  val b1 = book("B1", s1, 1, "Alpha-1")
  val b2 = book("B2", s1, 2, "Alpha-2")
  val b3 = book("B3", s2, 1, "beta-1")
  val b4 = book("B4", s3, 1, "Oneshot")
  val books = listOf(b1, b2, b3, b4)

  private fun media(book: Book) =
    Media(
      status = Media.Status.READY,
      mediaType = "application/zip",
      pages = (1..3).map { BookPage("p$it.jpg", "image/jpeg", Dimension(800, 1200), "ph$it", 100L * it) },
      bookId = book.id,
      createdDate = book.createdDate,
    )

  private fun bookMetadata(
    book: Book,
    release: LocalDate?,
    tags: Set<String>,
  ) = BookMetadata(
    title = "${book.name} title",
    summary = "summary ${book.id}",
    number = book.number.toString(),
    numberSort = book.number.toFloat(),
    releaseDate = release,
    authors = listOf(Author("Author ${book.seriesId}", "writer"), Author("Pen ${book.id}", "penciller")),
    tags = tags,
    isbn = "",
    links = listOf(WebLink("home", URI("https://example.org/${book.id}"))),
    bookId = book.id,
    createdDate = book.createdDate,
  )

  private fun seriesMetadata(
    series: Series,
    title: String,
    age: Int?,
    labels: Set<String>,
    genres: Set<String>,
    tags: Set<String>,
    language: String,
    publisher: String,
  ) = SeriesMetadata(
    title = title,
    ageRating = age,
    sharingLabels = labels,
    genres = genres,
    tags = tags,
    language = language,
    publisher = publisher,
    seriesId = series.id,
    createdDate = series.createdDate,
  )

  val c1 = SeriesCollection(name = "Coll One", ordered = true, seriesIds = listOf("S2", "S1"), id = "C1", createdDate = d(2020, 4, 1))
  val c2 = SeriesCollection(name = "coll two", seriesIds = listOf("S3"), id = "C2", createdDate = d(2020, 4, 2))
  val r1 = ReadList(name = "Read One", summary = "rl", bookIds = sortedMapOf(0 to "B3", 1 to "B1"), id = "R1", createdDate = d(2020, 5, 1))
  val r2 = ReadList(name = "read two", ordered = false, bookIds = sortedMapOf(0 to "B4"), id = "R2", createdDate = d(2020, 5, 2))

  fun seed(db: OracleDb) {
    db.libraryDao.insert(l1)
    db.libraryDao.insert(l2)
    users.forEach { db.komgaUserDao.insert(it) }
    listOf(s1, s2, s3).forEach { db.seriesDao.insert(it) }
    db.seriesMetadataDao.insert(seriesMetadata(s1, "Alpha", 10, setOf("kids"), setOf("action", "drama"), setOf("t1"), "en", "Pub1"))
    db.seriesMetadataDao.insert(seriesMetadata(s2, "beta", 16, setOf("adult"), setOf("drama"), setOf("t2"), "fr", "Pub2"))
    db.seriesMetadataDao.insert(seriesMetadata(s3, "Oneshot", null, emptySet(), emptySet(), emptySet(), "ja", ""))
    books.forEach {
      db.bookDao.insert(it)
      db.mediaDao.insert(media(it))
    }
    db.bookMetadataDao.insert(bookMetadata(b1, LocalDate.of(2019, 1, 1), setOf("bt1")))
    db.bookMetadataDao.insert(bookMetadata(b2, LocalDate.of(2020, 6, 15), setOf("bt2")))
    db.bookMetadataDao.insert(bookMetadata(b3, LocalDate.of(2018, 12, 31), emptySet()))
    db.bookMetadataDao.insert(bookMetadata(b4, null, setOf("bt1", "bt4")))
    listOf(s1 to LocalDate.of(2019, 1, 1), s2 to LocalDate.of(2018, 12, 31), s3 to null).forEach { (s, r) ->
      db.bookMetadataAggregationDao.insert(
        BookMetadataAggregation(authors = listOf(Author("Author ${s.id}", "writer")), tags = setOf("bt1"), releaseDate = r, seriesId = s.id, createdDate = s.createdDate),
      )
    }
    listOf(admin, all).forEach { u ->
      db.readProgressDao.save(ReadProgress("B1", u.id, 3, true, readDate = d(2021, 1, 1), createdDate = d(2021, 1, 1)))
      db.readProgressDao.save(ReadProgress("B2", u.id, 1, false, readDate = d(2021, 1, 2), createdDate = d(2021, 1, 2)))
    }
    db.seriesCollectionDao.insert(c1)
    db.seriesCollectionDao.insert(c2)
    db.readListDao.insert(r1)
    db.readListDao.insert(r2)
    RestOracle.fixNow(db)
    RestOracle.sql(
      db,
      "update SERIES set CREATED_DATE = '2020-02-01 10:00:00', LAST_MODIFIED_DATE = '2020-06-03 10:00:00' where ID = 'S1'",
      "update SERIES set CREATED_DATE = '2020-02-03 10:00:00', LAST_MODIFIED_DATE = '2020-06-01 10:00:00' where ID = 'S2'",
      "update SERIES set CREATED_DATE = '2020-02-02 10:00:00', LAST_MODIFIED_DATE = '2020-06-02 10:00:00' where ID = 'S3'",
      "update BOOK set CREATED_DATE = '2020-03-11 10:00:00', LAST_MODIFIED_DATE = '2020-04-04 10:00:00' where ID = 'B1'",
      "update BOOK set CREATED_DATE = '2020-03-12 10:00:00', LAST_MODIFIED_DATE = '2020-04-02 10:00:00' where ID = 'B2'",
      "update BOOK set CREATED_DATE = '2020-03-13 10:00:00', LAST_MODIFIED_DATE = '2020-04-03 10:00:00' where ID = 'B3'",
      "update BOOK set CREATED_DATE = '2020-03-14 10:00:00', LAST_MODIFIED_DATE = '2020-04-01 10:00:00' where ID = 'B4'",
      "update COLLECTION set LAST_MODIFIED_DATE = '2020-07-01 00:00:00' where ID = 'C2'",
      "update READLIST set LAST_MODIFIED_DATE = '2020-07-01 00:00:00' where ID = 'R2'",
    )
  }
}
