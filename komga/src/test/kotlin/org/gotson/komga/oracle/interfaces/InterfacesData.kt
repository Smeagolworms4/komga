package org.gotson.komga.oracle.interfaces

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.BookMetadataAggregation
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
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
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Shared data set of the web layer oracles (OPDS, Kobo, KOReader, SSE...), mirrored by test/unit/interfaces/data.ts.
 *
 * - libraries L1 "Comics" and L2 "Mangä & Co"
 * - series S1 "Batman" (L1, 3 CBZ books B1..B3), S2 "One Piece" (L2, EPUB B4 and PDF B5, age rating 16, label "adult"),
 *   S3 oneshot "Tom & Jerry <Special>" (L1, EPUB B6, kepub)
 * - collection C1 "Heroes" (S1, S3), read list R1 "Arc" (B2, B4)
 * - users U1 admin, U2 limited to L1, U3 age restricted (allow only under 13), read progress of U1 on B1 (completed) and B2 (page 3)
 */
object InterfacesData {
  val date: LocalDateTime = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

  val admin = KomgaUser("admin@example.org", "pw", roles = UserRoles.entries.toSet(), id = "U1", createdDate = date)
  val limited = KomgaUser("limited@example.org", "pw", roles = setOf(UserRoles.PAGE_STREAMING, UserRoles.KOBO_SYNC), sharedAllLibraries = false, sharedLibrariesIds = setOf("L1"), id = "U2", createdDate = date)
  val restricted =
    KomgaUser(
      "kid@example.org",
      "pw",
      roles = setOf(UserRoles.FILE_DOWNLOAD, UserRoles.PAGE_STREAMING),
      restrictions = ContentRestrictions(ageRestriction = AgeRestriction(13, AllowExclude.ALLOW_ONLY)),
      id = "U3",
      createdDate = date,
    )

  private fun pages(
    n: Int,
    type: String = "image/jpeg",
  ) = (1..n).map { BookPage("p$it.jpg", type, Dimension(800, 1200), fileSize = 1000L * it) }

  private fun populate(db: OracleDb) {

    db.libraryDao.insert(Library("Comics", URL("file:/data/comics"), id = "L1", createdDate = date))
    db.libraryDao.insert(Library("Mangä & Co", URL("file:/data/manga%20co"), id = "L2", createdDate = date))
    db.komgaUserDao.insert(admin)
    db.komgaUserDao.insert(limited)
    db.komgaUserDao.insert(restricted)

    series("S1", "L1", "Batman", "file:/data/comics/Batman", publisher = "DC Comics", genres = setOf("super hero"), tags = setOf("dark"))
    series("S2", "L2", "One Piece", "file:/data/manga%20co/One%20Piece", publisher = "Shueisha", ageRating = 16, sharingLabels = setOf("adult"), language = "ja")
    series("S3", "L1", "Tom & Jerry <Special>", "file:/data/comics/Tom%20&%20Jerry.epub", oneshot = true, summary = "Cat \"and\" mouse")

    book("B1", "S1", "L1", "Batman 001", "file:/data/comics/Batman/Batman%20001.cbz", 1, "application/zip", pages(3), releaseDate = LocalDate.of(2019, 5, 1), authors = listOf(Author("Bob Kane", "writer"), Author("Bill Finger", "penciller")))
    book("B2", "S1", "L1", "Batman 002", "file:/data/comics/Batman/Batman%20002.cbz", 2, "application/zip", pages(5, "image/png"), releaseDate = LocalDate.of(2019, 6, 1))
    book("B3", "S1", "L1", "Batman 003", "file:/data/comics/Batman/Batman%20003.cbz", 3, "application/zip", pages(2, "image/webp"))
    book("B4", "S2", "L2", "One Piece v01", "file:/data/manga%20co/One%20Piece/One%20Piece%20v01.epub", 1, "application/epub+zip", emptyList(), isbn = "9781569319017")
    book("B5", "S2", "L2", "One Piece v02", "file:/data/manga%20co/One%20Piece/One%20Piece%20v02.pdf", 2, "application/pdf", pages(4))
    book("B6", "S3", "L1", "Tom & Jerry <Special>", "file:/data/comics/Tom%20&%20Jerry.epub", 1, "application/epub+zip", emptyList(), oneshot = true, kepub = true)

    db.seriesCollectionDao.insert(SeriesCollection("Heroes", seriesIds = listOf("S1", "S3"), id = "C1", createdDate = date))
    db.readListDao.insert(ReadList("Arc", summary = "An arc", bookIds = sortedMapOf(1 to "B2", 2 to "B4"), id = "R1", createdDate = date))

    db.readProgressDao.save(ReadProgress("B1", "U1", 3, true, readDate = date.plusDays(1), createdDate = date))
    db.readProgressDao.save(ReadProgress("B2", "U1", 3, false, readDate = date.plusDays(2), createdDate = date))
  }

  private lateinit var currentDb: OracleDb

  private fun series(
    id: String,
    libraryId: String,
    title: String,
    url: String,
    oneshot: Boolean = false,
    publisher: String = "",
    ageRating: Int? = null,
    sharingLabels: Set<String> = emptySet(),
    language: String = "",
    genres: Set<String> = emptySet(),
    tags: Set<String> = emptySet(),
    summary: String = "",
  ) {
    currentDb.seriesDao.insert(Series(title, URL(url), date, id = id, libraryId = libraryId, oneshot = oneshot, createdDate = date))
    currentDb.seriesMetadataDao.insert(
      SeriesMetadata(
        title = title,
        summary = summary,
        publisher = publisher,
        ageRating = ageRating,
        language = language,
        genres = genres,
        tags = tags,
        sharingLabels = sharingLabels,
        links = listOf(WebLink("Site", URI("https://example.org/$id"))),
        seriesId = id,
        createdDate = date,
      ),
    )
    currentDb.bookMetadataAggregationDao.insert(BookMetadataAggregation(seriesId = id, createdDate = date))
  }

  private fun book(
    id: String,
    seriesId: String,
    libraryId: String,
    name: String,
    url: String,
    number: Int,
    mediaType: String,
    pages: List<BookPage>,
    releaseDate: LocalDate? = null,
    authors: List<Author> = emptyList(),
    isbn: String = "",
    oneshot: Boolean = false,
    kepub: Boolean = false,
  ) {
    currentDb.bookDao.insert(
      Book(name, URL(url), date, fileSize = 1_000_000L * number + 123, fileHash = "hash$id", number = number, id = id, seriesId = seriesId, libraryId = libraryId, oneshot = oneshot, createdDate = date),
    )
    currentDb.mediaDao.insert(Media(Media.Status.READY, mediaType, pages, pageCount = if (pages.isEmpty()) 12 else pages.size, bookId = id, epubIsKepub = kepub, createdDate = date))
    currentDb.bookMetadataDao.insert(
      BookMetadata(
        title = name,
        summary = "Summary of $name",
        number = "$number",
        numberSort = number.toFloat(),
        releaseDate = releaseDate,
        authors = authors,
        isbn = isbn,
        bookId = id,
        createdDate = date,
      ),
    )
  }

  /** CBZ with the pages p1.png, p2.jpg, p3.gif (2x3 pixels) */
  const val CBZ = "UEsDBBQAAAAAAAAAIVDFuI0CTAAAAEwAAAAGAAAAcDEucG5niVBORw0KGgoAAAANSUhEUgAAAAIAAAADCAIAAAA2iEnWAAAAE0lEQVR4nGP8z8DAwMDAxIBMAQAUQAEF3SN5DgAAAABJRU5ErkJgglBLAwQUAAAAAAAAACFQt9lnaXkCAAB5AgAABgAAAHAyLmpwZ//Y/+AAEEpGSUYAAQEAAAEAAQAA/9sAQwAIBgYHBgUIBwcHCQkICgwUDQwLCwwZEhMPFB0aHx4dGhwcICQuJyAiLCMcHCg3KSwwMTQ0NB8nOT04MjwuMzQy/9sAQwEJCQkMCwwYDQ0YMiEcITIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy/8AAEQgAAwACAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMRAD8A1qKKK/ND8gP/2VBLAwQUAAAAAAAAACFQtMA2wS0AAAAtAAAABgAAAHAzLmdpZkdJRjg3YQIAAwCBAAAAAP8AAAAAAAAAAAAsAAAAAAIAAwAACAYAAQgcGBAAO1BLAQIUAxQAAAAAAAAAIVDFuI0CTAAAAEwAAAAGAAAAAAAAAAAAAACAAQAAAABwMS5wbmdQSwECFAMUAAAAAAAAACFQt9lnaXkCAAB5AgAABgAAAAAAAAAAAAAAgAFwAAAAcDIuanBnUEsBAhQDFAAAAAAAAAAhULTANsEtAAAALQAAAAYAAAAAAAAAAAAAAIABDQMAAHAzLmdpZlBLBQYAAAAAAwADAJwAAABeAwAAAAA="

  /** zipped EPUB-like content: mimetype, OEBPS/ch 1.xhtml, OEBPS/style.css, OEBPS/fonts/f.woff2 */
  const val EPUB = "UEsDBBQAAAAAAAAAIVBvYassFAAAABQAAAAIAAAAbWltZXR5cGVhcHBsaWNhdGlvbi9lcHViK3ppcFBLAwQUAAAAAAAAACFQ033h4CYAAAAmAAAAEAAAAE9FQlBTL2NoIDEueGh0bWw8aHRtbD48Ym9keT5DaGFwdGVyIDEgw6k8L2JvZHk+PC9odG1sPlBLAwQUAAAAAAAAACFQvaRMAw8AAAAPAAAADwAAAE9FQlBTL3N0eWxlLmNzc2JvZHl7Y29sb3I6cmVkfVBLAwQUAAAAAAAAACFQT9JweggAAAAIAAAAEwAAAE9FQlBTL2ZvbnRzL2Yud29mZjJGT05UREFUQVBLAQIUAxQAAAAAAAAAIVBvYassFAAAABQAAAAIAAAAAAAAAAAAAACAAQAAAABtaW1ldHlwZVBLAQIUAxQAAAAAAAAAIVDTfeHgJgAAACYAAAAQAAAAAAAAAAAAAACAAToAAABPRUJQUy9jaCAxLnhodG1sUEsBAhQDFAAAAAAAAAAhUL2kTAMPAAAADwAAAA8AAAAAAAAAAAAAAIABjgAAAE9FQlBTL3N0eWxlLmNzc1BLAQIUAxQAAAAAAAAAIVBP0nB6CAAAAAgAAAATAAAAAAAAAAAAAACAAcoAAABPRUJQUy9mb250cy9mLndvZmYyUEsFBgAAAAAEAAQA8gAAAAMBAAAAAA=="

  /** writes real.cbz and real.epub in [dir] and adds the books B7 (CBZ) and B8 (EPUB) to S1, with their media */
  fun realBooks(
    db: OracleDb,
    dir: java.nio.file.Path,
  ) {
    val cbz = dir.resolve("real.cbz").also { java.nio.file.Files.write(it, java.util.Base64.getDecoder().decode(CBZ)) }
    val epub = dir.resolve("real.epub").also { java.nio.file.Files.write(it, java.util.Base64.getDecoder().decode(EPUB)) }
    db.bookDao.insert(Book("real", cbz.toUri().toURL(), date, fileSize = java.nio.file.Files.size(cbz), number = 7, id = "B7", seriesId = "S1", libraryId = "L1", createdDate = date))
    db.bookDao.insert(Book("real epub", epub.toUri().toURL(), date, fileSize = java.nio.file.Files.size(epub), number = 8, id = "B8", seriesId = "S1", libraryId = "L1", createdDate = date))
    db.mediaDao.insert(
      Media(
        Media.Status.READY,
        "application/zip",
        listOf(BookPage("p1.png", "image/png", Dimension(2, 3)), BookPage("p2.jpg", "image/jpeg", Dimension(2, 3)), BookPage("p3.gif", "image/gif", Dimension(2, 3))),
        bookId = "B7",
        createdDate = date,
      ),
    )
    db.mediaDao.insert(
      Media(
        Media.Status.READY,
        "application/epub+zip",
        files =
          listOf(
            org.gotson.komga.domain.model.MediaFile("OEBPS/ch 1.xhtml", "application/xhtml+xml", org.gotson.komga.domain.model.MediaFile.SubType.EPUB_PAGE),
            org.gotson.komga.domain.model.MediaFile("OEBPS/style.css", "text/css", org.gotson.komga.domain.model.MediaFile.SubType.EPUB_ASSET),
            org.gotson.komga.domain.model.MediaFile("OEBPS/fonts/f.woff2", "font/woff2", org.gotson.komga.domain.model.MediaFile.SubType.EPUB_ASSET),
            org.gotson.komga.domain.model.MediaFile("OEBPS/missing.css", "text/css", org.gotson.komga.domain.model.MediaFile.SubType.EPUB_ASSET),
          ),
        pageCount = 1,
        bookId = "B8",
        createdDate = date,
      ),
    )
    db.bookMetadataDao.insert(BookMetadata(title = "real", number = "7", numberSort = 7F, bookId = "B7", createdDate = date))
    db.bookMetadataDao.insert(BookMetadata(title = "real epub", number = "8", numberSort = 8F, bookId = "B8", createdDate = date))
  }

  /** populates [db] (must be called inside a case) */
  fun setup(db: OracleDb) {
    currentDb = db
    populate(db)
  }
}
