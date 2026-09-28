package org.gotson.komga.oracle.infrastructure.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema
import org.gotson.komga.infrastructure.openapi.InheritanceFlattenerConfiguration
import org.gotson.komga.oracle.OracleTest

class InheritanceFlattenerConfigurationOracleTest : OracleTest() {
  private fun json(openApi: OpenAPI): String {
    // as served by springdoc 2.8 (OpenAPI 3.1 by default): version and serializer set by AbstractOpenApiResource
    openApi.openapi("3.1.0")
    openApi.specVersion = SpecVersion.V31
    val tree = Json31.mapper().readValue(Json31.mapper().writeValueAsString(openApi), Any::class.java)
    return ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).writeValueAsString(tree)
  }

  private fun ref(name: String) = Schema<Any>().`$ref`("#/components/schemas/$name")

  private fun inline(vararg props: String) = Schema<Any>().types(setOf("object")).properties(props.associateWith { Schema<Any>().types(setOf("string")) }.toMutableMap() as Map<String, Schema<*>>)

  private fun named(
    name: String,
    allOf: List<Schema<*>>?,
  ) = Schema<Any>().name(name).allOf(allOf)

  override fun cases() {
    val customizer = InheritanceFlattenerConfiguration().flattenInheritedSchemasCustomizer()
    func("flattenInheritedSchemasCustomizer") {
      case("no components") { json(OpenAPI().also { customizer.customise(it) }) }
      case("no schemas") { json(OpenAPI().components(Components()).also { customizer.customise(it) }) }
      case("flattened and untouched schemas") {
        val openApi =
          OpenAPI().components(
            Components()
              .addSchemas("SearchOperatorIs", named("SearchOperatorIs", listOf(ref("SearchOperatorEquality"), inline("value"))))
              .addSchemas("SearchConditionAnyOfBook", named("SearchConditionAnyOfBook", listOf(inline("anyOf", "x"), ref("SearchConditionBook"))))
              .addSchemas("SearchConditionOnlyRef", named("SearchConditionOnlyRef", listOf(ref("A"), ref("B"))))
              .addSchemas("SearchOperatorOnlyInline", named("SearchOperatorOnlyInline", listOf(inline("v"))))
              .addSchemas("SearchOperatorNoAllOf", named("SearchOperatorNoAllOf", null).types(setOf("object")))
              .addSchemas("searchOperatorLowerCase", named("searchOperatorLowerCase", listOf(ref("X"), inline("y"))))
              .addSchemas("OtherSchema", named("OtherSchema", listOf(ref("X"), inline("y"))))
              .addSchemas("SearchOperatorTwoInline", named("SearchOperatorTwoInline", listOf(ref("R"), inline("first"), inline("second")))),
          )
        customizer.customise(openApi)
        json(openApi)
      }
      case("schema without name") {
        val openApi = OpenAPI().components(Components().addSchemas("SearchOperatorX", Schema<Any>().allOf(listOf(ref("A"), inline("b")))))
        exceptionType { customizer.customise(openApi) }
      }
    }
  }
}
