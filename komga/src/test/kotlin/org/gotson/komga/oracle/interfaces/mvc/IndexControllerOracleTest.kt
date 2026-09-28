package org.gotson.komga.oracle.interfaces.mvc

import org.gotson.komga.interfaces.mvc.IndexController
import org.gotson.komga.oracle.OracleTest
import org.springframework.mock.web.MockServletContext
import org.springframework.ui.ExtendedModelMap

class IndexControllerOracleTest : OracleTest() {
  private fun controller(contextPath: String) = IndexController(MockServletContext().apply { this.contextPath = contextPath })

  override fun cases() {
    listOf("" to "root", "/komga" to "context path", "/a/b" to "nested context path").forEach { (path, name) ->
      func("index") {
        case(name) {
          val model = ExtendedModelMap()
          listOf(controller(path).index(model), model["baseUrl"], model.size)
        }
      }
      func("indexNext") {
        case(name) {
          val model = ExtendedModelMap()
          listOf(controller(path).indexNext(model), model["baseUrl"], model.size)
        }
      }
    }
  }
}
