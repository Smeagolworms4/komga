package org.gotson.komga.oracle.interfaces.api.kobo

import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.KomgaSyncToken
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.service.SyncPointLifecycle
import org.gotson.komga.infrastructure.kobo.KoboProxy
import org.gotson.komga.infrastructure.kobo.KomgaSyncTokenGenerator
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.api.kobo.KoboController
import org.gotson.komga.interfaces.api.kobo.dto.AuthDto
import org.gotson.komga.interfaces.api.kobo.dto.KoboBookMetadataDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.FakeKoboStore
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.gotson.komga.oracle.interfaces.OpdsSupport
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import org.springframework.web.util.UriBuilder
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class KoboControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val tokens = KomgaSyncTokenGenerator(WebOracle.mapper)
  private val store = FakeKoboStore()
  private val proxy by lazy { KoboProxy(WebOracle.mapper, tokens, services.settings).also { store.install(it) } }
  private val controller by lazy {
    KoboController(
      proxy,
      services.kepubConverter,
      SyncPointLifecycle(db.syncPointDao),
      db.syncPointDao,
      tokens,
      db.properties,
      db.koboDtoDao,
      WebOracle.mapper,
      services.commonBookController,
      services.bookLifecycle,
      db.bookDao,
      db.thumbnailBookDao,
      db.readProgressDao,
      services.imageConverter,
      db.mediaDao,
      services.contentRestrictionChecker,
      WebOracle.mapper,
    )
  }
  private val date = LocalDateTime.of(2020, 1, 2, 3, 4, 5)
  private val apiKey = ApiKey(id = "K1", userId = "U1", key = "hashed", comment = "My Kobo", createdDate = date)
  private val admin by lazy { KomgaPrincipal(InterfacesData.admin, apiKey = apiKey, name = "masked") }
  private val limited by lazy { KomgaPrincipal(InterfacesData.limited) }
  private val restricted by lazy { KomgaPrincipal(InterfacesData.restricted) }
  private var lastToken: String? = null

  private fun <T> kobo(
    path: String = "/kobo/TOKEN/v1/library/sync",
    headers: List<Pair<String, String>> = emptyList(),
    method: String = "GET",
    block: () -> T,
  ): T = WebOracle.withRequest(WebOracle.request(method = method, uri = path, headers = headers, host = "komga.local", port = 25600), block)

  private fun json(v: Any?) = WebOracle.stableText(WebOracle.mapper.writeValueAsString(v))

  private fun describe(e: ResponseEntity<*>): List<Any?> {
    val d = WebOracle.describeEntity(e)
    val body = e.body
    val b =
      when (body) {
        is StreamingResponseBody -> ByteArrayOutputStream().also { body.writeTo(it) }.toByteArray()
        is ByteArray -> body
        else -> json(body)
      }
    return listOf(d[0], d[1], b)
  }

  /** sync response: status, sync header, decoded sync token (ids neutralised), body */
  private fun sync(
    principal: KomgaPrincipal,
    token: String? = lastToken,
  ): Any? {
    val e =
      try {
        kobo(headers = listOfNotNull(token?.let { "X-Kobo-SyncToken" to it })) { controller.syncLibrary(principal, "TOKEN") }
      } catch (ex: Exception) {
        return listOf(ex::class.java.simpleName, ex.message)
      }
    lastToken = e.headers.getFirst("X-Kobo-SyncToken")
    return listOf(e.statusCode.value(), e.headers.getFirst("X-Kobo-Sync"), stable(lastToken?.let { tokens.fromBase64(it) }), json(e.body), store.drain())
  }

  private fun stateBody(
    bookId: String,
    status: String = "Reading",
    progress: Int? = 50,
    contentProgress: Int? = 25,
    source: String? = "OEBPS/ch 1.xhtml",
    locationType: String = "KoboSpan",
  ): ByteArray =
    """
    {"ReadingStates":[{"EntitlementId":"$bookId","LastModified":"2023-05-06T07:08:09Z",
      "CurrentBookmark":{"LastModified":"2023-05-06T07:08:09Z"${progress?.let { ",\"ProgressPercent\":$it" } ?: ""}${contentProgress?.let { ",\"ContentSourceProgressPercent\":$it" } ?: ""}${source?.let { ",\"Location\":{\"Value\":\"kobo.2.1\",\"Type\":\"$locationType\",\"Source\":\"$it\"}" } ?: ""}},
      "Statistics":{"LastModified":"2023-05-06T07:08:09Z","SpentReadingMinutes":3,"RemainingTimeMinutes":7},
      "StatusInfo":{"LastModified":"2023-05-06T07:08:09Z","Status":"$status","TimesStartedReading":1}}]}
    """.trimIndent().toByteArray()

  private fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = KoboController::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size && (args.isEmpty() || args[0] == null || it.parameterTypes[0].isInstance(args[0])) }
    m.isAccessible = true
    return try {
      m.invoke(controller, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, e.message?.substringBefore("\n")?.replace(tempDir.toString(), "<tmp>"))
    }

  override fun cases() {
    func("ping") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
        OpdsSupport.thumbnails(db)
        db.komgaUserDao.insert(apiKey)
      }
      case("pong") { controller.ping() }
    }
    func("initialization") {
      case("proxy disabled: native resources") { kobo("/kobo/TOKEN/v1/initialization") { describe(controller.initialization("TOKEN")) } }
      case("proxy enabled") {
        services.settings.koboProxy = true
        store.respond(200, """{"Resources":{"custom":"value","image_host":"x"}}""")
        kobo("/kobo/TOKEN/v1/initialization") { describe(controller.initialization("TOKEN")) }.let { listOf(it, store.drain()) }
      }
      case("proxy without resources") {
        store.respond(200, """{"Other":1}""")
        kobo("/kobo/T%20K/v1/initialization") { describe(controller.initialization("T K")) }.let { listOf(it[0], it[1], store.drain()) }
      }
      case("proxy unauthorized") {
        store.respond(401, "{}")
        attempt { kobo("/kobo/TOKEN/v1/initialization") { describe(controller.initialization("TOKEN")) } }.let { listOf(it, store.drain()) }
      }
      case("proxy error") {
        store.respond(500, "{}")
        kobo("/kobo/TOKEN/v1/initialization") { describe(controller.initialization("TOKEN")) }.let { listOf(it[0], it[1], store.drain()) }
      }
    }
    func("authDevice") {
      case("proxy") {
        store.respond(200, """{"AccessToken":"a"}""")
        kobo("/kobo/TOKEN/v1/auth/device", method = "POST") { controller.authDevice("""{"UserKey":"uk"}""".toByteArray()).let { r -> if (r is ResponseEntity<*>) describe(r) else json(r) } }.let { listOf(it, store.drain()) }
      }
      case("fallback") {
        services.settings.koboProxy = false
        val a = kobo("/kobo/TOKEN/v1/auth/device", method = "POST") { controller.authDevice("""{"UserKey":"uk","DeviceId":"d"}""".toByteArray()) } as AuthDto
        listOf(a.accessToken.length, a.refreshToken.length, a.trackingId.length, a.userKey, a.tokenType)
      }
      case("fallback without user key") { (kobo("/kobo/TOKEN/v1/auth/device", method = "POST") { controller.authDevice("{}".toByteArray()) } as AuthDto).userKey }
      case("invalid body") { attempt { kobo("/kobo/TOKEN/v1/auth/device", method = "POST") { controller.authDevice("not json".toByteArray()) } } }
    }
    func("analyticsGetTests") {
      case("user key") { json(controller.analyticsGetTests("uk")) }
      case("no user key") { json(controller.analyticsGetTests(null)) }
    }
    func("syncLibrary") {
      case("first sync") { sync(admin, null) }
      case("nothing changed") { sync(admin) }
      case("changes") {
        db.readProgressDao.save(ReadProgress("B4", "U1", 2, false, date.plusDays(5), createdDate = date))
        db.bookDao.update(db.bookDao.findByIdOrNull("B6")!!.copy(fileSize = 42))
        sync(admin)
      }
      case("item limit: first page") {
        db.properties.kobo.syncItemLimit = 1
        sync(limited, null)
      }
      case("item limit: continue") { sync(limited) }
      case("item limit: continue again") { sync(limited) }
      case("item limit: done") {
        db.properties.kobo.syncItemLimit = 100
        sync(limited)
      }
      case("token of another user") { sync(restricted) }
      case("invalid token") { sync(restricted, "garbage") }
      case("proxy merge") {
        services.settings.koboProxy = true
        store.respond(200, """[{"NewEntitlement":{"Store":true}}]""", "X-Kobo-SyncToken" to "store-raw", "X-Kobo-Sync" to "continue")
        sync(admin, tokens.toBase64(KomgaSyncToken(rawKoboSyncToken = "raw-in")))
      }
      case("proxy failure") {
        store.respond(500, "{}")
        sync(admin, null)
      }
    }
    func("getBookMetadata") {
      case("book") { kobo { describe(controller.getBookMetadata(admin, "TOKEN", "B4")) } }
      case("unknown book, proxy") {
        store.respond(200, """[{"Proxied":true}]""")
        kobo("/kobo/TOKEN/v1/library/BX/metadata") { describe(controller.getBookMetadata(admin, "TOKEN", "BX")) }.let { listOf(it, store.drain()) }
      }
      case("restricted") {
        services.settings.koboProxy = false
        attempt { kobo { describe(controller.getBookMetadata(restricted, "TOKEN", "B4")) } }
      }
      case("unknown book, no proxy") { attempt { kobo { describe(controller.getBookMetadata(admin, "TOKEN", "BX")) } } }
    }
    func("getState") {
      case("in progress") { kobo { describe(controller.getState(admin, "B2")) } }
      case("no progress") { kobo { describe(controller.getState(admin, "B8")) } }
      case("unknown") { attempt { kobo { describe(controller.getState(admin, "BX")) } } }
      case("limited") { attempt { kobo { describe(controller.getState(limited, "B4")) } } }
      case("unknown, proxy") {
        services.settings.koboProxy = true
        store.respond(200, "[]")
        kobo("/kobo/TOKEN/v1/library/BX/state") { describe(controller.getState(admin, "BX")) }.let { listOf(it, store.drain()) }
      }
    }
    func("updateState") {
      case("epub positions") {
        services.settings.koboProxy = false
        db.mediaDao.update(
          db.mediaDao.findById("B8").copy(
            extension =
              MediaExtensionEpub(
                positions =
                  listOf(
                    R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0F, position = 1, totalProgression = 0F), koboSpan = "kobo.1.1"),
                    R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 0.5F, position = 2, totalProgression = 0.5F)),
                    R2Locator("OEBPS/ch 1.xhtml", "application/xhtml+xml", locations = R2Locator.Location(progression = 1F, position = 3, totalProgression = 1F)),
                  ),
              ),
          ),
        )
        "ok"
      }
      case("reading") { kobo { describe(controller.updateState(admin, "B8", stateBody("B8"), "dev")) }.let { listOf(it, stable(db.readProgressDao.findByBookIdAndUserIdOrNull("B8", "U1"))) } }
      case("exact position, other location type") {
        kobo { describe(controller.updateState(admin, "B8", stateBody("B8", contentProgress = 50, locationType = "Other"))) }.let { listOf(it, stable(db.readProgressDao.findByBookIdAndUserIdOrNull("B8", "U1"))) }
      }
      case("finished") { kobo { describe(controller.updateState(admin, "B8", stateBody("B8", "Finished"))) }.let { listOf(it, stable(db.readProgressDao.findByBookIdAndUserIdOrNull("B8", "U1"))) } }
      case("no extension") { kobo { describe(controller.updateState(admin, "B4", stateBody("B4"))) } }
      case("finished without extension") { attempt { kobo { describe(controller.updateState(admin, "B4", stateBody("B4", "Finished"))) } } }
      case("missing location") { attempt { kobo { describe(controller.updateState(admin, "B8", stateBody("B8", source = null))) } } }
      case("missing content progress") { attempt { kobo { describe(controller.updateState(admin, "B8", stateBody("B8", contentProgress = null))) } } }
      case("no reading state") { attempt { kobo { describe(controller.updateState(admin, "B8", """{"ReadingStates":[]}""".toByteArray())) } } }
      case("invalid body") { attempt { kobo { describe(controller.updateState(admin, "B8", "{".toByteArray())) } } }
      case("unknown book") { attempt { kobo { describe(controller.updateState(admin, "BX", stateBody("BX"))) } } }
      case("restricted") { attempt { kobo { describe(controller.updateState(restricted, "B4", stateBody("B4"))) } } }
      case("events") { services.drainEvents() }
    }
    func("getBookFile") {
      case("epub") { kobo { describe(controller.getBookFile(admin, "B8", false)) } }
      case("kepub, converter unavailable") { attempt { kobo { describe(controller.getBookFile(admin, "B8", true)) } } }
      case("kepub, converted") {
        val script = tempDir.resolve("bin").createDirectories().resolve("kepubify")
        script.writeText("#!/bin/sh\ncp \"$1\" \"$3\"\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        services.kepubConverter.configureKepubify(script.toString())
        kobo { describe(controller.getBookFile(admin, "B8", true)) }
      }
      case("kepub, cached") { kobo { describe(controller.getBookFile(admin, "B8", true)) } }
      case("kepub of a cbz") { attempt { kobo { describe(controller.getBookFile(admin, "B7", true)) } } }
      case("kepub, unknown") { attempt { kobo { describe(controller.getBookFile(admin, "BX", true)) } } }
      case("kepub, restricted") { attempt { kobo { describe(controller.getBookFile(restricted, "B4", true)) } } }
    }
    func("computeCacheKey") {
      case("book") { call("computeCacheKey", db.bookDao.findByIdOrNull("B8")!!.copy(fileLastModified = LocalDateTime.of(2021, 2, 3, 4, 5, 6, 7000))) }
    }
    func("getBookCover") {
      case("thumbnail") { kobo { describe(controller.getBookCover(admin, "TB7", "100", "200", null, "false")) } }
      case("unknown thumbnail") { attempt { kobo { describe(controller.getBookCover(admin, "TX", "100", "200", "85", "true")) } } }
      case("unknown thumbnail, proxy") {
        services.settings.koboProxy = true
        kobo { describe(controller.getBookCover(admin, "TX", "100", "200", "85", "true")) }
      }
      case("restricted") { attempt { kobo { describe(controller.getBookCover(limited, "TB7", "1", "1", null, null)) } } }
    }
    func("catchAll") {
      case("proxy") {
        store.respond(200, """{"a":[1,2]}""")
        kobo("/kobo/TOKEN/v1/user/profile", method = "PUT") { describe(controller.catchAll("""{"x":1}""".toByteArray())) }.let { listOf(it, store.drain()) }
      }
      case("no proxy") {
        services.settings.koboProxy = false
        kobo("/kobo/TOKEN/v1/user/profile") { describe(controller.catchAll(null)) }
      }
    }
    func("getDownloadUrlBuilder") {
      case("url") { kobo { (call("getDownloadUrlBuilder", "a b") as UriBuilder).build("B1", true).toString() } }
    }
    func("withDownloadUrls") {
      val builder = { kobo { call("getDownloadUrlBuilder", "TOKEN") as UriBuilder } }
      val metadata = { id: String -> db.koboDtoDao.findBookMetadataByIds(listOf(id)).first() }
      case("epub, no converter") {
        services.kepubConverter.configureKepubify(null)
        json(call("withDownloadUrls", metadata("B4"), builder()))
      }
      case("kepub") { json(call("withDownloadUrls", metadata("B6"), builder())) }
      case("pre-paginated") { json(call("withDownloadUrls", metadata("B4").copy(isPrePaginated = true), builder())) }
      case("kepub size from projection") {
        db.bookProjectionDao.save(org.gotson.komga.domain.model.BookProjection("B4", org.gotson.komga.domain.model.KEPUB_DEFAULT, 777))
        services.kepubConverter.configureKepubify("true")
        json(call("withDownloadUrls", metadata("B4"), builder()))
      }
    }
    func("getSyncPointVerified") {
      case("null") { call("getSyncPointVerified", null, "U1") }
      case("unknown") { call("getSyncPointVerified", "SPX", "U1") }
      case("other user") {
        val sp = SyncPointLifecycle(db.syncPointDao).createSyncPoint(InterfacesData.admin, null, null)
        listOf(call("getSyncPointVerified", sp.id, "U2"), (call("getSyncPointVerified", sp.id, "U1") as org.gotson.komga.domain.model.SyncPoint?)?.id == sp.id)
      }
    }
    func("getMetadataForRemovedBook") {
      case("book") { json(call("getMetadataForRemovedBook", "B9")) }
    }
    func("getEmptyReadProgressForBook@817") {
      case("book") { json(call("getEmptyReadProgressForBook", db.bookDao.findByIdOrNull("B1")!!)) }
    }
    func("getEmptyReadProgressForBook@835") {
      case("id and date") { json(call("getEmptyReadProgressForBook", "B1", ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(2)))) }
    }
  }
}
