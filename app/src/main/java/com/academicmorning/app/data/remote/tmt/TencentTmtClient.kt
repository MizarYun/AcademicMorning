package com.academicmorning.app.data.remote.tmt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * 腾讯云机器翻译 TMT 客户端。
 * 手写 TC3-HMAC-SHA256 签名（仅标准库，无第三方 SDK）。
 * 文档: https://cloud.tencent.com/document/api/551/15619
 */
class TencentTmtClient(
    private val secretId: String,
    private val secretKey: String
) {
    private val host = "tmt.tencentcloudapi.com"
    private val service = "tmt"
    private val action = "TextTranslate"
    private val version = "2018-03-21"
    private val region = "ap-guangzhou"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 英译中。返回译文；失败抛异常。 */
    suspend fun translateEnToZh(text: String): String = withContext(Dispatchers.IO) {
        val payload = buildString {
            append("{\"Source\":\"en\",\"Target\":\"zh\",\"ProjectId\":0,\"SourceText\":")
            append(json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(),
                kotlinx.serialization.json.JsonPrimitive(text)))
            append("}")
        }
        val timestamp = System.currentTimeMillis() / 1000
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp * 1000))

        val contentType = "application/json; charset=utf-8"
        val canonicalHeaders = "content-type:$contentType\nhost:$host\nx-tc-action:${action.lowercase(Locale.ROOT)}\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val hashedPayload = sha256Hex(payload)
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n$hashedPayload"

        val credentialScope = "$date/$service/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val kDate = hmacSha256(("TC3$secretKey").toByteArray(), date)
        val kService = hmacSha256(kDate, service)
        val kSigning = hmacSha256(kService, "tc3_request")
        val signature = hmacSha256(kSigning, stringToSign).toHex()

        val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        val req = Request.Builder()
            .url("https://$host")
            .header("Authorization", authorization)
            .header("Content-Type", contentType)
            .header("Host", host)
            .header("X-TC-Action", action)
            .header("X-TC-Version", version)
            .header("X-TC-Timestamp", timestamp.toString())
            .header("X-TC-Region", region)
            .post(payload.toRequestBody(contentType.toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body!!.string()
            val root = json.parseToJsonElement(body).jsonObject["Response"]?.jsonObject
                ?: throw Exception("TMT 响应异常")
            root["Error"]?.jsonObject?.let { err ->
                throw Exception(err["Message"]?.jsonPrimitive?.content ?: "TMT 错误")
            }
            root["TargetText"]?.jsonPrimitive?.content ?: throw Exception("TMT 无译文")
        }
    }

    suspend fun testConnection(): Result<String> = try {
        val r = translateEnToZh("Hello, science.")
        Result.success("连接正常：Hello, science. → $r")
    } catch (e: Exception) {
        Result.failure(Exception("连接失败：${e.message}"))
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).toHex()

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
