package org.gotson.komga.oracle.infrastructure.metadata.comicrack

import org.gotson.komga.infrastructure.metadata.comicrack.ReadListProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples

class ReadListProviderOracleTest : OracleTest() {
  private val provider = ReadListProvider()

  private fun cbl(books: String) = "<?xml version=\"1.0\"?><ReadingList><Name>L</Name><Books>$books</Books></ReadingList>".toByteArray()

  override fun cases() {
    func("importFromCbl") {
      for ((id, cls, content) in MetadataSamples.xmlCases) case("$cls #$id") { provider.importFromCbl(content) }
      case("empty") { provider.importFromCbl(ByteArray(0)) }
      case("volume 1") { provider.importFromCbl(cbl("<Book Series=\"S\" Number=\"1\" Volume=\"1\"/>")) }
      case("volume 2") { provider.importFromCbl(cbl("<Book Series=\"S\" Number=\" 2 \" Volume=\"2\"/>")) }
      case("blank series") { provider.importFromCbl(cbl("<Book Series=\" \" Number=\"1\"/>")) }
      case("missing number") { provider.importFromCbl(cbl("<Book Series=\"S\" Volume=\"3\" Year=\"2000\"><FileName>f.cbz</FileName></Book>")) }
      case("elements instead of attributes") { provider.importFromCbl(cbl("<Book><Series>S</Series><Number>5</Number></Book>")) }
      case("duplicate books") { provider.importFromCbl(cbl("<Book Series=\"S\" Number=\"1\"/><Book Series=\"S\" Number=\"1\"/>")) }
    }
  }
}
