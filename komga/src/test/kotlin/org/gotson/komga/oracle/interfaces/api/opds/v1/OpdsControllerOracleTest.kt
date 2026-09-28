package org.gotson.komga.oracle.interfaces.api.opds.v1

import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.opds.v1.OpdsController
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.gotson.komga.oracle.interfaces.OpdsSupport
import org.gotson.komga.oracle.interfaces.OpdsSupport.xml
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.util.UriComponentsBuilder
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsAuthor
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsEntryAcquisition
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsEntryNavigation
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsFeedAcquisition
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsFeedNavigation
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OpdsControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val controller by lazy {
    OpdsController(
      db.libraryDao,
      db.seriesCollectionDao,
      db.readListDao,
      db.seriesDtoDao,
      db.bookDtoDao,
      db.mediaDao,
      db.referentialDao,
      services.bookLifecycle,
      services.commonBookController,
      services.settings,
      services.contentRestrictionChecker,
      ImageType.JPEG,
    )
  }
  private val admin = KomgaPrincipal(InterfacesData.admin)
  private val limited = KomgaPrincipal(InterfacesData.limited)
  private val restricted = KomgaPrincipal(InterfacesData.restricted)
  private val page20: Pageable = PageRequest.of(0, 20)
  private val page0: Pageable = PageRequest.of(0, 2)
  private val page1: Pageable = PageRequest.of(1, 2)

  private fun <T> web(
    contextPath: String = "",
    block: () -> T,
  ): T = WebOracle.withRequest(WebOracle.request(uri = "$contextPath/opds/v1.2/catalog", host = "komga.local", port = 8080, contextPath = contextPath), block)

  private fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = OpdsController::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size && (args.isEmpty() || it.parameterTypes[0].isInstance(args[0]) || args[0] == null) }
    m.isAccessible = true
    return try {
      m.invoke(controller, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  /** entries are serialized inside a feed, as Komga sends them (namespaces declared by the feed) */
  private fun inFeed(entry: Any?): String =
    xml(
      when (entry) {
        is OpdsEntryAcquisition -> OpdsFeedAcquisition("id", "t", ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), OpdsAuthor("a"), emptyList(), listOf(entry))
        else -> OpdsFeedNavigation("id", "t", ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), OpdsAuthor("a"), emptyList(), listOf(entry as OpdsEntryNavigation))
      },
    )

  private fun book(id: String): BookDto = db.bookDtoDao.findByIdOrNull(id, "U1")!!

  private fun series(id: String): SeriesDto = db.seriesDtoDao.findByIdOrNull(id, "U1")!!

  override fun cases() {
    func("getCatalog") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
        OpdsSupport.thumbnails(db)
      }
      case("catalog") { web { xml(controller.getCatalog()) } }
      case("context path") { web("/komga") { xml(controller.getCatalog()) } }
    }
    func("getSearch") {
      case("search") { web { xml(controller.getSearch()) } }
    }
    func("getOnDeck") {
      case("admin") { web { xml(controller.getOnDeck(admin, page20)) } }
      case("limited") { web { xml(controller.getOnDeck(limited, page20)) } }
    }
    func("getKeepReading") {
      case("admin") { web { xml(controller.getKeepReading(admin, page20)) } }
      case("restricted") { web { xml(controller.getKeepReading(restricted, page20)) } }
    }
    func("getAllSeries") {
      case("admin") { web { xml(controller.getAllSeries(admin, null, null, page20)) } }
      case("first page") { web { xml(controller.getAllSeries(admin, null, null, page0)) } }
      case("second page") { web { xml(controller.getAllSeries(admin, null, null, page1)) } }
      case("publisher") { web { xml(controller.getAllSeries(admin, null, listOf("DC Comics", "Nope"), page20)) } }
      case("search, empty index") { web { xml(controller.getAllSeries(admin, "bat", null, page20)) } }
      case("blank search") { web { xml(controller.getAllSeries(admin, " ", null, page20)) } }
      case("limited") { web { xml(controller.getAllSeries(limited, null, null, page20)) } }
      case("restricted") { web { xml(controller.getAllSeries(restricted, null, null, page20)) } }
    }
    func("getLatestSeries") {
      case("admin") { web { xml(controller.getLatestSeries(admin, page20)) } }
      case("paged") { web { xml(controller.getLatestSeries(admin, page1)) } }
    }
    func("getLatestBooks") {
      case("admin") { web { xml(controller.getLatestBooks(admin, page20)) } }
      case("limited, paged") { web { xml(controller.getLatestBooks(limited, page0)) } }
    }
    func("getLibraries") {
      case("admin") { web { xml(controller.getLibraries(admin)) } }
      case("limited") { web { xml(controller.getLibraries(limited)) } }
    }
    func("getCollections") {
      case("admin") { web { xml(controller.getCollections(admin, page20)) } }
      case("restricted") { web { xml(controller.getCollections(restricted, page20)) } }
    }
    func("getReadLists") {
      case("admin") { web { xml(controller.getReadLists(admin, page20)) } }
      case("limited") { web { xml(controller.getReadLists(limited, page20)) } }
    }
    func("getPublishers") {
      case("admin") { web { xml(controller.getPublishers(admin, page20)) } }
      case("limited") { web { xml(controller.getPublishers(limited, page20)) } }
      case("paged") { web { xml(controller.getPublishers(admin, PageRequest.of(0, 1))) } }
    }
    func("getOneSeries") {
      case("S1") { web { xml(controller.getOneSeries(admin, "S1", page20)) } }
      case("S1 paged") { web { xml(controller.getOneSeries(admin, "S1", page1)) } }
      case("S2") { web { xml(controller.getOneSeries(admin, "S2", page20)) } }
      case("S3 oneshot") { web { xml(controller.getOneSeries(admin, "S3", page20)) } }
      case("restricted") { web { xml(controller.getOneSeries(restricted, "S2", page20)) } }
      case("unknown") { web { xml(controller.getOneSeries(admin, "SX", page20)) } }
    }
    func("getOneLibrary") {
      case("L1") { web { xml(controller.getOneLibrary(admin, "L1", page20)) } }
      case("L2 limited") { web { xml(controller.getOneLibrary(limited, "L2", page20)) } }
      case("unknown") { web { xml(controller.getOneLibrary(admin, "LX", page20)) } }
    }
    func("getOneCollection") {
      case("C1") { web { xml(controller.getOneCollection(admin, "C1", page20)) } }
      case("C1 restricted") { web { xml(controller.getOneCollection(restricted, "C1", page20)) } }
      case("unknown") { web { xml(controller.getOneCollection(admin, "CX", page20)) } }
    }
    func("getOneReadList") {
      case("R1") { web { xml(controller.getOneReadList(admin, "R1", page20)) } }
      case("R1 limited") { web { xml(controller.getOneReadList(limited, "R1", page20)) } }
      case("unknown") { web { xml(controller.getOneReadList(admin, "RX", page20)) } }
    }
    func("getBookThumbnailSmall") {
      case("generated thumbnail") { controller.getBookThumbnailSmall(admin, "B7") }
      case("no thumbnail") { controller.getBookThumbnailSmall(admin, "B1") }
      case("restricted") { controller.getBookThumbnailSmall(restricted, "B4") }
    }
    func("getBookPageOpds") {
      case("page 0 is the first page") { WebOracle.describeEntity(controller.getBookPageOpds(admin, ServletWebRequest(WebOracle.request()), "B7", 0, null)) }
      case("page 2") { WebOracle.describeEntity(controller.getBookPageOpds(admin, ServletWebRequest(WebOracle.request()), "B7", 2, "")) }
      case("out of range") { WebOracle.describeEntity(controller.getBookPageOpds(admin, ServletWebRequest(WebOracle.request()), "B7", 3, null)) }
    }
    func("linkStart") {
      case("link") { web { xml(call("linkStart")) } }
    }
    func("uriBuilder") {
      case("path") { web { (call("uriBuilder", "series/a b") as UriComponentsBuilder).toUriString() } }
      case("empty") { web("/ctx") { (call("uriBuilder", "") as UriComponentsBuilder).toUriString() } }
    }
    func("linkPage") {
      val builder = { UriComponentsBuilder.fromUriString("http://h/opds/v1.2/series?x=1") }
      case("single page") { (call("linkPage", builder(), PageImpl(listOf(1, 2), PageRequest.of(0, 20), 2)) as List<*>).map { xml(it) } }
      case("first of three") { (call("linkPage", builder(), PageImpl(listOf(1, 2), PageRequest.of(0, 2), 6)) as List<*>).map { xml(it) } }
      case("middle") { (call("linkPage", builder(), PageImpl(listOf(1, 2), PageRequest.of(1, 2), 6)) as List<*>).map { xml(it) } }
      case("last") { (call("linkPage", builder(), PageImpl(listOf(1, 2), PageRequest.of(2, 2), 6)) as List<*>).map { xml(it) } }
      case("unpaged") { (call("linkPage", builder(), PageImpl(listOf(1, 2))) as List<*>).map { xml(it) } }
    }
    func("toOpdsEntry@729") {
      case("series") { web { inFeed(call("toOpdsEntry", series("S1"), null)) } }
      case("series with prepend") { web { inFeed(call("toOpdsEntry", series("S2"), 12)) } }
    }
    func("toOpdsEntry@740") {
      listOf("B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8").forEach { id ->
        case(id) { web { inFeed(call("toOpdsEntry", book(id), db.mediaDao.findById(id), { b: BookDto -> "[${b.number}] " })) } }
      }
      case("epub divina compatible") { web { inFeed(call("toOpdsEntry", book("B4"), db.mediaDao.findById("B7").copy(mediaType = "application/epub+zip", epubDivinaCompatible = true), { _: BookDto -> "" })) } }
      case("mixed page types") { web { inFeed(call("toOpdsEntry", book("B7"), db.mediaDao.findById("B7").copy(pages = db.mediaDao.findById("B1").pages + db.mediaDao.findById("B3").pages), { _: BookDto -> "" })) } }
    }
    func("toOpdsEntry@787") {
      case("library") { web { inFeed(call("toOpdsEntry", db.libraryDao.findById("L2"))) } }
    }
    func("toOpdsEntry@796") {
      case("collection") { web { inFeed(call("toOpdsEntry", db.seriesCollectionDao.findByIdOrNull("C1", SearchContext.empty())!!)) } }
    }
    func("toOpdsEntry@805") {
      case("read list") { web { inFeed(call("toOpdsEntry", db.readListDao.findByIdOrNull("R1", SearchContext.empty())!!)) } }
    }
    func("getEntriesWithSeriesTitle") {
      case("books") { web { (call("getEntriesWithSeriesTitle", listOf(book("B1"), book("B6"))) as List<*>).map { inFeed(it) } } }
      case("empty") { web { (call("getEntriesWithSeriesTitle", emptyList<BookDto>()) as List<*>).map { xml(it) } } }
    }
    func("sanitize") {
      listOf("a.cbz", "a;b;c.cbz", ";", "", "é;漫.cbz").forEach { case(it) { call("sanitize", it) } }
    }
  }
}
