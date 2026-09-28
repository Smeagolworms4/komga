package org.gotson.komga.oracle.infrastructure.mediacontainer.epub

import org.gotson.komga.infrastructure.mediacontainer.epub.getManifest
import org.gotson.komga.infrastructure.mediacontainer.epub.normalizeHref
import org.gotson.komga.infrastructure.mediacontainer.epub.processOpfGuide
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.io.path.Path
import kotlin.io.path.readText

class OpfOracleTest : OracleTest() {
  private val opfs =
    listOf(
      "empty" to "",
      "no manifest" to "<package><metadata/></package>",
      "empty manifest" to "<package><manifest/></package>",
      "items" to
        """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="img/b%20c.png" media-type="image/png" properties="cover-image"/><item id="n" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav  scripted svg"/></manifest></package>""",
      "missing attributes" to "<package><manifest><item/><item id=\"x\"/><item href=\"y\"/></manifest></package>",
      "duplicate ids" to "<package><manifest><item id=\"a\" href=\"1\"/><item id=\"a\" href=\"2\"/></manifest></package>",
      "prefixed" to "<opf:package xmlns:opf=\"http://www.idpf.org/2007/opf\"><opf:manifest><opf:item id=\"a\" href=\"a.html\" media-type=\"text/html\"/></opf:manifest></opf:package>",
      "nested item not direct child" to "<package><manifest><group><item id=\"a\" href=\"a\"/></group><item id=\"b\" href=\"b\"/></manifest></package>",
      "two manifests" to "<package><manifest><item id=\"a\" href=\"a\"/></manifest><manifest><item id=\"b\" href=\"b\"/></manifest></package>",
      "uppercase tags" to "<package><MANIFEST><ITEM id=\"a\" href=\"a\"/></MANIFEST></package>",
      "guide" to
        """<package><guide><reference type="cover" title="Cover" href="cover.xhtml"/><reference type="toc" title="Table" href="text/toc.xhtml#t%C3%A9"/><reference type="x" title="" href=""/><reference type="y" href="  "/><reference title="Up" href="../up.html"/><reference title="Plus" href="a+b.html"/></guide></package>""",
      "empty guide" to "<package><guide/></package>",
      "prefixed guide" to "<opf:package xmlns:opf=\"x\"><opf:guide><opf:reference title=\"T\" href=\"t.html#a\"/></opf:guide></opf:package>",
      "fixture 1979" to Samples.komgaRes("epub/1979.opf").readText(),
      "fixture Panik" to Samples.komgaRes("epub/Panik im Paradies.opf").readText(),
      "fixture Panik namespace" to Samples.komgaRes("epub/Panik im Paradies - namespace.opf").readText(),
      "fixture clash" to Samples.komgaRes("epub/clash.opf").readText(),
      "fixture Die Drei" to Samples.komgaRes("epub/Die Drei 3.opf").readText(),
    )

  private val dirs = listOf(null, "", "OEBPS", "a/b", "/abs", "a/../b", ".", "..", "dir with space")

  private val hrefs =
    listOf(
      "x.html", "../x.html", "x.html#frag", "#only", "a/./b/../c.html", "", "x.html#", "a#b#c", "dir/", "  ", "x.html# ", "/root.html",
      "../../up.html", "%20.html", "a//b.html", "./x.html", "x.html#frag with space", "é/ü.html", "..", ".",
    )

  override fun cases() {
    func("getManifest") {
      for ((label, opf) in opfs) case(label) { Jsoup.parse(opf, "", Parser.xmlParser()).getManifest() }
    }
    func("normalizeHref") {
      for (dir in dirs) {
        for (href in hrefs) case("'$dir' + '$href'") { normalizeHref(dir?.let { Path(it) }, href) }
      }
    }
    func("processOpfGuide") {
      for ((label, opf) in opfs) {
        for (dir in listOf(null, "OEBPS", "a/b")) case("$label in '$dir'") { processOpfGuide(Jsoup.parse(opf, "", Parser.xmlParser()), dir?.let { Path(it) }) }
      }
    }
  }
}
