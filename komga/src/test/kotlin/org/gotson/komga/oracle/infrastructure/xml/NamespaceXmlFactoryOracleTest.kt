package org.gotson.komga.oracle.infrastructure.xml

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.gotson.komga.infrastructure.xml.NamespaceXmlFactory
import org.gotson.komga.interfaces.api.opds.v1.dto.ATOM
import org.gotson.komga.interfaces.api.opds.v1.dto.OPDS_PSE
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsAuthor
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsEntryNavigation
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsFeedNavigation
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsLinkFeedNavigation
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsLinkPageStreaming
import org.gotson.komga.interfaces.api.opds.v1.dto.prefixToNamespace
import org.gotson.komga.oracle.OracleTest
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NamespaceXmlFactoryOracleTest : OracleTest() {
  private val updated = ZonedDateTime.of(2021, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)

  private fun feed(title: String = "Komga") =
    OpdsFeedNavigation(
      "root",
      title,
      updated,
      OpdsAuthor("Komga", URI("https://komga.org")),
      listOf(
        OpdsLinkFeedNavigation("self", "/opds/v1.2/catalog"),
        OpdsLinkPageStreaming("image/jpeg", "/page/{pageNumber}", 10, 3, LocalDateTime.of(2020, 5, 6, 7, 8, 9)),
        OpdsLinkPageStreaming("image/png", "/p", 0, null, null),
      ),
      listOf(OpdsEntryNavigation("Entry", updated, "e1", "content", OpdsLinkFeedNavigation("subsection", "/x?a=1&b=2"))),
    )

  /** XmlMapper built like Spring Boot's Jackson2ObjectMapperBuilder bean, with the given factory */
  private fun mapper(factory: NamespaceXmlFactory): XmlMapper =
    Jackson2ObjectMapperBuilder()
      .createXmlMapper(true)
      .factory(factory)
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
      .build()

  private val factories =
    linkedMapOf(
      "no namespace" to { NamespaceXmlFactory() },
      "komga prefixes" to { NamespaceXmlFactory(prefixToNamespace = prefixToNamespace) },
      "default namespace atom" to { NamespaceXmlFactory(defaultNamespace = ATOM, prefixToNamespace = prefixToNamespace) },
      "default namespace other" to { NamespaceXmlFactory(defaultNamespace = "urn:other") },
      "atom prefixed" to { NamespaceXmlFactory(prefixToNamespace = mapOf("atom" to ATOM, "pse" to OPDS_PSE)) },
      "unused prefix" to { NamespaceXmlFactory(prefixToNamespace = mapOf("x" to "urn:x")) },
      "pse under another prefix" to { NamespaceXmlFactory(prefixToNamespace = mapOf("p" to OPDS_PSE)) },
    )

  override fun cases() {
    func("_createXmlWriter") {
      for ((name, f) in factories) case(name) { mapper(f()).writeValueAsString(feed()) }
      case("escaped text") { mapper(NamespaceXmlFactory(prefixToNamespace = prefixToNamespace)).writeValueAsString(feed("<a & b> \"c\" 'd' é\t\r\n]]>")) }
      case("invalid character") { exceptionType { mapper(NamespaceXmlFactory()).writeValueAsString(feed("bad \u0001")) } }
    }
    func("createGenerator@23") {
      for ((name, f) in factories) case(name) { String(mapper(f()).writeValueAsBytes(feed()), Charsets.UTF_8) }
    }
    func("createGenerator@28") {
      case("komga prefixes") {
        val f = NamespaceXmlFactory(prefixToNamespace = prefixToNamespace)
        val out = ByteArrayOutputStream()
        f.createGenerator(out).use { mapper(f).writeValue(it, feed()) }
        out.toString(Charsets.UTF_8)
      }
    }
    func("createGenerator@30") {
      case("komga prefixes") {
        val f = NamespaceXmlFactory(prefixToNamespace = prefixToNamespace)
        val w = StringWriter()
        f.createGenerator(w).use { mapper(f).writeValue(it, feed()) }
        w.toString()
      }
    }
    func("createGenerator@32") {
      case("komga prefixes, file") {
        val file = tempDir.resolve("feed.xml").toFile()
        mapper(NamespaceXmlFactory(prefixToNamespace = prefixToNamespace)).writeValue(file, feed())
        file.readText()
      }
    }
    func("createGenerator@37") {
      case("komga prefixes, stax writer") {
        val f = NamespaceXmlFactory(prefixToNamespace = prefixToNamespace)
        val w = StringWriter()
        f.createGenerator(f.xmlOutputFactory.createXMLStreamWriter(w)).use { mapper(f).writeValue(it, feed()) }
        w.toString()
      }
    }
    func("configure") {
      case("default and prefixes") { mapper(NamespaceXmlFactory(defaultNamespace = OPDS_PSE, prefixToNamespace = mapOf("a" to ATOM))).writeValueAsString(feed()) }
      case("empty prefix") { mapper(NamespaceXmlFactory(prefixToNamespace = mapOf("" to ATOM))).writeValueAsString(feed()) }
    }
  }
}
