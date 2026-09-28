package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.MediaExtension
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.ProxyExtension
import org.gotson.komga.domain.model.R2Progression
import org.gotson.komga.oracle.OracleTest

class MediaExtensionOracleTest : OracleTest() {
  override fun cases() {
    func("of") {
      case("null") { ProxyExtension.of(null) }
      case("epub extension") { ProxyExtension.of("org.gotson.komga.domain.model.MediaExtensionEpub") }
      case("proxy extension itself") { ProxyExtension.of("org.gotson.komga.domain.model.ProxyExtension") }
      case("interface") { ProxyExtension.of("org.gotson.komga.domain.model.MediaExtension") }
      case("komga class, not an extension") { ProxyExtension.of("org.gotson.komga.domain.model.R2Progression") }
      case("unknown class") { ProxyExtension.of("org.gotson.komga.domain.model.Nope") }
      case("empty") { exceptionType { ProxyExtension.of("") } }
      case("simple name") { exceptionType { ProxyExtension.of("MediaExtensionEpub") } }
    }
    val epub = ProxyExtension.of("org.gotson.komga.domain.model.MediaExtensionEpub")!!
    val proxy = ProxyExtension.of("org.gotson.komga.domain.model.ProxyExtension")!!
    func("proxyForType@22") {
      case("same type") { epub.proxyForType<MediaExtensionEpub>() }
      case("other type") { epub.proxyForType<ProxyExtension>() }
      case("interface") { epub.proxyForType<MediaExtension>() }
      case("proxy of proxy") { proxy.proxyForType<ProxyExtension>() }
    }
    func("proxyForType@24") {
      case("same type") { epub.proxyForType(MediaExtensionEpub::class) }
      case("other type") { epub.proxyForType(R2Progression::class) }
      case("interface") { epub.proxyForType(MediaExtension::class) }
      case("proxy of proxy") { proxy.proxyForType(ProxyExtension::class) }
    }
  }
}
