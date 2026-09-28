package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.EpubTocEntry
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.MediaFile
import org.gotson.komga.domain.model.MediaProfile
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.interfaces.api.WebPubGenerator
import org.gotson.komga.interfaces.api.dto.WPMetadataDto
import org.gotson.komga.interfaces.api.rest.dto.AuthorDto
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

/** Shared cases of the WebPubGenerator and OpdsGenerator oracles, mirrored by test/unit/interfaces/api/webpub-cases.ts */
abstract class WebPubCases : OracleTest() {
  protected val db = OracleDb()
  protected val services = InterfacesServices(db)
  protected abstract val generator: WebPubGenerator

  protected fun json(v: Any?): String = WebOracle.stableText(WebOracle.mapper.writeValueAsString(v))

  protected fun <T> web(
    contextPath: String = "",
    block: () -> T,
  ): T = WebOracle.withRequest(WebOracle.request(uri = "$contextPath/api/v1/books/B1/manifest", host = "komga.example.org", port = 8443, scheme = "https", contextPath = contextPath), block)

  protected fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m =
      generateSequence<Class<*>>(generator.javaClass) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }
        .first { it.name == name && it.parameterCount == args.size }
    m.isAccessible = true
    return try {
      m.invoke(generator, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  protected fun book(id: String): BookDto = db.bookDtoDao.findByIdOrNull(id, "U1")!!

  protected fun media(id: String): Media = db.mediaDao.findById(id)

  protected fun seriesMetadata(id: String): SeriesMetadata = db.seriesMetadataDao.findById(book(id).seriesId)

  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  protected val epubMedia =
    Media(
      Media.Status.READY,
      "application/epub+zip",
      files =
        listOf(
          MediaFile("OEBPS/ch 1.xhtml", "application/xhtml+xml", MediaFile.SubType.EPUB_PAGE, 10),
          MediaFile("OEBPS/style.css", "text/css", MediaFile.SubType.EPUB_ASSET),
          MediaFile("OEBPS/ç#2.xhtml", "application/xhtml+xml", MediaFile.SubType.EPUB_PAGE),
          MediaFile("OEBPS/img/cover.jpg", "image/jpeg", MediaFile.SubType.EPUB_ASSET),
          MediaFile("META-INF/container.xml", "application/xml", null),
        ),
      extension =
        MediaExtensionEpub(
          toc =
            listOf(
              EpubTocEntry("Chapter 1", "OEBPS/ch 1.xhtml#part-1", listOf(EpubTocEntry("Sub", "OEBPS/ch 1.xhtml"))),
              EpubTocEntry("No link", null),
              EpubTocEntry("Hash only", "#top"),
            ),
          landmarks = listOf(EpubTocEntry("Cover", "OEBPS/img/cover.jpg")),
          pageList = listOf(EpubTocEntry("1", "OEBPS/ch 1.xhtml#p1"), EpubTocEntry("2", "OEBPS/ç#2.xhtml")),
          isFixedLayout = true,
        ),
      bookId = "B4",
      createdDate = date,
    )

  protected val authors =
    listOf(
      AuthorDto("A1", "author"),
      AuthorDto("W1", "writer"),
      AuthorDto("P1", "penciller"),
      AuthorDto("P2", "penciler"),
      AuthorDto("T1", "translator"),
      AuthorDto("E1", "editor"),
      AuthorDto("AR", "artist"),
      AuthorDto("I1", "illustrator"),
      AuthorDto("L1", "letterer"),
      AuthorDto("C1", "colorist"),
      AuthorDto("IN", "inker"),
      AuthorDto("CV", "cover"),
      AuthorDto("A2", "author"),
    )

  protected fun commonCases() {
    func("toBasePublicationDto") {
      case("setup") { InterfacesData.setup(db) }
      listOf("B1", "B4", "B5", "B6").forEach { id -> case(id) { web { json(call("toBasePublicationDto", book(id))) } } }
      case("context path") { web("/komga") { json(call("toBasePublicationDto", book("B1"))) } }
    }
    func("getDefaultMediaType") {
      case("media type") { call("getDefaultMediaType").toString() }
    }
    func("buildThumbnailLinkDtos") {
      case("B1") { web { json(call("buildThumbnailLinkDtos", "B1")) } }
      case("special id") { web { json(call("buildThumbnailLinkDtos", "a b/c")) } }
    }
    func("toManifestDivina") {
      listOf("B1", "B2", "B3", "B5").forEach { id -> case(id) { web { json(generator.toManifestDivina(book(id), media(id), seriesMetadata(id))) } } }
      case("pages without dimension, unknown type") {
        val m = Media(Media.Status.READY, "application/zip", listOf(BookPage("a.jxl", "image/jxl"), BookPage("b.avif", "image/avif", Dimension(1, 2)), BookPage("c.gif", "image/gif")), bookId = "B1")
        web { json(generator.toManifestDivina(book("B1"), m, seriesMetadata("B1").copy(readingDirection = SeriesMetadata.ReadingDirection.WEBTOON))) }
      }
    }
    func("toManifestPdf") {
      case("B5") { web { json(generator.toManifestPdf(book("B5"), media("B5"), seriesMetadata("B5").copy(readingDirection = SeriesMetadata.ReadingDirection.RIGHT_TO_LEFT))) } }
      case("no page") { web { json(generator.toManifestPdf(book("B5"), media("B5").copy(pageCount = 0), seriesMetadata("B5"))) } }
    }
    func("toManifestEpub") {
      case("B4 without extension") { web { json(generator.toManifestEpub(book("B4"), media("B4"), seriesMetadata("B4"))) } }
      case("B4 with extension") { web { json(generator.toManifestEpub(book("B4"), epubMedia, seriesMetadata("B4").copy(readingDirection = SeriesMetadata.ReadingDirection.VERTICAL))) } }
      case("reflowable") { web { json(generator.toManifestEpub(book("B4"), epubMedia.copy(extension = (epubMedia.extension as MediaExtensionEpub).copy(isFixedLayout = false)), seriesMetadata("B4"))) } }
      case("proxy extension") {
        db.mediaDao.update(epubMedia)
        web { json(generator.toManifestEpub(book("B4"), media("B4"), seriesMetadata("B4"))) }
      }
    }
    func("toWPLinkDto") {
      case("toc entries") {
        web {
          val builder = ServletUriComponentsBuilder.fromCurrentContextPath().path("x/")
          json((epubMedia.extension as MediaExtensionEpub).toc.map { call("toWPLinkDto", it, builder) })
        }
      }
    }
    func("toWPMetadataDto") {
      listOf("B1", "B4", "B6").forEach { id -> case(id) { web { json(call("toWPMetadataDto", book(id))) } } }
    }
    func("getBookSeriesLink") {
      case("B1") { web { json(call("getBookSeriesLink", book("B1"))) } }
    }
    func("withSeriesMetadata") {
      listOf(null, SeriesMetadata.ReadingDirection.LEFT_TO_RIGHT, SeriesMetadata.ReadingDirection.RIGHT_TO_LEFT, SeriesMetadata.ReadingDirection.VERTICAL, SeriesMetadata.ReadingDirection.WEBTOON).forEach { d ->
        case("$d") { json(call("withSeriesMetadata", WPMetadataDto(title = "t"), seriesMetadata("B1").copy(readingDirection = d, language = "fr-CA"))) }
      }
    }
    func("withAuthors") {
      case("all roles") { json(call("withAuthors", WPMetadataDto(title = "t"), authors)) }
      case("no author") { json(call("withAuthors", WPMetadataDto(title = "t"), emptyList<AuthorDto>())) }
    }
    func("getExtraLinkProperties") {
      case("properties") { web { json(call("getExtraLinkProperties")) } }
    }
    func("getExtraLinks") {
      case("B1") { web { json(call("getExtraLinks", "B1")) } }
    }
    func("toWPLinkDtos") {
      listOf("B1", "B4", "B5").forEach { id -> case(id) { web { json(call("toWPLinkDtos", book(id), ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("api", "v1"))) } } }
      case("epub divina compatible") {
        db.mediaDao.update(media("B6").copy(epubDivinaCompatible = true))
        web { json(call("toWPLinkDtos", book("B6"), UriComponentsBuilder.fromUriString("http://h/p"))) }
      }
      case("unknown media type") {
        db.mediaDao.update(media("B3").copy(mediaType = "application/x-unknown"))
        web { json(call("toWPLinkDtos", book("B3"), UriComponentsBuilder.fromUriString("http://h/p"))) }
      }
    }
    func("mediaProfileToWebPub") {
      (MediaProfile.entries + listOf(null)).forEach { p -> case("$p") { call("mediaProfileToWebPub", p) } }
    }
  }
}

class WebPubGeneratorOracleTest : WebPubCases() {
  override val generator by lazy { services.webPubGenerator }

  override fun cases() = commonCases()
}
