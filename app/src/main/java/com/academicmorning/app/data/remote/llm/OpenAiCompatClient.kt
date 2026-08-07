package com.academicmorning.app.data.remote.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 翻译结果。 */
data class TranslationResult(
    val titleZh: String?,
    val abstractZh: String?,
    val oneLiner: String?
)

/** OpenAI 兼容 Chat Completions 客户端：DeepSeek / Kimi / 智谱 通用。 */
class OpenAiCompatClient(
    private val endpoint: String,   // 如 "https://api.deepseek.com"
    private val apiKey: String,
    private val model: String
) {

    companion object {
        fun forProvider(provider: String, apiKey: String): OpenAiCompatClient = when (provider) {
            "deepseek" -> OpenAiCompatClient("https://api.deepseek.com", apiKey, "deepseek-chat")
            "kimi" -> OpenAiCompatClient("https://api.moonshot.cn/v1", apiKey, "moonshot-v1-8k")
            "zhipu" -> OpenAiCompatClient("https://open.bigmodel.cn/api/paas/v4", apiKey, "glm-4-flash")
            else -> throw IllegalArgumentException("unknown provider: $provider")
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.2
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    private val systemPrompt = """
你是学术论文翻译助手。将用户给出的英文论文标题与摘要翻译为学术中文，并提炼核心发现。
要求：
1. 翻译准确流畅，专业术语保留英文原文（如 CRISPR-Cas9、LLM、Transformer）。
2. 只输出严格 JSON，不要输出任何其他文字：
{"title_zh":"中文标题","abstract_zh":"中文摘要","one_liner":"一句话核心发现（不超过60字）"}
""".trim()

    /** 翻译 + 一句话提炼，一次调用完成。 */
    suspend fun translatePaper(title: String, abstractText: String?): TranslationResult =
        withContext(Dispatchers.IO) {
            val userContent = buildString {
                append("标题：").append(title)
                if (!abstractText.isNullOrBlank()) {
                    append("\n\n摘要：").append(abstractText.take(3000))
                }
            }
            val content = chat(systemPrompt, userContent)
            parseResult(content)
        }

    /**
     * 混合省 token 模式：标题已由 TMT 机翻，大模型只处理摘要翻译 + 一句话提炼。
     */
    suspend fun summarizeAbstract(abstractText: String): TranslationResult =
        withContext(Dispatchers.IO) {
            val sys = """
你是学术论文翻译助手。将用户给出的英文论文摘要翻译为学术中文，并提炼核心发现。
要求：
1. 翻译准确流畅，专业术语保留英文原文（如 CRISPR-Cas9、LLM、Transformer）。
2. 只输出严格 JSON，不要输出任何其他文字：
{"abstract_zh":"中文摘要","one_liner":"一句话核心发现（不超过60字）"}
""".trim()
            val content = chat(sys, "摘要：" + abstractText.take(3000))
            val r = parseResult(content)
            r.copy(titleZh = null)
        }

    /** 连通性测试：最小化调用。 */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            chat("You are a helpful assistant.", "Say OK.", maxTokens = 8)
            Result.success("连接正常，模型 $model 可用")
        } catch (e: Exception) {
            Result.failure(Exception("连接失败：${e.message}"))
        }
    }

    private fun chat(system: String, user: String, maxTokens: Int = 1024): String {
        val reqObj = ChatRequest(
            model = model,
            messages = listOf(Message("system", system), Message("user", user))
        )
        val bodyStr = json.encodeToString(ChatRequest.serializer(), reqObj)
        val req = Request.Builder()
            .url("$endpoint/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code} ${resp.body!!.string().take(200)}")
            }
            val respStr = resp.body!!.string()
            val root = json.parseToJsonElement(respStr).jsonObject
            val arr = root["choices"] as kotlinx.serialization.json.JsonArray
            val msg = arr[0].jsonObject["message"]!!.jsonObject
            return msg["content"]!!.jsonPrimitive.content
        }
    }

    /** 容错解析模型输出的 JSON。 */
    private fun parseResult(content: String): TranslationResult {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = json.parseToJsonElement(cleaned).jsonObject
            TranslationResult(
                titleZh = obj["title_zh"]?.jsonPrimitive?.content?.ifBlank { null },
                abstractZh = obj["abstract_zh"]?.jsonPrimitive?.content?.ifBlank { null },
                oneLiner = obj["one_liner"]?.jsonPrimitive?.content?.ifBlank { null }
            )
        } catch (e: Exception) {
            TranslationResult(titleZh = null, abstractZh = cleaned, oneLiner = null)
        }
    }
}
