package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.AnnouncementController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user

class AnnouncementControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()
  private var response = 200 to ""
  private val controller = AnnouncementController(db.komgaUserDao, fakeWebClient(calls) { response })
  private val admin = user("U1")
  private val other = user("U2")

  private val feed =
    """{"version":"https://jsonfeed.org/version/1","title":"Komga","home_page_url":"https://komga.org/blog","description":"Blog",
      |"items":[
      |{"id":"https://komga.org/blog/b","url":"https://komga.org/blog/b","title":"B","summary":"sb","content_html":"<p>b</p>",
      |"date_modified":"2024-02-03T04:05:06Z","author":{"name":"gotson","url":"https://github.com/gotson"},"tags":["x","y"],"_komga":{"read":true}},
      |{"id":"https://komga.org/blog/a","content_html":"<p>a</p>","date_modified":"2023-12-31T23:00:00+01:00"}
      |]}
    """.trimMargin()

  override fun cases() {
    func("fetchWebsiteAnnouncements") {
      case("feed") {
        response = 200 to feed
        listOf(controller.fetchWebsiteAnnouncements(), calls.take())
      }
      case("empty body") {
        response = 200 to ""
        listOf(controller.fetchWebsiteAnnouncements(), calls.take())
      }
      case("invalid json") {
        response = 200 to "{\"version\":"
        listOf(RestOracle.thrown { controller.fetchWebsiteAnnouncements() }, calls.take())
      }
      case("error") {
        response = 404 to ""
        RestOracle.thrown { controller.fetchWebsiteAnnouncements() }.also { calls.take() }
      }
    }
    func("getAnnouncements") {
      case("empty body is not cached") {
        response = 200 to ""
        listOf(RestOracle.thrown { controller.getAnnouncements(principal(admin)) }, calls.take())
      }
      case("nothing read") {
        db.komgaUserDao.insert(admin)
        db.komgaUserDao.insert(other)
        response = 200 to feed
        listOf(controller.getAnnouncements(principal(admin)), calls.take())
      }
      case("cached, one read") {
        db.komgaUserDao.saveAnnouncementIdsRead(admin, setOf("https://komga.org/blog/a", "unknown"))
        listOf(controller.getAnnouncements(principal(admin)), calls.take())
      }
      case("other user") { controller.getAnnouncements(principal(other)).items.map { it.komgaExtension } }
    }
    func("markAnnouncementsRead") {
      case("mark") {
        controller.markAnnouncementsRead(principal(other), setOf("https://komga.org/blog/b"))
        listOf(db.komgaUserDao.findAnnouncementIdsReadByUserId("U2"), controller.getAnnouncements(principal(other)).items.map { it.komgaExtension })
      }
      case("mark again and empty") {
        controller.markAnnouncementsRead(principal(other), setOf("https://komga.org/blog/b", "https://komga.org/blog/a"))
        controller.markAnnouncementsRead(principal(other), emptySet())
        db.komgaUserDao.findAnnouncementIdsReadByUserId("U2")
      }
    }
  }
}
