package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.MarkSelectedPreference
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.SeriesSearch
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.domain.service.SeriesLifecycle
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.ContentRestrictionChecker
import org.gotson.komga.interfaces.api.rest.SeriesController
import org.gotson.komga.interfaces.api.rest.dto.TachiyomiReadProgressUpdateV2Dto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.PNG
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import java.net.URL
import kotlin.io.path.writeBytes

@Suppress("DEPRECATION")
class SeriesControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls (same fakes in the TypeScript twin) */
  private val seriesLifecycle =
    mockk<SeriesLifecycle> {
      every { getThumbnailBytes(any(), any()) } answers {
        calls.add("getThumbnailBytes", firstArg<String>(), secondArg<String>())
        if (firstArg<String>() == "S3") null else byteArrayOf(9)
      }
      every { getThumbnailBytesByThumbnailId(any()) } answers {
        calls.add("getThumbnailBytesByThumbnailId", firstArg<String>())
        if (firstArg<String>() == "TS2") null else byteArrayOf(8)
      }
      every { addThumbnailForSeries(any(), any()) } answers {
        calls.add("addThumbnailForSeries", firstArg<ThumbnailSeries>(), secondArg<MarkSelectedPreference>())
        firstArg()
      }
      every { deleteThumbnailForSeries(any()) } answers {
        calls.add("deleteThumbnailForSeries", firstArg<ThumbnailSeries>().id)
        if (firstArg<ThumbnailSeries>().selected) throw IllegalArgumentException("selected thumbnail cannot be deleted")
      }
      every { markReadProgressCompleted(any(), any()) } answers { calls.add("markReadProgressCompleted", firstArg<String>(), secondArg<KomgaUser>().id) }
      every { deleteReadProgress(any(), any()) } answers { calls.add("deleteReadProgress", firstArg<String>(), secondArg<KomgaUser>().id) }
    }
  private val bookLifecycle =
    mockk<BookLifecycle> {
      every { markReadProgressCompleted(any(), any()) } answers { calls.add("markReadProgressCompleted", firstArg<String>(), secondArg<KomgaUser>().id) }
    }
  private val c =
    SeriesController(
      taskEmitter(db, calls),
      db.seriesDao,
      seriesLifecycle,
      db.seriesMetadataDao,
      db.seriesDtoDao,
      bookLifecycle,
      db.bookDao,
      db.bookDtoDao,
      db.seriesCollectionDao,
      db.readProgressDtoDao,
      { calls.add("publishEvent", it) },
      ContentDetector(TikaConfig()),
      ImageAnalyzer(),
      db.thumbnailSeriesDao,
      ContentRestrictionChecker(db.seriesMetadataDao, db.bookDao, db.thumbnailBookDao, db.seriesDao, db.thumbnailSeriesDao),
    )
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val noAdult = principal(RestSamples.noAdult)
  private val p20 = PageRequest.of(0, 20)

  private fun thumb(
    id: String,
    seriesId: String,
    selected: Boolean,
  ) = ThumbnailSeries(
    thumbnail = byteArrayOf(4, id.length.toByte()),
    selected = selected,
    type = ThumbnailSeries.Type.USER_UPLOADED,
    mediaType = "image/png",
    fileSize = 2,
    dimension = Dimension(3, 2),
    id = id,
    seriesId = seriesId,
    createdDate = FIXED,
  )

  private fun search(json: String) = read<SeriesSearch>(json)

  private fun deprecated(
    p: KomgaPrincipal,
    searchTerm: String? = null,
    searchRegex: Pair<String, String>? = null,
    libraryIds: List<String>? = null,
    collectionIds: List<String>? = null,
    status: List<SeriesMetadata.Status>? = null,
    readStatus: List<ReadStatus>? = null,
    publishers: List<String>? = null,
    languages: List<String>? = null,
    genres: List<String>? = null,
    tags: List<String>? = null,
    ageRatings: List<String>? = null,
    releaseYears: List<String>? = null,
    sharingLabels: List<String>? = null,
    deleted: Boolean? = null,
    complete: Boolean? = null,
    oneshot: Boolean? = null,
    unpaged: Boolean = false,
    authors: List<Author>? = null,
    page: Pageable = p20,
    groups: Boolean = false,
  ): Any =
    if (groups) {
      c.getSeriesAlphabeticalGroupsDeprecated(
        p, searchTerm, searchRegex, libraryIds, collectionIds, status, readStatus, publishers, languages, genres, tags, ageRatings, releaseYears,
        sharingLabels, deleted, complete, oneshot, authors, page,
      )
    } else {
      c.getSeriesDeprecated(
        p, searchTerm, searchRegex, libraryIds, collectionIds, status, readStatus, publishers, languages, genres, tags, ageRatings, releaseYears,
        sharingLabels, deleted, complete, oneshot, unpaged, authors, page,
      )
    }

  override fun cases() {
    func("getSeriesDeprecated") {
      case("empty") { deprecated(admin) }
      case("admin, unsorted") {
        RestSamples.seed(db)
        db.thumbnailSeriesDao.insert(thumb("TS1", "S1", true))
        db.thumbnailSeriesDao.insert(thumb("TS2", "S1", false))
        db.thumbnailSeriesDao.insert(thumb("TS3", "S2", true))
        deprecated(admin)
      }
      case("user, sorted by title desc") { deprecated(all, page = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("metadata.titleSort")))) }
      case("paged") { deprecated(admin, page = PageRequest.of(1, 2, Sort.by("name"))) }
      case("unpaged") { deprecated(admin, unpaged = true, page = PageRequest.of(1, 1, Sort.by("name"))) }
      case("regex title") { deprecated(admin, searchRegex = "^[ab]" to "TITLE") }
      case("regex title_sort") { deprecated(admin, searchRegex = "a$" to "title_sort") }
      case("regex unknown field") { deprecated(admin, searchRegex = "x" to "other") }
      case("libraries and collections") { deprecated(admin, libraryIds = listOf("L1"), collectionIds = listOf("C1", "C2")) }
      case("metadata filters") {
        deprecated(admin, status = listOf(SeriesMetadata.Status.ONGOING), publishers = listOf("Pub1", "pub2"), languages = listOf("en"), genres = listOf("action"), tags = listOf("t1"))
      }
      case("read status") { deprecated(all, readStatus = listOf(ReadStatus.IN_PROGRESS)) }
      case("authors") { deprecated(admin, authors = listOf(Author("Author S3", "writer"))) }
      case("age ratings and years") { deprecated(admin, ageRatings = listOf("10", "none"), releaseYears = listOf("2019", "x")) }
      case("sharing labels") { deprecated(admin, sharingLabels = listOf("kids")) }
      case("flags") { deprecated(admin, oneshot = false, complete = false, deleted = false) }
      case("oneshot true") { deprecated(admin, oneshot = true) }
      case("restricted users") { listOf(deprecated(l1), deprecated(kids), deprecated(noAdult)) }
    }
    func("getSeries") {
      case("empty search") { c.getSeries(admin, search("{}"), false, p20) }
      case("condition") { c.getSeries(all, search("""{"condition":{"libraryId":{"operator":"is","value":"L1"}}}"""), false, PageRequest.of(0, 1, Sort.by("metadata.titleSort"))) }
      case("anyOf") {
        c.getSeries(admin, search("""{"condition":{"anyOf":[{"libraryId":{"operator":"is","value":"L2"}},{"title":{"operator":"beginsWith","value":"al"}}]}}"""), false, p20)
      }
      case("unpaged") { c.getSeries(kids, search("{}"), true, PageRequest.of(2, 1)) }
    }
    func("getSeriesAlphabeticalGroupsDeprecated") {
      case("admin") { deprecated(admin, groups = true) }
      case("filters") { deprecated(admin, libraryIds = listOf("L1"), groups = true) }
      case("kids") { deprecated(kids, groups = true) }
      case("regex") { deprecated(admin, searchRegex = "^O" to "title", groups = true) }
    }
    func("getSeriesAlphabeticalGroups") {
      case("admin") { c.getSeriesAlphabeticalGroups(admin, search("{}")) }
      case("condition") { c.getSeriesAlphabeticalGroups(l1, search("""{"condition":{"deleted":{"operator":"isFalse"}}}""")) }
    }
    func("getSeriesLatest") {
      case("admin") { c.getSeriesLatest(admin, null, null, null, false, p20) }
      case("filters") { c.getSeriesLatest(all, listOf("L1"), false, false, false, p20) }
      case("unpaged") { c.getSeriesLatest(kids, null, null, true, true, PageRequest.of(3, 1)) }
    }
    func("getSeriesNew") {
      case("admin") { c.getSeriesNew(admin, null, null, null, false, p20) }
      case("filters") { c.getSeriesNew(all, listOf("L2"), false, null, false, PageRequest.of(0, 1)) }
      case("deleted") { c.getSeriesNew(admin, null, true, null, false, p20) }
    }
    func("getSeriesUpdated") {
      case("admin") { c.getSeriesUpdated(admin, null, null, null, false, p20) }
      case("filters") { c.getSeriesUpdated(all, listOf("L1"), false, false, true, p20) }
    }
    func("getSeriesById") {
      case("admin") { c.getSeriesById(admin, "S1") }
      case("user") { c.getSeriesById(all, "S3") }
      case("with read progress") { c.getSeriesById(all, "S1") }
      case("library restricted") { c.getSeriesById(l1, "S3") }
      case("age restricted") { c.getSeriesById(kids, "S2") }
      case("unknown") { c.getSeriesById(admin, "SX") }
    }
    func("getSeriesThumbnail") {
      case("ok") { listOf(c.getSeriesThumbnail(all, "S1"), calls.take()) }
      case("none") { listOf(RestOracle.thrown { c.getSeriesThumbnail(admin, "S3") }, calls.take()) }
      case("restricted") { listOf(RestOracle.thrown { c.getSeriesThumbnail(kids, "S2") }, calls.take()) }
      case("unknown series") { listOf(RestOracle.thrown { c.getSeriesThumbnail(admin, "SX") }, calls.take()) }
    }
    func("getSeriesThumbnailById") {
      case("ok") { listOf(c.getSeriesThumbnailById(admin, "S1", "TS1"), calls.take()) }
      case("no bytes") { listOf(RestOracle.thrown { c.getSeriesThumbnailById(admin, "S1", "TS2") }, calls.take()) }
      case("thumbnail restricted") { listOf(RestOracle.thrown { c.getSeriesThumbnailById(kids, "S1", "TS3") }, calls.take()) }
      case("unknown thumbnail") { listOf(RestOracle.thrown { c.getSeriesThumbnailById(admin, "S1", "TX") }, calls.take()) }
    }
    func("getSeriesThumbnails") {
      case("S1") { c.getSeriesThumbnails(admin, "S1") }
      case("none") { c.getSeriesThumbnails(admin, "S3") }
      case("restricted") { c.getSeriesThumbnails(l1, "S3") }
    }
    func("addUserUploadedSeriesThumbnail") {
      case("selected") { listOf(stable(c.addUserUploadedSeriesThumbnail("S1", MockMultipartFile("file", "a.png", "image/png", PNG))), calls.take()) }
      case("not selected") { listOf(stable(c.addUserUploadedSeriesThumbnail("S2", MockMultipartFile("file", PNG), false)), calls.take()) }
      case("oneshot") { c.addUserUploadedSeriesThumbnail("S3", MockMultipartFile("file", PNG)) }
      case("not an image") { c.addUserUploadedSeriesThumbnail("S1", MockMultipartFile("file", "abc".toByteArray())) }
      case("unknown series") { c.addUserUploadedSeriesThumbnail("SX", MockMultipartFile("file", PNG)) }
    }
    func("markSeriesThumbnailSelected") {
      case("ok") {
        c.markSeriesThumbnailSelected("S1", "TS2")
        listOf(calls.take(), db.thumbnailSeriesDao.findAllBySeriesId("S1").map { listOf(it.id, it.selected) })
      }
      case("other series") { c.markSeriesThumbnailSelected("S2", "TS1") }
      case("unknown thumbnail") { c.markSeriesThumbnailSelected("S1", "TX") }
      case("unknown series") { c.markSeriesThumbnailSelected("SX", "TS1") }
    }
    func("deleteUserUploadedSeriesThumbnail") {
      case("ok") {
        c.deleteUserUploadedSeriesThumbnail("S1", "TS1")
        calls.take()
      }
      case("illegal argument") { listOf(RestOracle.thrown { c.deleteUserUploadedSeriesThumbnail("S1", "TS2") }, calls.take()) }
      case("other series") { c.deleteUserUploadedSeriesThumbnail("S2", "TS1") }
      case("unknown thumbnail") { c.deleteUserUploadedSeriesThumbnail("S1", "TX") }
      case("unknown series") { c.deleteUserUploadedSeriesThumbnail("SX", "TS1") }
    }
    func("getBooksBySeriesId") {
      case("admin") { c.getBooksBySeriesId(admin, "S1", page = p20) }
      case("user sorted desc") { c.getBooksBySeriesId(all, "S1", page = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("metadata.numberSort")))) }
      case("paged") { c.getBooksBySeriesId(admin, "S1", page = PageRequest.of(1, 1)) }
      case("unpaged") { c.getBooksBySeriesId(admin, "S1", unpaged = true, page = PageRequest.of(1, 1)) }
      case("filters") {
        c.getBooksBySeriesId(all, "S1", listOf(Media.Status.READY), listOf(ReadStatus.UNREAD, ReadStatus.IN_PROGRESS), listOf("bt2"), false, false, listOf(Author("Pen B2", "penciller")), p20)
      }
      case("restricted") { c.getBooksBySeriesId(kids, "S2", page = p20) }
      case("unknown series") { c.getBooksBySeriesId(admin, "SX", page = p20) }
    }
    func("getCollectionsBySeriesId") {
      case("S1") { c.getCollectionsBySeriesId(admin, "S1") }
      case("S3") { c.getCollectionsBySeriesId(all, "S3") }
      case("kids") { c.getCollectionsBySeriesId(kids, "S1") }
      case("restricted") { c.getCollectionsBySeriesId(l1, "S3") }
    }
    func("seriesAnalyze") {
      case("S1") {
        c.seriesAnalyze("S1")
        listOf(tasks(db), calls.take())
      }
      case("unknown") {
        c.seriesAnalyze("SX")
        listOf(tasks(db), calls.take())
      }
    }
    func("seriesRefreshMetadata") {
      case("S1") {
        c.seriesRefreshMetadata("S1")
        listOf(tasks(db), calls.take())
      }
      case("unknown") {
        c.seriesRefreshMetadata("SX")
        listOf(tasks(db), calls.take())
      }
    }
    func("markSeriesAsRead") {
      case("ok") {
        c.markSeriesAsRead("S1", all)
        calls.take()
      }
      case("restricted") { listOf(RestOracle.thrown { c.markSeriesAsRead("S2", kids) }, calls.take()) }
    }
    func("markSeriesAsUnread") {
      case("ok") {
        c.markSeriesAsUnread("S2", all)
        calls.take()
      }
      case("restricted") { listOf(RestOracle.thrown { c.markSeriesAsUnread("S2", noAdult) }, calls.take()) }
    }
    func("getMihonReadProgressBySeriesId") {
      case("with progress") { c.getMihonReadProgressBySeriesId("S1", all) }
      case("no progress") { c.getMihonReadProgressBySeriesId("S2", l1) }
      case("restricted") { c.getMihonReadProgressBySeriesId("S3", l1) }
    }
    func("updateMihonReadProgressBySeriesId") {
      case("up to 1") {
        c.updateMihonReadProgressBySeriesId("S1", TachiyomiReadProgressUpdateV2Dto(1f), all)
        calls.take()
      }
      case("up to 2.5") {
        c.updateMihonReadProgressBySeriesId("S1", TachiyomiReadProgressUpdateV2Dto(2.5f), all)
        calls.take()
      }
      case("user without progress") {
        c.updateMihonReadProgressBySeriesId("S1", TachiyomiReadProgressUpdateV2Dto(1.5f), l1)
        calls.take()
      }
      case("restricted") { listOf(RestOracle.thrown { c.updateMihonReadProgressBySeriesId("S2", TachiyomiReadProgressUpdateV2Dto(1f), kids) }, calls.take()) }
    }
    func("downloadSeriesAsZip") {
      case("existing and missing files") {
        val f = tempDir.resolve("series-file.cbz").also { it.writeBytes(oracleBytes(2000)) }
        db.bookDao.insert(Book(name = "file", url = URL("file:$f"), fileLastModified = FIXED, id = "B6", seriesId = "S2", libraryId = "L1", createdDate = FIXED))
        val e = c.downloadSeriesAsZip(admin, "S2")
        listOf(e.statusCode.value(), e.headers.getFirst("Content-Disposition"), e.headers.getFirst("Content-Type"), RestOracle.zipSummary(RestOracle.bodyBytes(e)))
      }
      case("no file") {
        val e = c.downloadSeriesAsZip(all, "S3")
        listOf(e.headers.getFirst("Content-Disposition"), RestOracle.zipSummary(RestOracle.bodyBytes(e)))
      }
      case("restricted") { c.downloadSeriesAsZip(kids, "S2") }
    }
    func("deleteSeriesFile") {
      case("S1") {
        c.deleteSeriesFile("S1")
        listOf(tasks(db), calls.take())
      }
      case("unknown") {
        c.deleteSeriesFile("SX")
        tasks(db)
      }
    }
  }
}
