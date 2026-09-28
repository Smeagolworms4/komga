package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaFile
import org.gotson.komga.domain.model.MediaNotReadyException
import org.gotson.komga.domain.model.PathContainedInPath
import org.gotson.komga.domain.model.TransientBook
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.domain.service.TransientBookLifecycle
import org.gotson.komga.infrastructure.cache.TransientBookCache
import org.gotson.komga.interfaces.api.rest.ScanRequestDto
import org.gotson.komga.interfaces.api.rest.TransientBooksController
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.entity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import java.net.URL
import java.nio.file.NoSuchFileException
import java.time.LocalDateTime

class TransientBooksControllerOracleTest : OracleTest() {
  private val calls = RestOracle.Calls()
  private val repository = TransientBookCache()

  private fun tb(
    id: String,
    path: String,
    mediaType: String? = null,
    status: Media.Status = Media.Status.UNKNOWN,
  ) = TransientBook(
    Book(name = id, url = URL("file:$path"), fileLastModified = FIXED, fileSize = 2048, id = id, createdDate = FIXED),
    Media(
      status = status,
      mediaType = mediaType,
      pages =
        if (status == Media.Status.READY) {
          listOf(BookPage("1.jpg", "image/jpeg", Dimension(10, 20), fileSize = 5), BookPage("2.png", "image/png"))
        } else {
          emptyList()
        },
      files = if (status == Media.Status.READY) listOf(MediaFile("ComicInfo.xml")) else emptyList(),
      comment = if (status == Media.Status.ERROR) "ERR_1001" else null,
      bookId = id,
      createdDate = FIXED,
      lastModifiedDate = LocalDateTime.of(2021, 5, 6, 7, 8, 9),
    ),
  )

  /** Records the calls (same fake in the TypeScript twin) */
  private val lifecycle =
    mockk<TransientBookLifecycle> {
      every { scanAndPersist(any()) } answers {
        val path = firstArg<String>()
        calls.add("scanAndPersist", path)
        if (path == "/bad") throw PathContainedInPath("contained", "ERR_1017")
        if (path == "/boom") throw IllegalStateException("boom")
        listOf(tb("T2", "/t/b.cbz"), tb("T1", "/t/A.cbz"), tb("T3", "/t/a/z.pdf"), tb("T4", "/t/a b.cbz")).onEach { repository.save(it) }
      }
      every { analyzeAndPersist(any()) } answers {
        val t = firstArg<TransientBook>()
        calls.add("analyzeAndPersist", t.book.id)
        val status = if (t.book.id == "T4") Media.Status.ERROR else Media.Status.READY
        val mediaType = if (t.book.id == "T3") "application/pdf" else "application/zip"
        tb(t.book.id, t.book.path.toString(), mediaType, status)
          .copy(metadata = TransientBook.Metadata(if (t.book.id == "T1") 1.5f else null, if (t.book.id == "T1") "S1" else null))
          .also { repository.save(it) }
      }
      every { getBookPage(any(), any()) } answers {
        val n = secondArg<Int>()
        calls.add("getBookPage", firstArg<TransientBook>().book.id, n)
        when (n) {
          99 -> throw IndexOutOfBoundsException("99")
          98 -> throw MediaNotReadyException()
          97 -> throw NoSuchFileException("/t/b.cbz")
          96 -> throw IllegalStateException("other")
          2 -> TypedBytes(byteArrayOf(2), "bad type")
          else -> TypedBytes(byteArrayOf(1, 2, 3), "image/jpeg")
        }
      }
    }
  private val analyzer =
    mockk<BookAnalyzer> {
      every { getPdfPagesDynamic(any()) } answers {
        calls.add("getPdfPagesDynamic", firstArg<Media>().bookId)
        listOf(BookPage("0", "image/jpeg", Dimension(1, 2)))
      }
    }
  private val c = TransientBooksController(lifecycle, repository, analyzer)

  private fun request(ifModifiedSince: String? = null) = ServletWebRequest(MockHttpServletRequest("GET", "/x").apply { ifModifiedSince?.let { addHeader("If-Modified-Since", it) } })

  override fun cases() {
    func("scanTransientBooks") {
      case("sorted by path") { listOf(c.scanTransientBooks(ScanRequestDto("/t")), calls.take()) }
      case("coded exception") { listOf(c.scanTransientBooks(ScanRequestDto("/bad")), calls.take()) }
      case("other exception") { listOf(exceptionType { c.scanTransientBooks(ScanRequestDto("/boom")) }, calls.take()) }
    }
    func("analyzeTransientBook") {
      case("cbz") { listOf(c.analyzeTransientBook("T1"), calls.take()) }
      case("pdf uses dynamic pages") { listOf(c.analyzeTransientBook("T3"), calls.take()) }
      case("error") { c.analyzeTransientBook("T4").also { calls.take() } }
      case("not found") { listOf(exceptionType { c.analyzeTransientBook("NOPE") }, calls.take()) }
    }
    func("getPageByTransientBookId") {
      case("page") { listOf(entity(c.getPageByTransientBookId("T1", 1, request())), calls.take()) }
      case("invalid media type") { entity(c.getPageByTransientBookId("T1", 2, request())).also { calls.take() } }
      case("not modified") { listOf(entity(c.getPageByTransientBookId("T1", 1, request("Thu, 06 May 2021 07:08:09 GMT"))), calls.take()) }
      case("modified since earlier") { entity(c.getPageByTransientBookId("T1", 1, request("Wed, 05 May 2021 07:08:09 GMT"))).also { calls.take() } }
      case("page does not exist") { c.getPageByTransientBookId("T1", 99, request()) }
      case("media not ready") { c.getPageByTransientBookId("T1", 98, request()) }
      case("file not found") { c.getPageByTransientBookId("T1", 97, request()) }
      case("other") { listOf(exceptionType { c.getPageByTransientBookId("T1", 96, request()) }, calls.take()) }
      case("unknown book") { c.getPageByTransientBookId("NOPE", 1, request()) }
    }
    func("toDto") {
      case("via scan: unanalyzed") { c.scanTransientBooks(ScanRequestDto("/t")).map { it.pages.size }.also { calls.take() } }
    }
  }
}
