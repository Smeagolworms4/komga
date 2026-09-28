package org.gotson.komga.oracle.infrastructure.search

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute
import org.apache.lucene.analysis.tokenattributes.TypeAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.LuceneHelper
import org.gotson.komga.infrastructure.search.LuceneSyncCommitter
import org.gotson.komga.infrastructure.search.MultiLingualAnalyzer
import org.gotson.komga.infrastructure.search.MultiLingualNGramAnalyzer
import org.gotson.komga.interfaces.api.rest.dto.AlternateTitleDto
import org.gotson.komga.interfaces.api.rest.dto.AuthorDto
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataAggregationDto
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataDto
import org.gotson.komga.interfaces.api.rest.dto.MediaDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesMetadataDto
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.Path

/** Shared data of the search oracle tests, mirrored by test/unit/infrastructure/search/searchSamples.ts in KomgaJS */
object SearchSamples {
  private val corpus: JsonNode by lazy { ObjectMapper().readTree(Path("src/test/resources/oracle/search/search-corpus.json").toFile()) }

  private val date: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun JsonNode.strings() = map { it.asText() }

  private fun JsonNode.textOrNull() = if (isNull) null else asText()

  private fun JsonNode.authors() = map { AuthorDto(it[0].asText(), it[1].asText()) }

  /** Analyzer inputs */
  val texts: List<String> by lazy { corpus["texts"].strings() }

  /** Search terms (null included) */
  val queries: List<String?> by lazy { corpus["queries"].map { it.textOrNull() } }

  val books: List<BookDto> by lazy {
    corpus["books"].map {
      BookDto(
        id = it["id"].asText(),
        seriesId = it["seriesId"].asText(),
        seriesTitle = "",
        libraryId = "LIBRARY",
        name = it["title"].asText(),
        url = "file:/komga/${it["id"].asText()}.cbz",
        number = 1,
        created = date,
        lastModified = date,
        fileLastModified = date,
        sizeBytes = 0,
        media = MediaDto(it["status"].asText(), "application/zip", 1, "", false, false),
        metadata =
          BookMetadataDto(
            title = it["title"].asText(),
            titleLock = false,
            summary = "",
            summaryLock = false,
            number = "1",
            numberLock = false,
            numberSort = 1F,
            numberSortLock = false,
            releaseDate = it["releaseDate"].textOrNull()?.let { d -> LocalDate.parse(d) },
            releaseDateLock = false,
            authors = it["authors"].authors(),
            authorsLock = false,
            tags = it["tags"].strings().toSet(),
            tagsLock = false,
            isbn = it["isbn"].asText(),
            isbnLock = false,
            links = emptyList(),
            linksLock = false,
            created = date,
            lastModified = date,
          ),
        deleted = it["deleted"].asBoolean(),
        fileHash = "",
        oneshot = it["oneshot"].asBoolean(),
      )
    }
  }

  val series: List<SeriesDto> by lazy {
    corpus["series"].map {
      SeriesDto(
        id = it["id"].asText(),
        libraryId = "LIBRARY",
        name = it["title"].asText(),
        url = "file:/komga/${it["id"].asText()}",
        created = date,
        lastModified = date,
        fileLastModified = date,
        booksCount = it["booksCount"].asInt(),
        booksReadCount = 0,
        booksUnreadCount = 0,
        booksInProgressCount = 0,
        metadata =
          SeriesMetadataDto(
            status = it["status"].asText(),
            statusLock = false,
            title = it["title"].asText(),
            titleLock = false,
            titleSort = it["titleSort"].asText(),
            titleSortLock = false,
            summary = "",
            summaryLock = false,
            readingDirection = it["readingDirection"].asText(),
            readingDirectionLock = false,
            publisher = it["publisher"].asText(),
            publisherLock = false,
            ageRating = if (it["ageRating"].isNull) null else it["ageRating"].asInt(),
            ageRatingLock = false,
            language = it["language"].asText(),
            languageLock = false,
            genres = it["genres"].strings().toSet(),
            genresLock = false,
            tags = it["tags"].strings().toSet(),
            tagsLock = false,
            totalBookCount = if (it["totalBookCount"].isNull) null else it["totalBookCount"].asInt(),
            totalBookCountLock = false,
            sharingLabels = it["sharingLabels"].strings().toSet(),
            sharingLabelsLock = false,
            links = emptyList(),
            linksLock = false,
            alternateTitles = it["alternateTitles"].map { a -> AlternateTitleDto(a[0].asText(), a[1].asText()) },
            alternateTitlesLock = false,
            created = date,
            lastModified = date,
          ),
        booksMetadata =
          BookMetadataAggregationDto(
            authors = it["authors"].authors(),
            tags = it["booksTags"].strings().toSet(),
            releaseDate = it["releaseDate"].textOrNull()?.let { d -> LocalDate.parse(d) },
            summary = "",
            summaryNumber = "",
            created = date,
            lastModified = date,
          ),
        deleted = it["deleted"].asBoolean(),
        oneshot = it["oneshot"].asBoolean(),
      )
    }
  }

  val collections: List<SeriesCollection> by lazy {
    corpus["collections"].map { SeriesCollection(it[1].asText(), id = it[0].asText(), createdDate = date) }
  }

  val readLists: List<ReadList> by lazy {
    corpus["readLists"].map { ReadList(it[1].asText(), id = it[0].asText(), createdDate = date) }
  }

  /** Tokens of [text]: term, offsets, position increment and length, type; then the final offset and position increment */
  fun tokens(
    analyzer: Analyzer,
    text: String,
    field: String = "title",
  ): List<Any?> =
    analyzer.tokenStream(field, text).use { ts ->
      val term = ts.addAttribute(CharTermAttribute::class.java)
      val offset = ts.addAttribute(OffsetAttribute::class.java)
      val posInc = ts.addAttribute(PositionIncrementAttribute::class.java)
      val posLen = ts.addAttribute(PositionLengthAttribute::class.java)
      val type = ts.addAttribute(TypeAttribute::class.java)
      val out = mutableListOf<Any?>()
      ts.reset()
      while (ts.incrementToken()) out.add(listOf(term.toString(), offset.startOffset(), offset.endOffset(), posInc.positionIncrement, posLen.positionLength, type.type()))
      ts.end()
      out.add(listOf("end", offset.endOffset(), posInc.positionIncrement))
      out
    }

  /** Fields of a document: name, value, stored, tokenized */
  fun fields(doc: Document): List<List<Any?>> = doc.fields.map { listOf(it.name(), it.stringValue(), it.fieldType().stored(), it.fieldType().tokenized()) }

  class Index(
    val directory: Directory = ByteBuffersDirectory(),
  ) {
    val indexAnalyzer = MultiLingualNGramAnalyzer(3, 10, true)
    val searchAnalyzer = MultiLingualAnalyzer()
    val writer = IndexWriter(directory, IndexWriterConfig(indexAnalyzer))
    val searcherManager = SearcherManager(writer, SearcherFactory())
    val helper = LuceneHelper(directory, searchAnalyzer, indexAnalyzer, writer, searcherManager, LuceneSyncCommitter(writer, searcherManager))

    /** Results of every query, for [entities] */
    fun searchAll(entities: List<LuceneEntity> = LuceneEntity.entries): List<Any?> =
      entities.map { entity -> listOf(entity, queries.map { q -> helper.searchEntitiesIds(q, entity) }) }

    /**
     * Results of every query as sorted ids, for an index holding deleted documents: Lucene counts them in the BM25
     * statistics until their segment is merged, so the relevance order depends on merges (see PORTING.md)
     */
    fun searchAllSorted(entities: List<LuceneEntity> = LuceneEntity.entries): List<Any?> =
      entities.map { entity -> listOf(entity, queries.map { q -> helper.searchEntitiesIds(q, entity)?.sorted() }) }
  }
}
