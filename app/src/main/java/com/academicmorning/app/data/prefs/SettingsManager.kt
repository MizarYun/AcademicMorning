package com.academicmorning.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.academicmorning.app.data.model.Discipline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 用户偏好（DataStore）。 */
class SettingsManager(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        val PUSH_MINUTES = intPreferencesKey("push_minutes")
        val ACTIVE_LLM = stringPreferencesKey("active_llm_provider")
        val TMT_ENABLED = booleanPreferencesKey("tmt_enabled")
        val FOLLOWED_DISCIPLINES = stringPreferencesKey("followed_disciplines")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val FAVORITE_FOLDERS = stringPreferencesKey("favorite_folders")
        val ACTIVE_MT = stringPreferencesKey("active_mt_provider")
        fun ifThreshold(discipline: String) = doublePreferencesKey("if_threshold_$discipline")
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val pushEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PUSH_ENABLED] ?: true }
    val pushTimeMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.PUSH_MINUTES] ?: 480 }
    val activeLlmProvider: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_LLM] }
    val tmtEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TMT_ENABLED] ?: false }

    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = v }
    suspend fun setPushEnabled(v: Boolean) = context.dataStore.edit { it[Keys.PUSH_ENABLED] = v }
    suspend fun setPushTimeMinutes(v: Int) = context.dataStore.edit { it[Keys.PUSH_MINUTES] = v }
    suspend fun setActiveLlmProvider(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(Keys.ACTIVE_LLM) else it[Keys.ACTIVE_LLM] = v
    }
    suspend fun setTmtEnabled(v: Boolean) = context.dataStore.edit { it[Keys.TMT_ENABLED] = v }

    /** 关注的学科分类（key 列表，最多 3 个）。 */
    val followedDisciplines: Flow<List<String>> = context.dataStore.data.map {
        it[Keys.FOLLOWED_DISCIPLINES]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

    suspend fun setFollowedDisciplines(keys: List<String>) = context.dataStore.edit {
        it[Keys.FOLLOWED_DISCIPLINES] = keys.take(3).joinToString(",")
    }

    /** 翻译引擎偏好："ask"（每次询问）| "tmt"（机器翻译）| "llm"（大模型）。 */
    val translationEngine: Flow<String> = context.dataStore.data.map {
        it[Keys.TRANSLATION_ENGINE] ?: "ask"
    }

    suspend fun setTranslationEngine(v: String) = context.dataStore.edit {
        it[Keys.TRANSLATION_ENGINE] = v
    }

    /** 激活的机器翻译供应商："tencent_tmt" | "baidu_translate" | null。 */
    val activeMtProvider: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_MT]
            // 兼容旧版：未设置但 tmtEnabled=true 时视为腾讯 TMT
            ?: if (prefs[Keys.TMT_ENABLED] == true) "tencent_tmt" else null
    }

    suspend fun setActiveMtProvider(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(Keys.ACTIVE_MT) else it[Keys.ACTIVE_MT] = v
    }

    /** 用户创建的收藏夹（可为空收藏夹）。 */
    val favoriteFolders: Flow<List<String>> = context.dataStore.data.map {
        it[Keys.FAVORITE_FOLDERS]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

    suspend fun addFavoriteFolder(name: String) = context.dataStore.edit { prefs ->
        val cur = prefs[Keys.FAVORITE_FOLDERS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        if (!cur.contains(name)) {
            prefs[Keys.FAVORITE_FOLDERS] = (cur + name).joinToString(",")
        }
    }

    fun ifThreshold(discipline: String): Flow<Double> = context.dataStore.data.map {
        it[Keys.ifThreshold(discipline)]
            ?: Discipline.fromKey(discipline)?.defaultIfThreshold ?: 3.0
    }

    suspend fun setIfThreshold(discipline: String, v: Double) =
        context.dataStore.edit { it[Keys.ifThreshold(discipline)] = v }
}
