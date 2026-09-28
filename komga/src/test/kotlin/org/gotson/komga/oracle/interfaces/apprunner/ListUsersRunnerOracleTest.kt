package org.gotson.komga.oracle.interfaces.apprunner

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.persistence.KomgaUserRepository
import org.gotson.komga.interfaces.apprunner.ListUsersRunner
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.DefaultApplicationArguments
import java.time.LocalDateTime

class ListUsersRunnerOracleTest : OracleTest() {
  private var calls = 0

  private fun repository(emails: List<String>) =
    mockk<KomgaUserRepository>().also {
      every { it.findAll() } answers {
        calls++
        emails.mapIndexed { i, e -> KomgaUser(e, "pw", id = "U$i", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)) }
      }
    }

  private fun run(
    emails: List<String>,
    vararg args: String,
  ): Any? {
    calls = 0
    ListUsersRunner(repository(emails)).run(DefaultApplicationArguments(*args))
    return calls
  }

  override fun cases() {
    func("run") {
      case("no argument") { run(listOf("a@example.org")) }
      case("other argument") { run(listOf("a@example.org"), "--list-user") }
      case("list users") { run(listOf("a@example.org", "b@example.org"), "--list-users") }
      case("list users with value") { run(listOf("a@example.org"), "--list-users=true") }
      case("no users") { run(emptyList(), "--list-users") }
      case("non option argument") { run(listOf("a@example.org"), "list-users") }
    }
  }
}
