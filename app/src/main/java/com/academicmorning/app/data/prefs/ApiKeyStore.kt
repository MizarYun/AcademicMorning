package com.academicmorning.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 加密存储：EncryptedSharedPreferences + Android Keystore。
 * Key 仅存本机，allowBackup=false 保证不随云备份导出。
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveKey(provider: String, key: String) {
        prefs.edit().putString(provider, key.trim()).apply()
    }

    fun getKey(provider: String): String? = prefs.getString(provider, null)

    fun hasKey(provider: String): Boolean = !prefs.getString(provider, null).isNullOrBlank()

    fun clearKey(provider: String) {
        prefs.edit().remove(provider).apply()
    }
}
