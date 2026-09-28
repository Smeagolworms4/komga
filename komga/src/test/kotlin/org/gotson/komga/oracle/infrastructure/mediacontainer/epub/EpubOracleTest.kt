package org.gotson.komga.oracle.infrastructure.mediacontainer.epub

import org.apache.commons.compress.archivers.zip.ZipFile
import org.gotson.komga.infrastructure.mediacontainer.epub.epub
import org.gotson.komga.infrastructure.mediacontainer.epub.getPackageFileContent
import org.gotson.komga.infrastructure.mediacontainer.epub.getPackagePath
import org.gotson.komga.infrastructure.util.use
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class EpubOracleTest : OracleTest() {
  override fun cases() {
    val files = Samples.epubFiles(tempDir.resolve("synthetic").createDirectories())
    func("epub") {
      for ((label, p) in files) {
        case(label) {
          pathless(p) {
            p.epub { (_, opfDoc, opfDir, manifest) ->
              listOf(opfDir, manifest, opfDoc.select("*|spine > *|itemref").map { it.attr("idref") })
            }
          }
        }
      }
      case("not a zip") { tempDir.resolve("text.epub").also { it.writeText("hello") }.let { p -> pathless(p) { p.epub { it.opfDir } } } }
      case("missing file") { exceptionType { tempDir.resolve("missing.epub").epub { it.opfDir } } }
    }
    func("getPackagePath") {
      for ((label, p) in files) case(label) { pathless(p) { ZipFile.builder().setPath(p).use { it.getPackagePath() } } }
    }
    func("getPackageFileContent") {
      for ((label, p) in files) case(label) { pathless(p) { getPackageFileContent(p) } }
      case("not a zip") { tempDir.resolve("text2.epub").also { it.writeText("hello") }.let { p -> pathless(p) { getPackageFileContent(p) } } }
    }
  }
}
