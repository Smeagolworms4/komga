package org.gotson.komga.oracle.infrastructure.web

import org.gotson.komga.infrastructure.web.DelimitedPairHandlerMethodArgumentResolver
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.ServletWebRequest

class DelimitedPairHandlerMethodArgumentResolverOracleTest : OracleTest() {
  private val resolver = DelimitedPairHandlerMethodArgumentResolver()
  private val method = AuthorsSample::class.java.methods.first { it.name == "handler" }

  private fun resolve(query: String?) = resolver.resolveArgument(MethodParameter(method, 1), null, ServletWebRequest(WebOracle.request(query = query)), null)

  override fun cases() {
    func("supportsParameter") {
      case("@Authors") { resolver.supportsParameter(MethodParameter(method, 0)) }
      case("@DelimitedPair") { resolver.supportsParameter(MethodParameter(method, 1)) }
      case("no annotation") { resolver.supportsParameter(MethodParameter(method, 2)) }
    }
    func("resolveArgument") {
      case("no parameter") { resolve(null) }
      case("other parameter") { resolve("author=a,b") }
      case("single empty") { resolve("search=") }
      case("single blank") { resolve("search=%20") }
      case("pair") { resolve("search=abc,TITLE") }
      case("first value only") { resolve("search=a,b&search=c,d") }
      case("first empty, second set") { resolve("search=&search=c,d") }
      case("without delimiter") { resolve("search=abc") }
    }
    func("parseParameterIntoPairs") {
      case("last delimiter splits") { resolve("search=a,b,c") }
      case("trailing delimiter") { resolve("search=abc,") }
      case("leading delimiter") { resolve("search=,abc") }
      case("only delimiter") { resolve("search=,") }
      case("spaces kept") { resolve("search=%20a%20,%20b%20") }
      case("regex") { resolve("search=%5E(a%7Cb)%2C%24,title") }
    }
  }
}
