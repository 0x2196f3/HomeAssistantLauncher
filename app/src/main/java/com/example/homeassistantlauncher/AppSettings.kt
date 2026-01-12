package com.example.homeassistantlauncher

import android.content.Context
import androidx.core.content.edit

class AppSettings(context: Context) {

    private val sharedPreferences by lazy {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_URLS = "urls"
        private const val KEY_SWITCH_DELAY = "switch_delay"

        const val DEFAULT_SWITCH_DELAY_VALUE = 0
    }

    fun getUrls(): List<String> {
        return (sharedPreferences.getString(KEY_URLS, "") ?: "").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun saveUrls(urls: List<String>) {
        val urlsString =
            urls.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(separator = "\n")
        sharedPreferences.edit {
            putString(KEY_URLS, urlsString)
        }
    }

    fun getSwitchDelay(): Int {
        return sharedPreferences.getInt(KEY_SWITCH_DELAY, DEFAULT_SWITCH_DELAY_VALUE)
    }

    fun saveSwitchDelay(delayValue: Int) {
        sharedPreferences.edit {
            putInt(KEY_SWITCH_DELAY, delayValue)
        }
    }
}
