package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.MarkSelectedPreference
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.R2Device
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.R2Progression
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.infrastructure.image.ImageType
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
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import java.net.URL
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class BookLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.bookLifecycle

  private val dir by lazy { tempDir.resolve("books").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }
  private val jpg by lazy { resource("barcode/page_384.jpg") }

  private val user = KomgaUser("u@example.org", "p", id = "U1", createdDate = date)

  private fun url(rel: String) = URL("file:$dir/$rel")

  private fun copy(
    from: java.nio.file.Path,
    name: String,
  ) = URL("file:" + Files.copy(from, dir.resolve(name)))

  private fun b(id: String) = db.bookDao.findByIdOrNull(id)!!

  private fun addBook(
    id: String,
    url: URL,
    libraryId: String = "L1",
    seriesId: String = "S1",
  ): Book {
    val bk = book(id, seriesId, libraryId, url = url)
    db.bookDao.insert(bk)
    db.mediaDao.insert(Media(bookId = id, createdDate = date))
    db.bookMetadataDao.insert(metadata(bk))
    return bk
  }

  private fun media(id: String) =
    db.mediaDao.findById(id).let { m ->
      listOf(
        m.status,
        m.mediaType,
        m.pageCount,
        m.pages,
        m.files,
        m.comment,
        m.epubDivinaCompatible,
        m.epubIsKepub,
        (db.mediaDao.findExtensionByIdOrNull(id) as? MediaExtensionEpub)?.let { listOf(it.isFixedLayout, it.positions.size, it.toc.size) },
      )
    }

  private fun typed(t: TypedBytes?) = t?.let { listOf(Samples.digest(it.bytes), it.mediaType) }

  private fun image(t: TypedBytes?) = t?.let { listOf(graph.describeImage(it.bytes), it.mediaType) }

  private fun thumbs(bookId: String) =
    db.thumbnailBookDao.findAllByBookId(bookId).sortedBy { it.id }.map {
      listOf(it.id, it.type, it.selected, it.mediaType, it.dimension, it.url, if (it.type == ThumbnailBook.Type.GENERATED) null else it.thumbnail)
    }

  private fun events() = graph.takeEvents().map { it.javaClass.simpleName }

  private fun bThumb(
    id: String,
    bookId: String,
    type: ThumbnailBook.Type = ThumbnailBook.Type.USER_UPLOADED,
    selected: Boolean = false,
    bytes: ByteArray? = oracleBytes(4),
    url: URL? = null,
    dimension: Dimension = Dimension(1, 1),
  ) = ThumbnailBook(bytes, url, selected, type, "image/jpeg", 4, dimension, id, bookId, date)

  private fun progression(
    href: String,
    position: Int? = null,
    progression: Float? = null,
    modified: ZonedDateTime = ZonedDateTime.of(2021, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
    koboSpan: String? = null,
  ) = R2Progression(modified, R2Device("dev", "Device"), R2Locator(href, "application/xhtml+xml", locations = R2Locator.Location(progression = progression, position = position), koboSpan = koboSpan))

  private fun progress(bookId: String) =
    stable(
      db.readProgressDao.findByBookIdAndUserIdOrNull(bookId, "U1")?.let {
        listOf(it.page, it.completed, it.readDate, it.deviceId, it.deviceName, it.locator)
      },
    )

  override fun cases() {
    func("analyzeAndPersist") {
      case("setup") {
        db.libraryDao.insert(library("L1", url("")))
        db.libraryDao.insert(library("L2").copy(hashFiles = false, analyzeDimensions = false))
        db.seriesDao.insert(series("S1", "L1", url("")))
        db.seriesDao.insert(series("S2", "L2"))
        db.komgaUserDao.insert(user)
        addBook("B1", zipFile(dir, "b1.cbz", listOf("p1.png" to png, "p2.jpg" to jpg, t("info.txt", "hello"), "dir/" to null)))
        addBook("B2", copy(Samples.fixture("epub/divina.epub"), "divina.epub"))
        addBook("B3", copy(Samples.fixture("epub/reflow.epub"), "reflow.epub"))
        addBook("B4", copy(Samples.komgaRes("pdf/komga.pdf"), "komga.pdf"))
        addBook("B5", url("missing.cbz"))
        addBook("B6", url("garbage.cbz").also { dir.resolve("garbage.cbz").writeBytes(oracleBytes(100)) })
        addBook("B7", zipFile(dir, "b7.cbz", listOf(t("a.txt", "a"))))
        addBook("B8", zipFile(dir, "b8.cbz", listOf("p1.png" to png)), libraryId = "L2", seriesId = "S2")
        db.bookDao.count()
      }
      for (id in listOf("B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8")) {
        case("book $id") { attempt(dir) { listOf(lifecycle.analyzeAndPersist(b(id)), media(id), events()) } }
      }
      case("outdated with other page count adjusts progress") {
        db.readProgressDao.save(ReadProgress("B1", "U1", 5, false, date, createdDate = date))
        db.komgaUserDao.insert(KomgaUser("u2@example.org", "p", id = "U2", createdDate = date))
        db.readProgressDao.save(ReadProgress("B1", "U2", 5, true, date, createdDate = date))
        db.mediaDao.update(db.mediaDao.findById("B1").copy(status = Media.Status.OUTDATED, pageCount = 5))
        attempt(dir) { listOf(lifecycle.analyzeAndPersist(b("B1")), db.readProgressDao.findAllByBookId("B1").sortedBy { it.userId }.map { listOf(it.userId, it.page, it.completed) }) }
      }
      case("outdated with same page count keeps progress") {
        db.readProgressDao.save(ReadProgress("B1", "U1", 2, false, date, createdDate = date))
        db.mediaDao.update(db.mediaDao.findById("B1").copy(status = Media.Status.OUTDATED))
        attempt(dir) { listOf(lifecycle.analyzeAndPersist(b("B1")), db.readProgressDao.findAllByBookId("B1").sortedBy { it.userId }.map { listOf(it.userId, it.page, it.completed) }) }
      }
      case("unknown library") { exceptionType { lifecycle.analyzeAndPersist(b("B1").copy(libraryId = "L9")) } }
    }
    func("hashAndPersist") {
      case("hashing enabled") {
        lifecycle.hashAndPersist(b("B1"))
        b("B1").fileHash
      }
      case("already hashed") {
        db.bookDao.update(b("B2").copy(fileHash = "existing"))
        lifecycle.hashAndPersist(b("B2"))
        b("B2").fileHash
      }
      case("hashing disabled") {
        lifecycle.hashAndPersist(b("B8"))
        b("B8").fileHash
      }
      case("missing file") { attempt(dir) { lifecycle.hashAndPersist(b("B5")) } }
    }
    func("hashKoreaderAndPersist") {
      case("hashing disabled") {
        lifecycle.hashKoreaderAndPersist(b("B1"))
        b("B1").fileHashKoreader
      }
      case("hashing enabled") {
        db.libraryDao.update(db.libraryDao.findById("L1").copy(hashKoreader = true))
        listOf("B1", "B4").map {
          lifecycle.hashKoreaderAndPersist(b(it))
          b(it).fileHashKoreader
        }
      }
      case("already hashed") {
        db.bookDao.update(b("B2").copy(fileHashKoreader = "existing"))
        lifecycle.hashKoreaderAndPersist(b("B2"))
        b("B2").fileHashKoreader
      }
      case("missing file") { attempt(dir) { lifecycle.hashKoreaderAndPersist(b("B5")) } }
    }
    func("hashPagesAndPersist") {
      case("hashing disabled") {
        lifecycle.hashPagesAndPersist(b("B1"))
        media("B1")
      }
      case("hashing enabled") {
        db.libraryDao.update(db.libraryDao.findById("L1").copy(hashPages = true))
        lifecycle.hashPagesAndPersist(b("B1"))
        media("B1")
      }
      case("epub divina") {
        lifecycle.hashPagesAndPersist(b("B2"))
        media("B2")
      }
      case("media not ready") { attempt(dir) { lifecycle.hashPagesAndPersist(b("B5")) } }
    }
    func("generateThumbnailAndPersist") {
      for (id in listOf("B1", "B2", "B3", "B4", "B5", "B7")) {
        case("book $id") {
          attempt(dir) {
            lifecycle.generateThumbnailAndPersist(b(id))
            listOf(thumbs(id), events())
          }
        }
      }
      case("again replaces the generated one") {
        val before = db.thumbnailBookDao.findAllByBookId("B1").map { it.id }
        lifecycle.generateThumbnailAndPersist(b("B1"))
        val after = db.thumbnailBookDao.findAllByBookId("B1")
        listOf(after.size, after.none { it.id in before }, after.single().selected)
      }
    }
    func("addThumbnailForBook") {
      case("uploaded, IF_NONE_OR_GENERATED, generated selected") {
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T1", "B1"), MarkSelectedPreference.IF_NONE_OR_GENERATED), thumbs("B1"), events()) }
      }
      case("uploaded, IF_NONE_OR_GENERATED, uploaded selected") {
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T2", "B1"), MarkSelectedPreference.IF_NONE_OR_GENERATED), thumbs("B1"), events()) }
      }
      case("uploaded, NO") { attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T3", "B1", selected = true), MarkSelectedPreference.NO), thumbs("B1")) } }
      case("uploaded, YES") { attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T4", "B1"), MarkSelectedPreference.YES), thumbs("B1")) } }
      case("sidecar") {
        dir.resolve("b1-cover.jpg").writeBytes(oracleBytes(12))
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T5", "B1", ThumbnailBook.Type.SIDECAR, bytes = null, url = url("b1-cover.jpg")), MarkSelectedPreference.NO), thumbs("B1")) }
      }
      case("sidecar same url replaces") {
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T6", "B1", ThumbnailBook.Type.SIDECAR, bytes = null, url = url("b1-cover.jpg")), MarkSelectedPreference.YES), thumbs("B1")) }
      }
      case("generated replaces generated, not selected") {
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T7", "B1", ThumbnailBook.Type.GENERATED), MarkSelectedPreference.IF_NONE_OR_GENERATED), thumbs("B1")) }
      }
      case("generated on book without thumbnail") {
        attempt(dir) { listOf(lifecycle.addThumbnailForBook(bThumb("T8", "B6", ThumbnailBook.Type.GENERATED, dimension = Dimension(100, 100)), MarkSelectedPreference.IF_NONE_OR_GENERATED), thumbs("B6")) }
      }
      case("unknown book") { exceptionType { lifecycle.addThumbnailForBook(bThumb("T9", "B9"), MarkSelectedPreference.YES) } }
    }
    func("getThumbnail") {
      case("selected sidecar exists") { attempt(dir) { lifecycle.getThumbnail("B1") } }
      case("selected sidecar deleted") {
        Files.delete(dir.resolve("b1-cover.jpg"))
        attempt(dir) { listOf(lifecycle.getThumbnail("B1"), thumbs("B1")) }
      }
      case("no thumbnail") { lifecycle.getThumbnail("B5") }
    }
    func("thumbnailsHouseKeeping") {
      case("several selected") {
        db.thumbnailBookDao.insert(bThumb("TA", "B5", selected = true))
        db.thumbnailBookDao.insert(bThumb("TB", "B5", selected = true))
        db.thumbnailBookDao.insert(bThumb("TC", "B5", ThumbnailBook.Type.SIDECAR, selected = true, bytes = null, url = url("gone.jpg")))
        db.thumbnailBookDao.markSelected(bThumb("TC", "B5"))
        attempt(dir) { listOf(lifecycle.getThumbnail("B5"), thumbs("B5")) }
      }
      case("none selected") {
        db.thumbnailBookDao.insert(bThumb("TD", "B7"))
        db.thumbnailBookDao.insert(bThumb("TE", "B7"))
        attempt(dir) { listOf(lifecycle.getThumbnail("B7"), thumbs("B7")) }
      }
    }
    func("getThumbnailBytes") {
      case("uploaded bytes") { typed(lifecycle.getThumbnailBytes("B1")) }
      case("uploaded bytes resized, not an image") { typed(lifecycle.getThumbnailBytes("B1", 100)) }
      case("generated resized") { image(lifecycle.getThumbnailBytes("B2", 50)) }
      case("sidecar url") {
        dir.resolve("b3-cover.jpg").writeBytes(png)
        db.thumbnailBookDao.insert(bThumb("TF", "B3", ThumbnailBook.Type.SIDECAR, bytes = null, url = url("b3-cover.jpg")))
        db.thumbnailBookDao.markSelected(bThumb("TF", "B3"))
        typed(lifecycle.getThumbnailBytes("B3"))
      }
      case("no thumbnail") { lifecycle.getThumbnailBytes("B6X") }
      case("neither bytes nor url") {
        db.thumbnailBookDao.insert(bThumb("TG", "B8", selected = true, bytes = null))
        typed(lifecycle.getThumbnailBytes("B8"))
      }
    }
    func("getThumbnailBytesOriginal") {
      case("generated gives the poster") { attempt(dir) { typed(lifecycle.getThumbnailBytesOriginal("B2")) } }
      case("generated pdf poster") { attempt(dir) { image(lifecycle.getThumbnailBytesOriginal("B4")) } }
      case("uploaded") { typed(lifecycle.getThumbnailBytesOriginal("B1")) }
      case("sidecar") { typed(lifecycle.getThumbnailBytesOriginal("B3")) }
      case("no thumbnail") { lifecycle.getThumbnailBytesOriginal("B9") }
    }
    func("getThumbnailBytesByThumbnailId") {
      case("uploaded") { typed(lifecycle.getThumbnailBytesByThumbnailId("T4")) }
      case("sidecar") { typed(lifecycle.getThumbnailBytesByThumbnailId("TF")) }
      case("unknown") { lifecycle.getThumbnailBytesByThumbnailId("TZ") }
    }
    func("getBytesFromThumbnailBook") {
      case("url file missing") { attempt(dir) { lifecycle.getThumbnailBytesByThumbnailId("TC") } }
      case("neither bytes nor url") { lifecycle.getThumbnailBytesByThumbnailId("TG") }
    }
    func("deleteThumbnailForBook") {
      case("generated refused") { attempt(dir) { lifecycle.deleteThumbnailForBook(bThumb("T7", "B1", ThumbnailBook.Type.GENERATED)) } }
      case("selected uploaded") {
        lifecycle.deleteThumbnailForBook(bThumb("T4", "B1"))
        attempt(dir) { listOf(thumbs("B1"), events()) }
      }
      case("unknown uploaded") {
        lifecycle.deleteThumbnailForBook(bThumb("TZ", "B1"))
        attempt(dir) { listOf(thumbs("B1"), events()) }
      }
    }
    func("findBookThumbnailsToRegenerate") {
      case("bigger only") { lifecycle.findBookThumbnailsToRegenerate(true).sorted() }
      case("all") { lifecycle.findBookThumbnailsToRegenerate(false).sorted() }
    }
    func("getBookPage") {
      case("png page") { attempt(dir) { typed(lifecycle.getBookPage(b("B1"), 1)) } }
      case("jpeg page") { attempt(dir) { typed(lifecycle.getBookPage(b("B1"), 2)) } }
      case("page 0") { exceptionType { lifecycle.getBookPage(b("B1"), 0) } }
      case("page after last") { exceptionType { lifecycle.getBookPage(b("B1"), 3) } }
      case("convert png to jpeg") { attempt(dir) { image(lifecycle.getBookPage(b("B1"), 1, ImageType.JPEG)) } }
      case("convert png to png") { attempt(dir) { typed(lifecycle.getBookPage(b("B1"), 1, ImageType.PNG)) } }
      case("convert jpeg to png") { attempt(dir) { image(lifecycle.getBookPage(b("B1"), 2, ImageType.PNG)) } }
      case("resize") { attempt(dir) { image(lifecycle.getBookPage(b("B1"), 2, resizeTo = 100)) } }
      case("resize and convert") { attempt(dir) { image(lifecycle.getBookPage(b("B1"), 1, ImageType.PNG, 64)) } }
      case("unsupported read format") {
        db.mediaDao.update(db.mediaDao.findById("B8").copy(status = Media.Status.READY, pages = listOf(BookPage("p1.png", "image/x-unknown")), mediaType = "application/zip"))
        exceptionType { lifecycle.getBookPage(b("B8"), 1, ImageType.JPEG) }
      }
      case("unsupported read format, no conversion") { attempt(dir) { typed(lifecycle.getBookPage(b("B8"), 1)) } }
      case("epub divina page") { attempt(dir) { typed(lifecycle.getBookPage(b("B2"), 2)) } }
      case("epub not divina") { exceptionType { lifecycle.getBookPage(b("B3"), 1) } }
      case("pdf page") { attempt(dir) { image(lifecycle.getBookPage(b("B4"), 1)) } }
      case("pdf page resized") { attempt(dir) { image(lifecycle.getBookPage(b("B4"), 1, resizeTo = 80)) } }
      case("media not ready") { exceptionType { lifecycle.getBookPage(b("B5"), 1) } }
      case("missing file") {
        db.mediaDao.update(db.mediaDao.findById("B5").copy(status = Media.Status.READY, mediaType = "application/zip", pages = listOf(BookPage("p1.png", "image/png"))))
        attempt(dir) { lifecycle.getBookPage(b("B5"), 1) }
      }
    }
    func("markReadProgress") {
      case("divina page") {
        lifecycle.markReadProgress(b("B1"), user, 1)
        listOf(progress("B1"), events())
      }
      case("last page completes") {
        lifecycle.markReadProgress(b("B1"), user, 2)
        listOf(progress("B1"), events())
      }
      case("page 0") { lifecycle.markReadProgress(b("B1"), user, 0) }
      case("page after last") { lifecycle.markReadProgress(b("B1"), user, 3) }
      case("epub divina") {
        lifecycle.markReadProgress(b("B2"), user, 2)
        listOf(progress("B2"), events())
      }
      case("epub not divina") { lifecycle.markReadProgress(b("B3"), user, 1) }
      case("epub divina without extension") {
        db.mediaDao.update(db.mediaDao.findById("B2").copy(extension = null))
        lifecycle.markReadProgress(b("B2"), user, 1)
      }
      case("pdf") {
        lifecycle.markReadProgress(b("B4"), user, 1)
        listOf(progress("B4"), events())
      }
      case("unknown user") { exceptionType { lifecycle.markReadProgress(b("B1"), user.copy(id = "U9"), 1) } }
    }
    func("markReadProgressCompleted") {
      case("divina") {
        lifecycle.markReadProgressCompleted("B1", user)
        listOf(progress("B1"), events())
      }
      case("media with 0 page") {
        lifecycle.markReadProgressCompleted("B7", user)
        listOf(progress("B7"), events())
      }
      case("unknown book") { exceptionType { lifecycle.markReadProgressCompleted("B9", user) } }
    }
    func("deleteReadProgress") {
      case("existing") {
        lifecycle.deleteReadProgress(b("B1"), user)
        listOf(progress("B1"), events())
      }
      case("none") {
        lifecycle.deleteReadProgress(b("B1"), user)
        events()
      }
    }
    func("markProgression") {
      case("setup") {
        lifecycle.analyzeAndPersist(b("B2"))
        events()
        (db.mediaDao.findExtensionByIdOrNull("B3") as MediaExtensionEpub).positions.map { listOf(it.href, it.locations?.position, it.locations?.progression, it.locations?.totalProgression) }
      }
      case("divina position") {
        lifecycle.markProgression(b("B1"), user, progression("p1.png", position = 2))
        listOf(progress("B1"), events())
      }
      case("older progression refused") { lifecycle.markProgression(b("B1"), user, progression("p1.png", position = 1, modified = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))) }
      case("same date refused") { lifecycle.markProgression(b("B1"), user, progression("p1.png", position = 1)) }
      case("divina position out of range") { lifecycle.markProgression(b("B1"), user, progression("p1.png", position = 3, modified = ZonedDateTime.of(2021, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC))) }
      case("divina without position") { lifecycle.markProgression(b("B1"), user, progression("p1.png", modified = ZonedDateTime.of(2021, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC))) }
      case("pdf position") {
        lifecycle.markProgression(b("B4"), user, progression("", position = 1, modified = ZonedDateTime.of(2021, 2, 1, 0, 0, 0, 0, ZoneOffset.ofHours(5))))
        listOf(progress("B4"), events())
      }
      case("media without profile") { lifecycle.markProgression(b("B6"), user, progression("", position = 1)) }
      case("epub unknown resource") { lifecycle.markProgression(b("B3"), user, progression("nope.xhtml", progression = 0F)) }
      case("epub without progression") { lifecycle.markProgression(b("B3"), user, progression("OPS/c1.xhtml")) }
      case("epub exact progression") {
        lifecycle.markProgression(b("B3"), user, progression("OPS/c1.xhtml#frag", progression = 0F, koboSpan = "kobo.1.1"))
        listOf(progress("B3"), events())
      }
      case("epub progression between positions") {
        lifecycle.markProgression(b("B3"), user, progression("OPS/c1.xhtml", progression = 0.6F, modified = ZonedDateTime.of(2021, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
        progress("B3")
      }
      case("epub progression after last position") {
        lifecycle.markProgression(b("B3"), user, progression("OPS/c1.xhtml", progression = 1F, modified = ZonedDateTime.of(2021, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
      }
      case("epub encoded href") {
        lifecycle.markProgression(b("B3"), user, progression("c2%2Exhtml", progression = 0F, modified = ZonedDateTime.of(2021, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
        progress("B3")
      }
      case("epub fixed layout single position") {
        lifecycle.markProgression(b("B2"), user, progression("OEBPS/Text/page2.xhtml", progression = 0.5F))
        listOf(progress("B2"), events())
      }
      case("epub without extension") {
        db.mediaDao.update(db.mediaDao.findById("B3").copy(extension = null))
        lifecycle.markProgression(b("B3"), user, progression("OPS/c1.xhtml", progression = 0F, modified = ZonedDateTime.of(2021, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
      }
    }
    func("deleteOne") {
      case("with related data") {
        db.readListDao.insert(ReadList("rl", bookIds = sortedMapOf(1 to "B7", 2 to "B6"), id = "RL1", createdDate = date))
        db.readProgressDao.save(ReadProgress("B7", "U1", 1, true, date, createdDate = date))
        lifecycle.deleteOne(b("B7"))
        listOf(
          db.bookDao.findByIdOrNull("B7"),
          db.readListDao.findByIdOrNull("RL1", org.gotson.komga.domain.model.SearchContext.empty())!!.bookIds,
          db.rawQuery("select count(*) from THUMBNAIL_BOOK where BOOK_ID = 'B7'"),
          db.rawQuery("select count(*) from MEDIA where BOOK_ID = 'B7'"),
          db.rawQuery("select count(*) from BOOK_METADATA where BOOK_ID = 'B7'"),
          db.rawQuery("select count(*) from READ_PROGRESS where BOOK_ID = 'B7'"),
          events(),
        )
      }
      case("unknown book") {
        lifecycle.deleteOne(book("B9", "S1", "L1"))
        events()
      }
    }
    func("softDeleteMany") {
      case("two books") {
        lifecycle.softDeleteMany(listOf(b("B6"), b("B8")))
        stable(listOf(db.bookDao.findAll().sortedBy { it.id }.map { listOf(it.id, it.deletedDate) }, events()))
      }
      case("empty") {
        lifecycle.softDeleteMany(emptyList())
        events()
      }
    }
    func("deleteMany") {
      case("two books") {
        lifecycle.deleteMany(listOf(b("B6"), b("B8")))
        listOf(db.bookDao.findAll().map { it.id }.sorted(), db.readListDao.findByIdOrNull("RL1", org.gotson.komga.domain.model.SearchContext.empty())?.bookIds, events())
      }
      case("empty") {
        lifecycle.deleteMany(emptyList())
        events()
      }
    }
    func("deleteBookFiles") {
      case("missing file") {
        lifecycle.deleteBookFiles(b("B5"))
        listOf(b("B5").deletedDate, events())
      }
      case("file with sidecar, folder kept") {
        dir.resolve("b1-side.jpg").writeBytes(oracleBytes(5))
        db.thumbnailBookDao.insert(bThumb("TS", "B1", ThumbnailBook.Type.SIDECAR, bytes = null, url = url("b1-side.jpg")))
        attempt(dir) {
          lifecycle.deleteBookFiles(b("B1"))
          listOf(Files.exists(dir.resolve("b1.cbz")), Files.exists(dir.resolve("b1-side.jpg")), b("B1").deletedDate != null, db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by TYPE"), events())
        }
      }
      case("last file removes folder") {
        val sub = dir.resolve("sub").createDirectories()
        sub.resolve("x.cbz").writeBytes(oracleBytes(5))
        addBook("BX", url("sub/x.cbz"))
        attempt(dir) {
          lifecycle.deleteBookFiles(b("BX"))
          listOf(Files.exists(sub), db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by TYPE, BOOK_ID"), events())
        }
      }
    }
  }
}
