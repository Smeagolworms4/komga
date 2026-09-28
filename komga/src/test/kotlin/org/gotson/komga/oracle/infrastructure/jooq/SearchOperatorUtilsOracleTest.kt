package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.infrastructure.jooq.toCondition
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.renderBinds
import org.jooq.Condition
import org.jooq.Field
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SearchOperatorUtilsOracleTest : OracleTest() {
  private val db = OracleDb()
  private val s = Tables.SERIES
  private val sm = Tables.SERIES_METADATA
  private val bma = Tables.BOOK_METADATA_AGGREGATION
  private val b = Tables.BOOK
  private val bm = Tables.BOOK_METADATA

  private fun series(c: Condition) =
    db.dsl
      .select(s.ID)
      .from(s)
      .leftJoin(sm)
      .on(s.ID.eq(sm.SERIES_ID))
      .leftJoin(bma)
      .on(s.ID.eq(bma.SERIES_ID))
      .where(c)
      .orderBy(s.ID)

  private fun books(c: Condition) =
    db.dsl
      .select(b.ID)
      .from(b)
      .leftJoin(bm)
      .on(b.ID.eq(bm.BOOK_ID))
      .where(c)
      .orderBy(b.ID)

  /** rendered query and matching series */
  private fun onSeries(c: Condition) = render(series(c)) + listOf(series(c).fetch(s.ID))

  private fun onBooks(c: Condition) = render(books(c)) + listOf(books(c).fetch(b.ID))

  private fun eqString(
    op: SearchOperator.Equality<String>,
    field: Field<String>,
    ignoreCase: Boolean? = null,
  ) = if (ignoreCase == null) op.toCondition(field) else op.toCondition(field, ignoreCase)

  private fun <T> eq(
    op: SearchOperator.Equality<T>,
    field: Field<T>,
  ) = op.toCondition<T>(field)

  private fun <T> eqConverter(
    op: SearchOperator.Equality<T>,
    field: Field<String>,
    converter: (T) -> String,
  ) = op.toCondition(field, converter)

  private fun str(
    op: SearchOperator.StringOp,
    field: Field<String>,
  ) = op.toCondition(field)

  private fun date(
    op: SearchOperator.Date,
    field: Field<LocalDate>,
  ) = op.toCondition(field)

  private fun numNull(
    op: SearchOperator.NumericNullable<Int>,
    field: Field<Int>,
  ) = op.toCondition(field)

  private fun num(
    op: SearchOperator.Numeric<Float>,
    field: Field<Float>,
  ) = op.toCondition(field)

  private fun bool(
    op: SearchOperator.Boolean,
    field: Field<Boolean>,
  ) = op.toCondition(field)

  override fun cases() {
    func("toCondition@10") {
      case("sample rows") { JooqSamples.insert(db) }
      case("is") { onSeries(eqString(SearchOperator.Is("dargaud"), sm.PUBLISHER)) }
      case("is, default ignoreCase") { onSeries(eqString(SearchOperator.Is("Dargaud"), sm.PUBLISHER, null)) }
      case("is ignoring case") { onSeries(eqString(SearchOperator.Is("DARGAUD"), sm.PUBLISHER, true)) }
      case("is ignoring case and accents") { onSeries(eqString(SearchOperator.Is("glenat"), sm.PUBLISHER, true)) }
      case("is not") { onSeries(eqString(SearchOperator.IsNot("dargaud"), sm.PUBLISHER, false)) }
      case("is not ignoring case") { onSeries(eqString(SearchOperator.IsNot("DARGAUD"), sm.PUBLISHER, true)) }
      case("is empty string") { onSeries(eqString(SearchOperator.Is(""), sm.PUBLISHER, true)) }
      case("language") { onSeries(eqString(SearchOperator.Is("en"), sm.LANGUAGE, true)) }
    }

    func("toCondition@18") {
      case("is int") { onSeries(eq(SearchOperator.Is(12), sm.AGE_RATING)) }
      case("is not int excludes null") { onSeries(eq(SearchOperator.IsNot(12), sm.AGE_RATING)) }
      case("is string") { onSeries(eq(SearchOperator.Is("S2"), s.ID)) }
      case("is not string") { onSeries(eq(SearchOperator.IsNot("S2"), s.ID)) }
      case("is boolean") { onSeries(eq(SearchOperator.Is(true), s.ONESHOT)) }
      case("is not boolean") { onSeries(eq(SearchOperator.IsNot(true), s.ONESHOT)) }
      case("is null value") { onSeries(eq(SearchOperator.Is(null as Int?), sm.AGE_RATING)) }
    }

    func("toCondition@24") {
      case("is status") { onSeries(eqConverter(SearchOperator.Is(SeriesMetadata.Status.ONGOING), sm.STATUS, SeriesMetadata.Status::name)) }
      case("is not status") { onSeries(eqConverter(SearchOperator.IsNot(SeriesMetadata.Status.ONGOING), sm.STATUS, SeriesMetadata.Status::name)) }
      case("converter") { onSeries(eqConverter(SearchOperator.Is(5), s.ID) { "S$it" }) }
      case("converter to lower case") { onSeries(eqConverter(SearchOperator.Is(SeriesMetadata.Status.HIATUS), sm.STATUS) { it.name.lowercase() }) }
    }

    func("toCondition@32") {
      case("begins with") { onSeries(str(SearchOperator.BeginsWith("e"), sm.TITLE)) }
      case("begins with accent") { onSeries(str(SearchOperator.BeginsWith("ÉL"), sm.TITLE)) }
      case("does not begin with") { onSeries(str(SearchOperator.DoesNotBeginWith("e"), sm.TITLE)) }
      case("ends with") { onSeries(str(SearchOperator.EndsWith("A"), sm.TITLE)) }
      case("ends with wildcard characters") { onSeries(str(SearchOperator.EndsWith("%_x"), sm.TITLE)) }
      case("does not end with") { onSeries(str(SearchOperator.DoesNotEndWith("a"), sm.TITLE)) }
      case("contains") { onSeries(str(SearchOperator.Contains("ça"), sm.TITLE)) }
      case("contains percent") { onSeries(str(SearchOperator.Contains("%"), sm.TITLE)) }
      case("contains underscore") { onSeries(str(SearchOperator.Contains("_"), sm.TITLE)) }
      case("contains escape character") { onSeries(str(SearchOperator.Contains("!"), sm.TITLE)) }
      case("contains empty") { onSeries(str(SearchOperator.Contains(""), sm.TITLE)) }
      case("does not contain") { onSeries(str(SearchOperator.DoesNotContain("A"), sm.TITLE)) }
      case("is") { onSeries(str(SearchOperator.Is("ALPHA"), sm.TITLE)) }
      case("is with accents") { onSeries(str(SearchOperator.Is("elan vital"), sm.TITLE)) }
      case("is not") { onSeries(str(SearchOperator.IsNot("elan vital"), sm.TITLE)) }
      case("title sort") { onSeries(str(SearchOperator.BeginsWith("be"), sm.TITLE_SORT)) }
    }

    func("toCondition@44") {
      val paris = ZoneId.of("Europe/Paris")
      case("after") { onSeries(date(SearchOperator.After(ZonedDateTime.of(2020, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC)), bma.RELEASE_DATE)) }
      case("after, converted to UTC date") { onSeries(date(SearchOperator.After(ZonedDateTime.of(2020, 5, 2, 1, 0, 0, 0, paris)), bma.RELEASE_DATE)) }
      case("after, far offset") { onSeries(date(SearchOperator.After(ZonedDateTime.of(2020, 5, 1, 20, 0, 0, 0, ZoneOffset.ofHours(-10))), bma.RELEASE_DATE)) }
      case("before") { onSeries(date(SearchOperator.Before(ZonedDateTime.of(2020, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC)), bma.RELEASE_DATE)) }
      case("before, converted to UTC date") { onSeries(date(SearchOperator.Before(ZonedDateTime.of(2020, 5, 2, 1, 30, 0, 0, paris)), bma.RELEASE_DATE)) }
      case("is in the last day") { stable(renderBinds(series(date(SearchOperator.IsInTheLast(Duration.ofDays(1)), bma.RELEASE_DATE)))) }
      case("is in the last 47 hours") { stable(renderBinds(series(date(SearchOperator.IsInTheLast(Duration.ofHours(47)), bma.RELEASE_DATE)))) }
      case("is in the last day ids") { series(date(SearchOperator.IsInTheLast(Duration.ofDays(1)), bma.RELEASE_DATE)).fetch(s.ID) }
      case("is in the last 200 years ids") { series(date(SearchOperator.IsInTheLast(Duration.ofDays(73000)), bma.RELEASE_DATE)).fetch(s.ID) }
      case("is not in the last zero day") { stable(renderBinds(series(date(SearchOperator.IsNotInTheLast(Duration.ZERO), bma.RELEASE_DATE)))) }
      case("is not in the last zero day ids") { series(date(SearchOperator.IsNotInTheLast(Duration.ZERO), bma.RELEASE_DATE)).fetch(s.ID) }
      case("is not in the last 200 years ids") { series(date(SearchOperator.IsNotInTheLast(Duration.ofDays(73000)), bma.RELEASE_DATE)).fetch(s.ID) }
      case("is in the last negative duration") { stable(renderBinds(series(date(SearchOperator.IsInTheLast(Duration.ofDays(-1)), bma.RELEASE_DATE)))) }
      case("is null") { onSeries(date(SearchOperator.IsNull, bma.RELEASE_DATE)) }
      case("is not null") { onSeries(date(SearchOperator.IsNotNull, bma.RELEASE_DATE)) }
      case("book release date") { onBooks(date(SearchOperator.Before(ZonedDateTime.of(2020, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC)), bm.RELEASE_DATE)) }
    }

    func("toCondition@54") {
      case("is") { onSeries(numNull(SearchOperator.Is(12), sm.AGE_RATING)) }
      case("is not includes null") { onSeries(numNull(SearchOperator.IsNot(12), sm.AGE_RATING)) }
      case("greater than is inclusive") { onSeries(numNull(SearchOperator.GreaterThan(12), sm.AGE_RATING)) }
      case("less than is inclusive") { onSeries(numNull(SearchOperator.LessThan(12), sm.AGE_RATING)) }
      case("is null") { onSeries(numNull(SearchOperator.IsNullT(), sm.AGE_RATING)) }
      case("is not null") { onSeries(numNull(SearchOperator.IsNotNullT(), sm.AGE_RATING)) }
      case("negative value") { onSeries(numNull(SearchOperator.GreaterThan(-5), sm.TOTAL_BOOK_COUNT)) }
    }

    func("toCondition@64") {
      case("is") { onBooks(num(SearchOperator.Is(1.0f), bm.NUMBER_SORT)) }
      case("is not") { onBooks(num(SearchOperator.IsNot(2.5f), bm.NUMBER_SORT)) }
      case("greater than is inclusive") { onBooks(num(SearchOperator.GreaterThan(2.5f), bm.NUMBER_SORT)) }
      case("less than is inclusive") { onBooks(num(SearchOperator.LessThan(1f), bm.NUMBER_SORT)) }
      case("float precision") { onBooks(num(SearchOperator.GreaterThan(0.1f), bm.NUMBER_SORT)) }
      case("negative") { onBooks(num(SearchOperator.LessThan(-0.5f), bm.NUMBER_SORT)) }
    }

    func("toCondition@72") {
      case("is true") { onSeries(bool(SearchOperator.IsTrue, s.ONESHOT)) }
      case("is false") { onSeries(bool(SearchOperator.IsFalse, s.ONESHOT)) }
      case("book") { onBooks(bool(SearchOperator.IsTrue, b.ONESHOT)) }
    }
  }
}
