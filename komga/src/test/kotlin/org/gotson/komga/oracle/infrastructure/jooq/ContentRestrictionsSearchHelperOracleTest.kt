package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.infrastructure.jooq.ContentRestrictionsSearchHelper
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render

class ContentRestrictionsSearchHelperOracleTest : OracleTest() {
  private val db = OracleDb()
  private val s = Tables.SERIES
  private val sm = Tables.SERIES_METADATA

  private fun run(r: ContentRestrictions): List<Any?> {
    val (condition, joins) = ContentRestrictionsSearchHelper(r).toCondition()
    val q =
      db.dsl
        .select(s.ID)
        .from(s)
        .leftJoin(sm)
        .on(s.ID.eq(sm.SERIES_ID))
        .where(condition)
        .orderBy(s.ID)
    return render(q) + listOf(joins, q.fetch(s.ID))
  }

  override fun cases() {
    func("toCondition") {
      case("sample rows") { JooqSamples.insert(db) }
      case("none") { run(ContentRestrictions()) }
      case("allow only 12") { run(ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY))) }
      case("allow only 0") { run(ContentRestrictions(AgeRestriction(0, AllowExclude.ALLOW_ONLY))) }
      case("allow only negative") { run(ContentRestrictions(AgeRestriction(-1, AllowExclude.ALLOW_ONLY))) }
      case("exclude 16") { run(ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE))) }
      case("exclude 0") { run(ContentRestrictions(AgeRestriction(0, AllowExclude.EXCLUDE))) }
      case("labels allow") { run(ContentRestrictions(labelsAllow = setOf("kids"))) }
      case("labels allow case and blank") { run(ContentRestrictions(labelsAllow = setOf("ADULT", " ", ""))) }
      case("labels exclude") { run(ContentRestrictions(labelsExclude = setOf("adult"))) }
      case("labels exclude several") { run(ContentRestrictions(labelsExclude = setOf("Kids", "teen"))) }
      case("labels allow and exclude") { run(ContentRestrictions(labelsAllow = setOf("kids", "teen"), labelsExclude = setOf("teen", "adult"))) }
      case("labels allow all excluded") { run(ContentRestrictions(labelsAllow = setOf("teen"), labelsExclude = setOf("TEEN"))) }
      case("age allow and labels allow") { run(ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY), labelsAllow = setOf("kids"))) }
      case("age allow and labels exclude") { run(ContentRestrictions(AgeRestriction(16, AllowExclude.ALLOW_ONLY), labelsExclude = setOf("adult"))) }
      case("age exclude and labels allow") { run(ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), labelsAllow = setOf("adult"))) }
      case("age exclude and labels exclude") { run(ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), labelsExclude = setOf("kids"))) }
      case("everything") {
        run(ContentRestrictions(AgeRestriction(18, AllowExclude.ALLOW_ONLY), labelsAllow = setOf("kids", "adult"), labelsExclude = setOf("teen")))
      }
    }
  }
}
