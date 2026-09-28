package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.infrastructure.jooq.RequiredJoin
import org.gotson.komga.infrastructure.jooq.SeriesSearchHelper
import org.gotson.komga.infrastructure.jooq.csAlias
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.user
import org.jooq.Condition
import org.jooq.Record1
import org.jooq.SelectJoinStep

class SeriesSearchHelperOracleTest : OracleTest() {
  private val db = OracleDb()
  private val s = Tables.SERIES
  private val sm = Tables.SERIES_METADATA
  private val bma = Tables.BOOK_METADATA_AGGREGATION
  private val rps = Tables.READ_PROGRESS_SERIES

  /** series matching a condition, the tables joined as listed */
  private fun query(p: Pair<Condition, Set<RequiredJoin>>): org.jooq.SelectSeekStep1<Record1<String>, String> {
    var q: SelectJoinStep<Record1<String>> = db.dsl.select(s.ID).from(s)
    p.second.forEach { j ->
      q =
        when (j) {
          RequiredJoin.SeriesMetadata -> q.leftJoin(sm).on(s.ID.eq(sm.SERIES_ID))
          RequiredJoin.BookMetadataAggregation -> q.leftJoin(bma).on(s.ID.eq(bma.SERIES_ID))
          is RequiredJoin.ReadProgress -> q.leftJoin(rps).on(s.ID.eq(rps.SERIES_ID).and(rps.USER_ID.eq(j.userId)))
          is RequiredJoin.Collection -> csAlias(j.collectionId).let { cs -> q.leftJoin(cs).on(s.ID.eq(cs.SERIES_ID)) }
          else -> q
        }
    }
    return q.where(p.first).orderBy(s.ID)
  }

  private fun run(p: Pair<Condition, Set<RequiredJoin>>): List<Any?> {
    val q = query(p)
    return render(q) + listOf(p.second, q.fetch(s.ID))
  }

  private fun parse(json: String): SearchCondition.Series = db.mapper.readValue(json, SearchCondition.Series::class.java)

  private val contexts =
    listOf(
      "empty" to SearchContext.empty(),
      "anonymous" to SearchContext.ofAnonymousUser(),
      "user" to SearchContext(user("U1")),
      "user with one library" to SearchContext(user("U1", setOf("L1"))),
      "user with no library" to SearchContext(user("U1", emptySet())),
      "user with libraries and age restriction" to SearchContext(user("U1", setOf("L1", "L2"), ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY)))),
      "user with excluded label" to SearchContext(user("U2", null, ContentRestrictions(labelsExclude = setOf("adult")))),
      "user with allowed label and excluded age" to SearchContext(user("U1", null, ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), labelsAllow = setOf("kids")))),
    )

  private val conditions =
    listOf(
      """{"allOf":[]}""",
      """{"anyOf":[]}""",
      """{"allOf":[{"libraryId":{"operator":"is","value":"L1"}},{"deleted":{"operator":"isFalse"}}]}""",
      """{"anyOf":[{"libraryId":{"operator":"is","value":"L2"}},{"oneShot":{"operator":"isTrue"}}]}""",
      """{"anyOf":[{"allOf":[{"libraryId":{"operator":"is","value":"L1"}},{"ageRating":{"operator":"greaterThan","value":12}}]},{"seriesStatus":{"operator":"is","value":"HIATUS"}}]}""",
      """{"libraryId":{"operator":"isNot","value":"L1"}}""",
      """{"deleted":{"operator":"isTrue"}}""",
      """{"deleted":{"operator":"isFalse"}}""",
      """{"releaseDate":{"operator":"after","dateTime":"2020-05-01T12:00:00Z"}}""",
      """{"releaseDate":{"operator":"before","dateTime":"2020-05-02T01:30:00+02:00"}}""",
      """{"releaseDate":{"operator":"isNull"}}""",
      """{"releaseDate":{"operator":"isNotNull"}}""",
      """{"readStatus":{"operator":"is","value":"UNREAD"}}""",
      """{"readStatus":{"operator":"is","value":"READ"}}""",
      """{"readStatus":{"operator":"is","value":"IN_PROGRESS"}}""",
      """{"readStatus":{"operator":"isNot","value":"UNREAD"}}""",
      """{"readStatus":{"operator":"isNot","value":"READ"}}""",
      """{"readStatus":{"operator":"isNot","value":"IN_PROGRESS"}}""",
      """{"seriesStatus":{"operator":"is","value":"ONGOING"}}""",
      """{"seriesStatus":{"operator":"isNot","value":"ENDED"}}""",
      """{"tag":{"operator":"is","value":"action"}}""",
      """{"tag":{"operator":"isNot","value":"ACTION"}}""",
      """{"tag":{"operator":"isNull"}}""",
      """{"tag":{"operator":"isNotNull"}}""",
      """{"author":{"operator":"is","value":{"name":"alice"}}}""",
      """{"author":{"operator":"is","value":{"role":"writer"}}}""",
      """{"author":{"operator":"is","value":{"name":"ALICE","role":"WRITER"}}}""",
      """{"author":{"operator":"is","value":{}}}""",
      """{"author":{"operator":"isNot","value":{"name":"bob"}}}""",
      """{"author":{"operator":"isNot","value":{}}}""",
      """{"oneShot":{"operator":"isTrue"}}""",
      """{"oneShot":{"operator":"isFalse"}}""",
      """{"ageRating":{"operator":"is","value":12}}""",
      """{"ageRating":{"operator":"isNot","value":16}}""",
      """{"ageRating":{"operator":"lessThan","value":12}}""",
      """{"ageRating":{"operator":"isNull"}}""",
      """{"ageRating":{"operator":"isNotNull"}}""",
      """{"collectionId":{"operator":"is","value":"C1"}}""",
      """{"collectionId":{"operator":"isNot","value":"C1"}}""",
      """{"complete":{"operator":"isTrue"}}""",
      """{"complete":{"operator":"isFalse"}}""",
      """{"genre":{"operator":"is","value":"comedy"}}""",
      """{"genre":{"operator":"isNot","value":"comedy"}}""",
      """{"genre":{"operator":"isNull"}}""",
      """{"genre":{"operator":"isNotNull"}}""",
      """{"language":{"operator":"is","value":"EN"}}""",
      """{"language":{"operator":"isNot","value":"en"}}""",
      """{"publisher":{"operator":"is","value":"glenat"}}""",
      """{"publisher":{"operator":"isNot","value":"dargaud"}}""",
      """{"sharingLabel":{"operator":"is","value":"kids"}}""",
      """{"sharingLabel":{"operator":"isNot","value":"kids"}}""",
      """{"sharingLabel":{"operator":"isNull"}}""",
      """{"sharingLabel":{"operator":"isNotNull"}}""",
      """{"title":{"operator":"contains","value":"a"}}""",
      """{"title":{"operator":"is","value":"ALPHA"}}""",
      """{"titleSort":{"operator":"beginsWith","value":"e"}}""",
      """{"allOf":[{"collectionId":{"operator":"is","value":"C1"}},{"readStatus":{"operator":"is","value":"READ"}},{"title":{"operator":"contains","value":"a"}}]}""",
      """{"allOf":[{"title":{"operator":"doesNotContain","value":"z"}},{"seriesStatus":{"operator":"isNot","value":"ABANDONED"}},{"releaseDate":{"operator":"isNotNull"}}]}""",
      """{"anyOf":[{"collectionId":{"operator":"is","value":"C1"}},{"collectionId":{"operator":"is","value":"C2"}}]}""",
    )

  override fun cases() {
    func("toCondition@27") {
      case("sample rows") { JooqSamples.insert(db) }
      contexts.forEach { (name, context) ->
        case(name) { run(SeriesSearchHelper(context).toCondition()) }
      }
    }

    func("toCondition@21") {
      case("null condition") { run(SeriesSearchHelper(SearchContext(user("U1"))).toCondition(null)) }
      case("null condition, restricted user") { run(SeriesSearchHelper(contexts[5].second).toCondition(null)) }
      conditions.forEach { json ->
        case(json) {
          val condition = parse(json)
          listOf(condition) + run(SeriesSearchHelper(SearchContext(user("U1"))).toCondition(condition))
        }
      }
      case("read status without user") { run(SeriesSearchHelper(SearchContext.empty()).toCondition(parse(conditions[13]))) }
      case("read status, anonymous") { run(SeriesSearchHelper(SearchContext.ofAnonymousUser()).toCondition(parse(conditions[13]))) }
      case("read status, other user") { run(SeriesSearchHelper(SearchContext(user("U2"))).toCondition(parse(conditions[13]))) }
      case("restricted user and conditions") { run(SeriesSearchHelper(contexts[6].second).toCondition(parse(conditions[56]))) }
      case("user with one library and library condition") { run(SeriesSearchHelper(contexts[3].second).toCondition(parse(conditions[5]))) }
    }

    func("toConditionInternal@33") {
      case("no library restriction") { run(SeriesSearchHelper(SearchContext(user("U1"))).toCondition()) }
      case("empty library list") { run(SeriesSearchHelper(SearchContext(user("U1", emptySet()))).toCondition()) }
      case("one library") { run(SeriesSearchHelper(SearchContext(user("U1", setOf("L2")))).toCondition()) }
      case("several libraries") { run(SeriesSearchHelper(SearchContext(user("U1", setOf("L3", "L1", "L9")))).toCondition()) }
    }

    func("toConditionInternal@39") {
      case("nested empty groups") { run(SeriesSearchHelper(SearchContext.empty()).toCondition(parse("""{"allOf":[{"anyOf":[]},{"allOf":[]}]}"""))) }
      case("single condition group") { run(SeriesSearchHelper(SearchContext.empty()).toCondition(parse("""{"anyOf":[{"deleted":{"operator":"isTrue"}}]}"""))) }
      case("duplicate joins") {
        run(SeriesSearchHelper(SearchContext.empty()).toCondition(parse("""{"allOf":[{"title":{"operator":"is","value":"x"}},{"publisher":{"operator":"is","value":"y"}}]}""")))
      }
    }
  }
}
