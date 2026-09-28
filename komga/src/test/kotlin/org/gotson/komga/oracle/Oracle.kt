package org.gotson.komga.oracle

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.Base64
import java.util.Date
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Oracle tests for KomgaJS (https://github.com/Smeagolworms4/komga-js), the TypeScript port of Komga.
 *
 * Each oracle test calls real Komga code and records, for every named case, a canonical description of the
 * result (or of the exception thrown). The records are written as JSON fixtures; the TypeScript twin test
 * (`test/unit/<path>.test.ts` in KomgaJS) replays the same case on the ported function and requires an
 * identical description.
 *
 * Conventions:
 * - one oracle class per Kotlin source file: `org.gotson.komga.oracle.<package>.<File>OracleTest`
 *   covers `org/gotson/komga/<package>/<File>.kt`
 * - cases are grouped by the exact Kotlin function name: `func("name") { case("description") { ... } }`,
 *   recorded as `"name: description"`. Overloads (or same-named functions of several classes in the file)
 *   can be told apart with the declaration line: `func("name@42")`.
 * - no Spring context: the functions are called directly (collaborators are built by hand or mocked).
 *
 * Output: system property `oracle.out` (default: build/oracle), one file per test class:
 * `<package below org.gotson.komga.oracle>/<File>.json`.
 */
abstract class OracleTest {
  private val cases = linkedMapOf<String, Any?>()
  private var prefix: String? = null

  /** Groups the cases of [block] under the Kotlin function [name]. */
  fun func(
    name: String,
    block: () -> Unit,
  ) {
    check(prefix == null) { "Nested func() is not supported" }
    prefix = name
    try {
      block()
    } finally {
      prefix = null
    }
  }

  /** Records the canonical form of [block]'s result, or of the exception it throws. */
  fun case(
    name: String,
    block: () -> Any?,
  ) {
    val key = checkNotNull(prefix) { "case() must be called inside func()" } + ": " + name
    require(!cases.containsKey(key)) { "Duplicate oracle case: $key" }
    cases[key] =
      try {
        Canon.dump(block())
      } catch (e: Throwable) {
        Canon.dumpThrowable(e)
      }
  }

  /** Deterministic content for test files, same generator in KomgaJS test/unit/oracle.ts */
  fun oracleBytes(size: Int): ByteArray = ByteArray(size) { ((it * 31 + 7) and 0xff).toByte() }

  /** Temporary directory for the files of the cases, deleted after the test */
  val tempDir: Path
    get() =
      tempDirOrNull ?: java.nio.file.Files
        .createTempDirectory("oracle")
        .also { tempDirOrNull = it }
  private var tempDirOrNull: Path? = null

  /** Simple name of the exception thrown by [block] (null if none), for errors whose message is not reproducible */
  fun exceptionType(block: () -> Any?): String? =
    try {
      block()
      null
    } catch (e: Throwable) {
      e::class.java.simpleName
    }

  /** Declares the cases. Called once by JUnit. */
  abstract fun cases()

  @Test
  fun oracle() {
    cases()
    check(cases.isNotEmpty()) { "No oracle case" }
  }

  @AfterAll
  fun writeOracle() {
    tempDirOrNull?.toFile()?.deleteRecursively()
    val out = File(System.getProperty("oracle.out") ?: "build/oracle")
    val rel =
      this::class.java.name
        .removePrefix("org.gotson.komga.oracle.")
        .removeSuffix("OracleTest")
        .replace('.', '/')
    val file = File(out, "$rel.json")
    file.parentFile.mkdirs()
    file.writeText(Canon.toJson(cases) + "\n")
  }
}

/**
 * Canonical, language-neutral description of a value, mirrored by test/unit/canon.ts in KomgaJS.
 *
 * - null, String, Boolean: as is; Char: string; numbers: JSON numbers (Float widened exactly to Double);
 *   Long beyond ±2^53: {"@long": "digits"}; NaN / ±Infinity: "NaN", "Infinity", "-Infinity"
 * - enum: {"@enum": "NAME"}
 * - java.time values: {"@time": toString()}; java.util.Date: {"@time": toInstant().toString()}
 * - URL, URI: {"@str": toString()}; Path, File: plain string (a Path is a string in KomgaJS)
 * - ByteArray: {"@bytes": base64}
 * - List, Array, Sequence, Pair, Triple: JSON array (tuples are arrays in KomgaJS);
 *   Set: {"@set": [...]} in iteration order; Map: {"@map": [[k, v], ...]} in iteration order
 * - Unit: {"@unit": true}; Kotlin object: {"@object": simpleName}
 * - Spring Page: {"@page": {content, totalElements, number, size}}
 * - other objects: {"@class": simpleName, <primary constructor properties in declaration order>}
 *   (classes without primary constructor properties: properties from the backing fields, in declaration order)
 * - exception: {"@throws": simpleName, "message": message} (an empty message is recorded as null)
 */
object Canon {
  private const val MAX_SAFE = 9007199254740991L

  fun dumpThrowable(e: Throwable): Map<String, Any?> = linkedMapOf("@throws" to e::class.java.simpleName, "message" to e.message?.ifEmpty { null })

  fun dump(v: Any?): Any? =
    when (v) {
      null -> null
      is String, is Boolean -> v
      is Char -> v.toString()
      is Float -> v.toDouble()
      is Long -> if (v > MAX_SAFE || v < -MAX_SAFE) mapOf("@long" to v.toString()) else v
      is Number -> v
      is Unit -> mapOf("@unit" to true)
      is Enum<*> -> mapOf("@enum" to v.name)
      is Temporal, is TemporalAmount -> mapOf("@time" to v.toString())
      is Date -> mapOf("@time" to v.toInstant().toString())
      is URL, is URI -> mapOf("@str" to v.toString())
      is Path, is File -> v.toString()
      is ByteArray -> mapOf("@bytes" to Base64.getEncoder().encodeToString(v))
      is Page<*> ->
        mapOf(
          "@page" to
            linkedMapOf(
              "content" to v.content.map { dump(it) },
              "totalElements" to v.totalElements,
              "number" to v.number,
              "size" to v.size,
            ),
        )
      is Set<*> -> mapOf("@set" to v.map { dump(it) })
      is Map<*, *> -> mapOf("@map" to v.entries.map { listOf(dump(it.key), dump(it.value)) })
      is Pair<*, *> -> listOf(dump(v.first), dump(v.second))
      is Triple<*, *, *> -> listOf(dump(v.first), dump(v.second), dump(v.third))
      is Iterable<*> -> v.map { dump(it) }
      is Array<*> -> v.map { dump(it) }
      is IntArray -> v.toList()
      is LongArray -> v.map { dump(it) }
      is FloatArray -> v.map { it.toDouble() }
      is DoubleArray -> v.toList()
      is Sequence<*> -> v.toList().map { dump(it) }
      else -> dumpObject(v)
    }

  private fun dumpObject(v: Any): Any? {
    val k = v::class
    if (k.objectInstance != null) return mapOf("@object" to k.java.simpleName)
    val out = linkedMapOf<String, Any?>("@class" to k.java.simpleName)
    val ctorParams = k.primaryConstructor?.parameters?.mapNotNull { it.name } ?: emptyList()
    val props = k.memberProperties.associateBy { it.name }
    val names =
      if (ctorParams.isNotEmpty() && ctorParams.all { props.containsKey(it) }) {
        ctorParams
      } else {
        // declaration order of the backing fields
        generateSequence<Class<*>>(k.java) { it.superclass }
          .takeWhile { it != Any::class.java }
          .toList()
          .reversed()
          .flatMap { c ->
            c.declaredFields
              .filter {
                !java.lang.reflect.Modifier
                  .isStatic(it.modifiers) && !it.isSynthetic && !it.name.contains('$')
              }.map { it.name }
          }.filter { props.containsKey(it) }
          .distinct()
      }
    for (n in names) {
      val p = props.getValue(n)
      p.isAccessible = true
      out[n] =
        try {
          dump(p.getter.call(v))
        } catch (e: Throwable) {
          dumpThrowable(e.cause ?: e)
        }
    }
    return out
  }

  /** Deterministic JSON writer (no dependency on Jackson configuration) */
  fun toJson(v: Any?): String =
    when (v) {
      null -> "null"
      is String -> quote(v)
      is Boolean -> v.toString()
      is Double -> if (v.isNaN() || v.isInfinite()) quote(v.toString()) else v.toString()
      is Number -> v.toString()
      is Map<*, *> -> v.entries.joinToString(",", "{", "}") { "${quote(it.key.toString())}:${toJson(it.value)}" }
      is Iterable<*> -> v.joinToString(",", "[", "]") { toJson(it) }
      else -> quote(v.toString())
    }

  private fun quote(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) {
      when {
        c == '"' -> sb.append("\\\"")
        c == '\\' -> sb.append("\\\\")
        c == '\n' -> sb.append("\\n")
        c == '\r' -> sb.append("\\r")
        c == '\t' -> sb.append("\\t")
        // control characters, and surrogates (a lone surrogate cannot be written as UTF-8)
        c.code < 0x20 || c.isSurrogate() -> sb.append(String.format("\\u%04x", c.code))
        else -> sb.append(c)
      }
    }
    return sb.append('"').toString()
  }
}
