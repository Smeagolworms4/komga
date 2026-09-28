package org.gotson.komga.oracle.infrastructure.configuration

import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.context.ApplicationEventPublisher

class KomgaSettingsProviderOracleTest : OracleTest() {
  private val db = OracleDb()
  private val events = mutableListOf<Any>()
  private val publisher = ApplicationEventPublisher { events.add(it) }

  private fun stored() = db.serverSettingsDao.getSettingByKey("REMEMBER_ME_KEY", String::class.java)

  private fun describe(key: String) = listOf(key.length, key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })

  override fun cases() {
    func("getRandomRememberMeKey") {
      case("generated at creation and stored") {
        val provider = KomgaSettingsProvider(db.serverSettingsDao, publisher)
        describe(provider.rememberMeKey) + listOf(stored() == provider.rememberMeKey)
      }
      case("read back by a new provider") {
        val first = KomgaSettingsProvider(db.serverSettingsDao, publisher).rememberMeKey
        KomgaSettingsProvider(db.serverSettingsDao, publisher).rememberMeKey == first
      }
      case("keys differ") { (1..5).map { KomgaSettingsProvider(OracleDb().serverSettingsDao, publisher).rememberMeKey }.toSet().size }
    }
    func("renewRememberMeKey") {
      case("new key stored") {
        val provider = KomgaSettingsProvider(db.serverSettingsDao, publisher)
        val before = provider.rememberMeKey
        provider.renewRememberMeKey()
        describe(provider.rememberMeKey) + listOf(provider.rememberMeKey != before, stored() == provider.rememberMeKey)
      }
      case("twice") {
        val provider = KomgaSettingsProvider(db.serverSettingsDao, publisher)
        provider.renewRememberMeKey()
        val first = provider.rememberMeKey
        provider.renewRememberMeKey()
        listOf(first != provider.rememberMeKey, KomgaSettingsProvider(db.serverSettingsDao, publisher).rememberMeKey == provider.rememberMeKey)
      }
      case("no event published") { events.size }
    }
  }
}
