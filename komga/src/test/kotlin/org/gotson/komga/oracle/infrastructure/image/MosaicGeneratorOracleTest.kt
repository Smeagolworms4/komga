package org.gotson.komga.oracle.infrastructure.image

import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.ThumbnailSize
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageConverter
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.image.MosaicGenerator
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.readBytes

class MosaicGeneratorOracleTest : OracleTest() {
  private val db = OracleDb()
  private val contentDetector = ContentDetector(TikaConfig())
  private val converter = ImageConverter(ImageAnalyzer(), contentDetector)
  private val settings = KomgaSettingsProvider(db.serverSettingsDao, ApplicationEventPublisher { })

  private fun res(p: String) = Path.of("src/test/resources", p).readBytes()

  private val png = res("barcode/komga.png")
  private val jpg = res("barcode/page_384.jpg")
  private val gif = res("hashpage/tr.gif/1.gif")

  private fun describe(out: ByteArray): Any? {
    val image = ImageIO.read(out.inputStream())
    return listOf(contentDetector.detectMediaType(out.inputStream()), image?.width, image?.height, image?.colorModel?.hasAlpha())
  }

  override fun cases() {
    func("createMosaic") {
      val jpeg = MosaicGenerator(settings, ImageType.JPEG, converter)
      case("no image") { describe(jpeg.createMosaic(emptyList())) }
      case("one image") { describe(jpeg.createMosaic(listOf(jpg))) }
      case("four images") { describe(jpeg.createMosaic(listOf(jpg, png, gif, jpg))) }
      case("five images") { describe(jpeg.createMosaic(listOf(jpg, png, gif, jpg, png))) }
      case("png thumbnail type") { describe(MosaicGenerator(settings, ImageType.PNG, converter).createMosaic(listOf(png, jpg))) }
      case("large thumbnail size") {
        settings.thumbnailSize = ThumbnailSize.LARGE
        describe(jpeg.createMosaic(listOf(gif)))
      }
      case("garbage image") { exceptionType { jpeg.createMosaic(listOf(oracleBytes(10))) } }
    }
  }
}
