package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.DuplicateNameException
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.ThumbnailSeriesCollection
import org.gotson.komga.domain.service.SeriesCollectionLifecycle
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.interfaces.api.rest.SeriesCollectionController
import org.gotson.komga.interfaces.api.rest.dto.CollectionCreationDto
import org.gotson.komga.interfaces.api.rest.dto.CollectionUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.PNG
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.entity
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.apache.tika.config.TikaConfig
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile

class SeriesCollectionControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls (same fake in the TypeScript twin) */
  private val lifecycle =
    mockk<SeriesCollectionLifecycle> {
      every { getThumbnailBytes(any(), any()) } answers {
        calls.add("getThumbnailBytes", firstArg<SeriesCollection>().id, secondArg<String>())
        byteArrayOf(7, 7)
      }
      every { addThumbnail(any()) } answers {
        calls.add("addThumbnail", firstArg<ThumbnailSeriesCollection>())
        firstArg()
      }
      every { markSelectedThumbnail(any()) } answers { calls.add("markSelectedThumbnail", firstArg<ThumbnailSeriesCollection>().id) }
      every { deleteThumbnail(any()) } answers { calls.add("deleteThumbnail", firstArg<ThumbnailSeriesCollection>().id) }
      every { addCollection(any()) } answers {
        val c = firstArg<SeriesCollection>()
        calls.add("addCollection", c)
        if (c.name == "dup") throw DuplicateNameException("Collection name already exists", "ERR_1005")
        c
      }
      every { updateCollection(any()) } answers {
        val c = firstArg<SeriesCollection>()
        calls.add("updateCollection", c)
        if (c.name == "dup") throw DuplicateNameException("Collection name already exists", "ERR_1005")
      }
      every { deleteCollection(any()) } answers { calls.add("deleteCollection", firstArg<SeriesCollection>().id) }
    }
  private val c =
    SeriesCollectionController(
      db.seriesCollectionDao,
      lifecycle,
      db.seriesDtoDao,
      ContentDetector(TikaConfig()),
      ImageAnalyzer(),
      db.thumbnailSeriesCollectionDao,
    ) { calls.add("publishEvent", it) }
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val noAdult = principal(RestSamples.noAdult)
  private val p20 = PageRequest.of(0, 20)

  private fun thumb(
    id: String,
    collectionId: String,
    selected: Boolean,
  ) = ThumbnailSeriesCollection(
    thumbnail = byteArrayOf(1, 2, id.length.toByte()),
    selected = selected,
    type = ThumbnailSeriesCollection.Type.USER_UPLOADED,
    mediaType = "image/png",
    fileSize = 3,
    dimension = Dimension(3, 2),
    id = id,
    collectionId = collectionId,
    createdDate = FIXED,
  )

  private fun series(
    id: String,
    p: org.gotson.komga.infrastructure.security.KomgaPrincipal,
    libraryIds: List<String>? = null,
    status: List<SeriesMetadata.Status>? = null,
    readStatus: List<ReadStatus>? = null,
    publishers: List<String>? = null,
    languages: List<String>? = null,
    genres: List<String>? = null,
    tags: List<String>? = null,
    ageRatings: List<String>? = null,
    releaseYears: List<String>? = null,
    deleted: Boolean? = null,
    complete: Boolean? = null,
    unpaged: Boolean = false,
    authors: List<Author>? = null,
    page: org.springframework.data.domain.Pageable = p20,
  ) = c.getSeriesByCollectionId(id, p, libraryIds, status, readStatus, publishers, languages, genres, tags, ageRatings, releaseYears, deleted, complete, unpaged, authors, page)

  override fun cases() {
    func("getCollections") {
      case("empty") { c.getCollections(admin, null, null, false, p20) }
      case("admin, default sort by name") {
        RestSamples.seed(db)
        db.thumbnailSeriesCollectionDao.insert(thumb("TC1", "C1", true))
        db.thumbnailSeriesCollectionDao.insert(thumb("TC2", "C1", false))
        db.thumbnailSeriesCollectionDao.insert(thumb("TC3", "C2", true))
        c.getCollections(admin, null, null, false, p20)
      }
      case("sorted by name desc") { c.getCollections(admin, null, null, false, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("name")))) }
      case("paged") { c.getCollections(admin, null, null, false, PageRequest.of(1, 1)) }
      case("unpaged") { c.getCollections(admin, null, null, true, PageRequest.of(1, 1)) }
      case("library filter") { c.getCollections(admin, null, listOf("L2"), false, p20) }
      case("library restricted") { c.getCollections(l1, null, null, false, p20) }
      case("library restricted asks L2") { c.getCollections(l1, null, listOf("L2"), false, p20) }
      case("age restricted") { c.getCollections(kids, null, null, false, p20) }
      case("label restricted") { c.getCollections(noAdult, null, null, false, p20) }
      case("blank search") { c.getCollections(admin, " ", null, false, p20) }
    }
    func("getCollectionById") {
      case("admin") { c.getCollectionById(admin, "C1") }
      case("filtered for kids") { c.getCollectionById(kids, "C1") }
      case("not visible") { c.getCollectionById(l1, "C2") }
      case("unknown") { c.getCollectionById(admin, "CX") }
    }
    func("getCollectionThumbnail") {
      case("ok") { listOf(entity(c.getCollectionThumbnail(all, "C1")), calls.take()) }
      case("not found") { listOf(exceptionType { c.getCollectionThumbnail(l1, "C2") }, calls.take()) }
    }
    func("getCollectionThumbnailById") {
      case("ok") { c.getCollectionThumbnailById(admin, "C1", "TC2") }
      case("thumbnail of other collection") { c.getCollectionThumbnailById(admin, "C1", "TC3") }
      case("unknown thumbnail") { c.getCollectionThumbnailById(admin, "C1", "TX") }
      case("collection not visible") { c.getCollectionThumbnailById(l1, "C2", "TC3") }
    }
    func("getCollectionThumbnails") {
      case("C1") { c.getCollectionThumbnails(admin, "C1") }
      case("C2") { c.getCollectionThumbnails(all, "C2") }
      case("not visible") { c.getCollectionThumbnails(l1, "C2") }
    }
    func("addUserUploadedCollectionThumbnail") {
      case("png") { listOf(stable(c.addUserUploadedCollectionThumbnail(admin, "C1", MockMultipartFile("file", "a.png", "image/png", PNG))), calls.take()) }
      case("not selected") { listOf(stable(c.addUserUploadedCollectionThumbnail(admin, "C2", MockMultipartFile("file", "a.png", null, PNG), false)), calls.take()) }
      case("not an image") { listOf(exceptionType { c.addUserUploadedCollectionThumbnail(admin, "C1", MockMultipartFile("file", "a.txt", "text/plain", "hello".toByteArray())) }, calls.take()) }
      case("empty file") { c.addUserUploadedCollectionThumbnail(admin, "C1", MockMultipartFile("file", ByteArray(0))) }
      case("unknown collection") { c.addUserUploadedCollectionThumbnail(admin, "CX", MockMultipartFile("file", "a.png", "image/png", PNG)) }
    }
    func("markCollectionThumbnailSelected") {
      case("ok") {
        c.markCollectionThumbnailSelected(admin, "C1", "TC2")
        calls.take()
      }
      case("other collection") { listOf(exceptionType { c.markCollectionThumbnailSelected(admin, "C1", "TC3") }, calls.take()) }
      case("unknown thumbnail is ignored") { listOf(c.markCollectionThumbnailSelected(admin, "C1", "TX"), calls.take()) }
      case("unknown collection") { c.markCollectionThumbnailSelected(admin, "CX", "TC1") }
    }
    func("deleteUserUploadedCollectionThumbnail") {
      case("ok") {
        c.deleteUserUploadedCollectionThumbnail(admin, "C1", "TC1")
        calls.take()
      }
      case("other collection") { c.deleteUserUploadedCollectionThumbnail(admin, "C2", "TC1") }
      case("unknown thumbnail") { c.deleteUserUploadedCollectionThumbnail(admin, "C1", "TX") }
      case("unknown collection") { c.deleteUserUploadedCollectionThumbnail(admin, "CX", "TC1") }
    }
    func("createCollection") {
      case("ok") { listOf(stable(c.createCollection(CollectionCreationDto("New", true, listOf("S2", "S1")))), calls.take()) }
      case("duplicate") { listOf(RestOracle.thrown { c.createCollection(CollectionCreationDto("dup", false, emptyList())) }, calls.take()) }
    }
    func("updateCollectionById") {
      case("no change") {
        c.updateCollectionById(admin, "C1", CollectionUpdateDto(null, null, null))
        calls.take()
      }
      case("all fields") {
        c.updateCollectionById(admin, "C1", CollectionUpdateDto("Renamed", false, listOf("S1")))
        calls.take()
      }
      case("filtered for kids") {
        c.updateCollectionById(kids, "C1", CollectionUpdateDto("K", null, null))
        calls.take()
      }
      case("duplicate") { listOf(RestOracle.thrown { c.updateCollectionById(admin, "C2", CollectionUpdateDto("dup", null, null)) }, calls.take()) }
      case("not found") { c.updateCollectionById(l1, "C2", CollectionUpdateDto("x", null, null)) }
    }
    func("deleteCollectionById") {
      case("ok") {
        c.deleteCollectionById(admin, "C2")
        calls.take()
      }
      case("not found") { c.deleteCollectionById(admin, "CX") }
    }
    func("getSeriesByCollectionId") {
      case("ordered collection, admin") { series("C1", admin) }
      case("unordered collection, user url restricted") { series("C2", all) }
      case("paged") { series("C1", admin, page = PageRequest.of(1, 1)) }
      case("unpaged") { series("C1", admin, unpaged = true, page = PageRequest.of(1, 1)) }
      case("kids") { series("C1", kids) }
      case("library filter") { series("C1", admin, libraryIds = listOf("L2")) }
      case("status") { series("C1", admin, status = listOf(SeriesMetadata.Status.ONGOING, SeriesMetadata.Status.ENDED)) }
      case("read status") { series("C1", all, readStatus = listOf(ReadStatus.IN_PROGRESS)) }
      case("publisher") { series("C1", admin, publishers = listOf("pub2")) }
      case("language") { series("C1", admin, languages = listOf("EN")) }
      case("genre and tag") { series("C1", admin, genres = listOf("drama"), tags = listOf("t1")) }
      case("age ratings, invalid is null") { series("C1", admin, ageRatings = listOf("16", "x")) }
      case("release years, invalid ignored") { series("C1", admin, releaseYears = listOf("2019", "abc")) }
      case("deleted and complete") { series("C1", admin, deleted = false, complete = false) }
      case("deleted true") { series("C1", admin, deleted = true) }
      case("authors") { series("C1", admin, authors = listOf(Author("Author S2", "writer"))) }
      case("not visible") { series("C2", l1) }
    }
  }
}
