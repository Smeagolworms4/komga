package org.gotson.komga.oracle.interfaces.mvc

import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.interfaces.mvc.IndexController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockServletContext
import org.springframework.ui.ExtendedModelMap

class IndexControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val provider = KomgaSettingsProvider(db.serverSettingsDao, ApplicationEventPublisher { })

  private fun controller(contextPath: String) = IndexController(MockServletContext().apply { this.contextPath = contextPath }, provider)

  override fun cases() {
    listOf("" to "root", "/komga" to "context path", "/a/b" to "nested context path").forEach { (path, name) ->
      func("index") {
        case(name) {
          val model = ExtendedModelMap()
          listOf(controller(path).index(model), model["baseUrl"], model.size)
        }
      }
      func("indexClassic") {
        case(name) {
          val model = ExtendedModelMap()
          listOf(controller(path).indexClassic(model), model["baseUrl"], model.size)
        }
      }
      func("indexNext") {
        case(name) {
          val model = ExtendedModelMap()
          listOf(controller(path).indexNext(model), model["baseUrl"], model.size)
        }
      }
    }
    func("index") {
      case("next ui as default") {
        provider.nextUiAsDefault = true
        val model = ExtendedModelMap()
        listOf(controller("/komga").index(model), model["baseUrl"], model.size, controller("").indexClassic(ExtendedModelMap()))
          .also { provider.nextUiAsDefault = false }
      }
    }
  }
}
