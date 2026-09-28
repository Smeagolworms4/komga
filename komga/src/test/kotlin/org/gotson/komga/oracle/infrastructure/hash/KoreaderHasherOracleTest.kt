package org.gotson.komga.oracle.infrastructure.hash

import org.gotson.komga.infrastructure.hash.KoreaderHasher
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.writeBytes

class KoreaderHasherOracleTest : OracleTest() {
  private val hasher = KoreaderHasher()
  private val sizes = listOf(0, 1, 100, 1023, 1024, 1025, 2048, 4095, 4096, 4097, 16384, 70000, 262144, 262145, 1_100_000, 4_194_305)

  override fun cases() {
    // partialMd5 is private: covered through computeHash
    func("computeHash") {
      for (n in sizes) {
        case("file of $n bytes") {
          val f = tempDir.resolve("koreader-$n")
          f.writeBytes(oracleBytes(n))
          hasher.computeHash(f)
        }
      }
      // the message holds the absolute path and the OS error text: only the exception type is compared
      case("missing file") { exceptionType { hasher.computeHash(tempDir.resolve("missing")) } }
    }
    func("partialMd5") {
      case("via computeHash, 5000 bytes") {
        val f = tempDir.resolve("koreader-partial")
        f.writeBytes(oracleBytes(5000))
        hasher.computeHash(f)
      }
    }
  }
}
