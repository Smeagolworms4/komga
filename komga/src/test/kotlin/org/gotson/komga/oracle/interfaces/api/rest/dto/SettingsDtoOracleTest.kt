package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.SettingMultiSource
import org.gotson.komga.interfaces.api.rest.dto.SettingsDto
import org.gotson.komga.interfaces.api.rest.dto.ThumbnailSizeDto
import org.gotson.komga.interfaces.api.rest.dto.public
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json

class SettingsDtoOracleTest : OracleTest() {
  private val full =
    SettingsDto(
      deleteEmptyCollections = true,
      deleteEmptyReadLists = false,
      rememberMeDurationDays = 365,
      thumbnailSize = ThumbnailSizeDto.LARGE,
      taskPoolSize = 4,
      serverPort = SettingMultiSource(8080, null, 8080),
      serverContextPath = SettingMultiSource(null, "/komga", "/komga"),
      koboProxy = true,
      koboPort = 443,
      kepubifyPath = SettingMultiSource(null, null, null),
      maxUploadFileSizeBytes = 1048576,
    )

  override fun cases() {
    func("public") {
      case("full") { full.public() }
      case("empty") { SettingsDto().public() }
      case("json full") { json(full) }
      case("json public") { json(full.public()) }
      case("json empty") { json(SettingsDto().public()) }
    }
  }
}
