package org.gotson.komga.oracle.infrastructure.cache

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.TransientBook
import org.gotson.komga.infrastructure.cache.TransientBookCache
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class TransientBookCacheOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun tb(
    id: String,
    name: String = "book $id",
    number: Float? = null,
  ) = TransientBook(
    Book(name, URL("file:/tmp/$id.cbz"), date, id = id, createdDate = date),
    Media(bookId = id, createdDate = date),
    TransientBook.Metadata(number, null),
  )

  override fun cases() {
    val cache = TransientBookCache()
    func("findByIdOrNull") {
      case("empty cache") { cache.findByIdOrNull("A") }
      case("empty id") { cache.findByIdOrNull("") }
    }
    func("save@19") {
      case("single") {
        cache.save(tb("A"))
        cache.findByIdOrNull("A")
      }
      case("same instance returned") {
        val b = tb("S")
        cache.save(b)
        cache.findByIdOrNull("S") === b
      }
      case("overwrite") {
        cache.save(tb("A", "second", 2.5f))
        cache.findByIdOrNull("A")?.book?.name
      }
      case("empty id") {
        cache.save(tb(""))
        cache.findByIdOrNull("")?.book?.name
      }
    }
    func("save@23") {
      case("empty collection") {
        cache.save(emptyList())
        cache.findByIdOrNull("B")
      }
      case("several") {
        cache.save(listOf(tb("B"), tb("C", "c", 1f)))
        listOf(cache.findByIdOrNull("B")?.book?.name, cache.findByIdOrNull("C")?.metadata?.number, cache.findByIdOrNull("A")?.book?.name)
      }
      case("duplicate ids, last wins") {
        cache.save(listOf(tb("D", "first"), tb("D", "last")))
        cache.findByIdOrNull("D")?.book?.name
      }
      case("set of books") {
        cache.save(setOf(tb("E"), tb("F")))
        listOf("E", "F", "G").map { cache.findByIdOrNull(it) != null }
      }
    }
    func("findByIdOrNull") {
      case("case sensitive") { cache.findByIdOrNull("a") }
      case("existing") { cache.findByIdOrNull("C")?.book?.id }
    }
  }
}
