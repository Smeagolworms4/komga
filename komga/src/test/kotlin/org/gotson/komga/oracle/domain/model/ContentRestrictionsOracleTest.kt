package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.oracle.OracleTest

class ContentRestrictionsOracleTest : OracleTest() {
  override fun cases() {
    func("<init>") {
      case("defaults") { ContentRestrictions() }
      case("age") { ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY)) }
      case("labels normalized") { ContentRestrictions(labelsAllow = setOf(" Kids ", "KIDS", "", "  ", "Teen"), labelsExclude = setOf("GORE", " gore", "\t")) }
      case("exclude wins") { ContentRestrictions(labelsAllow = setOf("a", "B", "c"), labelsExclude = setOf("b", "C ")) }
      case("all excluded") { ContentRestrictions(labelsAllow = setOf("x"), labelsExclude = setOf("X")) }
      case("unicode") { ContentRestrictions(labelsAllow = setOf(" Émile ", "İ"), labelsExclude = setOf("ΣΑΣ")) }
    }
    func("isRestricted") {
      case("defaults") { ContentRestrictions().isRestricted }
      case("age") { ContentRestrictions(AgeRestriction(0, AllowExclude.EXCLUDE)).isRestricted }
      case("allow") { ContentRestrictions(labelsAllow = setOf("a")).isRestricted }
      case("blank allow") { ContentRestrictions(labelsAllow = setOf(" ")).isRestricted }
      case("exclude") { ContentRestrictions(labelsExclude = setOf("a")).isRestricted }
      case("allow fully excluded") { ContentRestrictions(labelsAllow = setOf("a"), labelsExclude = setOf("a")).isRestricted }
    }
    func("toString") {
      case("defaults") { ContentRestrictions().toString() }
      case("full") { ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), setOf("B", "a"), setOf("z", "y")).toString() }
    }
    func("equals") {
      case("defaults") { ContentRestrictions() == ContentRestrictions() }
      case("normalized") { ContentRestrictions(labelsAllow = setOf("A ")) == ContentRestrictions(labelsAllow = setOf("a")) }
      case("set order ignored") { ContentRestrictions(labelsAllow = setOf("a", "b")) == ContentRestrictions(labelsAllow = setOf("b", "a")) }
      case("age differs") { ContentRestrictions(AgeRestriction(1, AllowExclude.EXCLUDE)) == ContentRestrictions(AgeRestriction(2, AllowExclude.EXCLUDE)) }
      case("restriction differs") { ContentRestrictions(AgeRestriction(1, AllowExclude.EXCLUDE)) == ContentRestrictions(AgeRestriction(1, AllowExclude.ALLOW_ONLY)) }
      case("same age") { ContentRestrictions(AgeRestriction(1, AllowExclude.EXCLUDE)) == ContentRestrictions(AgeRestriction(1, AllowExclude.EXCLUDE)) }
      case("exclude differs") { ContentRestrictions(labelsExclude = setOf("a")) == ContentRestrictions() }
      case("other type") { ContentRestrictions().equals("x") }
    }
    func("hashCode") {
      case("equal restrictions have equal hash") {
        ContentRestrictions(AgeRestriction(3, AllowExclude.EXCLUDE), setOf("b", "A"), setOf("c")).hashCode() ==
          ContentRestrictions(AgeRestriction(3, AllowExclude.EXCLUDE), setOf("a", "b "), setOf("C")).hashCode()
      }
    }
  }
}
