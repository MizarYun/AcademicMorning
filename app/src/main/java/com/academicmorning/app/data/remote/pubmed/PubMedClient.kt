package com.academicmorning.app.data.remote.pubmed

import android.util.Xml
import com.academicmorning.app.data.remote.RawPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/** PubMed E-utilities 客户端：esearch 找当日 PMID，efetch 取题录。 */
class PubMedClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ESearchResponse(val esearchresult: ESearchResult? = null)

    @Serializable
    private data class ESearchResult(val idlist: List<String> = emptyList())

    /** 抓 [fromDate, toDate] 范围内该期刊的新增论文。 */
    suspend fun fetchByJournal(
        journalQuery: String, fromDate: String, toDate: String,
        journalName: String, issn: String, retmax: Int = 30
    ): List<RawPaper> = withContext(Dispatchers.IO) {
        val mindate = fromDate.replace("-", "/")
        val maxdate = toDate.replace("-", "/")
        val esearchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi" +
            "?db=pubmed&term=\"$journalQuery\"[jour]" +
            "&datetype=pdat&mindate=$mindate&maxdate=$maxdate" +
            "&retmax=$retmax&retmode=json"
        val ids = get(esearchUrl)?.let {
            json.decodeFromString<ESearchResponse>(it).esearchresult?.idlist
        }.orEmpty()
        if (ids.isEmpty()) return@withContext emptyList()

        val efetchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi" +
            "?db=pubmed&id=${ids.joinToString(",")}&retmode=xml"
        val xml = get(efetchUrl) ?: return@withContext emptyList()
        parseEfetch(xml, journalName, issn, toDate)
    }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", "AcademicMorning/1.0").build()
        client.newCall(req).execute().use { r ->
            if (r.isSuccessful) r.body!!.string() else null
        }
    } catch (e: Exception) {
        null
    }

    /** 解析 PubMed efetch XML（XmlPullParser，零新依赖）。 */
    private fun parseEfetch(
        xml: String, journalName: String, issn: String, fallbackDate: String
    ): List<RawPaper> {
        val out = mutableListOf<RawPaper>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var inArticle = false
        var tag = ""
        var pmid = ""
        var title = StringBuilder()
        var abstractText = StringBuilder()
        var doi: String? = null
        var authors = mutableListOf<String>()
        var lastName = ""
        var foreName = ""
        var year = ""; var month = ""; var day = ""

        fun flush() {
            if (pmid.isEmpty()) return
            val date = if (year.isNotEmpty()) {
                "%04d-%02d-%02d".format(
                    year.toIntOrNull() ?: 1970,
                    month.toIntOrNull() ?: 1,
                    day.toIntOrNull() ?: 1
                )
            } else fallbackDate
            out.add(
                RawPaper(
                    doi = doi,
                    title = title.toString().replace(Regex("\\s+"), " ").trim(),
                    abstractText = abstractText.toString().replace(Regex("\\s+"), " ").trim()
                        .ifEmpty { null },
                    journalName = journalName,
                    journalIssn = issn,
                    authors = authors.take(6).joinToString(", "),
                    publishedDate = date,
                    url = "https://pubmed.ncbi.nlm.nih.gov/$pmid/",
                    isOpenAccess = false,
                    isPreprint = false
                )
            )
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "PubmedArticle" -> {
                            inArticle = true
                            pmid = ""; title = StringBuilder(); abstractText = StringBuilder()
                            doi = null; authors = mutableListOf()
                            year = ""; month = ""; day = ""
                        }
                        "ArticleId" -> {
                            if (parser.getAttributeValue(null, "IdType") == "doi") tag = "ArticleIdDoi"
                        }
                        "Author" -> { lastName = ""; foreName = "" }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inArticle) {
                        when (tag) {
                            "PMID" -> if (pmid.isEmpty()) pmid = parser.text.trim()
                            "ArticleTitle" -> title.append(parser.text)
                            "AbstractText" -> abstractText.append(parser.text).append(' ')
                            "ArticleIdDoi" -> doi = parser.text.trim()
                            "LastName" -> lastName = parser.text.trim()
                            "ForeName" -> foreName = parser.text.trim()
                            "Year" -> if (year.isEmpty()) year = parser.text.trim()
                            "Month" -> if (month.isEmpty()) {
                                month = monthToNumber(parser.text.trim())
                            }
                            "Day" -> if (day.isEmpty()) day = parser.text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "Author" -> {
                            if (lastName.isNotEmpty()) authors.add("$lastName $foreName".trim())
                        }
                        "PubmedArticle" -> { flush(); inArticle = false }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        return out.filter { it.title.isNotEmpty() }
    }

    private fun monthToNumber(m: String): String = when (m.lowercase().take(3)) {
        "jan" -> "1"; "feb" -> "2"; "mar" -> "3"; "apr" -> "4"; "may" -> "5"; "jun" -> "6"
        "jul" -> "7"; "aug" -> "8"; "sep" -> "9"; "oct" -> "10"; "nov" -> "11"; "dec" -> "12"
        else -> m.filter { it.isDigit() }.ifEmpty { "1" }
    }
}
