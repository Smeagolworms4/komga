package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.interfaces.api.rest.ClaimController
import org.gotson.komga.oracle.OracleTest

class ClaimControllerOracleTest : OracleTest() {
  private var count = 0L
  private val calls = RestOracle.Calls()
  private val lifecycle =
    mockk<KomgaUserLifecycle> {
      every { countUsers() } answers {
        calls.add("countUsers")
        count
      }
    }
  private val controller = ClaimController(lifecycle)

  override fun cases() {
    func("getClaimStatus") {
      case("no user") { listOf(controller.getClaimStatus(), calls.take()) }
      case("one user") {
        count = 1
        listOf(controller.getClaimStatus(), calls.take())
      }
      case("many users") {
        count = 42
        controller.getClaimStatus()
      }
    }
  }
}
