package com.academicmorning.app.data.repository

import com.academicmorning.app.data.prefs.ApiKeyStore
import com.academicmorning.app.data.prefs.SettingsManager
import com.academicmorning.app.data.remote.baidu.BaiduTranslateClient
import com.academicmorning.app.data.remote.llm.OpenAiCompatClient
import com.academicmorning.app.data.remote.tmt.TencentTmtClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** AI 服务配置仓库：DeepSeek / Kimi / 智谱 / 腾讯云 TMT / 百度翻译。 */
class AiConfigRepository(
    private val keyStore: ApiKeyStore,
    private val settings: SettingsManager
) {
    data class ProviderStatus(
        val provider: String,
        val label: String,
        val configured: Boolean,
        val active: Boolean
    )

    companion object {
        val PROVIDERS = listOf(
            "deepseek" to "DeepSeek",
            "kimi" to "Kimi",
            "zhipu" to "智谱清言",
            "tencent_tmt" to "腾讯云 TMT",
            "baidu_translate" to "百度翻译"
        )
        val APPLY_URLS = mapOf(
            "deepseek" to "https://platform.deepseek.com",
            "kimi" to "https://platform.moonshot.cn",
            "zhipu" to "https://open.bigmodel.cn",
            "tencent_tmt" to "https://console.cloud.tencent.com/tmt",
            "baidu_translate" to "https://fanyi-api.baidu.com"
        )
        /** 机器翻译类供应商（双字段：ID + 密钥）。 */
        val MT_PROVIDERS = setOf("tencent_tmt", "baidu_translate")

        fun idKeyOf(provider: String) =
            if (provider == "tencent_tmt") "tencent_tmt_secret_id" else "${provider}_app_id"

        fun secretKeyOf(provider: String) =
            if (provider == "tencent_tmt") "tencent_tmt_secret_key" else "${provider}_secret_key"
    }

    fun providers(): Flow<List<ProviderStatus>> =
        combine(settings.activeLlmProvider, settings.activeMtProvider) { active, mt ->
            PROVIDERS.map { (key, label) ->
                when (key) {
                    in MT_PROVIDERS -> ProviderStatus(
                        key, label,
                        configured = keyStore.hasKey(idKeyOf(key)) && keyStore.hasKey(secretKeyOf(key)),
                        active = mt == key
                    )
                    else -> ProviderStatus(
                        key, label,
                        configured = keyStore.hasKey(key),
                        active = active == key
                    )
                }
            }
        }

    suspend fun saveKey(provider: String, key: String) {
        keyStore.saveKey(provider, key)
        // 保存后自动激活该 LLM
        settings.setActiveLlmProvider(provider)
    }

    /** 保存机器翻译双字段凭证并激活（tencent_tmt / baidu_translate 通用）。 */
    suspend fun saveMtKeys(provider: String, appId: String, secret: String) {
        keyStore.saveKey(idKeyOf(provider), appId)
        keyStore.saveKey(secretKeyOf(provider), secret)
        settings.setActiveMtProvider(provider)
    }

    suspend fun removeKey(provider: String) {
        if (provider in MT_PROVIDERS) {
            keyStore.clearKey(idKeyOf(provider))
            keyStore.clearKey(secretKeyOf(provider))
            if (settings.activeMtProvider.first() == provider) {
                settings.setActiveMtProvider(null)
            }
        } else {
            keyStore.clearKey(provider)
            if (settings.activeLlmProvider.first() == provider) {
                settings.setActiveLlmProvider(null)
            }
        }
    }

    /** 设置激活供应商；null = 全部停用（纯英文模式）。 */
    suspend fun setActive(provider: String?) {
        if (provider == null) {
            settings.setActiveLlmProvider(null)
            settings.setActiveMtProvider(null)
        } else if (provider in MT_PROVIDERS) {
            settings.setActiveMtProvider(provider)
        } else {
            settings.setActiveLlmProvider(provider)
        }
    }

    suspend fun testConnection(provider: String): Result<String> {
        return try {
            when (provider) {
                "tencent_tmt" -> {
                    val id = keyStore.getKey(idKeyOf(provider))
                        ?: return Result.failure(Exception("请先填写 SecretId"))
                    val key = keyStore.getKey(secretKeyOf(provider))
                        ?: return Result.failure(Exception("请先填写 SecretKey"))
                    TencentTmtClient(id, key).testConnection()
                }
                "baidu_translate" -> {
                    val id = keyStore.getKey(idKeyOf(provider))
                        ?: return Result.failure(Exception("请先填写 APP ID"))
                    val key = keyStore.getKey(secretKeyOf(provider))
                        ?: return Result.failure(Exception("请先填写密钥"))
                    BaiduTranslateClient(id, key).testConnection()
                }
                else -> {
                    val apiKey = keyStore.getKey(provider)
                        ?: return Result.failure(Exception("请先填写 API Key"))
                    OpenAiCompatClient.forProvider(provider, apiKey).testConnection()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
