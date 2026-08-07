package com.academicmorning.app.data.repository

import com.academicmorning.app.data.prefs.ApiKeyStore
import com.academicmorning.app.data.prefs.SettingsManager
import com.academicmorning.app.data.remote.llm.OpenAiCompatClient
import com.academicmorning.app.data.remote.tmt.TencentTmtClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** AI 服务配置仓库：DeepSeek / Kimi / 智谱 / 腾讯云 TMT。 */
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
            "tencent_tmt" to "腾讯云 TMT"
        )
        val APPLY_URLS = mapOf(
            "deepseek" to "https://platform.deepseek.com",
            "kimi" to "https://platform.moonshot.cn",
            "zhipu" to "https://open.bigmodel.cn",
            "tencent_tmt" to "https://console.cloud.tencent.com/tmt"
        )
    }

    fun providers(): Flow<List<ProviderStatus>> =
        combine(settings.activeLlmProvider, settings.tmtEnabled) { active, tmt ->
            PROVIDERS.map { (key, label) ->
                when (key) {
                    "tencent_tmt" -> ProviderStatus(
                        key, label,
                        configured = keyStore.hasKey("tencent_tmt_secret_id") &&
                            keyStore.hasKey("tencent_tmt_secret_key"),
                        active = tmt && active == null
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
        // 保存后自动激活该 LLM，并关闭 TMT
        settings.setActiveLlmProvider(provider)
        settings.setTmtEnabled(false)
    }

    suspend fun saveTencentKeys(secretId: String, secretKey: String) {
        keyStore.saveKey("tencent_tmt_secret_id", secretId)
        keyStore.saveKey("tencent_tmt_secret_key", secretKey)
        settings.setTmtEnabled(true)
        settings.setActiveLlmProvider(null)
    }

    suspend fun removeKey(provider: String) {
        if (provider == "tencent_tmt") {
            keyStore.clearKey("tencent_tmt_secret_id")
            keyStore.clearKey("tencent_tmt_secret_key")
            settings.setTmtEnabled(false)
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
            settings.setTmtEnabled(false)
        } else if (provider == "tencent_tmt") {
            settings.setActiveLlmProvider(null)
            settings.setTmtEnabled(true)
        } else {
            settings.setActiveLlmProvider(provider)
            settings.setTmtEnabled(false)
        }
    }

    suspend fun testConnection(provider: String): Result<String> {
        return try {
            when (provider) {
                "tencent_tmt" -> {
                    val id = keyStore.getKey("tencent_tmt_secret_id")
                        ?: return Result.failure(Exception("请先填写 SecretId"))
                    val key = keyStore.getKey("tencent_tmt_secret_key")
                        ?: return Result.failure(Exception("请先填写 SecretKey"))
                    TencentTmtClient(id, key).testConnection()
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
