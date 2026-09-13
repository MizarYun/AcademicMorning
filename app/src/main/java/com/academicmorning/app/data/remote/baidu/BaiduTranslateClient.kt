package com.academicmorning.app.data.remote.baidu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 百度翻译开放平台（fanyi-api.baidu.com）通用文本翻译，en→zh。 */
class BaiduTranslateClient(
    private val appId: String,
    private val secretKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** 翻译英文为中文。百度单次 q 上限约 6000 字符。 */
    suspend fun translateEnToZh(text: String): String = withContext(Dispatchers.IO) {
        val q = text.take(5500)
        val salt = System.currentTimeMillis().toString()
        val sign = md5(appId + q + salt + secretKey)
        val url = "https://fanyi-api.baidu.com/api/trans/vip/translate" +
            "?q=${URLEncoder.encode(q, "UTF-8")}&from=en&to=zh" +
            "&appid=$appId&salt=$salt&sign=$sign"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        val body = resp.body!!.string()
        val obj = json.parseToJsonElement(body).jsonObject
        obj["error_code"]?.let {
            throw Exception("百度翻译错误 ${it.jsonPrimitive.content}: " +
                (obj["error_msg"]?.jsonPrimitive?.content ?: ""))
        }
        val results = obj["trans_result"]?.jsonArray
            ?: throw Exception("百度翻译返回异常")
        results.joinToString("\n") { it.jsonObject["dst"]!!.jsonPrimitive.content }
    }

    suspend fun testConnection(): Result<String> = try {
        val r = translateEnToZh("hello")
        Result.success("连接正常：hello → $r")
    } catch (e: Exception) {
        Result.failure(Exception("连接失败：${e.message}"))
    }
}
