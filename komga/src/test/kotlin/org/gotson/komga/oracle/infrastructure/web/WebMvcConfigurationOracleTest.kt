package org.gotson.komga.oracle.infrastructure.web

import org.gotson.komga.infrastructure.web.WebMvcConfiguration
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.mvc.WebContentInterceptor
import org.springframework.web.util.ServletRequestPathUtils

class WebMvcConfigurationOracleTest : OracleTest() {
  private val config = WebMvcConfiguration()

  private fun get(
    o: Any,
    c: Class<*>,
    name: String,
  ): Any? {
    val f = c.getDeclaredField(name)
    f.isAccessible = true
    return f.get(o)
  }

  private fun registry() = ResourceHandlerRegistry(StaticWebApplicationContext(), MockServletContext())

  @Suppress("UNCHECKED_CAST")
  private fun describe(registry: ResourceHandlerRegistry): List<Any?> =
    (get(registry, ResourceHandlerRegistry::class.java, "registrations") as List<ResourceHandlerRegistration>).map {
      listOf(
        (get(it, ResourceHandlerRegistration::class.java, "pathPatterns") as Array<String>).toList(),
        get(it, ResourceHandlerRegistration::class.java, "locationValues"),
        (get(it, ResourceHandlerRegistration::class.java, "cacheControl") as org.springframework.http.CacheControl?)?.headerValue,
      )
    }

  @Suppress("UNCHECKED_CAST")
  private fun interceptors(registry: InterceptorRegistry): List<HandlerInterceptor> {
    val m = InterceptorRegistry::class.java.getDeclaredMethod("getInterceptors")
    m.isAccessible = true
    return m.invoke(registry) as List<HandlerInterceptor>
  }

  private fun cacheControl(uri: String): List<Any?> {
    val registry = InterceptorRegistry()
    config.addInterceptors(registry)
    val request = WebOracle.request(uri = uri)
    ServletRequestPathUtils.parseAndCache(request)
    val response = WebOracle.response()
    val result = interceptors(registry).first().preHandle(request, response, Any())
    return listOf(result, response.getHeader("Cache-Control"))
  }

  override fun cases() {
    func("addResourceHandlers") {
      case("empty registry") {
        val registry = registry()
        config.addResourceHandlers(registry)
        describe(registry)
      }
      case("webjars already mapped") {
        val registry = registry()
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/other/")
        config.addResourceHandlers(registry)
        describe(registry)
      }
      case("swagger already mapped") {
        val registry = registry()
        registry.addResourceHandler("/swagger-ui.html**")
        config.addResourceHandlers(registry)
        describe(registry).size
      }
      case("mappings") {
        val registry = registry()
        config.addResourceHandlers(registry)
        listOf("/webjars/**", "/swagger-ui.html**", "/index.html", "/css/**", "/assets/**", "/js/*", "/other/**").map { registry.hasMappingForPattern(it) }
      }
    }
    func("addInterceptors") {
      case("interceptors") {
        val registry = InterceptorRegistry()
        config.addInterceptors(registry)
        interceptors(registry).map { it is WebContentInterceptor }
      }
      listOf("/api/v1/books", "/api", "/api/", "/opds/v1.2/catalog", "/opds", "/kobo/k/v1/library/sync", "/", "/index.html", "/apiv1", "/sse/v1/events")
        .forEach { uri -> case("cache control $uri") { cacheControl(uri) } }
    }
    func("addArgumentResolvers") {
      case("resolvers") {
        val resolvers = mutableListOf<HandlerMethodArgumentResolver>()
        config.addArgumentResolvers(resolvers)
        resolvers.map { it::class.java.simpleName }
      }
    }
  }
}
