package org.gotson.komga.oracle.interfaces.api.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.oracle.Canon
import org.gotson.komga.oracle.Canonical
import org.gotson.komga.oracle.OracleDb
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/**
 * Helpers of the oracle tests of `interfaces/api/rest` (controllers and DTOs), mirrored by
 * test/unit/interfaces/api/rest/rest-oracle.ts in KomgaJS.
 */
object RestOracle {
  /** Spring Boot's ObjectMapper (same configuration as OracleDb.mapper, without a database) */
  val mapper: ObjectMapper by lazy {
    Jackson2ObjectMapperBuilder
      .json()
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
      .featuresToEnable(
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES,
      ).build()
  }

  /** JSON body produced by Spring MVC for [v] */
  fun json(v: Any?): String = mapper.writeValueAsString(v)

  inline fun <reified T> read(json: String): T = mapper.readValue(json, T::class.java)

  /** A 3x2 RGB PNG (same bytes in the TypeScript twin) */
  val PNG: ByteArray = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAG0lEQVR42gXBAQEAAACCIOc0x0lOD4BGEthMOyg0BOFvkyVYAAAAAElFTkSuQmCC")

  val FIXED: LocalDateTime = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

  fun user(
    id: String,
    roles: Set<UserRoles> = emptySet(),
    sharedLibrariesIds: Set<String> = emptySet(),
    sharedAllLibraries: Boolean = true,
    restrictions: ContentRestrictions = ContentRestrictions(),
  ) = KomgaUser(
    email = "$id@example.org",
    password = "pwd-$id",
    roles = roles,
    sharedLibrariesIds = sharedLibrariesIds,
    sharedAllLibraries = sharedAllLibraries,
    restrictions = restrictions,
    id = id,
    createdDate = FIXED,
  )

  fun principal(user: KomgaUser) = KomgaPrincipal(user)

  /**
   * Canonical form of a ResponseEntity: [status code, headers (lower-case names, sorted), body].
   * A body that is a Resource or a StreamingResponseBody is read to bytes.
   */
  fun entity(e: ResponseEntity<*>): Canonical {
    val headers =
      e.headers
        .toSingleValueMap()
        .keys
        .map { it.lowercase() }
        .sorted()
        .map { name -> listOf(name, e.headers[name]) }
    val body =
      when (val b = e.body) {
        is Resource -> b.contentAsByteArray
        is StreamingResponseBody -> ByteArrayOutputStream().also { b.writeTo(it) }.toByteArray()
        else -> b
      }
    return Canonical(listOf(e.statusCode.value(), Canon.dump(headers), Canon.dump(body)))
  }

  /** Canonical form of the exception thrown by [block] (its type and message), null if none */
  fun thrown(block: () -> Any?): Canonical =
    Canonical(
      try {
        block()
        null
      } catch (e: Throwable) {
        Canon.dumpThrowable(e)
      },
    )

  /** The body of [e] as bytes (Resource or StreamingResponseBody read) */
  fun bodyBytes(e: ResponseEntity<*>): ByteArray =
    when (val b = e.body) {
      is Resource -> b.contentAsByteArray
      is StreamingResponseBody -> ByteArrayOutputStream().also { b.writeTo(it) }.toByteArray()
      is ByteArray -> b
      else -> error("not bytes")
    }

  /** A zip file (bytes) as [size, entry names from the central directory] (same helper in the TypeScript twin) */
  fun zipSummary(bytes: ByteArray): List<Any> {
    val names = mutableListOf<String>()
    val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    var i = 0
    while (i + 46 <= bytes.size) {
      if (b.getInt(i) == 0x02014b50) {
        val n = b.getShort(i + 28).toInt() and 0xffff
        names.add(String(bytes, i + 46, n, Charsets.UTF_8))
        i += 46 + n
      } else {
        i++
      }
    }
    return listOf(bytes.size, names)
  }

  /** Executes raw SQL statements on the main database of [db] (fixtures with fixed dates) */
  fun sql(
    db: OracleDb,
    vararg statements: String,
  ) {
    db.dataSource.connection.createStatement().use { st -> statements.forEach { st.execute(it) } }
  }

  private val datedTables =
    listOf(
      "LIBRARY", "SERIES", "SERIES_METADATA", "BOOK", "BOOK_METADATA", "BOOK_METADATA_AGGREGATION", "MEDIA", "READ_PROGRESS", "COLLECTION",
      "READLIST", "USER", "THUMBNAIL_BOOK", "THUMBNAIL_SERIES", "THUMBNAIL_COLLECTION", "THUMBNAIL_READLIST", "PAGE_HASH", "USER_API_KEY",
    )

  /** Replaces the dates set to "now" by the database (column defaults) with a fixed date (same helper in the TypeScript twin) */
  fun fixNow(db: OracleDb) {
    val statements =
      datedTables.flatMap { t ->
        listOf("CREATED_DATE", "LAST_MODIFIED_DATE").map { c -> "update $t set $c = '2020-01-01 08:00:00' where $c >= '2025'" }
      } + "update READ_PROGRESS_SERIES set LAST_MODIFIED_DATE = '2020-01-01 08:00:00' where LAST_MODIFIED_DATE >= '2025'"
    sql(db, *statements.toTypedArray())
  }

  /** Records the calls made on a fake collaborator: [name, args...] (same recorder in KomgaJS) */
  class Calls {
    val log = mutableListOf<List<Any?>>()

    fun add(
      name: String,
      vararg args: Any?,
    ) {
      log.add(listOf(name) + args.toList())
    }

    /** Canonical form of the calls recorded since the last take, then clears them */
    fun take(): Canonical = Canonical(Canon.stable(Canon.dump(log.toList()))).also { log.clear() }
  }
}

/**
 * WebClient.Builder whose requests are answered by [respond] (status, JSON body) and recorded in [calls]
 * (same fake `fetch` in the TypeScript twin). Codecs use Spring Boot's ObjectMapper, like Komga's builder.
 */
fun fakeWebClient(
  calls: RestOracle.Calls,
  respond: () -> Pair<Int, String>,
): org.springframework.web.reactive.function.client.WebClient.Builder =
  org.springframework.web.reactive.function.client.WebClient
    .builder()
    .codecs { it.defaultCodecs().jackson2JsonDecoder(org.springframework.http.codec.json.Jackson2JsonDecoder(RestOracle.mapper)) }
    .exchangeFunction { request ->
      calls.add(request.method().name(), request.url().toString())
      val (status, body) = respond()
      val httpRequest =
        object : org.springframework.http.HttpRequest {
          override fun getMethod() = request.method()

          override fun getURI() = request.url()

          override fun getHeaders() = request.headers()

          override fun getAttributes() = request.attributes()
        }
      reactor.core.publisher.Mono.just(
        org.springframework.web.reactive.function.client.ClientResponse
          .create(org.springframework.http.HttpStatusCode.valueOf(status))
          .header("Content-Type", "application/json")
          .request(httpRequest)
          // an empty body is sent as no content at all (Content-Length: 0), like a real HTTP response
          .apply { if (body.isNotEmpty()) body(body) }
          .build(),
      )
    }

/** The real TaskEmitter on the tasks database of [db]; published events are recorded in [calls] */
fun taskEmitter(
  db: OracleDb,
  calls: RestOracle.Calls,
) = org.gotson.komga.application.tasks.TaskEmitter(
  db.bookDao,
  io.mockk.mockk(),
  db.tasksDao,
) { calls.add("publishEvent", it) }

/**
 * The capabilities of RefreshBookMetadata are read back from the tasks database as a HashSet of enums, whose order
 * depends on identity hash codes: they are sorted by name (same helper in the TypeScript twin)
 */
private fun sortCapabilities(s: String) =
  Regex("capabilities=\\[([^\\]]*)]").replace(s) { m -> "capabilities=[" + m.groupValues[1].split(", ").filter { it.isNotEmpty() }.sorted().joinToString(", ") + "]" }

/** The tasks submitted since the last call ([toString, priority, groupId]), then empties the queue */
fun tasks(db: OracleDb): List<List<Any?>> =
  db.tasksDao
    .findAll()
    .map { listOf(sortCapabilities(it.toString()), it.priority, it.groupId) }
    .also { db.tasksDao.deleteAll() }
