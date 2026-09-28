package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.infrastructure.security.PasswordEncoderConfiguration
import org.gotson.komga.oracle.OracleTest

class PasswordEncoderConfigurationOracleTest : OracleTest() {
  private val config = PasswordEncoderConfiguration()

  override fun cases() {
    func("getPasswordEncoder") {
      val encoder = config.getPasswordEncoder()
      case("matches spring hash") { encoder.matches("password", "\$2a\$10\$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") }
      case("rejects wrong password") { encoder.matches("Password", "\$2a\$10\$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") }
      case("2b prefix") { encoder.matches("password", "\$2b\$10\$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") }
      case("2y prefix") { encoder.matches("password", "\$2y\$10\$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") }
      case("not a bcrypt hash") { encoder.matches("password", "password") }
      case("empty hash") { encoder.matches("password", "") }
      case("encode format") {
        val h = encoder.encode("secret")
        listOf(h.length, h.substring(0, 7), encoder.matches("secret", h), encoder.matches("Secret", h))
      }
      case("encode is salted") { encoder.encode("secret") != encoder.encode("secret") }
      case("unicode password") { encoder.encode("mötörhead 漫画").let { encoder.matches("mötörhead 漫画", it) } }
      case("upgrade encoding") { encoder.upgradeEncoding("\$2a\$10\$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") }
    }
    func("getTokenEncoder") {
      val encoder = config.getTokenEncoder()
      listOf("", "a", "key-one", "0123456789abcdef0123456789abcdef", "ünï 漫画", "\u0000", "x".repeat(10_000)).forEach { k ->
        case(if (k.length > 40) "long" else "[$k]") { encoder.encode(k) }
      }
      case("deterministic") { encoder.encode("abc") == config.getTokenEncoder().encode("abc") }
    }
  }
}
