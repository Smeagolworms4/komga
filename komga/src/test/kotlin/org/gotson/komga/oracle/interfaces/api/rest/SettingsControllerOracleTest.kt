package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.kobo.KepubConverter
import org.gotson.komga.infrastructure.web.WebServerEffectiveSettings
import org.gotson.komga.interfaces.api.rest.SettingsController
import org.gotson.komga.interfaces.api.rest.dto.SettingsUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockServletContext
import org.springframework.util.unit.DataSize
import java.nio.file.Path

class SettingsControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()
  private val publisher = ApplicationEventPublisher { calls.add("publishEvent", it) }
  private val provider = KomgaSettingsProvider(db.serverSettingsDao, publisher)
  private val kepub = KepubConverter(provider, db.bookProjectionDao, "/opt/kepubify")
  private val serverSettings = WebServerEffectiveSettings(MockServletContext().apply { contextPath = "/komga" })
  private val multipart = MultipartProperties()
  private val admin = principal(user("A", roles = setOf(org.gotson.komga.domain.model.UserRoles.ADMIN)))
  private val regular = principal(user("U"))

  private fun controller(
    port: Int? = null,
    path: String? = null,
  ) = SettingsController(provider, port, path, serverSettings, kepub, multipart)

  override fun cases() {
    func("getServerSettings") {
      case("defaults, admin") { listOf(controller().getServerSettings(admin), calls.take()) }
      case("defaults, user") { controller().getServerSettings(regular) }
      case("configured, admin") {
        serverSettings.effectiveServerPort = 25600
        KepubConverter::class.java.getDeclaredField("kepubifyPath").apply { isAccessible = true }.set(kepub, Path.of("/usr/bin/kepubify"))
        multipart.maxFileSize = DataSize.ofMegabytes(50)
        controller(8080, "/cfg").getServerSettings(admin)
      }
      case("no max file size") {
        multipart.maxFileSize = null
        listOf(controller().getServerSettings(admin), controller().getServerSettings(regular))
      }
    }
    func("updateServerSettings") {
      case("empty") {
        controller().updateServerSettings(read<SettingsUpdateDto>("{}"))
        listOf(controller().getServerSettings(admin), calls.take())
      }
      case("all values") {
        controller().updateServerSettings(
          read<SettingsUpdateDto>(
            """{"deleteEmptyCollections":false,"deleteEmptyReadLists":false,"rememberMeDurationDays":30,"thumbnailSize":"LARGE","taskPoolSize":3,
              |"serverPort":9000,"serverContextPath":"/k","koboProxy":true,"koboPort":443,"kepubifyPath":"/bin/kepubify"}
            """.trimMargin(),
          ),
        )
        listOf(controller().getServerSettings(admin), calls.take())
      }
      case("nulls reset nullable settings only") {
        controller().updateServerSettings(
          read<SettingsUpdateDto>("""{"deleteEmptyCollections":null,"taskPoolSize":null,"serverPort":null,"serverContextPath":null,"koboPort":null,"kepubifyPath":null}"""),
        )
        listOf(controller().getServerSettings(admin), calls.take())
      }
      case("renew remember me key") {
        val before = provider.rememberMeKey
        controller().updateServerSettings(read<SettingsUpdateDto>("""{"renewRememberMeKey":true}"""))
        listOf(before != provider.rememberMeKey, provider.rememberMeKey.length, calls.take().value.let { (it as List<*>).size })
      }
      case("renew false") {
        val before = provider.rememberMeKey
        controller().updateServerSettings(read<SettingsUpdateDto>("""{"renewRememberMeKey":false}"""))
        before == provider.rememberMeKey
      }
      case("stored") { db.rawQuery("SELECT KEY, VALUE FROM SERVER_SETTINGS WHERE KEY <> 'REMEMBER_ME_KEY' ORDER BY KEY") }
    }
  }
}
