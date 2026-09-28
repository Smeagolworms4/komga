package org.gotson.komga.oracle.interfaces.scheduler

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.interfaces.scheduler.InitialUserController
import org.gotson.komga.interfaces.scheduler.InitialUsersDevConfiguration
import org.gotson.komga.interfaces.scheduler.InitialUsersProdConfiguration
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class InitialUserControllerOracleTest : OracleTest() {
  private val created = mutableListOf<String>()

  private fun lifecycle(count: Long) =
    mockk<KomgaUserLifecycle>().also {
      every { it.countUsers() } returns count
      every { it.createUser(any()) } answers { firstArg<KomgaUser>().also { u -> created.add(u.email) } }
    }

  private val users =
    listOf(
      KomgaUser("a@example.org", "pa", id = "U1", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)),
      KomgaUser("b@example.org", "pb", id = "U2", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)),
    )

  private fun run(
    count: Long,
    initial: List<KomgaUser>,
  ): List<String> {
    created.clear()
    InitialUserController(lifecycle(count), initial).createInitialUserOnStartupIfNoneExist()
    return created.toList()
  }

  private fun describe(u: KomgaUser) = listOf(u.email, u.roles.map { it.name }.sorted(), u.sharedAllLibraries)

  override fun cases() {
    func("createInitialUserOnStartupIfNoneExist") {
      case("no user: create") { run(0, users) }
      case("users exist: nothing") { run(1, users) }
      case("no initial users") { run(0, emptyList()) }
    }
    func("initialUsers@41") {
      val u = InitialUsersDevConfiguration().initialUsers()
      case("users") { u.map { describe(it) } }
      case("passwords") { u.map { it.password } }
      case("ids differ") { u.map { it.id }.toSet().size }
    }
    func("initialUsers@52") {
      val u = InitialUsersProdConfiguration().initialUsers()
      case("users") { u.map { describe(it) } }
      case("password") { u.map { p -> listOf(p.password.length, p.password.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) } }
      case("random password") { InitialUsersProdConfiguration().initialUsers().first().password != u.first().password }
      case("admin") { u.first().roles == UserRoles.entries.toSet() }
    }
  }
}
