package org.gotson.komga.oracle.infrastructure.image

import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.oracle.OracleTest
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.readBytes

class ImageAnalyzerOracleTest : OracleTest() {
  private val analyzer = ImageAnalyzer()

  private fun res(p: String) = Path.of("src/test/resources", p).readBytes()

  override fun cases() {
    func("getDimension") {
      case("png rgba") { analyzer.getDimension(res("barcode/komga.png").inputStream()) }
      case("jpeg") { analyzer.getDimension(res("barcode/page_384.jpg").inputStream()) }
      case("jpeg exif") { analyzer.getDimension(res("hashpage/e-z.jpeg/1.jpg").inputStream()) }
      case("indexed png") { analyzer.getDimension(res("hashpage/dd.png/1.png").inputStream()) }
      case("gif") { analyzer.getDimension(res("hashpage/tr.gif/1.gif").inputStream()) }
      case("tiny png") {
        analyzer.getDimension(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAYAAACddGYaAAAAH0lEQVR4nGP4z8DwHwwZ/v9n4BKR+69hZPPfLSDqPwCJ2wq6OEinjgAAAABJRU5ErkJggg==").inputStream())
      }
      case("png header only") { analyzer.getDimension(res("barcode/komga.png").copyOf(33).inputStream()) }
      case("truncated jpeg") { analyzer.getDimension(res("barcode/page_384.jpg").copyOf(2000).inputStream()) }
      case("garbage") { analyzer.getDimension(oracleBytes(64).inputStream()) }
      case("empty") { analyzer.getDimension(ByteArray(0).inputStream()) }
    }
  }
}
