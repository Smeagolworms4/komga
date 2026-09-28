package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.MediaProfile
import org.gotson.komga.domain.model.MediaType
import org.gotson.komga.oracle.OracleTest

class MediaTypeOracleTest : OracleTest() {
  override fun cases() {
    func("entries") {
      case("properties") { MediaType.entries.map { listOf(it, it.ordinal, it.type, it.profile, it.fileExtension, it.exportType) } }
    }
    func("fromMediaType") {
      case("null") { MediaType.fromMediaType(null) }
      for (t in MediaType.entries) case(t.type) { MediaType.fromMediaType(t.type) }
      for (t in listOf("", "application/ZIP", "application/vnd.comicbook+zip", "application/x-rar-compressed;version=4", "application/x-rar-compressed; version=6", "image/jpeg", " application/pdf")) {
        case("unknown '$t'") { MediaType.fromMediaType(t) }
      }
    }
    func("matchingMediaProfile") {
      for (p in MediaProfile.entries) case(p.name) { MediaType.matchingMediaProfile(p) }
    }
  }
}
