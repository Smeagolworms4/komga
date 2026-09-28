package org.gotson.komga.oracle.infrastructure.metadata.mylar

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.domain.model.Series
import org.gotson.komga.infrastructure.metadata.mylar.MylarSeriesProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class MylarSeriesProviderOracleTest : OracleTest() {
  // the ObjectMapper of Spring Boot, configured like Komga
  private val mapper =
    Jackson2ObjectMapperBuilder
      .json()
      .featuresToEnable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
      .build<com.fasterxml.jackson.databind.ObjectMapper>()
  private val provider = MylarSeriesProvider(mapper)

  private fun series(
    dir: Path,
    oneshot: Boolean = false,
  ) = Series("series", dir.toUri().toURL(), MetadataSamples.date, id = "SERIES", oneshot = oneshot, createdDate = MetadataSamples.date)

  override fun cases() {
    func("getSeriesMetadata") {
      MetadataSamples.mylarCases.forEachIndexed { i, json ->
        case("#$i") {
          val dir = tempDir.resolve("mylar-$i").createDirectories()
          dir.resolve("series.json").writeText(json)
          provider.getSeriesMetadata(series(dir))
        }
      }
      case("oneshot") {
        val dir = tempDir.resolve("mylar-oneshot").createDirectories()
        dir.resolve("series.json").writeText(MetadataSamples.mylarCases[0])
        provider.getSeriesMetadata(series(dir, oneshot = true))
      }
      case("no series.json") { provider.getSeriesMetadata(series(tempDir.resolve("mylar-none").createDirectories())) }
      case("series.json is a directory") {
        val dir = tempDir.resolve("mylar-dir").createDirectories()
        dir.resolve("series.json").createDirectories()
        provider.getSeriesMetadata(series(dir))
      }
      case("missing series directory") { provider.getSeriesMetadata(series(tempDir.resolve("mylar-missing"))) }
    }
    func("shouldLibraryHandlePatch") {
      for ((name, library) in MetadataSamples.libraries) {
        for (target in MetadataPatchTarget.entries) case("$name, $target") { provider.shouldLibraryHandlePatch(library, target) }
      }
    }
    func("getSidecarSeriesType") { case("type") { provider.getSidecarSeriesType() } }
    func("getSidecarSeriesFilenames") { case("names") { provider.getSidecarSeriesFilenames() } }
  }
}
