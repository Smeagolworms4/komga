package org.gotson.komga.oracle.infrastructure.mediacontainer.epub

import org.gotson.komga.infrastructure.mediacontainer.epub.Epub3Nav
import org.gotson.komga.infrastructure.mediacontainer.epub.ResourceContent
import org.gotson.komga.infrastructure.mediacontainer.epub.epub
import org.gotson.komga.infrastructure.mediacontainer.epub.getNavResource
import org.gotson.komga.infrastructure.mediacontainer.epub.processNav
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class NavOracleTest : OracleTest() {
  private val navs =
    listOf(
      "fixture nav.xhtml" to Samples.komgaRes("epub/nav.xhtml").readText(),
      "empty" to "",
      "no nav" to "<html><body><ol><li><a href=\"a\">A</a></li></ol></body></html>",
      "toc" to
        """<html xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="a.html">A</a><ol><li><a href="a.html#1">A1</a></li><li><span>S</span><ol><li><a href="b%20c.html">BC</a></li></ol></li></ol></li><li><a href="../up.html">Up</a></li><li><ol><li><a href="orphan.html">Orphan</a></li></ol></li><li><a>No href</a></li><li><span>Span only</span></li><li><a href="">Empty href</a></li></ol></nav></body></html>""",
      "type without namespace" to """<html><body><nav type="toc"><ol><li><a href="x">X</a></li></ol></nav></body></html>""",
      "other prefix" to """<html xmlns:e="http://www.idpf.org/2007/ops"><body><nav e:type="landmarks"><ol><li><a href="l">L</a></li></ol></nav><nav e:type="page-list"><ol><li><a href="p#1">1</a></li></ol></nav></body></html>""",
      "several nav" to
        """<html xmlns:epub="x"><body><nav epub:type="toc"><ol><li><a href="first">First</a></li></ol></nav><nav epub:type="toc"><ol><li><a href="second">Second</a></li></ol></nav></body></html>""",
      "nested markup in title" to """<html xmlns:epub="x"><body><nav epub:type="toc"><ol><li><a href="t"><b>Bold</b>  and   <i>italic</i></a></li><li><a href="t2">Line
break</a></li></ol></nav></body></html>""",
      "ol not direct child" to """<html xmlns:epub="x"><body><nav epub:type="toc"><div><ol><li><a href="x">X</a></li></ol></div></nav></body></html>""",
      "encoded href" to """<html xmlns:epub="x"><body><nav epub:type="toc"><ol><li><a href="%C3%A9t%C3%A9.html#a%20b">Été</a></li><li><a href="a+b.html">Plus</a></li><li><a href="bad%zz.html">Bad</a></li></ol></nav></body></html>""",
    )

  private val navPaths = listOf("nav.xhtml", "OEBPS/nav.xhtml", "a/b/nav.xhtml", "../nav.xhtml")

  override fun cases() {
    func("getNavResource") {
      for ((label, p) in Samples.epubFiles(tempDir.resolve("synthetic").createDirectories())) case(label) { pathless(p) { p.epub { it.getNavResource() } } }
    }
    func("processNav") {
      for ((label, nav) in navs) {
        for (type in Epub3Nav.entries) {
          for (path in navPaths) case("$label, $type, $path") { processNav(ResourceContent(Path(path), nav), type) }
        }
      }
    }
    // private: exercised through processNav
    func("navLiElementToTocEntry") {
      for ((label, nav) in navs) case(label) { processNav(ResourceContent(Path("x/nav.xhtml"), nav), Epub3Nav.TOC) }
    }
  }
}
