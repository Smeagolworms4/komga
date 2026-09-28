package org.gotson.komga.oracle.infrastructure.mediacontainer.divina

import org.apache.tika.config.TikaConfig
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.mediacontainer.divina.RarExtractor
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.digest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import java.nio.file.Path
import kotlin.io.path.writeBytes

class RarExtractorOracleTest : OracleTest() {
  private val extractor = RarExtractor(ContentDetector(TikaConfig()), ImageAnalyzer())

  private val komgaArchives = listOf("rar4.rar", "rar4-solid.rar", "rar4-encrypted.rar", "rar5.rar", "rar5-solid.rar", "rar5-encrypted.rar", "zip.zip", "7zip.7z")

  private val fixtures = listOf("not-a-rar.rar", "r4-badcrc.rar", "r4-bad-file-crc.rar", "r4-bad-main-crc.rar", "r4-dirs.rar", "r4-dups.rar", "r4-empty-entry.rar", "r4-encfile.rar", "r4-future-version.rar", "r4-latin1name.rar", "r4-marker-only.rar", "r4-multivolume.rar", "r4-truncated-data.rar", "r4-truncated-header.rar", "r4-unix-slash.rar", "r4-utf8name.rar", "r5-solid-truncated.rar", "r5-truncated.rar", "r5-truncated-tail.rar", "rar4-encrypted.rar", "rar4.rar", "rar4-solid.rar", "rar5-encrypted.rar", "rar5.rar", "rar5-solid.rar")

  private fun getEntryStreams(path: Path): Any? =
    pathless(path) {
      val names = extractor.getEntries(path, false).map { it.name } + listOf("missing.png", "")
      names.map { name -> listOf(name, pathless(path) { digest(extractor.getEntryStream(path, name)) }) }
    }

  override fun cases() {
    func("mediaTypes") { case("rar") { extractor.mediaTypes() } }
    func("getEntries") {
      for (a in komgaArchives) {
        for (analyze in listOf(true, false)) {
          case("$a (analyze $analyze)") { Samples.komgaRes("archives/$a").let { p -> pathless(p) { extractor.getEntries(p, analyze) } } }
        }
      }
      for (f in fixtures) {
        for (analyze in listOf(true, false)) {
          case("fixture $f (analyze $analyze)") { Samples.fixture("rar/$f").let { p -> pathless(p) { extractor.getEntries(p, analyze) } } }
        }
      }
      case("empty file") { tempDir.resolve("empty.cbr").also { it.writeBytes(ByteArray(0)) }.let { p -> pathless(p) { extractor.getEntries(p, false) } } }
      case("text file") { tempDir.resolve("text.cbr").also { it.writeBytes("hello".toByteArray()) }.let { p -> pathless(p) { extractor.getEntries(p, false) } } }
      case("missing file") { exceptionType { extractor.getEntries(tempDir.resolve("missing.cbr"), false) } }
    }
    func("getEntryStream") {
      for (a in komgaArchives) case(a) { getEntryStreams(Samples.komgaRes("archives/$a")) }
      for (f in fixtures) case("fixture $f") { getEntryStreams(Samples.fixture("rar/$f")) }
      case("rar4 entry names") {
        val p = Samples.komgaRes("archives/rar4.rar")
        listOf("komga.png", "KOMGA.PNG", "/komga.png", "komga").map { listOf(it, pathless(p) { digest(extractor.getEntryStream(p, it)) }) }
      }
      case("missing file") { exceptionType { extractor.getEntryStream(tempDir.resolve("missing.cbr"), "a") } }
    }
  }
}
