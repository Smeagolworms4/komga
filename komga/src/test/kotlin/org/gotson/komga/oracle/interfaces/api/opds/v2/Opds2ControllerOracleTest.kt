package org.gotson.komga.oracle.interfaces.api.opds.v2

import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.opds.v2.Opds2Controller
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.gotson.komga.oracle.interfaces.OpdsSupport
import org.gotson.komga.oracle.interfaces.OpdsSupport.json
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.util.UriComponentsBuilder

class Opds2ControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val controller by lazy {
    Opds2Controller(
      db.libraryDao,
      db.seriesCollectionDao,
      db.readListDao,
      db.seriesDtoDao,
      db.bookDtoDao,
      db.referentialDao,
      services.commonBookController,
      services.opdsGenerator,
      services.contentRestrictionChecker,
    )
  }
  private val admin = KomgaPrincipal(InterfacesData.admin)
  private val limited = KomgaPrincipal(InterfacesData.limited)
  private val restricted = KomgaPrincipal(InterfacesData.restricted)
  private val page20: Pageable = PageRequest.of(0, 20)
  private val page1: Pageable = PageRequest.of(1, 1)

  private fun <T> web(
    contextPath: String = "",
    block: () -> T,
  ): T = WebOracle.withRequest(WebOracle.request(uri = "$contextPath/opds/v2/catalog", host = "opds.example", port = 443, scheme = "https", contextPath = contextPath), block)

  private fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = Opds2Controller::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size && (args.isEmpty() || args[0] == null || it.parameterTypes[0].isInstance(args[0])) }
    m.isAccessible = true
    return try {
      m.invoke(controller, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  override fun cases() {
    func("getLibrariesRecommended") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
        OpdsSupport.thumbnails(db)
      }
      case("catalog admin") { web { json(controller.getLibrariesRecommended(admin, null)) } }
      case("catalog limited") { web { json(controller.getLibrariesRecommended(limited, null)) } }
      case("library L1") { web { json(controller.getLibrariesRecommended(admin, "L1")) } }
      case("library L2 restricted") { web { json(controller.getLibrariesRecommended(restricted, "L2")) } }
      case("context path") { web("/komga") { json(controller.getLibrariesRecommended(admin, "L2")) } }
    }
    func("getKeepReading") {
      case("admin") { web { json(controller.getKeepReading(admin, null, page20)) } }
      case("library") { web { json(controller.getKeepReading(admin, "L2", page20)) } }
    }
    func("getOnDeck") {
      case("admin") { web { json(controller.getOnDeck(admin, null, page20)) } }
      case("library") { web { json(controller.getOnDeck(admin, "L1", page20)) } }
    }
    func("getLatestBooks") {
      case("admin") { web { json(controller.getLatestBooks(admin, null, page20)) } }
      case("paged") { web { json(controller.getLatestBooks(admin, "L1", page1)) } }
      case("limited, forbidden library") { web { json(controller.getLatestBooks(limited, "L2", page20)) } }
    }
    func("getLatestSeries") {
      case("admin") { web { json(controller.getLatestSeries(admin, null, page20)) } }
      case("paged") { web { json(controller.getLatestSeries(admin, null, page1)) } }
      case("unknown library") { web { json(controller.getLatestSeries(admin, "LX", page20)) } }
    }
    func("getLibrariesBrowse") {
      case("admin") { web { json(controller.getLibrariesBrowse(admin, null, null, page20)) } }
      case("publisher") { web { json(controller.getLibrariesBrowse(admin, null, listOf("Shueisha"), page20)) } }
      case("library paged") { web { json(controller.getLibrariesBrowse(admin, "L1", null, page1)) } }
      case("restricted") { web { json(controller.getLibrariesBrowse(restricted, null, null, page20)) } }
    }
    func("getLibrariesCollections") {
      case("admin") { web { json(controller.getLibrariesCollections(admin, null, page20)) } }
      case("library L2") { web { json(controller.getLibrariesCollections(admin, "L2", page20)) } }
    }
    func("getOneCollection") {
      case("C1") { web { json(controller.getOneCollection(admin, "C1", page20)) } }
      case("C1 paged") { web { json(controller.getOneCollection(admin, "C1", page1)) } }
      case("unknown") { web { json(controller.getOneCollection(admin, "CX", page20)) } }
    }
    func("getLibrariesReadLists") {
      case("admin") { web { json(controller.getLibrariesReadLists(admin, null, page20)) } }
      case("limited") { web { json(controller.getLibrariesReadLists(limited, null, page20)) } }
    }
    func("getOneReadList") {
      case("R1") { web { json(controller.getOneReadList(admin, "R1", page20)) } }
      case("R1 limited") { web { json(controller.getOneReadList(limited, "R1", page20)) } }
      case("unknown") { web { json(controller.getOneReadList(admin, "RX", page20)) } }
    }
    func("checkLibraryAccess") {
      fun check(
        id: String?,
        p: KomgaPrincipal,
      ) = (call("checkLibraryAccess", id, p) as Pair<*, *>).let { (l, ids) -> listOf((l as org.gotson.komga.domain.model.Library?)?.id, (ids as Collection<*>?)?.toList()) }
      case("no library") { check(null, admin) }
      case("no library limited") { check(null, limited) }
      case("allowed") { check("L1", limited) }
      case("forbidden") { check("L2", limited) }
      case("unknown") { check("LX", admin) }
    }
    func("getOneSeries") {
      case("S1") { web { json(controller.getOneSeries(admin, "S1", null, page20)) } }
      case("S1 tag") { web { json(controller.getOneSeries(admin, "S1", "dark", page20)) } }
      case("S2 restricted") { web { json(controller.getOneSeries(restricted, "S2", null, page20)) } }
      case("unknown") { web { json(controller.getOneSeries(admin, "SX", null, page20)) } }
    }
    func("getSearchResults") {
      case("no query") { web { json(controller.getSearchResults(admin, null)) } }
      case("query, empty index") { web { json(controller.getSearchResults(admin, "one  piece")) } }
    }
    func("getAuthDocument") {
      case("document") { web { json(controller.getAuthDocument()) } }
    }
    func("getBookPage") {
      case("page 1") { WebOracle.describeEntity(controller.getBookPage(admin, ServletWebRequest(WebOracle.request()), "B7", 1, null)) }
      case("page 0") { WebOracle.describeEntity(controller.getBookPage(admin, ServletWebRequest(WebOracle.request()), "B7", 0, null)) }
    }
    func("getWebPubManifest") {
      case("B1") { web { json(controller.getWebPubManifest(admin, "B1")) } }
      case("B8") { web { json(controller.getWebPubManifest(admin, "B8")) } }
    }
    func("getWebPubManifestEpub") {
      case("B4") { web { json(controller.getWebPubManifestEpub(admin, "B4")) } }
    }
    func("getWebPubManifestPdf") {
      case("B5") { web { json(controller.getWebPubManifestPdf(admin, "B5")) } }
    }
    func("getWebPubManifestDivina") {
      case("B2") { web { json(controller.getWebPubManifestDivina(admin, "B2")) } }
    }
    func("linkStart") {
      case("link") { web { json(call("linkStart")) } }
    }
    func("linkSearch") {
      case("link") { web("/ctx") { json(call("linkSearch")) } }
    }
    func("uriBuilder") {
      case("path") { web { (call("uriBuilder", "a b/c") as UriComponentsBuilder).toUriString() } }
    }
    func("linkPage") {
      val builder = { UriComponentsBuilder.fromUriString("https://h/opds/v2/x") }
      case("first") { json(call("linkPage", builder(), PageImpl(listOf(1), PageRequest.of(0, 1), 3))) }
      case("middle") { json(call("linkPage", builder(), PageImpl(listOf(1), PageRequest.of(1, 1), 3))) }
      case("single") { json(call("linkPage", builder(), PageImpl(listOf(1)))) }
    }
    func("linkSelf@119") {
      case("path") { web { json(call("linkSelf", "series", null)) } }
      case("path and type") { web { json(call("linkSelf", "series", "application/opds+json")) } }
    }
    func("linkSelf@124") {
      case("builder") { json(call("linkSelf", UriComponentsBuilder.fromUriString("https://h/x?y=1"), "t")) }
    }
    func("getLibrariesFeedGroup") {
      case("admin") { web { json(call("getLibrariesFeedGroup", admin)) } }
      case("limited") { web { json(call("getLibrariesFeedGroup", limited)) } }
    }
    func("getLibraryNavigation") {
      case("all admin") { web { json(call("getLibraryNavigation", InterfacesData.admin, null)) } }
      case("L2 admin") { web { json(call("getLibraryNavigation", InterfacesData.admin, "L2")) } }
      case("L1 limited") { web { json(call("getLibraryNavigation", InterfacesData.limited, "L1")) } }
    }
    func("toWPLinkDto@905") {
      case("library") { web { json(call("toWPLinkDto", db.libraryDao.findById("L2"))) } }
    }
    func("toWPLinkDto@912") {
      case("series") { web { json(call("toWPLinkDto", db.seriesDtoDao.findByIdOrNull("S3", "U1"))) } }
    }
    func("toWPLinkDto@919") {
      case("collection") { web { json(call("toWPLinkDto", db.seriesCollectionDao.findByIdOrNull("C1", SearchContext.empty()))) } }
    }
    func("toWPLinkDto@926") {
      case("read list") { web { json(call("toWPLinkDto", db.readListDao.findByIdOrNull("R1", SearchContext.empty()))) } }
    }
  }
}
