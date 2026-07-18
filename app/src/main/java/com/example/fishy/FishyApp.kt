package com.example.fishy

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.fishy.data.local.FishyDatabase
import com.example.fishy.data.repo.FishyRepository
import com.example.fishy.data.settings.AppLanguage
import com.example.fishy.data.settings.SettingsRepository
import com.example.fishy.notifications.NotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FishyApp : Application() {

    lateinit var database: FishyDatabase
        private set
    lateinit var repository: FishyRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var notificationScheduler: NotificationScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = FishyDatabase.get(this)
        repository = FishyRepository(database)
        settingsRepository = SettingsRepository(this)
        applyStoredLanguage()
        notificationScheduler = NotificationScheduler(this, repository)
        notificationScheduler.rescheduleAll()
    }

    private fun applyStoredLanguage() {
        val lang = runBlocking { settingsRepository.settings.first().language }
        applyAppLanguage(lang)
    }

    companion object {
        @Volatile
        lateinit var instance: FishyApp
            private set

        fun applyAppLanguage(lang: AppLanguage) {
            val resolved = if (lang == AppLanguage.SYSTEM) AppLanguage.RU else lang
            val desired = LocaleListCompat.forLanguageTags(resolved.tag)
            val current = AppCompatDelegate.getApplicationLocales()
            if (current.toLanguageTags() != desired.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(desired)
            }
        }
    }
}
