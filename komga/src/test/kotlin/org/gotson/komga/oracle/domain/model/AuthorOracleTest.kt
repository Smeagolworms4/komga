package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.Author
import org.gotson.komga.oracle.OracleTest

class AuthorOracleTest : OracleTest() {
  private val authors =
    linkedMapOf(
      "plain" to ("John Doe" to "writer"),
      "trimmed" to ("  John Doe \t" to "  WRITER \n"),
      "unicode spaces" to (" Jöhn " to "　Penciller\u001C"),
      "empty" to ("" to ""),
      "blank" to ("   " to "   "),
      "special lowercase" to ("İsmail" to "İLLUSTRATOR ΣΑΣ"),
      "bom not trimmed" to ("﻿Name" to "﻿Role"),
    )

  override fun cases() {
    func("<init>") { for ((n, a) in authors) case(n) { Author(a.first, a.second) } }
    func("toString") { for ((n, a) in authors) case(n) { Author(a.first, a.second).toString() } }
    func("equals") {
      case("same values") { Author("John", "writer") == Author("John", "writer") }
      case("normalized values") { Author(" John ", "WRITER") == Author("John", "writer") }
      case("different name") { Author("John", "writer") == Author("Jane", "writer") }
      case("different role") { Author("John", "writer") == Author("John", "editor") }
      case("name case") { Author("john", "writer") == Author("John", "writer") }
      case("same instance") { Author("a", "b").let { it == it } }
      case("other type") { Author("a", "b").equals("Author(a, b)") }
      case("null") { Author("a", "b").equals(null) }
      case("in set") { setOf(Author("a", "B"), Author(" a", "b "), Author("b", "a")) }
      case("list contains") { listOf(Author("a", "b")).contains(Author("a ", "B")) }
    }
    func("hashCode") {
      case("equal authors have equal hash") { Author(" John ", "WRITER").hashCode() == Author("John", "writer").hashCode() }
    }
  }
}
