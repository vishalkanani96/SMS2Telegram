package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSettings(token: String, chatId: String) {
        prefs.edit {
            putString(KEY_API_TOKEN, token.trim())
            putString(KEY_CHAT_ID, chatId.trim())
            // Track when token was created for rotation reminders
            putLong(KEY_TOKEN_CREATED_AT, System.currentTimeMillis())
        }
    }

    fun loadSettings(): TelegramSettings? {
        val token = prefs.getString(KEY_API_TOKEN, null)?.takeIf { it.isNotBlank() }
        val chatId = prefs.getString(KEY_CHAT_ID, null)?.takeIf { it.isNotBlank() }
        val createdAt = prefs.getLong(KEY_TOKEN_CREATED_AT, System.currentTimeMillis())
        return if (token != null && chatId != null) {
            TelegramSettings(token, chatId, createdAt)
        } else {
            null
        }
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunch(isFirstLaunch: Boolean) {
        prefs.edit {
            putBoolean(KEY_FIRST_LAUNCH, isFirstLaunch)
        }
    }

    fun saveLastForwardStatus(status: String) {
        prefs.edit {
            putString(KEY_LAST_STATUS, status)
        }
    }

    fun loadLastForwardStatus(): String? = prefs.getString(KEY_LAST_STATUS, null)

    fun isForwardingEnabled(): Boolean = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_FORWARDING_ENABLED, enabled)
        }
    }

    fun isShowSimNameEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_SIM_NAME, false)

    fun setShowSimNameEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_SHOW_SIM_NAME, enabled)
        }
    }

    /**
     * Checks if the API token should be rotated (older than 90 days)
     * Recommend user to rotate token periodically for security
     */
    fun shouldRotateToken(): Boolean {
        val createdAt = prefs.getLong(KEY_TOKEN_CREATED_AT, System.currentTimeMillis())
        val ageInDays = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60 * 24)
        return ageInDays > 90  // Recommend rotation every 90 days
    }

    /**
     * Gets days until token should be rotated
     */
    fun daysUntilTokenRotation(): Long {
        val createdAt = prefs.getLong(KEY_TOKEN_CREATED_AT, System.currentTimeMillis())
        val ageInDays = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60 * 24)
        return maxOf(0, 90 - ageInDays)
    }

    data class TelegramSettings(
        val apiToken: String,
        val chatId: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    companion object {
        private const val PREFS_NAME = "sms_forwarder_prefs"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_TOKEN_CREATED_AT = "token_created_at"
        private const val KEY_LAST_STATUS = "last_forward_status"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SHOW_SIM_NAME = "show_sim_name"
    }
}
