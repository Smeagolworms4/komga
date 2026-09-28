package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.interfaces.api.OpdsGenerator

class OpdsGeneratorOracleTest : WebPubCases() {
  override val generator: OpdsGenerator by lazy { services.opdsGenerator }

  override fun cases() {
    commonCases()
    func("toOpdsPublicationDto") {
      listOf("B1", "B4", "B5", "B6").forEach { id -> case(id) { web { json(generator.toOpdsPublicationDto(book(id))) } } }
      case("context path") { web("/komga") { json(generator.toOpdsPublicationDto(book("B2"))) } }
    }
    func("generateOpdsAuthDocument") {
      case("document") { web { json(generator.generateOpdsAuthDocument()) } }
      case("context path") { web("/k/sub") { json(generator.generateOpdsAuthDocument()) } }
    }
  }
}
