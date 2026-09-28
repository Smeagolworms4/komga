package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.AlternateTitle
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
import org.gotson.komga.domain.model.MediaProfile
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.infrastructure.search.SearchIndexLifecycle
import org.gotson.komga.oracle.OracleDb
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Realistic data set shared by the DAO oracle tests (N-Z and Dto DAOs), mirrored by
 * test/unit/infrastructure/jooq/main/nzDaoSeed.ts in KomgaJS. Fixed ids and dates; the dates set by the database
 * (CREATED_DATE, LAST_MODIFIED_DATE) are overwritten with fixed values.
 *
 * - libraries L1 (Comics), L2 (Mangas)
 * - users U1 (admin), U2 (L1+L2, age <= 12 only), U3 (L2 only, no "adult" label), U4 (age < 16 excluded or "kids")
 * - series S1..S6 with metadata (unicode titles), S4 deleted, S5 oneshot
 * - books B1..B11 with metadata, media and pages, B8 deleted, B1/B3 same hash and size
 * - read progress for U1 and U2, collections C1..C3, read lists RL1..RL3, book thumbnails
 */
object NzDaoSeed {
  fun dt(
    month: Int,
    day: Int,
    hour: Int = 0,
  ): LocalDateTime = LocalDateTime.of(2020, month, day, hour, 0)

  val u1 = KomgaUser("admin@example.org", "p", roles = setOf(UserRoles.ADMIN), id = "U1", createdDate = dt(1, 1))
  val u2 =
    KomgaUser(
      "kid@example.org",
      "p",
      sharedLibrariesIds = setOf("L1", "L2"),
      sharedAllLibraries = false,
      restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY)),
      id = "U2",
      createdDate = dt(1, 1),
    )
  val u3 =
    KomgaUser(
      "noadult@example.org",
      "p",
      sharedLibrariesIds = setOf("L2"),
      sharedAllLibraries = false,
      restrictions = ContentRestrictions(labelsExclude = setOf("adult")),
      id = "U3",
      createdDate = dt(1, 1),
    )
  val u4 =
    KomgaUser(
      "teen@example.org",
      "p",
      restrictions = ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), labelsAllow = setOf("kids")),
      id = "U4",
      createdDate = dt(1, 1),
    )

  private fun series(
    id: String,
    lib: String,
    name: String,
    deleted: Boolean = false,
    oneshot: Boolean = false,
  ) = Series(
    name = name,
    url = URL("file:/${if (lib == "L1") "lib1" else "lib2"}/${name.replace(" ", "%20")}"),
    fileLastModified = dt(2, 1),
    id = id,
    libraryId = lib,
    deletedDate = if (deleted) dt(6, 1) else null,
    oneshot = oneshot,
    createdDate = dt(1, 1),
  )

  val allSeries =
    listOf(
      series("S1", "L1", "Batman"),
      series("S2", "L1", "Élan"),
      series("S3", "L2", "Naruto"),
      series("S4", "L2", "zorro", deleted = true),
      series("S5", "L1", "Æon Flux", oneshot = true),
      series("S6", "L2", "Ångström"),
    )

  val bookCounts = mapOf("S1" to 3, "S2" to 2, "S3" to 3, "S4" to 1, "S5" to 1, "S6" to 1)

  val seriesMetadata =
    listOf(
      SeriesMetadata(
        status = SeriesMetadata.Status.ONGOING,
        title = "Batman",
        summary = "The dark knight",
        readingDirection = SeriesMetadata.ReadingDirection.LEFT_TO_RIGHT,
        publisher = "DC Comics",
        ageRating = 12,
        language = "en",
        genres = setOf("Superhero", "Action"),
        tags = setOf("dark", "hero"),
        totalBookCount = 3,
        sharingLabels = setOf("kids"),
        links = listOf(WebLink("wiki", URI("https://en.wikipedia.org/wiki/Batman"))),
        alternateTitles = listOf(AlternateTitle("fr", "L'homme chauve-souris")),
        titleLock = true,
        seriesId = "S1",
      ),
      SeriesMetadata(
        status = SeriesMetadata.Status.ENDED,
        title = "Élan vital",
        titleSort = "Elan vital",
        publisher = "Dupuis",
        language = "fr",
        genres = setOf("drama"),
        tags = setOf("hero"),
        totalBookCount = 2,
        seriesId = "S2",
      ),
      SeriesMetadata(
        status = SeriesMetadata.Status.ONGOING,
        title = "ナルト",
        titleSort = "Naruto",
        readingDirection = SeriesMetadata.ReadingDirection.RIGHT_TO_LEFT,
        publisher = "Shueisha",
        ageRating = 16,
        language = "ja",
        genres = setOf("action", "shonen"),
        tags = setOf("ninja"),
        totalBookCount = 72,
        sharingLabels = setOf("adult"),
        alternateTitles = listOf(AlternateTitle("en", "Naruto"), AlternateTitle("romaji", "Naruto")),
        seriesId = "S3",
      ),
      SeriesMetadata(
        status = SeriesMetadata.Status.ABANDONED,
        title = "zorro",
        publisher = "dc comics",
        ageRating = 7,
        language = "es",
        seriesId = "S4",
      ),
      SeriesMetadata(
        status = SeriesMetadata.Status.ENDED,
        title = "Æon Flux",
        titleSort = "Aeon Flux",
        ageRating = 18,
        language = "en",
        totalBookCount = 1,
        sharingLabels = setOf("adult"),
        seriesId = "S5",
      ),
      SeriesMetadata(
        status = SeriesMetadata.Status.HIATUS,
        title = "Ångström",
        titleSort = "Angstrom",
        publisher = "Kōdansha",
        ageRating = 18,
        language = "sv",
        genres = setOf("science"),
        sharingLabels = setOf("adult", "kids"),
        seriesId = "S6",
      ),
    )

  private fun book(
    id: String,
    series: String,
    lib: String,
    name: String,
    number: Int,
    size: Long,
    hash: String,
    deleted: Boolean = false,
    oneshot: Boolean = false,
  ) = Book(
    name = name,
    url = URL("file:/${if (lib == "L1") "lib1" else "lib2"}/$series/${name.replace(" ", "%20")}.cbz"),
    fileLastModified = dt(2, 2),
    fileSize = size,
    fileHash = hash,
    fileHashKoreader = if (hash.isEmpty()) "" else "K$hash",
    number = number,
    id = id,
    seriesId = series,
    libraryId = lib,
    deletedDate = if (deleted) dt(6, 2) else null,
    oneshot = oneshot,
    createdDate = dt(1, 1),
  )

  val books =
    listOf(
      book("B1", "S1", "L1", "Batman 001", 1, 1000, "H1"),
      book("B2", "S1", "L1", "Batman 002", 2, 2000, "H2"),
      book("B3", "S1", "L1", "Batman 003", 3, 1000, "H1"),
      book("B4", "S2", "L1", "Élan 1", 1, 500, ""),
      book("B5", "S2", "L1", "Élan 2", 2, 600, "H5"),
      book("B6", "S3", "L2", "Naruto 1", 1, 3000, "H6"),
      book("B7", "S3", "L2", "Naruto 1.5", 2, 3100, "H7"),
      book("B8", "S3", "L2", "Naruto 2", 3, 3200, "H8", deleted = true),
      book("B9", "S4", "L2", "zorro 1", 1, 100, "H9"),
      book("B10", "S5", "L1", "Æon Flux", 1, 700, "H10", oneshot = true),
      book("B11", "S6", "L2", "Ångström 1", 1, 800, "H11"),
    )

  private fun meta(
    id: String,
    title: String,
    number: String,
    numberSort: Float,
    release: LocalDate?,
    authors: List<Author> = emptyList(),
    tags: Set<String> = emptySet(),
    isbn: String = "",
  ) = BookMetadata(
    title = title,
    summary = "Summary of $title",
    number = number,
    numberSort = numberSort,
    releaseDate = release,
    authors = authors,
    tags = tags,
    isbn = isbn,
    bookId = id,
  )

  val bookMetadata =
    listOf(
      meta("B1", "Year One", "1", 1f, LocalDate.of(1987, 2, 1), listOf(Author("Frank Miller", "writer"), Author("David Mazzucchelli", "penciller")), setOf("classic"), "9781401207526"),
      meta("B2", "Year Two", "2", 2f, LocalDate.of(1987, 3, 1), listOf(Author("Frank Miller", "writer"))),
      meta("B3", "The Killing Joke", "3", 3f, LocalDate.of(1988, 3, 29), listOf(Author("Alan Moore", "writer"), Author("Brian Bolland", "penciller")), setOf("classic", "joker")),
      meta("B4", "Début", "1", 1f, LocalDate.of(2001, 1, 1), listOf(Author("Émile Zola", "writer"))),
      meta("B5", "Fin", "2", 2f, null),
      meta("B6", "うずまきナルト", "1", 1f, LocalDate.of(1999, 9, 21), listOf(Author("Masashi Kishimoto", "writer"))),
      meta("B7", "Special", "1.5", 1.5f, LocalDate.of(2000, 1, 1), listOf(Author("Masashi Kishimoto", "writer")), setOf("special")),
      meta("B8", "Deleted", "2", 2f, LocalDate.of(2000, 3, 3)),
      meta("B9", "Zorro", "01", 1f, LocalDate.of(1950, 6, 1)),
      meta("B10", "Æon Flux", "1", 1f, LocalDate.of(1995, 1, 1), listOf(Author("Peter Chung", "writer"))),
      meta("B11", "Ångström", "1", 1f, LocalDate.of(2010, 10, 10), tags = setOf("science")),
    )

  private fun pages(
    n: Int,
    hashPrefix: String,
  ) = (1..n).map { BookPage("p$it.jpg", "image/jpeg", Dimension(800, 1200), if (hashPrefix.isEmpty()) "" else "$hashPrefix$it", 100L + it) }

  val media =
    listOf(
      Media(Media.Status.READY, "application/zip", pages(3, "PH"), bookId = "B1"),
      Media(Media.Status.READY, "application/zip", pages(2, "PX"), bookId = "B2"),
      Media(Media.Status.READY, "application/zip", pages(3, "PH"), bookId = "B3"),
      Media(Media.Status.READY, "application/x-rar-compressed; version=4", pages(1, ""), bookId = "B4"),
      Media(Media.Status.ERROR, null, emptyList(), comment = "ERR_1001", bookId = "B5"),
      Media(Media.Status.READY, "application/zip", pages(4, "PH"), bookId = "B6"),
      Media(Media.Status.READY, "application/pdf", pages(2, "PQ"), bookId = "B7"),
      Media(Media.Status.OUTDATED, "application/zip", pages(1, ""), bookId = "B8"),
      Media(Media.Status.UNKNOWN, bookId = "B9"),
      Media(Media.Status.READY, "application/epub+zip", pages(2, ""), bookId = "B10"),
      Media(Media.Status.UNSUPPORTED, "application/x-7z-compressed", comment = "ERR_1002", bookId = "B11"),
    )

  val aggregations =
    listOf(
      BookMetadataAggregation(listOf(Author("Frank Miller", "writer"), Author("Alan Moore", "writer"), Author("David Mazzucchelli", "penciller")), setOf("classic", "joker"), LocalDate.of(1987, 2, 1), "Batman summary", "1", "S1"),
      BookMetadataAggregation(listOf(Author("Émile Zola", "writer")), emptySet(), LocalDate.of(2001, 1, 1), "", "", "S2"),
      BookMetadataAggregation(listOf(Author("Masashi Kishimoto", "writer")), setOf("special"), LocalDate.of(1999, 9, 21), "", "", "S3"),
      BookMetadataAggregation(emptyList(), emptySet(), LocalDate.of(1950, 6, 1), "", "", "S4"),
      BookMetadataAggregation(listOf(Author("Peter Chung", "writer")), emptySet(), LocalDate.of(1995, 1, 1), "", "", "S5"),
      BookMetadataAggregation(emptyList(), setOf("science"), LocalDate.of(2010, 10, 10), "", "", "S6"),
    )

  val readProgress =
    listOf(
      ReadProgress("B1", "U1", 3, true, LocalDateTime.of(2021, 1, 1, 10, 0)),
      ReadProgress("B2", "U1", 1, false, LocalDateTime.of(2021, 1, 2, 10, 0), "dev1", "Kobo"),
      ReadProgress("B6", "U1", 4, true, LocalDateTime.of(2021, 1, 3, 10, 0)),
      ReadProgress("B7", "U1", 2, true, LocalDateTime.of(2021, 1, 4, 10, 0)),
      ReadProgress("B4", "U1", 1, false, LocalDateTime.of(2021, 1, 5, 10, 0)),
      ReadProgress("B1", "U2", 2, false, LocalDateTime.of(2021, 2, 1, 10, 0)),
      ReadProgress("B4", "U2", 1, true, LocalDateTime.of(2021, 2, 2, 10, 0)),
      ReadProgress("B11", "U1", 1, true, LocalDateTime.of(2021, 1, 6, 10, 0)),
    )

  val collections =
    listOf(
      SeriesCollection("Heroes", ordered = false, seriesIds = listOf("S1", "S3", "S2"), id = "C1"),
      SeriesCollection("Ordered Æ", ordered = true, seriesIds = listOf("S6", "S1"), id = "C2"),
      SeriesCollection("empty", id = "C3"),
    )

  val readLists =
    listOf(
      ReadList("Reading order", "The canonical order", ordered = true, bookIds = sortedMapOf(1 to "B3", 2 to "B1", 3 to "B6"), id = "RL1"),
      ReadList("Unordered", ordered = false, bookIds = sortedMapOf(0 to "B7", 1 to "B4", 2 to "B11", 3 to "B5"), id = "RL2"),
      ReadList("Empty list", id = "RL3"),
    )

  val thumbnails =
    listOf(
      ThumbnailBook(ByteArray(4) { it.toByte() }, null, true, ThumbnailBook.Type.GENERATED, "image/jpeg", 4, Dimension(300, 400), "TB1", "B1"),
      ThumbnailBook(null, URL("file:/lib1/S1/cover.jpg"), false, ThumbnailBook.Type.SIDECAR, "image/jpeg", 1234, Dimension(1000, 1500), "TB2", "B1"),
      ThumbnailBook(ByteArray(2) { 9 }, null, true, ThumbnailBook.Type.USER_UPLOADED, "image/png", 2, Dimension(100, 100), "TB3", "B6"),
      ThumbnailBook(ByteArray(2) { 8 }, null, false, ThumbnailBook.Type.GENERATED, "image/jpeg", 2, Dimension(200, 250), "TB4", "B6"),
    )

  /** Inserts the whole data set, fixes the database dates and indexes everything in Lucene */
  fun seed(
    db: OracleDb,
    lucene: Boolean = false,
  ) {
    db.libraryDao.insert(Library("Comics", URL("file:/lib1"), id = "L1"))
    db.libraryDao.insert(Library("Mangas", URL("file:/lib2"), id = "L2"))
    listOf(u1, u2, u3, u4).forEach { db.komgaUserDao.insert(it) }
    allSeries.forEach {
      db.seriesDao.insert(it)
      db.seriesDao.update(it.copy(bookCount = bookCounts.getValue(it.id)), false)
    }
    seriesMetadata.forEach { db.seriesMetadataDao.insert(it) }
    aggregations.forEach { db.bookMetadataAggregationDao.insert(it) }
    db.bookDao.insert(books)
    db.bookMetadataDao.insert(bookMetadata)
    db.mediaDao.insert(media)
    db.readProgressDao.save(readProgress)
    collections.forEach { db.seriesCollectionDao.insert(it) }
    readLists.forEach { db.readListDao.insert(it) }
    thumbnails.forEach { db.thumbnailBookDao.insert(it) }
    fixDates(db)
    if (lucene) {
      SearchIndexLifecycle(db.seriesCollectionDao, db.readListDao, db.bookDtoDao, db.seriesDtoDao, db.lucene).rebuildIndex()
    }
  }

  private val tables =
    listOf(
      "LIBRARY",
      "\"USER\"",
      "SERIES",
      "SERIES_METADATA",
      "BOOK_METADATA_AGGREGATION",
      "BOOK",
      "BOOK_METADATA",
      "MEDIA",
      "READ_PROGRESS",
      "COLLECTION",
      "READLIST",
      "THUMBNAIL_BOOK",
    )

  /** Fixed dates instead of the database defaults: distinct creation dates per series and book, some updated later */
  fun fixDates(db: OracleDb) {
    tables.forEach { db.dsl.execute("update $it set CREATED_DATE = '2020-01-01 08:00:00', LAST_MODIFIED_DATE = '2020-01-01 08:00:00'") }
    db.dsl.execute("update READ_PROGRESS_SERIES set LAST_MODIFIED_DATE = '2020-01-01 08:00:00'")
    val seriesDates = listOf("S1" to 5, "S2" to 3, "S3" to 6, "S4" to 1, "S5" to 4, "S6" to 2)
    seriesDates.forEach { (id, day) ->
      val modified = if (id == "S2" || id == "S3") "2020-05-0$day 12:00:00" else "2020-01-0$day 12:00:00"
      db.dsl.execute("update SERIES set CREATED_DATE = '2020-01-0$day 12:00:00', LAST_MODIFIED_DATE = '$modified' where ID = '$id'")
    }
    books.forEachIndexed { i, b ->
      val day = (i * 7) % 11 + 10
      db.dsl.execute("update BOOK set CREATED_DATE = '2020-02-$day 12:00:00', LAST_MODIFIED_DATE = '2020-03-$day 12:00:00' where ID = '${b.id}'")
    }
    readProgress.forEachIndexed { i, r ->
      db.dsl.execute("update READ_PROGRESS set CREATED_DATE = '2021-03-1$i 09:00:00', LAST_MODIFIED_DATE = '2021-04-1$i 09:00:00' where BOOK_ID = '${r.bookId}' and USER_ID = '${r.userId}'")
    }
    db.dsl.execute("update COLLECTION set LAST_MODIFIED_DATE = '2020-07-01 00:00:00' where ID = 'C2'")
    db.dsl.execute("update READLIST set LAST_MODIFIED_DATE = '2020-07-01 00:00:00' where ID = 'RL2'")
  }

  private fun zdt(
    year: Int,
    offset: Int = 0,
  ) = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(offset))

  /** Series search conditions exercised on SeriesDao, SeriesDtoDao and SeriesSearchHelper (same list in nzDaoSeed.ts) */
  val seriesConditions: List<Pair<String, SearchCondition.Series?>> =
    listOf(
      "no condition" to null,
      "library is" to SearchCondition.LibraryId(SearchOperator.Is("L1")),
      "library is not" to SearchCondition.LibraryId(SearchOperator.IsNot("L1")),
      "deleted" to SearchCondition.Deleted(SearchOperator.IsTrue),
      "not deleted" to SearchCondition.Deleted(SearchOperator.IsFalse),
      "oneshot" to SearchCondition.OneShot(SearchOperator.IsTrue),
      "not oneshot" to SearchCondition.OneShot(SearchOperator.IsFalse),
      "complete" to SearchCondition.Complete(SearchOperator.IsTrue),
      "not complete" to SearchCondition.Complete(SearchOperator.IsFalse),
      "title contains without accent" to SearchCondition.Title(SearchOperator.Contains("elan")),
      "title contains with accent" to SearchCondition.Title(SearchOperator.Contains("ÅNG")),
      "title contains japanese" to SearchCondition.Title(SearchOperator.Contains("ルト")),
      "title begins with" to SearchCondition.Title(SearchOperator.BeginsWith("bat")),
      "title does not begin with" to SearchCondition.Title(SearchOperator.DoesNotBeginWith("Æ")),
      "title ends with" to SearchCondition.Title(SearchOperator.EndsWith("FLUX")),
      "title does not end with" to SearchCondition.Title(SearchOperator.DoesNotEndWith("m")),
      "title does not contain" to SearchCondition.Title(SearchOperator.DoesNotContain("a")),
      "title contains percent" to SearchCondition.Title(SearchOperator.Contains("%")),
      "title contains underscore" to SearchCondition.Title(SearchOperator.Contains("_")),
      "title is ignoring case" to SearchCondition.Title(SearchOperator.Is("batman")),
      "title is ignoring accents" to SearchCondition.Title(SearchOperator.Is("elan vital")),
      "title is not" to SearchCondition.Title(SearchOperator.IsNot("ZORRO")),
      "title sort begins with" to SearchCondition.TitleSort(SearchOperator.BeginsWith("a")),
      "title sort is" to SearchCondition.TitleSort(SearchOperator.Is("naruto")),
      "publisher is ignoring case" to SearchCondition.Publisher(SearchOperator.Is("DC COMICS")),
      "publisher is not" to SearchCondition.Publisher(SearchOperator.IsNot("dc comics")),
      "publisher is with accent" to SearchCondition.Publisher(SearchOperator.Is("kodansha")),
      "language is" to SearchCondition.Language(SearchOperator.Is("EN")),
      "language is not" to SearchCondition.Language(SearchOperator.IsNot("en")),
      "genre is" to SearchCondition.Genre(SearchOperator.Is("ACTION")),
      "genre is not" to SearchCondition.Genre(SearchOperator.IsNot("action")),
      "genre is null" to SearchCondition.Genre(SearchOperator.IsNullT()),
      "genre is not null" to SearchCondition.Genre(SearchOperator.IsNotNullT()),
      "tag is from books" to SearchCondition.Tag(SearchOperator.Is("Joker")),
      "tag is from series" to SearchCondition.Tag(SearchOperator.Is("hero")),
      "tag is not" to SearchCondition.Tag(SearchOperator.IsNot("hero")),
      "tag is null" to SearchCondition.Tag(SearchOperator.IsNullT()),
      "tag is not null" to SearchCondition.Tag(SearchOperator.IsNotNullT()),
      "sharing label is" to SearchCondition.SharingLabel(SearchOperator.Is("ADULT")),
      "sharing label is not" to SearchCondition.SharingLabel(SearchOperator.IsNot("kids")),
      "sharing label is null" to SearchCondition.SharingLabel(SearchOperator.IsNullT()),
      "sharing label is not null" to SearchCondition.SharingLabel(SearchOperator.IsNotNullT()),
      "age rating greater than" to SearchCondition.AgeRating(SearchOperator.GreaterThan(16)),
      "age rating less than" to SearchCondition.AgeRating(SearchOperator.LessThan(12)),
      "age rating is" to SearchCondition.AgeRating(SearchOperator.Is(18)),
      "age rating is not" to SearchCondition.AgeRating(SearchOperator.IsNot(18)),
      "age rating is null" to SearchCondition.AgeRating(SearchOperator.IsNullT()),
      "age rating is not null" to SearchCondition.AgeRating(SearchOperator.IsNotNullT()),
      "release date before" to SearchCondition.ReleaseDate(SearchOperator.Before(zdt(1996, 5))),
      "release date after" to SearchCondition.ReleaseDate(SearchOperator.After(zdt(1999, -10))),
      "release date in the last" to SearchCondition.ReleaseDate(SearchOperator.IsInTheLast(Duration.ofDays(20000))),
      "release date not in the last" to SearchCondition.ReleaseDate(SearchOperator.IsNotInTheLast(Duration.ofDays(20000))),
      "release date is null" to SearchCondition.ReleaseDate(SearchOperator.IsNull),
      "release date is not null" to SearchCondition.ReleaseDate(SearchOperator.IsNotNull),
      "read status is read" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.READ)),
      "read status is unread" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.UNREAD)),
      "read status is in progress" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.IN_PROGRESS)),
      "read status is not read" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.READ)),
      "read status is not unread" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.UNREAD)),
      "read status is not in progress" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.IN_PROGRESS)),
      "collection is" to SearchCondition.CollectionId(SearchOperator.Is("C1")),
      "collection is not" to SearchCondition.CollectionId(SearchOperator.IsNot("C1")),
      "series status is" to SearchCondition.SeriesStatus(SearchOperator.Is(SeriesMetadata.Status.ENDED)),
      "series status is not" to SearchCondition.SeriesStatus(SearchOperator.IsNot(SeriesMetadata.Status.ONGOING)),
      "author name" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch("frank MILLER"))),
      "author role" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch(role = "Writer"))),
      "author name and role" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch("david mazzucchelli", "writer"))),
      "author empty match" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch())),
      "author is not" to SearchCondition.Author(SearchOperator.IsNot(SearchCondition.AuthorMatch("Peter Chung"))),
      "author is not empty match" to SearchCondition.Author(SearchOperator.IsNot(SearchCondition.AuthorMatch())),
      "any of" to SearchCondition.AnyOfSeries(SearchCondition.LibraryId(SearchOperator.Is("L2")), SearchCondition.OneShot(SearchOperator.IsTrue)),
      "all of" to
        SearchCondition.AllOfSeries(
          SearchCondition.LibraryId(SearchOperator.Is("L2")),
          SearchCondition.Deleted(SearchOperator.IsFalse),
          SearchCondition.AgeRating(SearchOperator.GreaterThan(17)),
        ),
      "nested" to
        SearchCondition.AllOfSeries(
          SearchCondition.AnyOfSeries(SearchCondition.Genre(SearchOperator.Is("action")), SearchCondition.Tag(SearchOperator.Is("science"))),
          SearchCondition.Deleted(SearchOperator.IsFalse),
          SearchCondition.AnyOfSeries(SearchCondition.CollectionId(SearchOperator.Is("C2")), SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.IN_PROGRESS))),
        ),
      "empty any of" to SearchCondition.AnyOfSeries(emptyList()),
      "empty all of" to SearchCondition.AllOfSeries(emptyList()),
    )

  /** Book search conditions exercised on BookDtoDao and BookSearchHelper (same list in nzDaoSeed.ts) */
  val bookConditions: List<Pair<String, SearchCondition.Book?>> =
    listOf(
      "no condition" to null,
      "library is" to SearchCondition.LibraryId(SearchOperator.Is("L2")),
      "library is not" to SearchCondition.LibraryId(SearchOperator.IsNot("L2")),
      "series is" to SearchCondition.SeriesId(SearchOperator.Is("S1")),
      "series is not" to SearchCondition.SeriesId(SearchOperator.IsNot("S1")),
      "read list is" to SearchCondition.ReadListId(SearchOperator.Is("RL1")),
      "read list is not" to SearchCondition.ReadListId(SearchOperator.IsNot("RL1")),
      "title contains" to SearchCondition.Title(SearchOperator.Contains("YEAR")),
      "title is" to SearchCondition.Title(SearchOperator.Is("the killing joke")),
      "title begins with without accent" to SearchCondition.Title(SearchOperator.BeginsWith("debut")),
      "title does not contain" to SearchCondition.Title(SearchOperator.DoesNotContain("e")),
      "deleted" to SearchCondition.Deleted(SearchOperator.IsTrue),
      "not deleted" to SearchCondition.Deleted(SearchOperator.IsFalse),
      "oneshot" to SearchCondition.OneShot(SearchOperator.IsTrue),
      "not oneshot" to SearchCondition.OneShot(SearchOperator.IsFalse),
      "release date before" to SearchCondition.ReleaseDate(SearchOperator.Before(zdt(1990))),
      "release date after" to SearchCondition.ReleaseDate(SearchOperator.After(zdt(2000, 2))),
      "release date is null" to SearchCondition.ReleaseDate(SearchOperator.IsNull),
      "release date is not null" to SearchCondition.ReleaseDate(SearchOperator.IsNotNull),
      "number sort greater than" to SearchCondition.NumberSort(SearchOperator.GreaterThan(1.5f)),
      "number sort less than" to SearchCondition.NumberSort(SearchOperator.LessThan(1f)),
      "number sort is" to SearchCondition.NumberSort(SearchOperator.Is(1.5f)),
      "number sort is not" to SearchCondition.NumberSort(SearchOperator.IsNot(1f)),
      "tag is" to SearchCondition.Tag(SearchOperator.Is("CLASSIC")),
      "tag is not" to SearchCondition.Tag(SearchOperator.IsNot("classic")),
      "tag is null" to SearchCondition.Tag(SearchOperator.IsNullT()),
      "tag is not null" to SearchCondition.Tag(SearchOperator.IsNotNullT()),
      "read status is read" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.READ)),
      "read status is unread" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.UNREAD)),
      "read status is in progress" to SearchCondition.ReadStatus(SearchOperator.Is(ReadStatus.IN_PROGRESS)),
      "read status is not read" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.READ)),
      "read status is not unread" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.UNREAD)),
      "read status is not in progress" to SearchCondition.ReadStatus(SearchOperator.IsNot(ReadStatus.IN_PROGRESS)),
      "media status is" to SearchCondition.MediaStatus(SearchOperator.Is(Media.Status.READY)),
      "media status is not" to SearchCondition.MediaStatus(SearchOperator.IsNot(Media.Status.READY)),
      "media profile divina" to SearchCondition.MediaProfile(SearchOperator.Is(MediaProfile.DIVINA)),
      "media profile pdf" to SearchCondition.MediaProfile(SearchOperator.Is(MediaProfile.PDF)),
      "media profile epub" to SearchCondition.MediaProfile(SearchOperator.Is(MediaProfile.EPUB)),
      "media profile is not divina" to SearchCondition.MediaProfile(SearchOperator.IsNot(MediaProfile.DIVINA)),
      "author name" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch("FRANK miller"))),
      "author role" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch(role = "penciller"))),
      "author name and role" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch("emile zola", "WRITER"))),
      "author empty match" to SearchCondition.Author(SearchOperator.Is(SearchCondition.AuthorMatch())),
      "author is not" to SearchCondition.Author(SearchOperator.IsNot(SearchCondition.AuthorMatch("Masashi Kishimoto"))),
      "poster generated" to SearchCondition.Poster(SearchOperator.Is(SearchCondition.PosterMatch(SearchCondition.PosterMatch.Type.GENERATED))),
      "poster selected" to SearchCondition.Poster(SearchOperator.Is(SearchCondition.PosterMatch(selected = true))),
      "poster sidecar not selected" to SearchCondition.Poster(SearchOperator.Is(SearchCondition.PosterMatch(SearchCondition.PosterMatch.Type.SIDECAR, false))),
      "poster is not user uploaded" to SearchCondition.Poster(SearchOperator.IsNot(SearchCondition.PosterMatch(SearchCondition.PosterMatch.Type.USER_UPLOADED))),
      "poster empty match" to SearchCondition.Poster(SearchOperator.Is(SearchCondition.PosterMatch())),
      "any of" to SearchCondition.AnyOfBook(SearchCondition.SeriesId(SearchOperator.Is("S2")), SearchCondition.OneShot(SearchOperator.IsTrue)),
      "all of" to SearchCondition.AllOfBook(SearchCondition.LibraryId(SearchOperator.Is("L1")), SearchCondition.MediaStatus(SearchOperator.Is(Media.Status.READY))),
      "nested" to
        SearchCondition.AllOfBook(
          SearchCondition.AnyOfBook(SearchCondition.Tag(SearchOperator.Is("classic")), SearchCondition.ReadListId(SearchOperator.Is("RL2"))),
          SearchCondition.Deleted(SearchOperator.IsFalse),
        ),
      "empty any of" to SearchCondition.AnyOfBook(emptyList()),
      "empty all of" to SearchCondition.AllOfBook(emptyList()),
    )
}
