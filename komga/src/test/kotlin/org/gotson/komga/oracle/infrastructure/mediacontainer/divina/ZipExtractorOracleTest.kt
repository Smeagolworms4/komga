package org.gotson.komga.oracle.infrastructure.mediacontainer.divina

import org.apache.tika.config.TikaConfig
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.mediacontainer.divina.ZipExtractor
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.digest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class ZipExtractorOracleTest : OracleTest() {
  private val extractor = ZipExtractor(ContentDetector(TikaConfig()), ImageAnalyzer())

  private val komgaArchives =
    listOf("zip.zip", "zip-copy.zip", "zip-bzip2.zip", "zip-deflate64.zip", "zip-lzma.zip", "zip-ppmd.zip", "zip-encrypted.zip", "epub3.epub", "zip-as-epub.epub")

  private val portZips =
    listOf(
      "badutf8.zip", "comment.zip", "cp437.zip", "datadesc.zip", "deflate-corrupt.zip", "deflate-truncated.zip", "dupnames.zip", "empty-file.zip",
      "empty.zip", "enc-aes.zip", "encflag-stored.zip", "enc-zipcrypto.zip", "fat-backslash.zip", "infozip.zip", "lzma-method.zip",
      "multidisk.zip", "notzip.zip", "overlap.zip", "prefix-rel.zip", "prefix.zip", "size-mismatch.zip", "truncated-end.zip", "truncated-mid.zip",
      "unix-backslash.zip", "unknown-method.zip", "upath-badcrc.zip", "upath-badver-local.zip", "upath-badver.zip", "upath-central.zip",
      "upath-local.zip", "upath-utf8flag.zip", "utf8names.zip", "xz-method.zip", "zip64-forced.zip", "zstd-method.zip",
    )

  private fun synthetic(): List<Pair<String, List<Pair<String, ByteArray?>>>> {
    val png = Samples.komgaRes("barcode/komga.png").readBytes()
    val gif = Samples.komgaRes("hashpage/tr.gif/1.gif").readBytes()
    return listOf(
      "no entry" to emptyList(),
      "natural sort" to listOf("10.png", "2.png", "1.png", "a10.png", "a2.png", "A1.png", "b.png", "B.png", "_1.png", "01.png").map { it to png },
      "directories" to listOf("dir/" to null, "dir/sub/" to null, "dir/sub/p 10.png" to png, "dir/sub/p 2.png" to png, "root.gif" to gif),
      "only directories" to listOf("a/" to null, "b/" to null),
      "mixed content" to listOf(t("notes.txt", "hello"), "empty.dat" to ByteArray(0), t("ComicInfo.xml", "<ComicInfo/>"), "img.jpg" to png, "x.bin" to oracleBytes(100)),
      "unicode names" to listOf("été/01.png" to png, "日本語.png" to png, "😀.gif" to gif, "Ä.png" to png, "ä.png" to png),
      "duplicate names" to listOf("a.png" to png, t("a.png", "text")),
      "backslash names" to listOf("dir\\a.png" to png, "dir\\b.png" to png),
      "corrupted image" to listOf("bad.png" to png.copyOf(40), "trunc.gif" to gif.copyOf(10)),
    )
  }

  private fun getEntries(
    path: Path,
    analyze: Boolean,
  ) = pathless(path) { extractor.getEntries(path, analyze) }

  private fun getEntryStreams(path: Path): Any? =
    pathless(path) {
      val names = extractor.getEntries(path, false).map { it.name } + listOf("missing.png", "")
      names.map { name -> listOf(name, pathless(path) { digest(extractor.getEntryStream(path, name)) }) }
    }

  override fun cases() {
    func("mediaTypes") { case("zip") { extractor.mediaTypes() } }
    func("getEntries") {
      for (a in komgaArchives) {
        case("$a with dimensions") { getEntries(Samples.komgaRes("archives/$a"), true) }
        case("$a without dimensions") { getEntries(Samples.komgaRes("archives/$a"), false) }
      }
      for (z in portZips) case("fixture $z") { getEntries(Samples.fixture("zip/$z"), false) }
      for ((name, entries) in synthetic()) {
        for (analyze in listOf(true, false)) {
          case("synthetic $name (analyze $analyze)") { getEntries(OracleZip.write(tempDir.resolve("s-$name-$analyze.cbz"), entries), analyze) }
        }
      }
      case("not a zip") { getEntries(tempDir.resolve("text.cbz").also { it.writeBytes("hello".toByteArray()) }, false) }
      case("empty file") { getEntries(tempDir.resolve("empty.cbz").also { it.writeBytes(ByteArray(0)) }, false) }
      case("missing file") { exceptionType { extractor.getEntries(tempDir.resolve("missing.cbz"), false) } }
    }
    func("getEntryStream") {
      for (a in komgaArchives) case(a) { getEntryStreams(Samples.komgaRes("archives/$a")) }
      for (z in portZips) case("fixture $z") { getEntryStreams(Samples.fixture("zip/$z")) }
      for ((name, entries) in synthetic()) {
        case("synthetic $name") { getEntryStreams(OracleZip.write(tempDir.resolve("e-$name.cbz"), entries)) }
      }
      case("directory entry") {
        val p = OracleZip.write(tempDir.resolve("dir-entry.cbz"), listOf("dir/" to null, t("dir/a.txt", "a")))
        listOf("dir/", "dir", "dir/a.txt", "DIR/A.TXT", "/dir/a.txt").map { listOf(it, pathless(p) { digest(extractor.getEntryStream(p, it)) }) }
      }
      case("missing file") { exceptionType { extractor.getEntryStream(tempDir.resolve("missing.cbz"), "a") } }
    }
  }
}
