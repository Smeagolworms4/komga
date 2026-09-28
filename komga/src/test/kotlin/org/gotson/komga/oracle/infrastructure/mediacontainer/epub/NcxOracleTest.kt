package org.gotson.komga.oracle.infrastructure.mediacontainer.epub

import org.gotson.komga.infrastructure.mediacontainer.epub.Epub2Nav
import org.gotson.komga.infrastructure.mediacontainer.epub.ResourceContent
import org.gotson.komga.infrastructure.mediacontainer.epub.epub
import org.gotson.komga.infrastructure.mediacontainer.epub.getNcxResource
import org.gotson.komga.infrastructure.mediacontainer.epub.processNcx
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class NcxOracleTest : OracleTest() {
  private val ncxs =
    listOf(
      "fixture toc.ncx" to Samples.komgaRes("epub/toc.ncx").readText(),
      "empty" to "",
      "empty navMap" to "<ncx><navMap/><pageList/></ncx>",
      "nested" to
        """<ncx><navMap><navPoint><navLabel><text>A</text></navLabel><content src="a.html"/><navPoint><navLabel><text>A1</text></navLabel><content src="a.html#x%20y"/><navPoint><navLabel><text>A11</text></navLabel><content src="../a11.html"/></navPoint></navPoint></navPoint><navPoint><content src="notitle.html"/><navPoint><navLabel><text>Child of untitled</text></navLabel><content src="c.html"/></navPoint></navPoint><navPoint><navLabel><text>No content</text></navLabel></navPoint><navPoint><navLabel><text>  Spaces   inside  </text></navLabel><content src=""/></navPoint></navMap></ncx>""",
      "page list" to
        """<ncx><navMap><navPoint><navLabel><text>T</text></navLabel><content src="t.html"/></navPoint></navMap><pageList><pageTarget><navLabel><text>1</text></navLabel><content src="p.html#1"/></pageTarget><pageTarget><navLabel><text>2</text></navLabel><content src="p.html#2"/><pageTarget><navLabel><text>nested</text></navLabel><content src="n.html"/></pageTarget></pageTarget></pageList></ncx>""",
      "navPoint not direct child" to "<ncx><navMap><div><navPoint><navLabel><text>X</text></navLabel><content src=\"x\"/></navPoint></div></navMap></ncx>",
      "label text nested" to "<ncx><navMap><navPoint><navLabel><div><text>Deep</text></div></navLabel><content src=\"d\"/></navPoint><navPoint><navLabel><text><b>Bold</b> text</text></navLabel><content src=\"b\"/></navPoint></navMap></ncx>",
      "encoded src" to "<ncx><navMap><navPoint><navLabel><text>E</text></navLabel><content src=\"%C3%A9.html\"/></navPoint><navPoint><navLabel><text>P</text></navLabel><content src=\"a+b.html\"/></navPoint></navMap></ncx>",
      "two navMaps" to "<ncx><navMap><navPoint><navLabel><text>1</text></navLabel></navPoint></navMap><navMap><navPoint><navLabel><text>2</text></navLabel></navPoint></navMap></ncx>",
    )

  private val ncxPaths = listOf("toc.ncx", "OEBPS/toc.ncx", "a/b/toc.ncx")

  override fun cases() {
    func("getNcxResource") {
      for ((label, p) in Samples.epubFiles(tempDir.resolve("synthetic").createDirectories())) case(label) { pathless(p) { p.epub { it.getNcxResource() } } }
    }
    func("processNcx") {
      for ((label, ncx) in ncxs) {
        for (type in Epub2Nav.entries) {
          for (path in ncxPaths) case("$label, $type, $path") { processNcx(ResourceContent(Path(path), ncx), type) }
        }
      }
      case("bad encoding in src") { exceptionType { processNcx(ResourceContent(Path("t.ncx"), "<ncx><navMap><navPoint><navLabel><text>B</text></navLabel><content src=\"bad%zz\"/></navPoint></navMap></ncx>"), Epub2Nav.TOC) } }
    }
    // private: exercised through processNcx
    func("ncxElementToTocEntry") {
      for ((label, ncx) in ncxs) case(label) { processNcx(ResourceContent(Path("x/toc.ncx"), ncx), Epub2Nav.TOC) }
    }
  }
}
