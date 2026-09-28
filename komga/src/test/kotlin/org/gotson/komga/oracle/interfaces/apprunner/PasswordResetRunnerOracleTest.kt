package org.gotson.komga.oracle.interfaces.apprunner

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.interfaces.apprunner.PasswordResetRunner
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.DefaultApplicationArguments
import java.time.LocalDateTime

class PasswordResetRunnerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = mutableListOf<List<Any?>>()
  private val lifecycle =
    mockk<KomgaUserLifecycle>().also {
      every { it.updatePassword(any(), any(), any()) } answers { calls.add(listOf(firstArg<KomgaUser>().id, secondArg<String>(), thirdArg<Boolean>())) }
    }
  private val runner = PasswordResetRunner(db.komgaUserDao, lifecycle)

  private fun run(vararg args: String): List<List<Any?>> {
    calls.clear()
    runner.run(DefaultApplicationArguments(*args))
    return calls.toList()
  }

  override fun cases() {
    func("run") {
      case("populate") {
        db.komgaUserDao.insert(KomgaUser("user@example.org", "pw", id = "U1", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)))
        db.komgaUserDao.insert(KomgaUser("Other@Example.org", "pw", id = "U2", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)))
      }
      case("no argument") { run() }
      case("only reset") { run("--reset=user@example.org") }
      case("only new password") { run("--newpassword=secret") }
      case("both") { run("--reset=user@example.org", "--newpassword=secret") }
      case("case insensitive email") { run("--reset=USER@example.org", "--newpassword=secret") }
      case("several users") { run("--reset=user@example.org", "--reset=other@example.org", "--reset=unknown@example.org", "--newpassword=s") }
      case("duplicate user") { run("--reset=user@example.org", "--reset=user@example.org", "--newpassword=s") }
      case("blank password") { run("--reset=user@example.org", "--newpassword=  ") }
      case("empty password") { run("--reset=user@example.org", "--newpassword=") }
      case("password without value") { run("--reset=user@example.org", "--newpassword") }
      case("several passwords: first") { run("--reset=user@example.org", "--newpassword=a", "--newpassword=b") }
      case("reset without value") { run("--reset", "--newpassword=a") }
      case("password with equals") { run("--reset=user@example.org", "--newpassword=a=b") }
    }
  }
}
