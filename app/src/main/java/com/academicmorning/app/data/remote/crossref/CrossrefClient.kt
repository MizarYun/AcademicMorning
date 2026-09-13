package com.academicmorning.app.data.remote.crossref

import com.academicmorning.app.data.remote.RawPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Crossref REST API 客户端：按期刊 ISSN 抓指定日期新增论文。 */
class CrossrefClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val message: Message? = null)

    @Serializable
    private data class Message(val items: List<Work> = emptyList())

    @Serializable
    private data class Work(
        @SerialName("DOI") val doi: String? = null,
        val title: List<String> = emptyList(),
        val abstract: String? = null,
        val author: List<Author> = emptyList(),
        val published: DateParts? = null,
        @SerialName("URL") val url: String? = null,
        val type: String? = null,
        val license: List<License> = emptyList()
    )

    @Serializable
    private data class Author(val given: String? = null, val family: String? = null)

    @Serializable
    private data class DateParts(@SerialName("date-parts") val dateParts: List<List<Int>> = emptyList())

    @Serializable
    private data class License(@SerialName("URL") val url: String? = null)

    /** 抓 [fromDate, toDate] ("yyyy-MM-dd") 范围内该期刊的新增论文。 */
    suspend fun fetchByIssn(
        issn: String, fromDate: String, toDate: String, journalName: String, rows: Int = 30
    ): List<RawPaper> = withContext(Dispatchers.IO) {
        val url = "https://api.crossref.org/journals/$issn/works" +
            "?filter=from-pub-date:$fromDate,until-pub-date:$toDate" +
            "&rows=$rows&select=DOI,title,abstract,author,published,URL,type,license"
        val req = Request.Builder().url(url)
            .header("User-Agent", "AcademicMorning/1.0 (mailto:academicmorning@example.com)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body!!.string()
            val items = json.decodeFromString<Response>(body).message?.items ?: return@withContext emptyList()
            items.mapNotNull { w ->
                val title = w.title.firstOrNull()?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val dp = w.published?.dateParts?.firstOrNull()
                val dateStr = if (dp != null && dp.isNotEmpty()) {
                    "%04d-%02d-%02d".format(dp.getOrElse(0) { 1970 }, dp.getOrElse(1) { 1 }, dp.getOrElse(2) { 1 })
                } else toDate
                RawPaper(
                    doi = w.doi,
                    title = title,
                    abstractText = w.abstract?.let { stripJats(it) },
                    journalName = journalName,
                    journalIssn = issn,
                    authors = w.author.take(6).joinToString(", ") {
                        listOfNotNull(it.family, it.given).joinToString(" ")
                    },
                    publishedDate = dateStr,
                    url = w.url ?: w.doi?.let { "https://doi.org/$it" }.orEmpty(),
                    isOpenAccess = w.license.any { it.url?.contains("creativecommons") == true },
                    isPreprint = w.type == "posted-content"
                )
            }
        }
    }

    /**
     * 拉取该期刊最近发表的一批论文日期（按发表时间倒序），用于识别出刊周期。
     * 失败或无数据时返回空列表。
     */
    suspend fun fetchRecentDates(issn: String, rows: Int = 14): List<java.time.LocalDate> =
        withContext(Dispatchers.IO) {
            val url = "https://api.crossref.org/journals/$issn/works" +
                "?sort=published&order=desc&rows=$rows&select=DOI,published"
            val req = Request.Builder().url(url)
                .header("User-Agent", "AcademicMorning/1.0 (mailto:academicmorning@example.com)")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body!!.string()
                    val items = json.decodeFromString<Response>(body).message?.items
                        ?: return@withContext emptyList()
                    items.mapNotNull { w ->
                        val dp = w.published?.dateParts?.firstOrNull() ?: return@mapNotNull null
                        if (dp.isEmpty()) return@mapNotNull null
                        runCatching {
                            java.time.LocalDate.of(
                                dp[0], dp.getOrElse(1) { 1 }, dp.getOrElse(2) { 1 }
                            )
                        }.getOrNull()
                    }.distinct().sortedDescending()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** 去除 Crossref 摘要中的 JATS XML 标签。 */
    private fun stripJats(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
