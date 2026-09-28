package org.gotson.komga.oracle.language

import org.gotson.komga.language.contains
import org.gotson.komga.language.lowerNotBlank
import org.gotson.komga.language.mostFrequent
import org.gotson.komga.language.notEquals
import org.gotson.komga.language.stripAccents
import org.gotson.komga.language.toCurrentTimeZone
import org.gotson.komga.language.toDate
import org.gotson.komga.language.toEnumeration
import org.gotson.komga.language.toIndexedMap
import org.gotson.komga.language.toUTC
import org.gotson.komga.language.toUTCZoned
import org.gotson.komga.language.toZonedDateTime
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class LanguageUtilsOracleTest : OracleTest() {
  private val dates =
    linkedMapOf(
      "winter" to LocalDateTime.of(2021, 1, 15, 10, 20, 30),
      "summer" to LocalDateTime.of(2021, 7, 15, 10, 20, 30, 123456789),
      "dst gap" to LocalDateTime.of(2021, 3, 28, 2, 30),
      "dst overlap" to LocalDateTime.of(2021, 10, 31, 2, 30),
      "midnight" to LocalDateTime.of(2000, 1, 1, 0, 0),
      "epoch" to LocalDateTime.of(1970, 1, 1, 0, 0, 0, 1000000),
      "old" to LocalDateTime.of(1900, 6, 1, 12, 0),
      "far" to LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999999),
    )

  override fun cases() {
    func("toIndexedMap") {
      case("empty") { emptyList<String>().toIndexedMap() }
      case("strings") { listOf("a", "b", "c").toIndexedMap() }
      case("duplicates and nulls") { listOf("x", null, "x", null).toIndexedMap() }
      case("single") { listOf(42).toIndexedMap() }
    }

    func("toEnumeration") {
      case("empty") { emptyList<String>().toEnumeration().hasMoreElements() }
      case("drain") {
        val e = listOf("a", "b", "c").toEnumeration()
        buildList { while (e.hasMoreElements()) add(e.nextElement()) }
      }
    }

    func("hasMoreElements") {
      case("sequence") {
        val e = listOf(1, 2).toEnumeration()
        listOf(e.hasMoreElements(), e.nextElement(), e.hasMoreElements(), e.nextElement(), e.hasMoreElements(), e.hasMoreElements())
      }
    }

    func("nextElement") {
      case("empty list") { emptyList<Int>().toEnumeration().nextElement() }
      case("after the end") {
        val e = listOf("a").toEnumeration()
        e.nextElement()
        e.nextElement()
      }
      case("null element") { listOf<String?>(null).toEnumeration().nextElement() }
    }

    func("mostFrequent") {
      case("empty") { emptyList<String>().mostFrequent { it } }
      case("identity") { listOf("a", "b", "b", "c").mostFrequent { it } }
      case("tie keeps first") { listOf("x", "y", "y", "x", "z").mostFrequent { it } }
      case("all transformed to null") { listOf("a", "b").mostFrequent<String, String> { null } }
      case("some null") { listOf("a", "", "", "b", "").mostFrequent { it.ifEmpty { null } } }
      case("transform") { listOf("aa", "b", "cc", "ddd", "e").mostFrequent { it.length } }
      case("set source") { setOf(3, 1, 4, 5, 9, 2, 6).mostFrequent { it % 2 == 0 } }
    }

    func("lowerNotBlank") {
      case("empty") { emptyList<String>().lowerNotBlank() }
      case("mixed") { listOf("  ABC ", "", "   ", "Déjà Vu", "\tTab\n").lowerNotBlank() }
      case("unicode spaces") { listOf(" ", " x ", "\u001C", "﻿", "​", "　Ab　", "\u0085").lowerNotBlank() }
      case("special lowercase") { listOf("İSTANBUL", "ΣΑΣ", "ΌΣΟΣ Σ", "ǅ", "ẞ", "Ⅻ", "ＡＢＣ").lowerNotBlank() }
      case("keeps order and duplicates") { listOf("b", "A", "b", "a").lowerNotBlank() }
    }

    func("notEquals") {
      val a = LocalDateTime.of(2021, 1, 1, 10, 0, 0, 123_456_789)
      case("same") { a.notEquals(a) }
      case("below millis") { a.notEquals(a.withNano(123_999_999)) }
      case("different millis") { a.notEquals(a.withNano(124_000_000)) }
      case("seconds precision, same") { a.notEquals(a.withNano(999_000_000), ChronoUnit.SECONDS) }
      case("seconds precision, different") { a.notEquals(a.plusSeconds(1), ChronoUnit.SECONDS) }
      case("days precision") { a.notEquals(a.withHour(23), ChronoUnit.DAYS) }
      case("nanos precision") { a.notEquals(a.plusNanos(1), ChronoUnit.NANOS) }
      case("minutes precision") { a.notEquals(a.plusSeconds(59), ChronoUnit.MINUTES) }
      case("hours precision") { a.notEquals(a.plusMinutes(60), ChronoUnit.HOURS) }
      case("months precision (unsupported)") { a.notEquals(a, ChronoUnit.MONTHS) }
    }

    func("stripAccents") {
      val inputs =
        linkedMapOf(
          "empty" to "",
          "ascii" to "Hello, World!",
          "latin" to "éàüçÉÀÜÇ ñÑ ÿ",
          "angstrom" to "Ångström",
          "remaining" to "Đakovo đ Łódź ł Ŧŧ Ɨɨ Ʉʉ ᵻᵾ",
          "not decomposable" to "øØæÆœŒßı",
          "ligatures and compat" to "ﬁﬂ ① Ⅻ x² ½ ＡＢ ㎏",
          "hangul" to "한국어",
          "japanese" to "がぎぐ パピプ",
          "vietnamese" to "Tiếng Việt",
          "greek" to "Άλφα ΐ ϊ",
          "cyrillic" to "Йй Ёё",
          "combining outside block" to "a᪰ e⃝ o᷀ u︠",
          "lone combining" to "́abc̈",
          "emoji" to "😀 👍🏽 é",
          "arabic" to "مَرْحَبًا",
          "hebrew" to "שָׁלוֹם",
          "devanagari" to "क़",
          "titlecase" to "ǅ ǈ",
        )
      for ((name, s) in inputs) case(name) { s.stripAccents() }
    }

    func("toDate") {
      case("2020-01-01") { LocalDate.of(2020, 1, 1).toDate() }
      case("epoch") { LocalDate.of(1970, 1, 1).toDate() }
      case("before epoch") { LocalDate.of(1900, 2, 28).toDate() }
      case("leap day") { LocalDate.of(2024, 2, 29).toDate() }
      case("dst day") { LocalDate.of(2021, 3, 28).toDate() }
      case("year 1") { LocalDate.of(1, 1, 1).toDate() }
      case("year 9999") { LocalDate.of(9999, 12, 31).toDate() }
    }

    func("toUTC") { for ((name, d) in dates) case(name) { d.toUTC() } }
    func("toUTCZoned") { for ((name, d) in dates) case(name) { d.toUTCZoned() } }
    func("toZonedDateTime") { for ((name, d) in dates) case(name) { d.toZonedDateTime() } }
    func("toCurrentTimeZone") { for ((name, d) in dates) case(name) { d.toCurrentTimeZone() } }

    func("contains") {
      val l = listOf("Alpha", "beta", "ÉTÉ", "straße", "İ", "σ", "ǅ", "")
      val queries = listOf("Alpha", "alpha", "BETA", "été", "STRASSE", "i", "ς", "Σ", "ǆ", "Ǆ", "", " ", "gamma")
      for (q in queries) {
        case("'$q' case sensitive") { l.contains(q) }
        case("'$q' ignore case") { l.contains(q, ignoreCase = true) }
      }
      case("empty list") { emptyList<String>().contains("", true) }
      case("set") { setOf("a", "B").contains("b", true) }
    }
  }
}
