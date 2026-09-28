package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadListRequest
import org.gotson.komga.domain.model.ReadListRequestBook
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.service.ReadListMatcher
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series

class ReadListMatcherOracleTest : OracleTest() {
  private val db = OracleDb()
  private val matcher = ReadListMatcher(db.readListDao, db.readListRequestDao)

  override fun cases() {
    func("matchReadListRequest") {
      case("setup") {
        db.libraryDao.insert(library("L1"))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Batman", seriesId = "S1", createdDate = date))
        db.seriesDao.insert(series("S2", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Robin", seriesId = "S2", createdDate = date))
        listOf(book("B1", "S1", "L1", number = 1), book("B2", "S1", "L1", number = 2), book("B3", "S2", "L1", number = 1)).forEach {
          db.bookDao.insert(it)
          db.bookMetadataDao.insert(metadata(it))
        }
        db.readListDao.insert(ReadList(name = "Existing", bookIds = sortedMapOf(0 to "B1"), id = "RL1", createdDate = date))
        db.bookDao.count()
      }
      case("new name, no books") { matcher.matchReadListRequest(ReadListRequest("New", emptyList())) }
      case("existing name") { matcher.matchReadListRequest(ReadListRequest("Existing", emptyList())) }
      case("existing name other case") { matcher.matchReadListRequest(ReadListRequest("existing", emptyList())) }
      case("books matched") {
        matcher.matchReadListRequest(
          ReadListRequest(
            "List",
            listOf(
              ReadListRequestBook(setOf("Batman"), "1"),
              ReadListRequestBook(setOf("Robin", "Batman"), "1"),
              ReadListRequestBook(setOf("batman"), "2"),
              ReadListRequestBook(setOf("Unknown"), "1"),
              ReadListRequestBook(setOf("Batman"), "9"),
              ReadListRequestBook(emptySet(), "1"),
            ),
          ),
        )
      }
    }
  }
}
