package org.gotson.komga.oracle.infrastructure.hash

import org.gotson.komga.infrastructure.hash.Hasher
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.writeBytes

class HasherOracleTest : OracleTest() {
  private val hasher = Hasher()
  private val sizes = listOf(0, 1, 15, 16, 17, 128, 129, 240, 241, 1024, 8191, 8192, 8193, 20000, 100_000)

  override fun cases() {
    func("computeHash@17") {
      for (n in sizes) {
        case("file of $n bytes") {
          val f = tempDir.resolve("file-$n")
          f.writeBytes(oracleBytes(n))
          hasher.computeHash(f)
        }
      }
      // the message holds the absolute path and the OS error text: only the exception type is compared
      case("missing file") { exceptionType { hasher.computeHash(tempDir.resolve("missing")) } }
    }
    func("computeHash@23") {
      for (s in listOf("", "a", "komga", "é", "日本語", "😀", "a".repeat(10000), "line1\nline2\r\n")) {
        case("'${s.take(20)}' (${s.length})") { hasher.computeHash(s) }
      }
    }
    func("computeHash@25") {
      for (n in sizes) case("stream of $n bytes") { hasher.computeHash(oracleBytes(n).inputStream()) }
    }
    func("toHexString") {
      case("empty") { with(hasher) { ByteArray(0).toHexString() } }
      case("all bytes") { with(hasher) { ByteArray(256) { it.toByte() }.toHexString() } }
    }
  }
}
