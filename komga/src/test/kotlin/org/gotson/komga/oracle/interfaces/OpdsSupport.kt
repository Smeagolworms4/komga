package org.gotson.komga.oracle.interfaces

import com.fasterxml.jackson.databind.SerializationFeature
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.infrastructure.xml.MappingJackson2XmlHttpMessageConverterConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.WebOracle
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.util.Base64
import java.util.zip.ZipInputStream

/** Serialization of the OPDS feeds, mirrored by test/unit/interfaces/opds-support.ts */
object OpdsSupport {
  private val xmlMapper by lazy {
    MappingJackson2XmlHttpMessageConverterConfiguration()
      .mappingJackson2XmlHttpMessageConverter(
        Jackson2ObjectMapperBuilder().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS),
      ).objectMapper
  }

  /** OPDS 1.2 feed as sent by Komga (XML), with the "now" dates neutralised */
  fun xml(v: Any?): String = WebOracle.stableText(xmlMapper.writeValueAsString(v))

  /** OPDS 2 feed as sent by Komga (JSON), with the "now" dates neutralised */
  fun json(v: Any?): String = WebOracle.stableText(WebOracle.mapper.writeValueAsString(v))

  /** p2.jpg of [InterfacesData.CBZ] */
  val jpeg: ByteArray by lazy {
    ZipInputStream(Base64.getDecoder().decode(InterfacesData.CBZ).inputStream()).use { zis ->
      generateSequence { zis.nextEntry }.first { it.name == "p2.jpg" }
      zis.readBytes()
    }
  }

  /** generated thumbnail TB7 (bytes in database) for B7 */
  fun thumbnails(db: OracleDb) {
    db.thumbnailBookDao.insert(ThumbnailBook(thumbnail = jpeg, type = ThumbnailBook.Type.GENERATED, mediaType = "image/jpeg", fileSize = jpeg.size.toLong(), dimension = Dimension(2, 3), selected = true, id = "TB7", bookId = "B7"))
  }
}
