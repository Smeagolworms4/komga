package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.restoreHashFrom
import org.gotson.komga.oracle.OracleTest

class BookPageOracleTest : OracleTest() {
  private val full = BookPage("001.jpg", "image/jpeg", Dimension(800, 1200), "abc", 12345L)
  private val minimal = BookPage("002.png", "image/png")

  override fun cases() {
    func("<init>") {
      case("defaults") { minimal }
      case("full") { full }
    }
    func("toString") {
      case("defaults") { minimal.toString() }
      case("full") { full.toString() }
      case("quotes") { BookPage("it's.jpg", "image/'x'", fileHash = "h'").toString() }
    }
    func("copy") {
      case("no change") { full.copy() }
      case("fileHash") { full.copy(fileHash = "new") }
      case("all") { full.copy("a", "b", null, "", null) }
      case("dimension") { minimal.copy(dimension = Dimension(1, 2), fileSize = 0) }
      case("class") { full.copy()::class.simpleName }
    }
    func("restoreHashFrom") {
      val a = BookPage("a.jpg", "image/jpeg", fileSize = 10)
      val b = BookPage("b.jpg", "image/jpeg", fileSize = 20)
      val c = BookPage("c.jpg", "image/png", fileSize = null)
      case("empty") { emptyList<BookPage>().restoreHashFrom(listOf(a.copy(fileHash = "x"))) }
      case("empty source") { listOf(a, b).restoreHashFrom(emptyList()) }
      case("matching") { listOf(a, b, c).restoreHashFrom(listOf(b.copy(fileHash = "hb"), a.copy(fileHash = "ha"), c.copy(fileHash = "hc"))) }
      case("blank hash ignored") { listOf(a).restoreHashFrom(listOf(a.copy(fileHash = "  "), a.copy(fileHash = ""))) }
      case("first non blank wins") { listOf(a).restoreHashFrom(listOf(a.copy(fileHash = " "), a.copy(fileHash = "h1"), a.copy(fileHash = "h2"))) }
      case("different size") { listOf(a).restoreHashFrom(listOf(a.copy(fileSize = 11, fileHash = "x"))) }
      case("null vs non null size") { listOf(a).restoreHashFrom(listOf(a.copy(fileSize = null, fileHash = "x"))) }
      case("different media type") { listOf(a).restoreHashFrom(listOf(a.copy(mediaType = "image/png", fileHash = "x"))) }
      case("different name") { listOf(a).restoreHashFrom(listOf(a.copy(fileName = "A.jpg", fileHash = "x"))) }
      case("dimension ignored") { listOf(a.copy(dimension = Dimension(1, 1))).restoreHashFrom(listOf(a.copy(fileHash = "x"))) }
      case("existing hash replaced") { listOf(a.copy(fileHash = "old")).restoreHashFrom(listOf(a.copy(fileHash = "new"))) }
      case("existing hash kept") { listOf(a.copy(fileHash = "old")).restoreHashFrom(listOf(b.copy(fileHash = "new"))) }
      case("unicode blank") { listOf(a).restoreHashFrom(listOf(a.copy(fileHash = "  "))) }
      case("same instance") { listOf(a).restoreHashFrom(listOf(b)).first() === a }
    }
  }
}
