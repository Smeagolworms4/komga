package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.EpubTocEntry
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.infrastructure.jooq.UnpagedSorted
import org.gotson.komga.infrastructure.jooq.buildPage
import org.gotson.komga.infrastructure.jooq.csAlias
import org.gotson.komga.infrastructure.jooq.deserializeJsonGz
import org.gotson.komga.infrastructure.jooq.deserializeMediaExtension
import org.gotson.komga.infrastructure.jooq.inOrNoCondition
import org.gotson.komga.infrastructure.jooq.noCase
import org.gotson.komga.infrastructure.jooq.rlbAlias
import org.gotson.komga.infrastructure.jooq.serializeJsonGz
import org.gotson.komga.infrastructure.jooq.sortByValues
import org.gotson.komga.infrastructure.jooq.toCondition
import org.gotson.komga.infrastructure.jooq.toOrderBy
import org.gotson.komga.infrastructure.jooq.toSortField
import org.gotson.komga.infrastructure.jooq.udfStripAccents
import org.gotson.komga.infrastructure.jooq.unicode1
import org.gotson.komga.infrastructure.jooq.unicode3
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.gunzip
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.gzip
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render
import org.jooq.Condition
import org.jooq.Field
import org.jooq.OrderField
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class UtilsOracleTest : OracleTest() {
  private val db = OracleDb()
  private val s = Tables.SERIES
  private val sm = Tables.SERIES_METADATA
  private val mapper get() = db.mapper

  private fun series(c: Condition) =
    db.dsl
      .select(s.ID)
      .from(s)
      .leftJoin(sm)
      .on(s.ID.eq(sm.SERIES_ID))
      .where(c)
      .orderBy(s.ID)

  private fun seriesIds(c: Condition) = series(c).fetch(s.ID)

  private fun titles(vararg order: OrderField<*>) =
    db.dsl
      .select(sm.TITLE)
      .from(sm)
      .orderBy(*order)

  private fun ordered(order: List<SortField<out Any>>) =
    db.dsl
      .select(sm.SERIES_ID)
      .from(sm)
      .orderBy(order)

  private val sorts: Map<String, Field<out Any>> = mapOf("title" to sm.TITLE, "age" to sm.AGE_RATING, "id" to sm.SERIES_ID)

  private fun page(p: Page<*>) = listOf(p, p.totalPages, p.sort.toString(), p.pageable.isPaged, p.hasNext())

  private val locator =
    R2Locator(
      href = "chapter1.xhtml",
      type = "application/xhtml+xml",
      title = "Chapitre ü",
      locations = R2Locator.Location(fragments = listOf("p1"), progression = 0.5f, position = 3, totalProgression = 0.1f),
      text = R2Locator.Text(before = "a", highlight = "b"),
    )

  override fun cases() {
    func("noCase") {
      case("sample rows") { JooqSamples.insert(db) }
      case("render") { render(titles(sm.TITLE.noCase(), sm.SERIES_ID.asc())) }
      case("order") { titles(sm.TITLE.noCase(), sm.SERIES_ID.asc()).fetch(sm.TITLE) }
      case("order desc") { titles(sm.TITLE.noCase().desc(), sm.SERIES_ID.asc()).fetch(sm.TITLE) }
      case("equal ignores ascii case") { seriesIds(sm.TITLE.noCase().eq("ALPHA")) }
      case("equal keeps accents and non-ascii case") { seriesIds(sm.TITLE.noCase().eq("élan vital")) }
      case("render equal") { render(series(sm.PUBLISHER.noCase().eq("DARGAUD"))) }
      case("publisher") { seriesIds(sm.PUBLISHER.noCase().eq("DARGAUD")) }
    }

    func("unicode1") {
      case("render") { render(titles(sm.TITLE.unicode1(), sm.SERIES_ID.asc())) }
      case("order") { titles(sm.TITLE.unicode1(), sm.SERIES_ID.asc()).fetch(sm.TITLE) }
      case("equal ignores case and accents") { seriesIds(sm.TITLE.unicode1().eq("elan VITAL")) }
      case("equal with cedilla") { seriesIds(sm.TITLE.unicode1().eq("beta ca")) }
      case("equal with ligature and symbols") { seriesIds(sm.TITLE.unicode1().eq("ecole 100%_X")) }
      case("not equal") { seriesIds(sm.PUBLISHER.unicode1().ne("glenat")) }
      case("render not equal") { render(series(sm.PUBLISHER.unicode1().ne("glenat"))) }
      case("like does not use the collation") { seriesIds(sm.TITLE.unicode1().like("e%")) }
    }

    func("unicode3") {
      case("render") { render(titles(sm.TITLE.unicode3(), sm.SERIES_ID.asc())) }
      case("order") { titles(sm.TITLE.unicode3(), sm.SERIES_ID.asc()).fetch(sm.TITLE) }
      case("order desc") { titles(sm.TITLE.unicode3().desc()).fetch(sm.TITLE) }
      case("equal is case sensitive") { seriesIds(sm.TITLE.unicode3().eq("Alpha")) }
      case("equal same case") { seriesIds(sm.TITLE.unicode3().eq("alpha")) }
      case("equal is accent sensitive") { seriesIds(sm.PUBLISHER.unicode3().eq("Glenat")) }
    }

    func("udfStripAccents") {
      case("render") {
        render(
          db.dsl
            .select(sm.TITLE.udfStripAccents())
            .from(sm)
            .orderBy(sm.SERIES_ID),
        )
      }
      case("values") {
        db.dsl
          .select(sm.TITLE.udfStripAccents())
          .from(sm)
          .orderBy(sm.SERIES_ID)
          .fetch()
          .map { it.value1() }
      }
      case("bound value") {
        db.dsl
          .select(DSL.value("Ça été Æsop Øre ñ ß ﬁ Ǆ").udfStripAccents())
          .fetchOne()
          ?.value1()
      }
      case("empty string") {
        db.dsl
          .select(DSL.value("").udfStripAccents())
          .fetchOne()
          ?.value1()
      }
      case("null value") {
        db.dsl
          .select(DSL.value(null as String?).udfStripAccents())
          .fetchOne()
          ?.value1()
      }
      case("render bound value") { render(db.dsl.select(DSL.value("é").udfStripAccents())) }
      case("in condition") { seriesIds(sm.TITLE.udfStripAccents().eq("Elan Vital")) }
      case("publisher without accents") { seriesIds(sm.PUBLISHER.udfStripAccents().eq("Glenat")) }
    }

    func("toOrderBy") {
      case("single property") { render(ordered(Sort.by("title").toOrderBy(sorts))) }
      case("single property ids") { ordered(Sort.by("title").toOrderBy(sorts)).fetch(sm.SERIES_ID) }
      case("several orders") { render(ordered(Sort.by(Sort.Order.desc("age"), Sort.Order.asc("id")).toOrderBy(sorts))) }
      case("several orders ids") { ordered(Sort.by(Sort.Order.desc("age"), Sort.Order.asc("id")).toOrderBy(sorts)).fetch(sm.SERIES_ID) }
      case("unknown properties are dropped") { Sort.by("nope", "title", "Title", "").toOrderBy(sorts).size }
      case("unknown properties render") { render(ordered(Sort.by("nope", "age").toOrderBy(sorts))) }
      case("unsorted") { Sort.unsorted().toOrderBy(sorts).size }
      case("empty map") { Sort.by("title").toOrderBy(emptyMap()).size }
      case("ignore case and null handling are not rendered") {
        render(ordered(Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.desc("age").nullsLast()).toOrderBy(sorts)))
      }
    }

    func("toSortField") {
      case("ascending") { render(ordered(listOfNotNull(Sort.Order.asc("age").toSortField(sorts)))) }
      case("descending") { render(ordered(listOfNotNull(Sort.Order.desc("age").toSortField(sorts)))) }
      case("descending ids") { ordered(listOfNotNull(Sort.Order.desc("age").toSortField(sorts), Sort.Order.asc("id").toSortField(sorts))).fetch(sm.SERIES_ID) }
      case("default direction") { render(ordered(listOfNotNull(Sort.Order.by("title").toSortField(sorts)))) }
      case("missing property") { Sort.Order.asc("missing").toSortField(sorts) }
      case("property is case sensitive") { Sort.Order.asc("TITLE").toSortField(sorts) }
    }

    func("sortByValues") {
      case("render") {
        render(
          db.dsl
            .select(s.ID)
            .from(s)
            .orderBy(s.ID.sortByValues(listOf("S3", "S1")), s.ID),
        )
      }
      case("ascending") {
        db.dsl
          .select(s.ID)
          .from(s)
          .orderBy(s.ID.sortByValues(listOf("S3", "S1", "S5")), s.ID)
          .fetch(s.ID)
      }
      case("descending render") {
        render(
          db.dsl
            .select(s.ID)
            .from(s)
            .orderBy(s.ID.sortByValues(listOf("S3", "S1"), false), s.ID),
        )
      }
      case("descending") {
        db.dsl
          .select(s.ID)
          .from(s)
          .orderBy(s.ID.sortByValues(listOf("S3", "S1", "S5"), false), s.ID)
          .fetch(s.ID)
      }
      case("empty values") {
        render(
          db.dsl
            .select(s.ID)
            .from(s)
            .orderBy(s.ID.sortByValues(emptyList()), s.ID),
        )
      }
      case("empty values ids") {
        db.dsl
          .select(s.ID)
          .from(s)
          .orderBy(s.ID.sortByValues(emptyList()), s.ID.desc())
          .fetch(s.ID)
      }
      case("duplicate values") {
        db.dsl
          .select(s.ID)
          .from(s)
          .orderBy(s.ID.sortByValues(listOf("S2", "S4", "S2")), s.ID)
          .fetch(s.ID)
      }
      case("dummy value") {
        db.dsl
          .select(s.ID)
          .from(s)
          .orderBy(s.ID.sortByValues(listOf("dummy dsl", "S6")), s.ID)
          .fetch(s.ID)
      }
      case("selected value") {
        db.dsl
          .select(s.ID, s.ID.sortByValues(listOf("S2", "S1"), false))
          .from(s)
          .orderBy(s.ID)
          .fetch()
          .map { listOf(it.value1(), it.value2()) }
      }
    }

    func("inOrNoCondition") {
      case("null list") { render(series(s.ID.inOrNoCondition(null))) }
      case("null list ids") { seriesIds(s.ID.inOrNoCondition(null)) }
      case("empty list") { render(series(s.ID.inOrNoCondition(emptyList()))) }
      case("empty list ids") { seriesIds(s.ID.inOrNoCondition(emptyList())) }
      case("one value") { render(series(s.ID.inOrNoCondition(listOf("S2")))) }
      case("several values") { render(series(s.ID.inOrNoCondition(setOf("S5", "S1", "nope")))) }
      case("several values ids") { seriesIds(s.ID.inOrNoCondition(setOf("S5", "S1", "nope"))) }
      case("duplicates") { render(series(s.ID.inOrNoCondition(listOf("S1", "S1")))) }
      case("combined with and") { render(series(s.LIBRARY_ID.eq("L1").and(s.ID.inOrNoCondition(null)))) }
      case("combined with or") { seriesIds(s.LIBRARY_ID.eq("L3").or(s.ID.inOrNoCondition(emptyList()))) }
    }

    func("toCondition") {
      val restrictions =
        listOf(
          "none" to ContentRestrictions(),
          "allow only 12" to ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY)),
          "allow only 0" to ContentRestrictions(AgeRestriction(0, AllowExclude.ALLOW_ONLY)),
          "exclude 16" to ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE)),
          "labels allow" to ContentRestrictions(labelsAllow = setOf("kids")),
          "labels allow case" to ContentRestrictions(labelsAllow = setOf("ADULT", " ")),
          "labels exclude" to ContentRestrictions(labelsExclude = setOf("adult")),
          "labels allow and exclude" to ContentRestrictions(labelsAllow = setOf("kids", "teen"), labelsExclude = setOf("teen", "adult")),
          "age and labels allow" to ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY), labelsAllow = setOf("kids")),
          "age exclude and labels exclude" to ContentRestrictions(AgeRestriction(16, AllowExclude.EXCLUDE), labelsExclude = setOf("kids")),
          "everything" to ContentRestrictions(AgeRestriction(18, AllowExclude.ALLOW_ONLY), labelsAllow = setOf("kids", "adult"), labelsExclude = setOf("teen")),
        )
      restrictions.forEach { (name, r) ->
        case(name) { render(series(r.toCondition())) }
        case("$name ids") { seriesIds(r.toCondition()) }
      }
    }

    func("serializeJsonGz") {
      case("map") { gunzip(mapper.serializeJsonGz(linkedMapOf("a" to 1, "b" to listOf("x", null), "c" to "é\"\n"))) }
      case("list") { gunzip(mapper.serializeJsonGz(listOf(1, 2.5, true, "s"))) }
      case("string") { gunzip(mapper.serializeJsonGz("Ça")) }
      case("number") { gunzip(mapper.serializeJsonGz(42)) }
      case("locator") { gunzip(mapper.serializeJsonGz(locator)) }
      case("locator without optional fields") { gunzip(mapper.serializeJsonGz(R2Locator(href = "h", type = "t", locations = R2Locator.Location()))) }
      case("epub extension") {
        gunzip(
          mapper.serializeJsonGz(
            MediaExtensionEpub(
              toc = listOf(EpubTocEntry("Chapter 1", "c1.xhtml", listOf(EpubTocEntry("Section", null)))),
              isFixedLayout = true,
              positions = listOf(locator),
            ),
          ),
        )
      }
      case("gzip magic") { mapper.serializeJsonGz("x")?.copyOf(3) }
    }

    func("deserializeJsonGz") {
      case("null") { mapper.deserializeJsonGz<R2Locator>(null) }
      case("not gzip") { mapper.deserializeJsonGz<R2Locator>("{}".toByteArray()) }
      case("empty bytes") { mapper.deserializeJsonGz<R2Locator>(ByteArray(0)) }
      case("locator") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":"a","type":"b","locations":{"progression":0.25,"position":2}}""")) }
      case("round trip") { mapper.deserializeJsonGz<R2Locator>(mapper.serializeJsonGz(locator)) }
      case("unknown property") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":"a","type":"b","other":1}""")) }
      case("case insensitive property") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"HREF":"a","Type":"b"}""")) }
      case("missing required property") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":"a"}""")) }
      case("null required property") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":null,"type":"b"}""")) }
      case("invalid json") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":""")) }
      case("empty content") { mapper.deserializeJsonGz<R2Locator>(gzip("")) }
      case("truncated gzip") { mapper.deserializeJsonGz<R2Locator>(gzip("""{"href":"a","type":"b"}""").copyOf(12)) }
      case("json null") { mapper.deserializeJsonGz<R2Locator>(gzip("null")) }
      case("string target") { mapper.deserializeJsonGz<String>(gzip("\"é\"")) }
    }

    func("deserializeMediaExtension") {
      val epub = "org.gotson.komga.domain.model.MediaExtensionEpub"
      case("null class") { mapper.deserializeMediaExtension(null, gzip("{}")) }
      case("null blob") { mapper.deserializeMediaExtension(epub, null) }
      case("both null") { mapper.deserializeMediaExtension(null, null) }
      case("empty epub") { mapper.deserializeMediaExtension(epub, gzip("{}")) }
      case("epub") {
        mapper.deserializeMediaExtension(
          epub,
          gzip("""{"toc":[{"title":"T","href":"t.xhtml","children":[]}],"landmarks":[],"pageList":[],"isFixedLayout":true,"positions":[{"href":"p","type":"x"}]}"""),
        )
      }
      case("round trip") {
        mapper.deserializeMediaExtension(epub, mapper.serializeJsonGz(MediaExtensionEpub(pageList = listOf(EpubTocEntry("p1", "p1.xhtml")), positions = listOf(locator))))
      }
      case("unknown class") { mapper.deserializeMediaExtension("org.gotson.komga.domain.model.Nope", gzip("{}")) }
      case("not a media extension") { mapper.deserializeMediaExtension("org.gotson.komga.domain.model.R2Locator", gzip("""{"href":"a","type":"b"}""")) }
      case("interface") { mapper.deserializeMediaExtension("org.gotson.komga.domain.model.MediaExtension", gzip("{}")) }
      case("not gzip") { mapper.deserializeMediaExtension(epub, "{}".toByteArray()) }
      case("invalid json") { mapper.deserializeMediaExtension(epub, gzip("[")) }
      case("wrong type") { mapper.deserializeMediaExtension(epub, gzip("""{"isFixedLayout":"maybe"}""")) }
    }

    func("rlbAlias") {
      case("render") {
        render(
          db.dsl
            .select(rlbAlias("R1").BOOK_ID)
            .from(rlbAlias("R1"))
            .where(rlbAlias("R1").READLIST_ID.eq("R1"))
            .orderBy(rlbAlias("R1").NUMBER),
        )
      }
      case("values") {
        db.dsl
          .select(rlbAlias("R1").BOOK_ID)
          .from(rlbAlias("R1"))
          .where(rlbAlias("R1").READLIST_ID.eq("R1"))
          .orderBy(rlbAlias("R1").NUMBER)
          .fetch()
          .map { it.value1() }
      }
      case("name") { rlbAlias("R1").name }
      case("name with special characters") { render(db.dsl.select(rlbAlias("a\"b c").READLIST_ID).from(rlbAlias("a\"b c"))) }
      case("empty id") { rlbAlias("").name }
    }

    func("csAlias") {
      case("render") {
        render(
          db.dsl
            .select(csAlias("C1").SERIES_ID)
            .from(csAlias("C1"))
            .where(csAlias("C1").COLLECTION_ID.eq("C1"))
            .orderBy(csAlias("C1").NUMBER),
        )
      }
      case("values") {
        db.dsl
          .select(csAlias("C1").SERIES_ID)
          .from(csAlias("C1"))
          .where(csAlias("C1").COLLECTION_ID.eq("C1"))
          .orderBy(csAlias("C1").NUMBER)
          .fetch()
          .map { it.value1() }
      }
      case("name") { csAlias("C1").name }
      case("join") {
        render(
          db.dsl
            .select(s.ID)
            .from(s)
            .leftJoin(csAlias("C2"))
            .on(s.ID.eq(csAlias("C2").SERIES_ID))
            .where(csAlias("C2").COLLECTION_ID.eq("C2")),
        )
      }
    }

    func("buildPage") {
      case("paged, sort from pageable") { page(buildPage(listOf("a", "b"), PageRequest.of(1, 2, Sort.by("title")), 10, null)) }
      case("paged, explicit sort") { page(buildPage(listOf("a"), PageRequest.of(0, 5, Sort.by("title")), 1, Sort.by(Sort.Order.desc("age")))) }
      case("paged, count smaller than page") { page(buildPage(listOf("a", "b", "c"), PageRequest.of(2, 10), 5, null)) }
      case("paged, empty content") { page(buildPage(emptyList<String>(), PageRequest.of(3, 10), 5, null)) }
      case("unpaged, small count") { page(buildPage(listOf("a"), Pageable.unpaged(), 5, null)) }
      case("unpaged, large count") { page(buildPage(List(30) { "x$it" }, Pageable.unpaged(), 30, Sort.by("id"))) }
      case("unpaged, count 20") { page(buildPage(emptyList<String>(), Pageable.unpaged(), 20, null)) }
      case("unpaged, zero count") { page(buildPage(emptyList<String>(), Pageable.unpaged(), 0, null)) }
      case("unpaged sorted") { page(buildPage(listOf(1, 2, 3), UnpagedSorted(Sort.by(Sort.Order.asc("seriesId"), Sort.Order.desc("number"))), 3, null)) }
      case("unpaged sorted, explicit sort") { page(buildPage(listOf(1), UnpagedSorted(Sort.by("a")), 1, Sort.unsorted())) }
      case("unpaged sorted, large count") { page(buildPage(listOf(1), UnpagedSorted(Sort.unsorted()), 25, null)) }
    }
  }
}
