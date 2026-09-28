package org.gotson.komga.oracle.infrastructure.image

import org.apache.tika.config.TikaConfig
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageConverter
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.oracle.OracleTest
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.readBytes

/**
 * Pixels differ between the JVM and KomgaJS (see PORTING.md, images): the outputs are described by their detected media
 * type, dimensions and alpha channel. Only formats decoded without native libraries (JPEG, PNG, GIF) are used, so
 * that the fixtures do not depend on the machine.
 */
class ImageConverterOracleTest : OracleTest() {
  private val contentDetector = ContentDetector(TikaConfig())
  private val converter = ImageConverter(ImageAnalyzer(), contentDetector)

  private fun res(p: String) = Path.of("src/test/resources", p).readBytes()

  private val png = res("barcode/komga.png") // 48x48 RGBA with transparency
  private val jpg = res("barcode/page_384.jpg")
  private val indexed = res("hashpage/dd.png/1.png")
  private val gif = res("hashpage/tr.gif/1.gif")
  private val opaque = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAYAAACddGYaAAAAH0lEQVR4nGP4z8DwHwwZ/v9n4BKR+69hZPPfLSDqPwCJ2wq6OEinjgAAAABJRU5ErkJggg==")
  private val transparent = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVR4nGP4zwAE/xkaQOR/Ribm/wAt+QWCAUaJagAAAABJRU5ErkJggg==")
  private val garbage = oracleBytes(64)

  private fun describe(
    out: ByteArray,
    input: ByteArray? = null,
  ): Any? {
    if (out === input) return "same"
    if (out.isEmpty()) return "empty"
    val image = ImageIO.read(out.inputStream())
    return listOf(contentDetector.detectMediaType(out.inputStream()), image?.width, image?.height, image?.colorModel?.hasAlpha())
  }

  private fun describe(image: BufferedImage) = listOf(image.width, image.height, image.colorModel.hasAlpha())

  override fun cases() {
    func("chooseWebpReader") {
      case("constructor succeeds") { ImageConverter(ImageAnalyzer(), contentDetector).let { true } }
    }

    func("canConvertMediaType") {
      for ((from, to) in listOf(
        "image/jpeg" to "image/png",
        "image/png" to "image/jpeg",
        "image/gif" to "image/png",
        "image/bmp" to "image/jpeg",
        "image/jpeg" to "image/gif",
        "image/jpeg" to "image/jpeg",
        "application/pdf" to "image/jpeg",
        "image/jpeg" to "application/pdf",
        "IMAGE/JPEG" to "image/png",
        "image/jpg" to "image/png",
        "" to "",
      )) {
        case("$from -> $to") { converter.canConvertMediaType(from, to) }
      }
    }

    func("convertImage") {
      case("png with transparency to jpeg") { describe(converter.convertImage(png, "jpeg")) }
      case("png with transparency to png") { describe(converter.convertImage(png, "png")) }
      case("opaque rgba png to jpeg") { describe(converter.convertImage(opaque, "jpeg")) }
      case("jpeg to png") { describe(converter.convertImage(jpg, "png")) }
      case("jpeg to jpeg") { describe(converter.convertImage(jpg, "jpeg")) }
      case("indexed png to jpeg") { describe(converter.convertImage(indexed, "jpeg")) }
      case("gif to png") { describe(converter.convertImage(gif, "png")) }
      case("upper case format") { describe(converter.convertImage(opaque, "JPEG")) }
      case("unknown format") { describe(converter.convertImage(opaque, "xyz")) }
      case("garbage input") { exceptionType { converter.convertImage(garbage, "png") } }
      case("empty input") { exceptionType { converter.convertImage(ByteArray(0), "png") } }
    }

    func("containsAlphaChannel") {
      case("rgba to jpeg drops alpha") { describe(converter.convertImage(transparent, "jpeg")) }
      case("rgb stays rgb") { describe(converter.convertImage(jpg, "jpeg")) }
    }

    func("containsTransparency") {
      case("transparent pixels") { describe(converter.convertImage(transparent, "jpeg")) }
      case("opaque pixels") { describe(converter.convertImage(opaque, "jpeg")) }
    }

    func("resizeImageToByteArray") {
      case("png to jpeg, no upscale") { describe(converter.resizeImageToByteArray(png, ImageType.JPEG, 100), png) }
      case("png to png, smaller than size") { describe(converter.resizeImageToByteArray(png, ImageType.PNG, 100), png) }
      case("png to png, equal size") { describe(converter.resizeImageToByteArray(png, ImageType.PNG, 48), png) }
      case("png to png, downscale") { describe(converter.resizeImageToByteArray(png, ImageType.PNG, 24), png) }
      case("jpeg to jpeg, downscale") { describe(converter.resizeImageToByteArray(jpg, ImageType.JPEG, 300), jpg) }
      case("jpeg to jpeg, huge size") { describe(converter.resizeImageToByteArray(jpg, ImageType.JPEG, 5000), jpg) }
      case("jpeg to png") { describe(converter.resizeImageToByteArray(jpg, ImageType.PNG, 150), jpg) }
      case("gif to jpeg") { describe(converter.resizeImageToByteArray(gif, ImageType.JPEG, 100), gif) }
      case("size 1") { describe(converter.resizeImageToByteArray(indexed, ImageType.PNG, 1), indexed) }
      case("garbage") { exceptionType { converter.resizeImageToByteArray(garbage, ImageType.JPEG, 100) } }
    }

    func("resizeImageToBufferedImage") {
      case("png smaller than size") { describe(converter.resizeImageToBufferedImage(png, ImageType.PNG, 100)) }
      case("png to jpeg") { describe(converter.resizeImageToBufferedImage(png, ImageType.JPEG, 100)) }
      case("png downscale") { describe(converter.resizeImageToBufferedImage(png, ImageType.PNG, 10)) }
      case("jpeg downscale") { describe(converter.resizeImageToBufferedImage(jpg, ImageType.JPEG, 150)) }
      case("opaque upscale prevented") { describe(converter.resizeImageToBufferedImage(opaque, ImageType.JPEG, 300)) }
      case("garbage") { exceptionType { converter.resizeImageToBufferedImage(garbage, ImageType.JPEG, 100) } }
    }

    func("resizeImageBuilder") {
      case("same type and smaller keeps bytes") { converter.resizeImageToByteArray(opaque, ImageType.PNG, 3) === opaque }
      case("other type converts") { converter.resizeImageToByteArray(opaque, ImageType.JPEG, 3) === opaque }
    }
  }
}
