package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.ReleaseController
import org.gotson.komga.oracle.OracleTest

class ReleaseControllerOracleTest : OracleTest() {
  private val calls = RestOracle.Calls()
  private var response = 200 to "[]"
  private val controller = ReleaseController(fakeWebClient(calls) { response })

  private val releases =
    """[
      {"html_url":"https://github.com/gotson/komga/releases/tag/1.2.0","tag_name":"1.2.0","published_at":"2024-05-06T07:08:09Z","body":"## Changes\n- a","prerelease":false,"extra":1},
      {"html_url":"https://github.com/gotson/komga/releases/tag/1.2.0-rc","tag_name":"1.2.0-rc","published_at":"2024-05-01T10:00:00+02:00","body":"","prerelease":true}
    ]"""

  override fun cases() {
    func("fetchGitHubReleases") {
      case("ok") {
        response = 200 to releases
        listOf(controller.fetchGitHubReleases(), calls.take())
      }
      case("empty body") {
        response = 200 to ""
        listOf(controller.fetchGitHubReleases(), calls.take())
      }
      case("invalid json") {
        response = 200 to "[{"
        listOf(RestOracle.thrown { controller.fetchGitHubReleases() }, calls.take())
      }
      case("empty list") {
        response = 200 to "[]"
        controller.fetchGitHubReleases()
      }
      case("not found") {
        response = 404 to "{}"
        listOf(RestOracle.thrown { controller.fetchGitHubReleases() }, calls.take())
      }
      case("server error") {
        response = 500 to ""
        RestOracle.thrown { controller.fetchGitHubReleases() }
      }
    }
    func("getReleases") {
      case("error is not cached") {
        response = 503 to ""
        listOf(RestOracle.thrown { controller.getReleases() }, calls.take())
      }
      case("fetched") {
        response = 200 to releases
        listOf(controller.getReleases(), calls.take())
      }
      case("cached") {
        response = 200 to "[]"
        listOf(controller.getReleases(), calls.take())
      }
      case("new controller, empty list") {
        response = 200 to "[]"
        listOf(ReleaseController(fakeWebClient(calls) { response }).getReleases(), calls.take())
      }
    }
  }
}
