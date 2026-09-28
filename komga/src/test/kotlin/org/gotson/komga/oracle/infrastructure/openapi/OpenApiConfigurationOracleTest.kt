package org.gotson.komga.oracle.infrastructure.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.Operation
import org.gotson.komga.infrastructure.openapi.OpenApiConfiguration
import org.gotson.komga.oracle.OracleTest
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.method.HandlerMethod

class OpenApiConfigurationOracleTest : OracleTest() {
  @PreAuthorize("hasRole('ADMIN')")
  class AdminController {
    fun inherited() {}

    @PreAuthorize("hasRole('FILE_DOWNLOAD') and hasRole('PAGE_STREAMING')")
    fun own() {}
  }

  class OpenController {
    fun none() {}

    @PreAuthorize("isAuthenticated()")
    fun noRole() {}

    @PreAuthorize("hasRole('KOBO_SYNC') or hasRole('ADMIN') or hasRole('KOBO_SYNC')")
    fun several() {}

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    fun anyRole() {}

    @PreAuthorize("hasRole(\"ADMIN\") and hasRole('')")
    fun oddQuotes() {}
  }

  /** JSON written like springdoc (OpenAPI 3.1, writer-with-order-by-keys), keys sorted recursively */
  private fun json(openApi: OpenAPI): String {
    // as served by springdoc 2.8 (OpenAPI 3.1 by default): version and serializer set by AbstractOpenApiResource
    openApi.openapi("3.1.0")
    openApi.specVersion = SpecVersion.V31
    val tree = Json31.mapper().readValue(Json31.mapper().writeValueAsString(openApi), Any::class.java)
    return ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).writeValueAsString(tree)
  }

  override fun cases() {
    func("openApi") {
      case("default profile") { json(OpenApiConfiguration("1.2.3", MockEnvironment()).openApi()) }
      case("generate-openapi profile") { json(OpenApiConfiguration("", MockEnvironment().apply { setActiveProfiles("generate-openapi") }).openApi()) }
    }

    func("roleDescriptionCustomizer") {
      val customizer = OpenApiConfiguration("1", MockEnvironment()).roleDescriptionCustomizer()

      fun run(
        bean: Any,
        method: String,
        description: String? = null,
      ) = customizer.customize(Operation().description(description), HandlerMethod(bean, method)).description

      case("class annotation") { run(AdminController(), "inherited") }
      case("method annotation wins") { run(AdminController(), "own") }
      case("existing description") { run(AdminController(), "own", "Existing.") }
      case("empty description") { run(AdminController(), "inherited", "") }
      case("no annotation") { run(OpenController(), "none", "Kept") }
      case("no role in expression") { run(OpenController(), "noRole") }
      case("several roles with duplicate") { run(OpenController(), "several") }
      case("hasAnyRole not matched") { run(OpenController(), "anyRole") }
      case("odd quotes") { run(OpenController(), "oddQuotes") }
      case("same operation returned") {
        val op = Operation()
        customizer.customize(op, HandlerMethod(AdminController(), "own")) === op
      }
    }
  }
}
