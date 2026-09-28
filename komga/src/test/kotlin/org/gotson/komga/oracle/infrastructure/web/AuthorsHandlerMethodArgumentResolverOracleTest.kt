package org.gotson.komga.oracle.infrastructure.web

import org.gotson.komga.domain.model.Author
import org.gotson.komga.infrastructure.web.Authors
import org.gotson.komga.infrastructure.web.AuthorsHandlerMethodArgumentResolver
import org.gotson.komga.infrastructure.web.DelimitedPair
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.ServletWebRequest

@Suppress("UNUSED_PARAMETER", "DEPRECATION")
class AuthorsSample {
  fun handler(
    @Authors authors: List<Author>?,
    @DelimitedPair("search") pair: Pair<String, String>?,
    plain: String?,
  ) {}
}

class AuthorsHandlerMethodArgumentResolverOracleTest : OracleTest() {
  private val resolver = AuthorsHandlerMethodArgumentResolver()
  private val method = AuthorsSample::class.java.methods.first { it.name == "handler" }

  private fun resolve(query: String?) = resolver.resolveArgument(MethodParameter(method, 0), null, ServletWebRequest(WebOracle.request(query = query)), null)

  override fun cases() {
    func("supportsParameter") {
      case("@Authors") { resolver.supportsParameter(MethodParameter(method, 0)) }
      case("@DelimitedPair") { resolver.supportsParameter(MethodParameter(method, 1)) }
      case("no annotation") { resolver.supportsParameter(MethodParameter(method, 2)) }
    }
    func("resolveArgument") {
      case("no parameter") { resolve(null) }
      case("other parameter") { resolve("authors=a,b") }
      case("single empty") { resolve("author=") }
      case("single blank") { resolve("author=%20%20") }
      case("single") { resolve("author=john,writer") }
      case("several") { resolve("author=john,writer&author=jane,penciller") }
      case("empty among several") { resolve("author=&author=jane,penciller") }
      case("without delimiter") { resolve("author=john") }
      case("encoded comma") { resolve("author=john%2Cwriter") }
    }
    func("parseParameterIntoAuthors") {
      case("last delimiter splits") { resolve("author=Doe,%20John,writer") }
      case("trailing delimiter") { resolve("author=john,") }
      case("leading delimiter") { resolve("author=,writer") }
      case("only delimiter") { resolve("author=,") }
      case("role case and spaces") { resolve("author=%20John%20Doe%20,%20WRITER%20") }
      case("unicode") { resolve("author=%C3%A9mile%20%E6%BC%AB,%C3%89diteur") }
      case("mixed") { resolve("author=a&author=b,c&author=d,e,f") }
    }
  }
}
