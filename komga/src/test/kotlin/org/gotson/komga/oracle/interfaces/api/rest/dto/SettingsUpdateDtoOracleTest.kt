package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.SettingsUpdateDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read

class SettingsUpdateDtoOracleTest : OracleTest() {
  @Suppress("DEPRECATION")
  private fun state(json: String) =
    read<SettingsUpdateDto>(json).let { d ->
      listOf("serverPort", "serverContextPath", "koboPort", "kepubifyPath", "taskPoolSize").map { d.isSet(it) } +
        listOf(d.serverPort, d.serverContextPath, d.koboPort, d.kepubifyPath, d.taskPoolSize, d.thumbnailSize, d.rememberMeDurationDays)
    }

  override fun cases() {
    func("isSet") {
      case("empty body") { state("{}") }
      case("nulls") { state("""{"serverPort":null,"serverContextPath":null,"koboPort":null,"kepubifyPath":null,"taskPoolSize":null}""") }
      case("values") { state("""{"serverPort":8080,"serverContextPath":"/k","koboPort":443,"kepubifyPath":"/bin/k","taskPoolSize":2,"thumbnailSize":"XLARGE","rememberMeDurationDays":30}""") }
      case("setter") {
        SettingsUpdateDto().apply { serverPort = 1 }.let { listOf(it.isSet("serverPort"), it.isSet("koboPort")) }
      }
    }
  }
}
