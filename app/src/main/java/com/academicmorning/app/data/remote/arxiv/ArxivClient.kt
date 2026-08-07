package com.academicmorning.app.data.remote.arxiv

import android.util.Xml
import com.academicmorning.app.data.remote.RawPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/** arXiv API 客户端：按分类抓最新提交，过滤出昨日条目。 */
class ArxivClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 抓 [fromDate, toDate] 范围内该分类的最新提交。 */
    suspend fun fetchByCategory(
        category: String, fromDate: String, toDate: String,
        journalName: String, issn: String, maxResults: Int = 50
    ): List<RawPaper> = withContext(Dispatchers.IO) {
        val url = "https://export.arxiv.org/api/query" +
            "?search_query=cat:$category&sortBy=submittedDate&sortOrder=descending&max_results=$maxResults"
        val xml = try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "AcademicMorning/1.0").build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body!!.string() else return@withContext emptyList()
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        parseAtom(xml, fromDate, toDate, journalName, issn)
    }

    private fun parseAtom(
        xml: String, fromDate: String, toDate: String, journalName: String, issn: String
    ): List<RawPaper> {
        val out = mutableListOf<RawPaper>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var inEntry = false
        var tag = ""
        var title = StringBuilder()
        var summary = StringBuilder()
        var id = ""
        var published = ""
        var doi: String? = null
        var authors = mutableListOf<String>()
        var authorName = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "entry" -> {
                            inEntry = true
                            title = StringBuilder(); summary = StringBuilder()
                            id = ""; published = ""; doi = null; authors = mutableListOf()
                        }
                        "name" -> authorName = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inEntry) {
                        when {
                            tag == "title" -> title.append(parser.text)
                            tag == "summary" -> summary.append(parser.text)
                            tag == "id" && id.isEmpty() -> id = parser.text.trim()
                            tag == "published" -> published = parser.text.trim()
                            tag == "name" -> authorName.append(parser.text)
                            tag.endsWith("doi") -> doi = parser.text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "name" -> authors.add(authorName.toString().trim())
                        "entry" -> {
                            val pubDate = published.take(10)
                            // 只保留日期范围内的条目（ISO 日期字符串可直接比较）
                            if (pubDate in fromDate..toDate && title.isNotEmpty()) {
                                out.add(
                                    RawPaper(
                                        doi = doi,
                                        title = title.toString().replace(Regex("\\s+"), " ").trim(),
                                        abstractText = summary.toString()
                                            .replace(Regex("\\s+"), " ").trim().ifEmpty { null },
                                        journalName = journalName,
                                        journalIssn = issn,
                                        authors = authors.take(6).filter { it.isNotEmpty() }
                                            .joinToString(", "),
                                        publishedDate = pubDate,
                                        url = id,
                                        isOpenAccess = true,
                                        isPreprint = true
                                    )
                                )
                            }
                            inEntry = false
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        return out
    }
}
