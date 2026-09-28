package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ImageConversionException
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.MarkSelectedPreference
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.MediaNotReadyException
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.CommonBookController
import org.gotson.komga.interfaces.api.ContentRestrictionChecker
import org.gotson.komga.interfaces.api.WebPubGenerator
import org.gotson.komga.interfaces.api.dto.WPMetadataDto
import org.gotson.komga.interfaces.api.dto.WPPublicationDto
import org.gotson.komga.interfaces.api.rest.BookController
import org.gotson.komga.interfaces.api.rest.dto.BookImportBatchDto
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.PNG
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.entity
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.thrown
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.context.request.ServletWebRequest
import java.net.URL
import java.nio.file.NoSuchFileException
import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("DEPRECATION")
class BookControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls (same fakes in the TypeScript twin) */
  private val bookLifecycle =
    mockk<BookLifecycle> {
      every { getThumbnailBytes(any(), any()) } answers {
        calls.add("getThumbnailBytes", firstArg<String>(), secondArg<Int?>())
        if (firstArg<String>() == "B4") null else TypedBytes(byteArrayOf(6), "image/jpeg")
      }
      every { getThumbnailBytesByThumbnailId(any()) } answers {
        calls.add("getThumbnailBytesByThumbnailId", firstArg<String>())
        if (firstArg<String>() == "TB2") null else TypedBytes(byteArrayOf(7), "image/png")
      }
      every { addThumbnailForBook(any(), any()) } answers {
        calls.add("addThumbnailForBook", firstArg<ThumbnailBook>(), secondArg<MarkSelectedPreference>())
        firstArg()
      }
      every { deleteThumbnailForBook(any()) } answers {
        calls.add("deleteThumbnailForBook", firstArg<ThumbnailBook>().id)
        if (firstArg<ThumbnailBook>().selected) throw IllegalArgumentException("selected thumbnail cannot be deleted")
      }
      every { getBookPage(any(), any(), any(), any()) } answers {
        val n = secondArg<Int>()
        calls.add("getBookPage", firstArg<Book>().id, n, thirdArg<ImageType?>(), arg<Int?>(3))
        when (n) {
          99 -> throw IndexOutOfBoundsException("99")
          98 -> throw ImageConversionException("Cannot convert", "ERR_1011")
          97 -> throw MediaNotReadyException()
          96 -> throw NoSuchFileException("/x")
          else -> TypedBytes(byteArrayOf(n.toByte()), if (n == 2) "invalid" else "image/jpeg")
        }
      }
      every { markReadProgressCompleted(any(), any()) } answers { calls.add("markReadProgressCompleted", firstArg<String>(), secondArg<KomgaUser>().id) }
      every { markReadProgress(any(), any(), any()) } answers {
        calls.add("markReadProgress", firstArg<Book>().id, secondArg<KomgaUser>().id, thirdArg<Int>())
        if (thirdArg<Int>() > 3) throw IllegalArgumentException("page out of range")
      }
      every { deleteReadProgress(any(), any()) } answers { calls.add("deleteReadProgress", firstArg<Book>().id, secondArg<KomgaUser>().id) }
    }
  private val analyzer =
    mockk<BookAnalyzer> {
      every { getPdfPagesDynamic(any()) } answers {
        calls.add("getPdfPagesDynamic", firstArg<Media>().bookId)
        listOf(BookPage("0", "image/jpeg", Dimension(1, 2)))
      }
    }
  private val manifest = WPPublicationDto(MediaType.parseMediaType("application/divina+json"), null, WPMetadataDto("Manifest"), emptyList())
  private val common =
    mockk<CommonBookController> {
      every { getWebPubManifestInternal(any(), any(), any()) } answers {
        calls.add("getWebPubManifestInternal", firstArg<KomgaPrincipal>().user.id, secondArg<String>())
        manifest
      }
      every { getWebPubManifestEpubInternal(any(), any(), any()) } answers {
        calls.add("getWebPubManifestEpubInternal", firstArg<KomgaPrincipal>().user.id, secondArg<String>())
        manifest
      }
      every { getWebPubManifestPdfInternal(any(), any(), any()) } answers {
        calls.add("getWebPubManifestPdfInternal", firstArg<KomgaPrincipal>().user.id, secondArg<String>())
        manifest
      }
      every { getWebPubManifestDivinaInternal(any(), any(), any()) } answers {
        calls.add("getWebPubManifestDivinaInternal", firstArg<KomgaPrincipal>().user.id, secondArg<String>())
        manifest
      }
    }
  private val c =
    BookController(
      taskEmitter(db, calls),
      analyzer,
      bookLifecycle,
      db.bookDao,
      db.bookMetadataDao,
      db.mediaDao,
      db.bookDtoDao,
      db.readListDao,
      ContentDetector(TikaConfig()),
      ImageAnalyzer(),
      { calls.add("publishEvent", it) },
      db.thumbnailBookDao,
      mockk<WebPubGenerator>(),
      ContentRestrictionChecker(db.seriesMetadataDao, db.bookDao, db.thumbnailBookDao, db.seriesDao, db.thumbnailSeriesDao),
      common,
    )
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val noAdult = principal(RestSamples.noAdult)
  private val p20 = PageRequest.of(0, 20)

  private fun thumb(
    id: String,
    bookId: String,
    selected: Boolean,
  ) = ThumbnailBook(
    thumbnail = byteArrayOf(2, id.length.toByte()),
    selected = selected,
    type = ThumbnailBook.Type.USER_UPLOADED,
    mediaType = "image/png",
    fileSize = 2,
    dimension = Dimension(3, 2),
    id = id,
    bookId = bookId,
    createdDate = FIXED,
  )

  private fun extraBook(
    id: String,
    seriesId: String,
    fileHash: String,
    media: Media,
  ) {
    db.bookDao.insert(Book(name = id, url = URL("file:/lib1/extra/$id.cbz"), fileLastModified = FIXED, fileHash = fileHash, number = 5, id = id, seriesId = seriesId, libraryId = "L1", createdDate = FIXED))
    db.mediaDao.insert(media.copy(bookId = id, createdDate = FIXED))
    db.bookMetadataDao.insert(BookMetadata(title = id, number = "5", numberSort = 5f, bookId = id, createdDate = FIXED))
    RestOracle.fixNow(db)
  }

  private fun request(ifModifiedSince: String? = null) = MockHttpServletRequest("GET", "/x").apply { ifModifiedSince?.let { addHeader("If-Modified-Since", it) } }

  private fun deprecated(
    p: KomgaPrincipal,
    searchTerm: String? = null,
    libraryIds: List<String>? = null,
    mediaStatus: List<Media.Status>? = null,
    readStatus: List<ReadStatus>? = null,
    releasedAfter: LocalDate? = null,
    tags: List<String>? = null,
    unpaged: Boolean = false,
    page: Pageable = p20,
  ) = c.getAllBooksDeprecated(p, searchTerm, libraryIds, mediaStatus, readStatus, releasedAfter, tags, unpaged, page)

  override fun cases() {
    func("getAllBooksDeprecated") {
      case("empty") { deprecated(admin) }
      case("admin") {
        RestSamples.seed(db)
        db.thumbnailBookDao.insert(thumb("TB1", "B1", true))
        db.thumbnailBookDao.insert(thumb("TB2", "B1", false))
        db.thumbnailBookDao.insert(thumb("TB3", "B3", true))
        deprecated(admin)
      }
      case("user, sorted") { deprecated(all, page = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("metadata.title")))) }
      case("paged") { deprecated(admin, page = PageRequest.of(1, 2, Sort.by("name"))) }
      case("unpaged") { deprecated(admin, unpaged = true, page = PageRequest.of(1, 1, Sort.by("name"))) }
      case("filters") { deprecated(all, libraryIds = listOf("L1"), mediaStatus = listOf(Media.Status.READY), readStatus = listOf(ReadStatus.UNREAD, ReadStatus.READ), tags = listOf("bt1")) }
      case("released after") { deprecated(admin, releasedAfter = LocalDate.of(2019, 1, 1)) }
      case("restricted") { listOf(deprecated(l1), deprecated(kids), deprecated(noAdult)) }
    }
    func("getBooks") {
      case("empty search") { c.getBooks(admin, read("{}"), false, p20) }
      case("condition") { c.getBooks(all, read("""{"condition":{"seriesId":{"operator":"is","value":"S1"}}}"""), false, PageRequest.of(0, 1, Sort.by("metadata.numberSort"))) }
      case("allOf") {
        c.getBooks(admin, read("""{"condition":{"allOf":[{"libraryId":{"operator":"is","value":"L1"}},{"readStatus":{"operator":"isNot","value":"READ"}}]}}"""), false, p20)
      }
      case("unpaged") { c.getBooks(kids, read("{}"), true, PageRequest.of(1, 1)) }
    }
    func("getBooksLatest") {
      case("admin") { c.getBooksLatest(admin, false, p20) }
      case("paged") { c.getBooksLatest(all, false, PageRequest.of(1, 2)) }
      case("unpaged kids") { c.getBooksLatest(kids, true, PageRequest.of(1, 2)) }
    }
    func("getBooksOnDeck") {
      case("all") { c.getBooksOnDeck(all, null, p20) }
      case("admin library L2") { c.getBooksOnDeck(admin, listOf("L2"), p20) }
      case("kids") { c.getBooksOnDeck(kids, null, p20) }
    }
    func("getBookById") {
      case("admin") { c.getBookById(admin, "B1") }
      case("user with progress") { c.getBookById(all, "B2") }
      case("restricted library") { c.getBookById(l1, "B4") }
      case("age restricted") { c.getBookById(kids, "B3") }
      case("unknown") { c.getBookById(admin, "BX") }
    }
    func("getBookSiblingPrevious") {
      case("B2") { c.getBookSiblingPrevious(all, "B2") }
      case("first") { c.getBookSiblingPrevious(admin, "B1") }
      case("restricted") { c.getBookSiblingPrevious(kids, "B3") }
      case("unknown") { c.getBookSiblingPrevious(admin, "BX") }
    }
    func("getBookSiblingNext") {
      case("B1") { c.getBookSiblingNext(admin, "B1") }
      case("last") { c.getBookSiblingNext(all, "B2") }
      case("restricted") { c.getBookSiblingNext(l1, "B4") }
    }
    func("getReadListsByBookId") {
      case("B1") { c.getReadListsByBookId(admin, "B1") }
      case("kids") { c.getReadListsByBookId(kids, "B1") }
      case("none") { c.getReadListsByBookId(all, "B2") }
      case("restricted") { c.getReadListsByBookId(kids, "B3") }
    }
    func("getBookThumbnail") {
      case("ok") { listOf(c.getBookThumbnail(all, "B1"), calls.take()) }
      case("none") { listOf(thrown { c.getBookThumbnail(admin, "B4") }, calls.take()) }
      case("restricted") { listOf(thrown { c.getBookThumbnail(kids, "B3") }, calls.take()) }
    }
    func("getBookThumbnailById") {
      case("ok") { listOf(c.getBookThumbnailById(admin, "B1", "TB1"), calls.take()) }
      case("no bytes") { listOf(thrown { c.getBookThumbnailById(admin, "B1", "TB2") }, calls.take()) }
      case("thumbnail of restricted book") { listOf(thrown { c.getBookThumbnailById(kids, "B1", "TB3") }, calls.take()) }
      case("unknown thumbnail") { listOf(thrown { c.getBookThumbnailById(admin, "B1", "TX") }, calls.take()) }
    }
    func("getBookThumbnails") {
      case("B1") { c.getBookThumbnails(admin, "B1") }
      case("none") { c.getBookThumbnails(all, "B2") }
      case("restricted") { c.getBookThumbnails(l1, "B4") }
    }
    func("addUserUploadedBookThumbnail") {
      case("selected") { listOf(stable(c.addUserUploadedBookThumbnail(admin, "B1", MockMultipartFile("file", "a.png", "image/png", PNG))), calls.take()) }
      case("not selected") { listOf(stable(c.addUserUploadedBookThumbnail(admin, "B2", MockMultipartFile("file", PNG), false)), calls.take()) }
      case("not an image") { c.addUserUploadedBookThumbnail(admin, "B1", MockMultipartFile("file", "abc".toByteArray())) }
      case("unknown book") { c.addUserUploadedBookThumbnail(admin, "BX", MockMultipartFile("file", PNG)) }
    }
    func("markBookThumbnailSelected") {
      case("ok") {
        c.markBookThumbnailSelected(admin, "B1", "TB2")
        listOf(calls.take(), db.thumbnailBookDao.findAllByBookId("B1").map { listOf(it.id, it.selected) })
      }
      case("other book") { c.markBookThumbnailSelected(admin, "B3", "TB1") }
      case("unknown thumbnail") { c.markBookThumbnailSelected(admin, "B1", "TX") }
      case("unknown book") { c.markBookThumbnailSelected(admin, "BX", "TB1") }
    }
    func("deleteUserUploadedBookThumbnail") {
      case("ok") {
        c.deleteUserUploadedBookThumbnail(admin, "B1", "TB1")
        calls.take()
      }
      case("illegal argument") { listOf(thrown { c.deleteUserUploadedBookThumbnail(admin, "B1", "TB2") }, calls.take()) }
      case("other book") { c.deleteUserUploadedBookThumbnail(admin, "B3", "TB1") }
      case("unknown thumbnail") { c.deleteUserUploadedBookThumbnail(admin, "B1", "TX") }
      case("unknown book") { c.deleteUserUploadedBookThumbnail(admin, "BX", "TB1") }
    }
    func("getBookPages") {
      case("ready") { c.getBookPages(all, "B1") }
      case("restricted") { c.getBookPages(kids, "B3") }
      case("unknown") { c.getBookPages(admin, "BX") }
      case("statuses") {
        extraBook("B10", "S1", "h10", Media(status = Media.Status.UNKNOWN))
        extraBook("B11", "S1", "h11", Media(status = Media.Status.OUTDATED))
        extraBook("B12", "S1", "h12", Media(status = Media.Status.ERROR))
        extraBook("B13", "S1", "h13", Media(status = Media.Status.UNSUPPORTED))
        extraBook("B14", "S1", "h14", Media(status = Media.Status.READY, mediaType = "application/pdf", pages = listOf(BookPage("1", "image/jpeg"))))
        listOf("B10", "B11", "B12", "B13").map { thrown { c.getBookPages(admin, it) } }
      }
      case("pdf uses dynamic pages") { listOf(c.getBookPages(admin, "B14"), calls.take()) }
    }
    func("getBookPageThumbnailByNumber") {
      case("ok") { listOf(entity(c.getBookPageThumbnailByNumber(all, ServletWebRequest(request()), "B1", 1)), calls.take()) }
      case("invalid media type") { listOf(entity(c.getBookPageThumbnailByNumber(all, ServletWebRequest(request()), "B1", 2)), calls.take()) }
      case("not modified") { listOf(entity(c.getBookPageThumbnailByNumber(all, ServletWebRequest(request("Sat, 14 Mar 2020 09:00:00 GMT")), "B1", 1)), calls.take()) }
      case("not modified before restriction check") { entity(c.getBookPageThumbnailByNumber(kids, ServletWebRequest(request("Wed, 01 Jan 2031 00:00:00 GMT")), "B3", 1)) }
      case("restricted") { listOf(thrown { c.getBookPageThumbnailByNumber(kids, ServletWebRequest(request()), "B3", 1) }, calls.take()) }
      case("errors") {
        listOf(99, 98, 97, 96).map { n -> thrown { c.getBookPageThumbnailByNumber(admin, ServletWebRequest(request()), "B1", n) } } + listOf(calls.take())
      }
      case("unknown book") { c.getBookPageThumbnailByNumber(admin, ServletWebRequest(request()), "BX", 1) }
    }
    func("getBookWebPubManifest") {
      case("ok") {
        val e = c.getBookWebPubManifest(all, "B1")
        listOf(e.statusCode.value(), e.headers.getFirst("Content-Type"), json(e.body), calls.take())
      }
    }
    func("getBookWebPubManifestEpub") {
      case("ok") { listOf(json(c.getBookWebPubManifestEpub(admin, "B1")), calls.take()) }
    }
    func("getBookWebPubManifestPdf") {
      case("ok") { listOf(json(c.getBookWebPubManifestPdf(admin, "B2")), calls.take()) }
    }
    func("getBookWebPubManifestDivina") {
      case("ok") { listOf(json(c.getBookWebPubManifestDivina(kids, "B1")), calls.take()) }
    }
    func("getBooksDuplicates") {
      case("none") { c.getBooksDuplicates(admin, false, p20) }
      case("duplicates by hash") {
        extraBook("B7", "S2", "hashB1", Media(status = Media.Status.READY, mediaType = "application/zip"))
        extraBook("B8", "S3", "hashB3", Media(status = Media.Status.READY, mediaType = "application/zip"))
        c.getBooksDuplicates(admin, false, p20)
      }
      case("sorted desc") { c.getBooksDuplicates(admin, false, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("fileHash")))) }
      case("paged") { c.getBooksDuplicates(all, false, PageRequest.of(1, 2)) }
      case("unpaged") { c.getBooksDuplicates(admin, true, PageRequest.of(1, 1)) }
    }
    func("getBookPositions") {
      case("no extension") { c.getBookPositions(request(), admin, "B1") }
      case("epub") {
        extraBook(
          "B9",
          "S1",
          "h9",
          Media(
            status = Media.Status.READY,
            mediaType = "application/epub+zip",
            extension = MediaExtensionEpub(positions = listOf(R2Locator("ch1.xhtml", "application/xhtml+xml", "Chapter 1", R2Locator.Location(progression = 0f, position = 1, totalProgression = 0f)))),
            lastModifiedDate = LocalDateTime.of(2021, 1, 1, 0, 0),
          ),
        )
        entity(c.getBookPositions(request(), admin, "B9"))
      }
      case("not modified") { entity(c.getBookPositions(request("Sat, 02 Jan 2021 00:00:00 GMT"), kids, "B9")) }
      case("restricted") { c.getBookPositions(request(), kids, "B3") }
      case("unknown") { c.getBookPositions(request(), admin, "BX") }
    }
    func("bookAnalyze") {
      case("ok") {
        c.bookAnalyze("B1")
        listOf(tasks(db), calls.take())
      }
      case("unknown") { listOf(thrown { c.bookAnalyze("BX") }, tasks(db)) }
    }
    func("bookRefreshMetadata") {
      case("ok") {
        c.bookRefreshMetadata("B3")
        listOf(tasks(db), calls.take())
      }
      case("unknown") { listOf(thrown { c.bookRefreshMetadata("BX") }, tasks(db)) }
    }
    func("updateBookMetadata") {
      case("patch") {
        c.updateBookMetadata("B2", read<BookMetadataUpdateDto>("""{"title":"New title","summary":null,"tags":["x"],"releaseDate":"2022-02-02","numberSort":7.5}"""))
        listOf(stable(db.bookMetadataDao.findById("B2")), tasks(db), calls.take())
      }
      case("unknown") { listOf(thrown { c.updateBookMetadata("BX", read<BookMetadataUpdateDto>("{}")) }, tasks(db), calls.take()) }
    }
    func("updateBookMetadataByBatch") {
      case("batch") {
        c.updateBookMetadataByBatch(
          linkedMapOf(
            "B1" to read<BookMetadataUpdateDto>("""{"titleLock":true}"""),
            "BX" to read<BookMetadataUpdateDto>("""{"title":"nope"}"""),
            "B3" to read<BookMetadataUpdateDto>("""{"authors":[{"name":"New","role":"writer"}]}"""),
            "B2" to read<BookMetadataUpdateDto>("""{"isbn":null}"""),
          ),
        )
        listOf(listOf("B1", "B2", "B3").map { stable(db.bookMetadataDao.findById(it)) }, tasks(db), calls.take())
      }
      case("empty") {
        c.updateBookMetadataByBatch(emptyMap())
        listOf(tasks(db), calls.take())
      }
    }
    func("deleteBookReadProgress") {
      case("ok") {
        c.deleteBookReadProgress("B1", all)
        calls.take()
      }
      case("restricted") { listOf(thrown { c.deleteBookReadProgress("B3", kids) }, calls.take()) }
      case("unknown") { c.deleteBookReadProgress("BX", all) }
    }
    func("importBooks") {
      case("batch") {
        c.importBooks(
          read<BookImportBatchDto>(
            """{"books":[{"sourceFile":"/import/a.cbz","seriesId":"S1"},{"sourceFile":"/import/b.cbz","seriesId":"S2","upgradeBookId":"B3","destinationName":"b2"}],"copyMode":"HARDLINK"}""",
          ),
        )
        listOf(tasks(db), calls.take())
      }
      case("empty") {
        c.importBooks(read<BookImportBatchDto>("""{"copyMode":"MOVE"}"""))
        tasks(db)
      }
    }
    func("deleteBookFile") {
      case("ok") {
        c.deleteBookFile("B1")
        listOf(tasks(db), calls.take())
      }
      case("unknown id is still submitted") {
        c.deleteBookFile("BX")
        tasks(db)
      }
    }
    func("booksRegenerateThumbnails") {
      case("default") {
        c.booksRegenerateThumbnails()
        listOf(tasks(db), calls.take())
      }
      case("bigger only") {
        c.booksRegenerateThumbnails(true)
        tasks(db)
      }
    }
  }
}
