package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadataAggregation
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.springframework.data.domain.Pageable
import java.net.URL
import java.time.LocalDate
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SeriesMetadataLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.seriesMetadataLifecycle

  private val dir by lazy { tempDir.resolve("lib").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun comicInfo(body: String) = t("ComicInfo.xml", "<?xml version=\"1.0\"?><ComicInfo>$body</ComicInfo>")

  private fun addSeries(
    id: String,
    oneshot: Boolean = false,
  ) {
    dir.resolve(id).createDirectories()
    db.seriesDao.insert(series(id, "L1", URL("file:$dir/$id")).copy(oneshot = oneshot))
    db.seriesMetadataDao.insert(SeriesMetadata(title = "series $id", seriesId = id, createdDate = date))
    db.bookMetadataAggregationDao.insert(BookMetadataAggregation(seriesId = id, createdDate = date))
  }

  private fun addBook(
    id: String,
    seriesId: String,
    info: String?,
  ) {
    val entries = listOfNotNull("p1.png" to png, info?.let { comicInfo(it) })
    val bk = book(id, seriesId, "L1", url = zipFile(dir.resolve(seriesId), "$id.cbz", entries))
    db.bookDao.insert(bk)
    db.mediaDao.insert(Media(bookId = id, createdDate = date))
    db.bookMetadataDao.insert(metadata(bk))
    graph.bookLifecycle.analyzeAndPersist(bk)
  }

  private fun s(id: String) = db.seriesDao.findByIdOrNull(id)!!

  private fun state(id: String) =
    attempt(dir) {
      listOf(
        db.seriesMetadataDao.findById(id).let {
          listOf(it.status, it.title, it.titleSort, it.summary, it.readingDirection, it.publisher, it.ageRating, it.language, it.genres, it.totalBookCount)
        },
        db.seriesCollectionDao.findAll(SearchContext.empty(), Pageable.unpaged()).content.map { listOf(it.name, it.seriesIds) },
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  private fun lib(block: (Library) -> Library) = db.libraryDao.update(block(db.libraryDao.findById("L1")))

  override fun cases() {
    func("refreshMetadata") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$dir")))
        addSeries("S1")
        addBook("B1", "S1", "<Series>Batman</Series><Volume>2016</Volume><Manga>YesAndRightToLeft</Manga><Publisher>DC</Publisher><AgeRating>Teen</AgeRating><LanguageISO>en</LanguageISO><Genre>Action, Hero</Genre><Count>10</Count><SeriesGroup>Group A, Group B</SeriesGroup>")
        addBook("B2", "S1", "<Series>Batman</Series><Volume>2016</Volume><Manga>No</Manga><Publisher>Marvel</Publisher><AgeRating>Mature 17+</AgeRating><LanguageISO>fr</LanguageISO><Genre>Drama</Genre><Count>12</Count><SeriesGroup>Group A</SeriesGroup>")
        addBook("B3", "S1", "<Series>Robin</Series><Publisher>DC</Publisher><LanguageISO>not a language tag!</LanguageISO>")
        addBook("B4", "S1", null)
        addSeries("S2")
        dir.resolve("S2/series.json").writeText(
          """{"version":"1.0.2","metadata":{"type":"comicSeries","publisher":"DC Comics","imprint":null,"name":"Batman","comicid":"1","year":2016,"description_text":"text","description_formatted":"formatted","volume":3,"booktype":"Print","age_rating":"15+","comic_image":"x","total_issues":100,"publication_run":"x","status":"Ended"}}""",
        )
        addBook("B5", "S2", "<Series>Other</Series>")
        addSeries("S3", oneshot = true)
        addBook("B6", "S3", "<Title>One shot title</Title><Summary>one shot summary</Summary><Series>OS</Series>")
        db.bookMetadataDao.update(db.bookMetadataDao.findById("B6").copy(title = "One shot title", summary = "one shot summary"))
        addSeries("S4")
        dir.resolve("S4/series.json").writeText("{ not json")
        graph.takeEvents()
        true
      }
      case("comic info, defaults") {
        lifecycle.refreshMetadata(s("S1"))
        state("S1")
      }
      case("comic info, append volume disabled") {
        lib { it.copy(importComicInfoSeriesAppendVolume = false) }
        lifecycle.refreshMetadata(s("S1"))
        state("S1")
      }
      case("locked fields") {
        db.seriesMetadataDao.update(db.seriesMetadataDao.findById("S1").copy(title = "locked", titleLock = true, genresLock = true, publisherLock = true))
        lifecycle.refreshMetadata(s("S1"))
        state("S1")
      }
      case("mylar and comic info") {
        lifecycle.refreshMetadata(s("S2"))
        state("S2")
      }
      case("oneshot") {
        lifecycle.refreshMetadata(s("S3"))
        state("S3")
      }
      case("invalid mylar json") {
        lifecycle.refreshMetadata(s("S4"))
        state("S4")
      }
      case("series import disabled, collections enabled") {
        lib { it.copy(importComicInfoSeries = false, importMylarSeries = false) }
        db.seriesMetadataDao.update(SeriesMetadata(title = "series S1", seriesId = "S1", createdDate = date))
        lifecycle.refreshMetadata(s("S1"))
        state("S1")
      }
      case("everything disabled but oneshot") {
        lib { it.copy(importComicInfoCollection = false, importEpubSeries = false) }
        listOf(
          lifecycle.refreshMetadata(s("S1")).let { state("S1") },
          lifecycle.refreshMetadata(s("S2")).let { state("S2") },
        )
      }
      case("unknown library") { exceptionType { lifecycle.refreshMetadata(s("S1").copy(libraryId = "L9")) } }
    }
    func("handlePatchForSeriesMetadata@102") {
      case("most frequent values, max numbers") {
        lib { it.copy(importComicInfoSeries = true) }
        db.seriesMetadataDao.update(SeriesMetadata(title = "series S1", seriesId = "S1", createdDate = date))
        lifecycle.refreshMetadata(s("S1"))
        state("S1")
      }
      case("no patch at all") {
        addSeries("S5")
        lifecycle.refreshMetadata(s("S5"))
        state("S5")
      }
    }
    func("handlePatchForSeriesMetadata@129") {
      case("single patch from mylar") {
        lib { it.copy(importMylarSeries = true, importComicInfoSeries = false) }
        lifecycle.refreshMetadata(s("S2"))
        state("S2")
      }
      case("null patch") {
        lifecycle.refreshMetadata(s("S5"))
        state("S5")
      }
    }
    func("aggregateMetadata") {
      case("books metadata") {
        db.bookMetadataDao.update(
          db.bookMetadataDao.findById("B1").copy(summary = "first", releaseDate = LocalDate.of(2010, 1, 1), authors = listOf(Author("A", "writer")), tags = setOf("x")),
        )
        db.bookMetadataDao.update(
          db.bookMetadataDao.findById("B2").copy(summary = "second", releaseDate = LocalDate.of(2005, 6, 1), authors = listOf(Author("A", "writer"), Author("B", "penciller")), tags = setOf("y")),
        )
        lifecycle.aggregateMetadata(s("S1"))
        stable(listOf(db.bookMetadataAggregationDao.findById("S1"), graph.takeEvents().map { it.javaClass.simpleName }))
      }
      case("series without books") {
        lifecycle.aggregateMetadata(s("S5"))
        stable(listOf(db.bookMetadataAggregationDao.findById("S5"), graph.takeEvents().map { it.javaClass.simpleName }))
      }
      case("unknown series") {
        lifecycle.aggregateMetadata(series("S9", "L1"))
        stable(graph.takeEvents().map { it.javaClass.simpleName })
      }
    }
  }
}
