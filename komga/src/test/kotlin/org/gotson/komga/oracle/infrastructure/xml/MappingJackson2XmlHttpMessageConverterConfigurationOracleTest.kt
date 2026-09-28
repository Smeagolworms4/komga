package org.gotson.komga.oracle.infrastructure.xml

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.gotson.komga.infrastructure.xml.MappingJackson2XmlHttpMessageConverterConfiguration
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsAuthor
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsFeedAcquisition
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsLinkPageStreaming
import org.gotson.komga.interfaces.api.opds.v1.dto.OpdsLinkSearch
import org.gotson.komga.oracle.OracleTest
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MappingJackson2XmlHttpMessageConverterConfigurationOracleTest : OracleTest() {
  // the Jackson2ObjectMapperBuilder bean of Spring Boot (dates as ISO strings)
  private val converter =
    MappingJackson2XmlHttpMessageConverterConfiguration().mappingJackson2XmlHttpMessageConverter(
      Jackson2ObjectMapperBuilder().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS),
    )

  override fun cases() {
    func("mappingJackson2XmlHttpMessageConverter") {
      case("mapper factory") { (converter.objectMapper as XmlMapper).factory::class.simpleName }
      case("feed with page streaming") {
        converter.objectMapper.writeValueAsString(
          OpdsFeedAcquisition(
            "id",
            "Titre é",
            ZonedDateTime.of(2022, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC),
            OpdsAuthor("Komga"),
            listOf(OpdsLinkSearch("/search"), OpdsLinkPageStreaming("image/webp", "/p/{pageNumber}", 5, 0, LocalDateTime.of(2021, 3, 28, 2, 30))),
            emptyList(),
          ),
        )
      }
    }
  }
}
