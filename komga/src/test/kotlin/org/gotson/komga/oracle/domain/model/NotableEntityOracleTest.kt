package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.NotableEntity
import org.gotson.komga.oracle.OracleTest

class NotableEntityOracleTest : OracleTest() {
  private val entities =
    linkedMapOf(
      "plain" to ("team" to "X-Men"),
      "trimmed" to ("  TEAM \t" to "  X-Men \n"),
      "unicode spaces" to ("　Location\u001C" to " Gōtham "),
      "empty" to ("" to ""),
      "blank" to ("   " to "   "),
      "special lowercase" to ("İLLUSTRATOR ΣΑΣ" to "İsmail"),
      "bom not trimmed" to ("﻿Kind" to "﻿Name"),
    )

  override fun cases() {
    func("<init>") { for ((n, e) in entities) case(n) { NotableEntity(e.first, e.second) } }
    func("toString") { for ((n, e) in entities) case(n) { NotableEntity(e.first, e.second).toString() } }
    func("equals") {
      case("same values") { NotableEntity("team", "X-Men") == NotableEntity("team", "X-Men") }
      case("normalized values") { NotableEntity("TEAM ", " X-Men") == NotableEntity("team", "X-Men") }
      case("different name") { NotableEntity("team", "X-Men") == NotableEntity("team", "Avengers") }
      case("different kind") { NotableEntity("team", "X-Men") == NotableEntity("location", "X-Men") }
      case("name case") { NotableEntity("team", "x-men") == NotableEntity("team", "X-Men") }
      case("same instance") { NotableEntity("a", "b").let { it == it } }
      case("other type") { NotableEntity("a", "b").equals("NotableEntity(a, b)") }
      case("null") { NotableEntity("a", "b").equals(null) }
      case("in set") { setOf(NotableEntity("A", "b"), NotableEntity(" a", "b "), NotableEntity("b", "a")) }
      case("list contains") { listOf(NotableEntity("a", "b")).contains(NotableEntity("A ", "b")) }
    }
    func("hashCode") {
      case("equal entities have equal hash") { NotableEntity(" TEAM ", "X-Men").hashCode() == NotableEntity("team", "X-Men").hashCode() }
      case("value") { NotableEntity("team", "X-Men").hashCode() }
    }
  }
}
