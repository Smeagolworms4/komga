package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.ComicRackListException
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.DuplicateNameException
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadListMatch
import org.gotson.komga.domain.model.ReadListRequestMatch
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.ThumbnailReadList
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.domain.service.ReadListLifecycle
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.rest.ReadListController
import org.gotson.komga.interfaces.api.rest.dto.ReadListCreationDto
import org.gotson.komga.interfaces.api.rest.dto.ReadListUpdateDto
import org.gotson.komga.interfaces.api.rest.dto.TachiyomiReadProgressUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.PNG
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.entity
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import java.net.URL
import kotlin.io.path.writeBytes

class ReadListControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls (same fakes in the TypeScript twin) */
  private val lifecycle =
    mockk<ReadListLifecycle> {
      every { getThumbnailBytes(any()) } answers {
        calls.add("getThumbnailBytes", firstArg<ReadList>().id)
        byteArrayOf(5)
      }
      every { addThumbnail(any()) } answers {
        calls.add("addThumbnail", firstArg<ThumbnailReadList>())
        firstArg()
      }
      every { markSelectedThumbnail(any()) } answers { calls.add("markSelectedThumbnail", firstArg<ThumbnailReadList>().id) }
      every { deleteThumbnail(any()) } answers { calls.add("deleteThumbnail", firstArg<ThumbnailReadList>().id) }
      every { addReadList(any()) } answers {
        val r = firstArg<ReadList>()
        calls.add("addReadList", r)
        if (r.name == "dup") throw DuplicateNameException("Read list name already exists", "ERR_1009")
        r
      }
      every { updateReadList(any()) } answers {
        val r = firstArg<ReadList>()
        calls.add("updateReadList", r)
        if (r.name == "dup") throw DuplicateNameException("Read list name already exists", "ERR_1009")
      }
      every { deleteReadList(any()) } answers { calls.add("deleteReadList", firstArg<ReadList>().id) }
      every { matchComicRackList(any()) } answers {
        val bytes = firstArg<ByteArray>()
        calls.add("matchComicRackList", bytes)
        if (bytes.isEmpty()) throw ComicRackListException("empty", "ERR_1029")
        ReadListRequestMatch(ReadListMatch(String(bytes), ""), emptyList())
      }
    }
  private val bookLifecycle =
    mockk<BookLifecycle> {
      every { markReadProgressCompleted(any(), any()) } answers { calls.add("markReadProgressCompleted", firstArg<String>(), secondArg<KomgaUser>().id) }
    }
  private val c =
    ReadListController(
      db.readListDao,
      lifecycle,
      db.bookDtoDao,
      db.bookDao,
      db.readProgressDtoDao,
      db.thumbnailReadListDao,
      ContentDetector(TikaConfig()),
      ImageAnalyzer(),
      bookLifecycle,
    ) { calls.add("publishEvent", it) }
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)
  private val l1 = principal(RestSamples.l1Only)
  private val kids = principal(RestSamples.kids)
  private val p20 = PageRequest.of(0, 20)

  private fun thumb(
    id: String,
    readListId: String,
    selected: Boolean,
  ) = ThumbnailReadList(
    thumbnail = byteArrayOf(3, id.length.toByte()),
    selected = selected,
    type = ThumbnailReadList.Type.USER_UPLOADED,
    mediaType = "image/png",
    fileSize = 2,
    dimension = Dimension(3, 2),
    id = id,
    readListId = readListId,
    createdDate = FIXED,
  )

  private fun books(
    id: String,
    p: KomgaPrincipal,
    libraryIds: List<String>? = null,
    readStatus: List<ReadStatus>? = null,
    tags: List<String>? = null,
    mediaStatus: List<Media.Status>? = null,
    deleted: Boolean? = null,
    unpaged: Boolean = false,
    authors: List<Author>? = null,
    page: Pageable = p20,
  ) = c.getBooksByReadListId(id, p, libraryIds, readStatus, tags, mediaStatus, deleted, unpaged, authors, page)

  override fun cases() {
    func("getReadLists") {
      case("empty") { c.getReadLists(admin, null, null, false, p20) }
      case("admin") {
        RestSamples.seed(db)
        db.thumbnailReadListDao.insert(thumb("TR1", "R1", true))
        db.thumbnailReadListDao.insert(thumb("TR2", "R1", false))
        db.thumbnailReadListDao.insert(thumb("TR3", "R2", true))
        c.getReadLists(admin, null, null, false, p20)
      }
      case("sorted desc") { c.getReadLists(admin, null, null, false, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("name")))) }
      case("paged") { c.getReadLists(admin, null, null, false, PageRequest.of(1, 1)) }
      case("unpaged") { c.getReadLists(admin, null, null, true, PageRequest.of(1, 1)) }
      case("library filter") { c.getReadLists(admin, null, listOf("L2"), false, p20) }
      case("restricted") { c.getReadLists(l1, null, null, false, p20) }
      case("kids") { c.getReadLists(kids, null, null, false, p20) }
      case("blank search") { c.getReadLists(admin, "", null, false, p20) }
    }
    func("getReadListById") {
      case("admin") { c.getReadListById(admin, "R1") }
      case("kids filtered") { c.getReadListById(kids, "R1") }
      case("not visible") { c.getReadListById(l1, "R2") }
      case("unknown") { c.getReadListById(admin, "RX") }
    }
    func("getReadListThumbnail") {
      case("ok") { listOf(entity(c.getReadListThumbnail(all, "R1")), calls.take()) }
      case("not found") { listOf(exceptionType { c.getReadListThumbnail(l1, "R2") }, calls.take()) }
    }
    func("getReadListThumbnailById") {
      case("ok") { c.getReadListThumbnailById(admin, "R1", "TR2") }
      case("other read list") { c.getReadListThumbnailById(admin, "R1", "TR3") }
      case("unknown thumbnail") { c.getReadListThumbnailById(admin, "R1", "TX") }
      case("not visible") { c.getReadListThumbnailById(l1, "R2", "TR3") }
    }
    func("getReadListThumbnails") {
      case("R1") { c.getReadListThumbnails(admin, "R1") }
      case("not visible") { c.getReadListThumbnails(l1, "R2") }
    }
    func("addUserUploadedReadListThumbnail") {
      case("png") { listOf(stable(c.addUserUploadedReadListThumbnail(admin, "R1", MockMultipartFile("file", "a.png", "image/png", PNG))), calls.take()) }
      case("not selected") { stable(c.addUserUploadedReadListThumbnail(admin, "R2", MockMultipartFile("file", PNG), false)).also { calls.take() } }
      case("not an image") { c.addUserUploadedReadListThumbnail(admin, "R1", MockMultipartFile("file", "x".toByteArray())) }
      case("unknown") { c.addUserUploadedReadListThumbnail(admin, "RX", MockMultipartFile("file", PNG)) }
    }
    func("markReadListThumbnailSelected") {
      case("ok") {
        c.markReadListThumbnailSelected(admin, "R1", "TR2")
        calls.take()
      }
      case("other read list") { c.markReadListThumbnailSelected(admin, "R1", "TR3") }
      case("unknown thumbnail ignored") { listOf(c.markReadListThumbnailSelected(admin, "R1", "TX"), calls.take()) }
      case("unknown read list") { c.markReadListThumbnailSelected(admin, "RX", "TR1") }
    }
    func("deleteUserUploadedReadListThumbnail") {
      case("ok") {
        c.deleteUserUploadedReadListThumbnail(admin, "R1", "TR1")
        calls.take()
      }
      case("other read list") { c.deleteUserUploadedReadListThumbnail(admin, "R2", "TR1") }
      case("unknown thumbnail") { c.deleteUserUploadedReadListThumbnail(admin, "R1", "TX") }
    }
    func("createReadList") {
      case("ok, summary ignored") { listOf(stable(c.createReadList(ReadListCreationDto("New", "sum", false, listOf("B3", "B1", "B2")))), calls.take()) }
      case("duplicate") { listOf(RestOracle.thrown { c.createReadList(ReadListCreationDto("dup", "", true, listOf("B1"))) }, calls.take()) }
    }
    func("matchComicRackList") {
      case("ok") { listOf(c.matchComicRackList(MockMultipartFile("file", "list.cbl", null, "My list".toByteArray())), calls.take()) }
      case("coded exception") { listOf(RestOracle.thrown { c.matchComicRackList(MockMultipartFile("file", ByteArray(0))) }, calls.take()) }
    }
    func("updateReadListById") {
      case("no change") {
        c.updateReadListById(admin, "R1", ReadListUpdateDto(null, null, null, null))
        calls.take()
      }
      case("all fields, summary ignored") {
        c.updateReadListById(admin, "R1", ReadListUpdateDto("Renamed", "s", listOf("B2", "B1"), false))
        calls.take()
      }
      case("kids") {
        c.updateReadListById(kids, "R1", ReadListUpdateDto("k", null, null, null))
        calls.take()
      }
      case("duplicate") { listOf(RestOracle.thrown { c.updateReadListById(admin, "R2", ReadListUpdateDto("dup", null, null, null)) }, calls.take()) }
      case("not found") { c.updateReadListById(l1, "R2", ReadListUpdateDto("x", null, null, null)) }
    }
    func("deleteReadListById") {
      case("ok") {
        c.deleteReadListById(admin, "R2")
        calls.take()
      }
      case("not found") { c.deleteReadListById(admin, "RX") }
    }
    func("getBooksByReadListId") {
      case("ordered, admin") { books("R1", admin) }
      case("user url restricted") { books("R1", all) }
      case("unordered read list") { books("R2", admin) }
      case("paged") { books("R1", admin, page = PageRequest.of(1, 1)) }
      case("unpaged") { books("R1", admin, unpaged = true, page = PageRequest.of(1, 1)) }
      case("kids") { books("R1", kids) }
      case("library filter") { books("R1", admin, libraryIds = listOf("L2")) }
      case("read status") { books("R1", all, readStatus = listOf(ReadStatus.READ, ReadStatus.IN_PROGRESS)) }
      case("unread status") { books("R1", all, readStatus = listOf(ReadStatus.UNREAD)) }
      case("tags") { books("R1", admin, tags = listOf("BT1")) }
      case("media status") { books("R1", admin, mediaStatus = listOf(Media.Status.ERROR)) }
      case("deleted") { books("R1", admin, deleted = false) }
      case("authors") { books("R1", admin, authors = listOf(Author("Pen B3", "penciller"))) }
      case("not visible") { books("R2", l1) }
    }
    func("getBookSiblingPreviousInReadList") {
      case("previous of B1") { c.getBookSiblingPreviousInReadList(admin, "R1", "B1") }
      case("first has none") { c.getBookSiblingPreviousInReadList(admin, "R1", "B3") }
      case("kids") { c.getBookSiblingPreviousInReadList(kids, "R1", "B1") }
      case("user") { c.getBookSiblingPreviousInReadList(all, "R1", "B1") }
      case("book not in list") { c.getBookSiblingPreviousInReadList(admin, "R1", "B4") }
    }
    func("getBookSiblingNextInReadList") {
      case("next of B3") { c.getBookSiblingNextInReadList(all, "R1", "B3") }
      case("last has none") { c.getBookSiblingNextInReadList(admin, "R1", "B1") }
      case("not visible") { c.getBookSiblingNextInReadList(l1, "R2", "B4") }
    }
    func("getMihonReadProgressByReadListId") {
      case("with progress") { c.getMihonReadProgressByReadListId("R1", all) }
      case("no progress") { c.getMihonReadProgressByReadListId("R1", kids) }
      case("not found") { c.getMihonReadProgressByReadListId("RX", admin) }
    }
    func("updateMihonReadProgressByReadListId") {
      case("first book") {
        c.updateMihonReadProgressByReadListId("R1", TachiyomiReadProgressUpdateDto(1), all)
        calls.take()
      }
      case("all, completed skipped") {
        c.updateMihonReadProgressByReadListId("R1", TachiyomiReadProgressUpdateDto(5), all)
        calls.take()
      }
      case("zero") {
        c.updateMihonReadProgressByReadListId("R1", TachiyomiReadProgressUpdateDto(0), kids)
        calls.take()
      }
      case("kids sees one book") {
        c.updateMihonReadProgressByReadListId("R1", TachiyomiReadProgressUpdateDto(2), kids)
        calls.take()
      }
      case("not found") { c.updateMihonReadProgressByReadListId("RX", TachiyomiReadProgressUpdateDto(1), all) }
    }
    func("downloadReadListAsZip") {
      case("existing and missing files") {
        val f = tempDir.resolve("readlist-file.cbz").also { it.writeBytes(oracleBytes(3000)) }
        db.bookDao.insert(Book(name = "file", url = URL("file:$f"), fileLastModified = FIXED, id = "B5", seriesId = "S1", libraryId = "L1", createdDate = FIXED))
        db.readListDao.insert(ReadList(name = "Zip é", bookIds = sortedMapOf(0 to "B1", 4 to "B5"), id = "R3", createdDate = FIXED))
        val e = c.downloadReadListAsZip(admin, "R3")
        listOf(e.statusCode.value(), e.headers.getFirst("Content-Disposition"), e.headers.getFirst("Content-Type"), RestOracle.zipSummary(RestOracle.bodyBytes(e)))
      }
      case("no file") {
        val e = c.downloadReadListAsZip(admin, "R1")
        listOf(e.headers.getFirst("Content-Disposition"), RestOracle.zipSummary(RestOracle.bodyBytes(e)))
      }
      case("not found") { c.downloadReadListAsZip(l1, "R2") }
    }
  }
}
