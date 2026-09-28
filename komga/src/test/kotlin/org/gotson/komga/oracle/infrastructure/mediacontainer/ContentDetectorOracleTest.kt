package org.gotson.komga.oracle.infrastructure.mediacontainer

import org.apache.tika.config.TikaConfig
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeBytes

class ContentDetectorOracleTest : OracleTest() {
  private val detector = ContentDetector(TikaConfig())

  private val resources =
    listOf(
      "archives/zip.zip", "archives/zip-copy.zip", "archives/zip-bzip2.zip", "archives/zip-deflate64.zip", "archives/zip-lzma.zip",
      "archives/zip-ppmd.zip", "archives/zip-encrypted.zip", "archives/epub3.epub", "archives/zip-as-epub.epub", "archives/rar4.rar",
      "archives/rar4-solid.rar", "archives/rar4-encrypted.rar", "archives/rar5.rar", "archives/rar5-solid.rar", "archives/rar5-encrypted.rar",
      "archives/7zip.7z", "archives/7zip-encrypted.7z", "pdf/komga.pdf", "barcode/komga.png", "barcode/page_384.jpg",
      "epub/The Incomplete Theft - Ralph Burke.epub", "epub/toc.ncx", "epub/nav.xhtml", "epub/1979.opf",
      "hashpage/e-drq.webp/1.webp", "hashpage/tr.gif/1.gif", "hashpage/m-d.jpeg/1.jpg", "hashpage/dd.png/1.png",
    )

  override fun cases() {
    func("detectMediaType@15") {
      for (r in resources) case("resource $r") { detector.detectMediaType(Samples.komgaRes(r)) }
      for ((sample, bytes) in Samples.contents(::oracleBytes)) {
        for (name in Samples.names) {
          case("$sample as $name") {
            val dir = tempDir.resolve("detect-$sample").createDirectories()
            val f = dir.resolve(name)
            f.writeBytes(bytes)
            detector.detectMediaType(f)
          }
        }
      }
      case("missing file") { exceptionType { detector.detectMediaType(tempDir.resolve("missing.cbz")) } }
      case("directory") { exceptionType { detector.detectMediaType(tempDir.resolve("dir").createDirectories()) } }
    }
    func("detectMediaType@31") {
      for (r in resources) case("resource $r") { Samples.komgaRes(r).toFile().inputStream().buffered().use { detector.detectMediaType(it) } }
      for ((sample, bytes) in Samples.contents(::oracleBytes)) {
        case(sample) { detector.detectMediaType(bytes.inputStream()) }
        case("$sample: stream position after detection") {
          val s = bytes.inputStream()
          detector.detectMediaType(s)
          s.readBytes().size
        }
      }
    }
    func("isImage") {
      for (m in listOf("image/png", "image/", "image", "IMAGE/PNG", "application/zip", "", " image/png", "image/webp", "video/mp4", "text/image/png")) {
        case("'$m'") { detector.isImage(m) }
      }
    }
    func("mediaTypeToExtension") {
      for (m in listOf(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff", "image/jxl", "image/avif", "image/heif", "image/heic",
        "application/zip", "application/x-rar-compressed", "application/x-rar-compressed; version=4", "application/x-rar-compressed; version=5",
        "application/vnd.rar", "application/x-7z-compressed", "application/epub+zip", "application/pdf", "application/vnd.comicbook+zip",
        "application/vnd.comicbook-rar", "text/plain", "text/html", "application/xhtml+xml", "application/xml", "text/css", "application/octet-stream",
        "application/x-dtbncx+xml", "application/oebps-package+xml", "IMAGE/PNG", "Image/Jpeg", "image/png; charset=utf-8", "image/svg+xml",
        "font/ttf", "application/javascript", "unknown/type", "image/x-unknown", "", "foo", "/", "image/", " image/png",
      )) {
        case("'$m'") { detector.mediaTypeToExtension(m) }
      }
    }
  }
}
