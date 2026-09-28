package org.gotson.komga.oracle.infrastructure.mediacontainer

import org.apache.tika.metadata.Metadata
import org.gotson.komga.infrastructure.mediacontainer.TikaConfiguration
import org.gotson.komga.oracle.OracleTest

class TikaConfigurationOracleTest : OracleTest() {
  override fun cases() {
    func("tika") {
      // the TikaConfig itself is not comparable: its detector and MIME repository are exercised
      case("detector on zip bytes") { TikaConfiguration().tika().detector.detect(OracleZip.bytes(listOf(OracleZip.t("a", "b"))).inputStream(), Metadata()).toString() }
      case("detector on empty stream") { TikaConfiguration().tika().detector.detect(ByteArray(0).inputStream(), Metadata()).toString() }
      case("mime repository extension of image/png") { TikaConfiguration().tika().mimeRepository.forName("image/png").extension }
      case("mime repository extensions of image/jpeg") { TikaConfiguration().tika().mimeRepository.forName("image/jpeg").extensions }
      case("two instances are distinct") { TikaConfiguration().tika() !== TikaConfiguration().tika() }
    }
  }
}
