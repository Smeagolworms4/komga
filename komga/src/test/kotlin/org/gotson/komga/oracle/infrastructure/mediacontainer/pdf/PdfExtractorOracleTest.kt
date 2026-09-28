package org.gotson.komga.oracle.infrastructure.mediacontainer.pdf

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.mediacontainer.pdf.PdfExtractor
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class PdfExtractorOracleTest : OracleTest() {
  private val jpeg = PdfExtractor(ImageType.JPEG, 400F)
  private val png = PdfExtractor(ImageType.PNG, 150F)
  private val komga = PdfExtractor(ImageType.JPEG, 1536F)
  private val imageAnalyzer = ImageAnalyzer()

  private val pdfs = listOf("enc-owner.pdf", "enc-owner-rc4.pdf", "enc-user.pdf", "inherited.pdf", "komga.pdf", "nomediabox.pdf", "not-a-pdf.pdf", "no-xref.pdf", "rotate-inherited.pdf", "weird-0.pdf", "weird-10.pdf", "weird-1.pdf", "weird-2.pdf", "weird-3.pdf", "weird-4.pdf", "weird-5.pdf", "weird-6.pdf", "weird-7.pdf", "weird-8.pdf", "weird-9.pdf", "weird-boxes.pdf")

  /** PDF library error messages are not comparable (PDFBox against mupdf): only the exception type is kept */
  private fun typeOnError(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      listOf("throws", e::class.java.simpleName)
    }

  /** Rendered pixels and encoded bytes differ (accepted deviation): media type and image dimension are compared */
  private fun image(tb: TypedBytes) = listOf(tb.mediaType, imageAnalyzer.getDimension(tb.bytes.inputStream()))

  private fun pdf(tb: TypedBytes): Any? {
    val f = tempDir.resolve("extracted.pdf")
    f.writeBytes(tb.bytes)
    return listOf(tb.mediaType, jpeg.getPages(f, true))
  }

  override fun cases() {
    func("getPages") {
      for (p in pdfs) {
        for (analyze in listOf(true, false)) case("$p (analyze $analyze)") { typeOnError { jpeg.getPages(Samples.fixture("pdf/$p"), analyze) } }
      }
      case("komga resource") { jpeg.getPages(Samples.komgaRes("pdf/komga.pdf"), true) }
      case("text file") { typeOnError { jpeg.getPages(tempDir.resolve("text.pdf").also { it.writeText("hello") }, true) } }
      case("empty file") { typeOnError { jpeg.getPages(tempDir.resolve("empty.pdf").also { it.writeBytes(ByteArray(0)) }, true) } }
      case("missing file") { exceptionType { jpeg.getPages(tempDir.resolve("missing.pdf"), true) } }
    }
    func("getPageContentAsImage") {
      for (p in pdfs) {
        for (n in listOf(0, 1, 2, 5, 7, 8)) case("$p page $n") { typeOnError { image(jpeg.getPageContentAsImage(Samples.fixture("pdf/$p"), n)) } }
      }
      for (n in 1..3) case("png komga.pdf page $n") { typeOnError { image(png.getPageContentAsImage(Samples.fixture("pdf/komga.pdf"), n)) } }
      case("png weird-boxes.pdf page 1") { typeOnError { image(png.getPageContentAsImage(Samples.fixture("pdf/weird-boxes.pdf"), 1)) } }
      case("resolution 1536 komga.pdf page 1") { typeOnError { image(komga.getPageContentAsImage(Samples.fixture("pdf/komga.pdf"), 1)) } }
    }
    func("getPageContentAsPdf") {
      for (p in pdfs) {
        for (n in listOf(0, 1, 2, 7, 8)) case("$p page $n") { typeOnError { pdf(jpeg.getPageContentAsPdf(Samples.fixture("pdf/$p"), n)) } }
      }
    }
    // private: getScale(PDPage), through the size of the rendered images
    func("getScale@69") {
      for (r in listOf(1F, 72F, 100F, 333F)) {
        case("resolution $r") { typeOnError { image(PdfExtractor(ImageType.PNG, r).getPageContentAsImage(Samples.fixture("pdf/komga.pdf"), 1)) } }
      }
      case("rotated page") { typeOnError { image(PdfExtractor(ImageType.PNG, 50F).getPageContentAsImage(Samples.fixture("pdf/rotate-inherited.pdf"), 1)) } }
    }
    // private: getScale(width, height), through scaleDimension
    func("getScale@71") {
      for (r in listOf(0F, 1F, 1536F, -10F, 0.5F)) case("resolution $r") { PdfExtractor(ImageType.JPEG, r).scaleDimension(Dimension(600, 800)) }
    }
    func("scaleDimension") {
      val dims = listOf(0 to 0, 1 to 1, 0 to 100, 100 to 0, 595 to 842, 842 to 595, 1536 to 1536, 1000 to 10, 10 to 1000, 3 to 7, 12345 to 678, -5 to 10, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)
      for ((w, h) in dims) {
        case("${w}x$h") { jpeg.scaleDimension(Dimension(w, h)) }
        case("${w}x$h at 1536") { komga.scaleDimension(Dimension(w, h)) }
      }
    }
  }
}
