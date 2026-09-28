package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.SearchCondition
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.infrastructure.jooq.BookSearchHelper
import org.gotson.komga.infrastructure.jooq.RequiredJoin
import org.gotson.komga.infrastructure.jooq.rlbAlias
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.user
import org.jooq.Condition
import org.jooq.Record1
import org.jooq.SelectJoinStep

class BookSearchHelperOracleTest : OracleTest() {
  private val db = OracleDb()
  private val b = Tables.BOOK
  private val bm = Tables.BOOK_METADATA
  private val m = Tables.MEDIA
  private val rp = Tables.READ_PROGRESS
  private val sm = Tables.SERIES_METADATA

  /** books matching a condition, the tables joined as listed */
  private fun query(p: Pair<Condition, Set<RequiredJoin>>): org.jooq.SelectSeekStep1<Record1<String>, String> {
    var q: SelectJoinStep<Record1<String>> = db.dsl.select(b.ID).from(b)
    p.second.forEach { j ->
      q =
        when (j) {
          RequiredJoin.BookMetadata -> q.leftJoin(bm).on(b.ID.eq(bm.BOOK_ID))
          RequiredJoin.Media -> q.leftJoin(m).on(b.ID.eq(m.BOOK_ID))
          RequiredJoin.SeriesMetadata -> q.leftJoin(sm).on(b.SERIES_ID.eq(sm.SERIES_ID))
          is RequiredJoin.ReadProgress -> q.leftJoin(rp).on(b.ID.eq(rp.BOOK_ID).and(rp.USER_ID.eq(j.userId)))
          is RequiredJoin.ReadList -> rlbAlias(j.readListId).let { rlb -> q.leftJoin(rlb).on(b.ID.eq(rlb.BOOK_ID)) }
          else -> q
        }
    }
    return q.where(p.first).orderBy(b.ID)
  }

  private fun run(p: Pair<Condition, Set<RequiredJoin>>): List<Any?> {
    val q = query(p)
    return render(q) + listOf(p.second, q.fetch(b.ID))
  }

  private fun parse(json: String): SearchCondition.Book = db.mapper.readValue(json, SearchCondition.Book::class.java)

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
      """{"anyOf":[{"seriesId":{"operator":"is","value":"S3"}},{"oneShot":{"operator":"isTrue"}}]}""",
      """{"anyOf":[{"allOf":[{"libraryId":{"operator":"is","value":"L1"}},{"numberSort":{"operator":"greaterThan","value":2.0}}]},{"mediaStatus":{"operator":"is","value":"ERROR"}}]}""",
      """{"libraryId":{"operator":"isNot","value":"L1"}}""",
      """{"seriesId":{"operator":"is","value":"S1"}}""",
      """{"seriesId":{"operator":"isNot","value":"S1"}}""",
      """{"readListId":{"operator":"is","value":"R1"}}""",
      """{"readListId":{"operator":"isNot","value":"R1"}}""",
      """{"title":{"operator":"contains","value":"e"}}""",
      """{"title":{"operator":"is","value":"debut"}}""",
      """{"title":{"operator":"endsWith","value":"%_X"}}""",
      """{"deleted":{"operator":"isTrue"}}""",
      """{"deleted":{"operator":"isFalse"}}""",
      """{"releaseDate":{"operator":"after","dateTime":"2020-06-14T23:30:00-01:00"}}""",
      """{"releaseDate":{"operator":"isNotNull"}}""",
      """{"releaseDate":{"operator":"isNull"}}""",
      """{"numberSort":{"operator":"greaterThan","value":1.0}}""",
      """{"numberSort":{"operator":"lessThan","value":2.5}}""",
      """{"numberSort":{"operator":"is","value":1}}""",
      """{"numberSort":{"operator":"isNot","value":1.0}}""",
      """{"readStatus":{"operator":"is","value":"UNREAD"}}""",
      """{"readStatus":{"operator":"is","value":"READ"}}""",
      """{"readStatus":{"operator":"is","value":"IN_PROGRESS"}}""",
      """{"readStatus":{"operator":"isNot","value":"UNREAD"}}""",
      """{"readStatus":{"operator":"isNot","value":"READ"}}""",
      """{"readStatus":{"operator":"isNot","value":"IN_PROGRESS"}}""",
      """{"mediaStatus":{"operator":"is","value":"READY"}}""",
      """{"mediaStatus":{"operator":"isNot","value":"READY"}}""",
      """{"mediaProfile":{"operator":"is","value":"DIVINA"}}""",
      """{"mediaProfile":{"operator":"is","value":"PDF"}}""",
      """{"mediaProfile":{"operator":"is","value":"EPUB"}}""",
      """{"mediaProfile":{"operator":"isNot","value":"PDF"}}""",
      """{"tag":{"operator":"is","value":"tag1"}}""",
      """{"tag":{"operator":"is","value":"TAG2"}}""",
      """{"tag":{"operator":"isNot","value":"tag1"}}""",
      """{"tag":{"operator":"isNull"}}""",
      """{"tag":{"operator":"isNotNull"}}""",
      """{"author":{"operator":"is","value":{"name":"alice"}}}""",
      """{"author":{"operator":"is","value":{"role":"writer"}}}""",
      """{"author":{"operator":"is","value":{"name":"emile","role":"colorist"}}}""",
      """{"author":{"operator":"is","value":{}}}""",
      """{"author":{"operator":"isNot","value":{"name":"bob"}}}""",
      """{"author":{"operator":"isNot","value":{}}}""",
      """{"poster":{"operator":"is","value":{"type":"GENERATED"}}}""",
      """{"poster":{"operator":"is","value":{"selected":true}}}""",
      """{"poster":{"operator":"is","value":{"selected":false}}}""",
      """{"poster":{"operator":"is","value":{"type":"SIDECAR","selected":false}}}""",
      """{"poster":{"operator":"is","value":{}}}""",
      """{"poster":{"operator":"isNot","value":{"type":"GENERATED"}}}""",
      """{"poster":{"operator":"isNot","value":{"type":"USER_UPLOADED","selected":true}}}""",
      """{"poster":{"operator":"isNot","value":{}}}""",
      """{"oneShot":{"operator":"isTrue"}}""",
      """{"oneShot":{"operator":"isFalse"}}""",
      """{"allOf":[{"readListId":{"operator":"is","value":"R1"}},{"readStatus":{"operator":"is","value":"READ"}},{"mediaStatus":{"operator":"is","value":"READY"}}]}""",
      """{"allOf":[{"title":{"operator":"doesNotBeginWith","value":"z"}},{"numberSort":{"operator":"lessThan","value":50}},{"releaseDate":{"operator":"isNotNull"}}]}""",
      """{"anyOf":[{"readListId":{"operator":"is","value":"R1"}},{"readListId":{"operator":"is","value":"R2"}}]}""",
    )

  override fun cases() {
    func("toCondition@29") {
      case("sample rows") { JooqSamples.insert(db) }
      contexts.forEach { (name, context) ->
        case(name) { run(BookSearchHelper(context).toCondition()) }
      }
    }

    func("toCondition@23") {
      case("null condition") { run(BookSearchHelper(SearchContext(user("U1"))).toCondition(null)) }
      case("null condition, restricted user") { run(BookSearchHelper(contexts[5].second).toCondition(null)) }
      conditions.forEach { json ->
        case(json) {
          val condition = parse(json)
          listOf(condition) + run(BookSearchHelper(SearchContext(user("U1"))).toCondition(condition))
        }
      }
      case("read status without user") { run(BookSearchHelper(SearchContext.empty()).toCondition(parse(conditions[23]))) }
      case("read status, anonymous") { run(BookSearchHelper(SearchContext.ofAnonymousUser()).toCondition(parse(conditions[23]))) }
      case("read status, other user") { run(BookSearchHelper(SearchContext(user("U2"))).toCondition(parse(conditions[23]))) }
      case("restricted user and conditions") { run(BookSearchHelper(contexts[6].second).toCondition(parse(conditions[55]))) }
      case("user with one library and library condition") { run(BookSearchHelper(contexts[3].second).toCondition(parse(conditions[5]))) }
    }

    func("toConditionInternal@35") {
      case("no library restriction") { run(BookSearchHelper(SearchContext(user("U1"))).toCondition()) }
      case("empty library list") { run(BookSearchHelper(SearchContext(user("U1", emptySet()))).toCondition()) }
      case("one library") { run(BookSearchHelper(SearchContext(user("U1", setOf("L2")))).toCondition()) }
      case("several libraries") { run(BookSearchHelper(SearchContext(user("U1", setOf("L3", "L1", "L9")))).toCondition()) }
    }

    func("toConditionInternal@41") {
      case("nested empty groups") { run(BookSearchHelper(SearchContext.empty()).toCondition(parse("""{"allOf":[{"anyOf":[]},{"allOf":[]}]}"""))) }
      case("single condition group") { run(BookSearchHelper(SearchContext.empty()).toCondition(parse("""{"anyOf":[{"deleted":{"operator":"isTrue"}}]}"""))) }
      case("duplicate joins") {
        run(BookSearchHelper(SearchContext.empty()).toCondition(parse("""{"allOf":[{"title":{"operator":"is","value":"x"}},{"numberSort":{"operator":"is","value":1}}]}""")))
      }
    }
  }
}
