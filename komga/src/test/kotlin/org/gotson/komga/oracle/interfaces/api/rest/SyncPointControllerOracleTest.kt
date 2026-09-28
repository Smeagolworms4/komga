package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.persistence.SyncPointRepository
import org.gotson.komga.interfaces.api.rest.SyncPointController
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user

class SyncPointControllerOracleTest : OracleTest() {
  private val calls = RestOracle.Calls()
  private val repository =
    mockk<SyncPointRepository> {
      every { deleteByUserId(any()) } answers { calls.add("deleteByUserId", firstArg<String>()) }
      every { deleteByUserIdAndApiKeyIds(any(), any()) } answers { calls.add("deleteByUserIdAndApiKeyIds", firstArg<String>(), secondArg<Collection<String>>().toList()) }
    }
  private val controller = SyncPointController(repository)
  private val p = principal(user("U1"))

  override fun cases() {
    func("deleteSyncPointsForCurrentUser") {
      case("null keys") {
        controller.deleteSyncPointsForCurrentUser(p, null)
        calls.take()
      }
      case("empty keys") {
        controller.deleteSyncPointsForCurrentUser(p, emptyList())
        calls.take()
      }
      case("with keys") {
        controller.deleteSyncPointsForCurrentUser(p, listOf("K2", "K1"))
        calls.take()
      }
    }
  }
}
