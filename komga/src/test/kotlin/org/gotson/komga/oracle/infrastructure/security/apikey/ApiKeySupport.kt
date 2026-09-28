package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.infrastructure.hash.Hasher
import org.gotson.komga.infrastructure.security.PasswordEncoderConfiguration
import org.gotson.komga.infrastructure.security.UserAgentWebAuthenticationDetails
import org.gotson.komga.infrastructure.security.UserAgentWebAuthenticationDetailsSource
import org.gotson.komga.oracle.OracleDb
import java.time.LocalDateTime

/** Shared fixtures of the apikey oracles, mirrored by test/unit/infrastructure/security/apikey/support.ts */
object ApiKeySupport {
  val hasher = Hasher()
  val tokenEncoder = PasswordEncoderConfiguration().getTokenEncoder()
  val detailsSource = UserAgentWebAuthenticationDetailsSource()
  val date: LocalDateTime = LocalDateTime.of(2020, 5, 6, 7, 8, 9)

  /** A user U1 (Kobo sync + streaming) with the API keys "key-one" (K1) and "key-two" (K2), and an admin U2 with "admin-key" (K3) */
  fun populate(db: OracleDb) {
    db.komgaUserDao.insert(KomgaUser("user@example.org", "pass", roles = setOf(UserRoles.KOBO_SYNC, UserRoles.PAGE_STREAMING), id = "U1", createdDate = date))
    db.komgaUserDao.insert(KomgaUser("admin@example.org", "pass", roles = UserRoles.entries.toSet(), id = "U2", createdDate = date))
    db.komgaUserDao.insert(ApiKey(id = "K1", userId = "U1", key = tokenEncoder.encode("key-one"), comment = "Kobo", createdDate = date))
    db.komgaUserDao.insert(ApiKey(id = "K2", userId = "U1", key = tokenEncoder.encode("key-two"), comment = "KOReader", createdDate = date))
    db.komgaUserDao.insert(ApiKey(id = "K3", userId = "U2", key = tokenEncoder.encode("admin-key"), comment = "Admin", createdDate = date))
  }

  fun describeDetails(details: Any?): List<Any?>? =
    (details as UserAgentWebAuthenticationDetails?)?.let { listOf(it::class.java.simpleName, it.remoteAddress, it.sessionId, it.userAgent) }
}
