package org.gotson.komga.oracle.infrastructure.metadata.barcode

import io.mockk.every
import io.mockk.mockk
import org.apache.commons.validator.routines.ISBNValidator
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.infrastructure.metadata.barcode.IsbnBarcodeProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples
import kotlin.io.path.readBytes

class IsbnBarcodeProviderOracleTest : OracleTest() {
  private val images =
    listOf("bottom-alpha.png", "cmyk.jpg", "eighth.png", "empty.bin", "garbage.bin", "gray.jpg", "half.jpg", "negate.jpg", "q5.jpg", "rot180.jpg", "rot270.jpg", "rot90.jpg", "truncated.jpg", "webp.webp").map { it to MetadataSamples.fixture("barcode/$it").readBytes() } +
      listOf("komga page_384.jpg" to Samples.komgaRes("barcode/page_384.jpg").readBytes(), "komga komga.png" to Samples.komgaRes("barcode/komga.png").readBytes())

  private val isbn = images.first { it.first == "komga page_384.jpg" }.second
  private val blank = images.first { it.first == "komga komga.png" }.second

  /** Result, and the pages requested from the book analyzer, in order */
  private fun run(
    pageCount: Int,
    mediaType: String = "application/zip",
    content: (Int) -> ByteArray,
  ): List<Any?> {
    val requested = mutableListOf<Int>()
    val analyzer =
      mockk<BookAnalyzer> {
        every { getPageContent(any(), any()) } answers {
          val p = secondArg<Int>()
          requested += p
          content(p)
        }
      }
    val book = BookWithMedia(MetadataSamples.book(), Media(mediaType = mediaType, pageCount = pageCount))
    return listOf(IsbnBarcodeProvider(analyzer, ISBNValidator(true)).getBookMetadataFromBook(book), requested)
  }

  override fun cases() {
    func("getBookMetadataFromBook") {
      for ((name, bytes) in images) case("single page $name") { run(1) { bytes } }
      for (n in listOf(0, 1, 2, 3, 4, 5, 6, 7, 10)) case("$n pages without barcode") { run(n) { blank } }
      for (p in listOf(1, 2, 3, 4, 5, 6, 8, 9, 10)) case("10 pages, barcode on page $p") { run(10) { if (it == p) isbn else blank } }
      case("barcode on two pages") { run(10) { if (it == 1 || it == 9) isbn else blank } }
      case("epub") { run(3, "application/epub+zip") { isbn } }
      case("pdf") { run(2, "application/pdf") { if (it == 2) isbn else blank } }
      case("unknown media type") { run(2, "application/octet-stream") { isbn } }
      case("analyzer error then barcode") { run(3) { if (it == 3) throw IllegalStateException("boom") else isbn } }
      case("not an image then barcode") { run(3) { if (it == 3) "text".toByteArray() else isbn } }
    }
    func("shouldLibraryHandlePatch") {
      val provider = IsbnBarcodeProvider(mockk(), ISBNValidator(true))
      for ((name, library) in MetadataSamples.libraries) {
        for (target in MetadataPatchTarget.entries) case("$name, $target") { provider.shouldLibraryHandlePatch(library, target) }
      }
    }
  }
}
