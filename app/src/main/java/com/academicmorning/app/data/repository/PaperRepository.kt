package com.academicmorning.app.data.repository

import android.content.Context
import android.util.Log
import com.academicmorning.app.data.local.AppDatabase
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.data.local.entity.ReadingEvent
import com.academicmorning.app.data.prefs.ApiKeyStore
import com.academicmorning.app.data.prefs.SettingsManager
import com.academicmorning.app.data.remote.RawPaper
import com.academicmorning.app.data.remote.arxiv.ArxivClient
import com.academicmorning.app.data.remote.crossref.CrossrefClient
import com.academicmorning.app.data.remote.llm.OpenAiCompatClient
import com.academicmorning.app.data.remote.llm.TranslationResult
import com.academicmorning.app.data.remote.pubmed.PubMedClient
import com.academicmorning.app.data.remote.tmt.TencentTmtClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 论文仓库：抓取（Crossref/PubMed/arXiv）→ 入库 → 翻译（LLM > TMT > 纯英文）。
 */
class PaperRepository(
    private val db: AppDatabase,
    private val settings: SettingsManager,
    private val keyStore: ApiKeyStore,
    private val context: Context
) {
    private val crossref = CrossrefClient()
    private val pubmed = PubMedClient()
    private val arxiv = ArxivClient()

    fun papersForDate(date: String): Flow<List<Paper>> = db.paperDao().getByDate(date)

    fun paper(id: String): Flow<Paper?> = db.paperDao().getById(id)

    fun favorites(): Flow<List<Paper>> = db.paperDao().getFavorites()

    fun todayString(): String = dateFormat().format(Date())

    fun yesterdayString(): String = daysAgoString(1)

    fun daysAgoString(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return dateFormat().format(cal.time)
    }

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 每日自动推送：抓昨日（1 天）。 */
    suspend fun refreshToday(): Int = refresh(rangeDays = 1)

    /** 定时推送 Worker：抓当日起近三日（提前 2 分钟触发，留足抓取时间）。 */
    suspend fun refreshAuto(): Int = refresh(rangeDays = 3)

    /** 手动更新：抓近三日，与本地已保存论文去重避免冗余。 */
    suspend fun refreshManual(): Int = refresh(rangeDays = 3)

    /** 期刊回顾：抓指定期刊（null=全部关注期刊）最近 n 天（n≤7）。 */
    suspend fun refreshReview(journalIssn: String?, days: Int): Int {
        val n = days.coerceIn(1, 7)
        return refresh(rangeDays = n, onlyIssn = journalIssn)
    }

    /** 指定日期范围抓取（yyyy-MM-dd，跨度最大 7 天，自动截断）。 */
    suspend fun refreshRange(fromDate: String, toDate: String, journalIssn: String?): Int {
        val from = runCatching { dateFormat().parse(fromDate) }.getOrNull() ?: return 0
        val to = runCatching { dateFormat().parse(toDate) }.getOrNull() ?: return 0
        if (to.before(from)) return 0
        val spanDays = ((to.time - from.time) / (24 * 3600 * 1000)).toInt() + 1
        val capped = spanDays.coerceAtMost(7)
        // 若跨度超 7 天，从 toDate 向前截断
        val cal = Calendar.getInstance().apply { time = to; add(Calendar.DAY_OF_YEAR, -(capped - 1)) }
        return refresh(dateFormat().format(cal.time), dateFormat().format(to), journalIssn)
    }

    /** 清理 30 天前的非收藏论文（收藏永久保留，链接随时可访问）。 */
    suspend fun purgeOld(): Int {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        return db.paperDao().deleteOlderThan(cutoff)
    }

    fun allPapers(): Flow<List<Paper>> = db.paperDao().getAll()

    /**
     * 抓取最近 rangeDays 天的论文入库并按配置翻译，返回新增篇数。
     * 去重：先按 DOI/标题哈希在批次内去重，insertAll(IGNORE) 再与本地已存记录去重。
     */
    private suspend fun refresh(rangeDays: Int, onlyIssn: String? = null): Int {
        val toDate = yesterdayString()
        val fromDate = daysAgoString(rangeDays)   // rangeDays=1 → 昨天；=3 → 前天起共3天
        return refresh(fromDate, toDate, onlyIssn)
    }

    private suspend fun refresh(fromDate: String, toDate: String, onlyIssn: String?): Int {
        var journals = db.journalDao().getFollowedOnce()
        if (onlyIssn != null) journals = journals.filter { it.issn == onlyIssn }
        if (journals.isEmpty()) return 0
        val today = todayString()
        val rangeDays = runCatching {
            val f = dateFormat().parse(fromDate)!!.time
            val t = dateFormat().parse(toDate)!!.time
            ((t - f) / (24 * 3600 * 1000)).toInt() + 1
        }.getOrDefault(1)

        // 1. 抓取（单期刊失败不影响整体）
        val ifByIssn = journals.associate { it.issn to it.impactFactor }
        val raw = mutableListOf<RawPaper>()
        for (j in journals) {
            try {
                val items = when (j.source) {
                    "pubmed" -> pubmed.fetchByJournal(j.sourceRef, fromDate, toDate, j.name, j.issn)
                    "arxiv" -> arxiv.fetchByCategory(j.sourceRef, fromDate, toDate, j.name, j.issn)
                    else -> crossref.fetchByIssn(j.sourceRef, fromDate, toDate, j.name)
                }
                raw.addAll(items.take(if (rangeDays > 1) 40 else 20))
            } catch (e: Exception) {
                Log.w("PaperRepository", "fetch failed: ${j.name}: ${e.message}")
            }
        }

        // 2. 批次内去重 + 总量截断（按期刊 IF 降序）
        val cap = if (rangeDays > 1) 120 else 60
        val deduped = raw.distinctBy { it.stableId() }
            .sortedByDescending { ifByIssn[it.journalIssn] ?: 0.0 }
            .take(cap)
        if (deduped.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val entities = deduped.map { r ->
            Paper(
                id = r.stableId(),
                doi = r.doi,
                title = r.title,
                titleZh = null,
                abstractText = r.abstractText,
                abstractZh = null,
                oneLinerZh = null,
                journalName = r.journalName,
                journalIssn = r.journalIssn,
                authors = r.authors,
                publishedDate = r.publishedDate,
                url = r.url,
                isOpenAccess = r.isOpenAccess,
                isPreprint = r.isPreprint,
                fetchedDate = today,
                fetchedAt = now,
                translatedAt = null
            )
        }
        val rowIds = db.paperDao().insertAll(entities)
        val inserted = entities.zip(rowIds).filter { it.second != -1L }.map { it.first }

        // 3. 不再自动翻译：首页仅保留英文原文，
        //    由用户在详情页点击翻译按钮后按所选引擎手动翻译

        // 4. 返回今日实际新增篇数
        return inserted.size
    }

    /** 带限流重试的调用：遇 429/限流错误指数退避重试，最多 4 次。 */
    private suspend fun <T> withRateLimitRetry(tag: String, block: suspend () -> T): T {
        var waitMs = 4000L
        var lastErr: Exception? = null
        repeat(4) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastErr = e
                val msg = e.message.orEmpty()
                val isRateLimit = msg.contains("429") || msg.contains("rate", ignoreCase = true) ||
                    msg.contains("too many", ignoreCase = true) || msg.contains("limit", ignoreCase = true)
                if (!isRateLimit) throw e
                Log.w("PaperRepository", "rate limited ($tag, attempt ${attempt + 1}), wait ${waitMs}ms")
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(60_000L)
            }
        }
        throw lastErr ?: Exception("rate limit retry exhausted")
    }

    private fun tmtClientOrNull(): TencentTmtClient? {
        val id = keyStore.getKey("tencent_tmt_secret_id") ?: return null
        val key = keyStore.getKey("tencent_tmt_secret_key") ?: return null
        return TencentTmtClient(id, key)
    }

    /** 当前已配置的翻译能力：first=大模型可用，second=TMT 机翻可用。 */
    suspend fun configuredEngines(): Pair<Boolean, Boolean> {
        val provider = settings.activeLlmProvider.first()
        val llmConfigured = provider != null && keyStore.hasKey(provider)
        val tmtConfigured = settings.tmtEnabled.first() && tmtClientOrNull() != null
        return llmConfigured to tmtConfigured
    }

    /**
     * 详情页手动单篇翻译。
     * @param engine "llm" = 大模型翻译（标题+摘要+一句话核心发现，消耗 token，内容不变）；
     *               "tmt" = 腾讯云机器翻译（标题+摘要，速度快、准确度较低、不耗大模型 token）
     */
    suspend fun translatePaper(id: String, engine: String): Boolean {
        return try {
            val paper = db.paperDao().getByIdOnce(id) ?: return false
            when (engine) {
                "llm" -> {
                    val provider = settings.activeLlmProvider.first()
                        ?: return false
                    if (!keyStore.hasKey(provider)) return false
                    val client = OpenAiCompatClient.forProvider(provider, keyStore.getKey(provider)!!)
                    val r: TranslationResult =
                        withRateLimitRetry(id) { client.translatePaper(paper.title, paper.abstractText) }
                    db.paperDao().updateTranslation(
                        id, r.titleZh, r.abstractZh, r.oneLiner, System.currentTimeMillis()
                    )
                    true
                }
                "tmt" -> {
                    val tmt = tmtClientOrNull() ?: return false
                    val titleZh = withRateLimitRetry(id) { tmt.translateEnToZh(paper.title) }
                    val abstractZh = paper.abstractText?.take(1800)?.let {
                        try { withRateLimitRetry(id) { tmt.translateEnToZh(it) } } catch (e: Exception) { null }
                    }
                    db.paperDao().updateTranslation(id, titleZh, abstractZh, null, System.currentTimeMillis())
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.w("PaperRepository", "manual translate failed: $id: ${e.message}")
            false
        }
    }

    suspend fun markRead(id: String, durationSec: Int = 0) {
        db.paperDao().markRead(id)
        db.readingEventDao().insert(
            ReadingEvent(paperId = id, timestamp = System.currentTimeMillis(), durationSec = durationSec)
        )
    }

    suspend fun setFavorite(id: String, fav: Boolean) = db.paperDao().setFavorite(id, fav)

    /** 收藏并归入自定义分类（tags 逗号分隔）。 */
    suspend fun setFavoriteWithTags(id: String, fav: Boolean, tags: String) =
        db.paperDao().setFavoriteWithTags(id, fav, tags)

    suspend fun updateTags(id: String, tags: String) = db.paperDao().updateTags(id, tags)

    /** 当前所有收藏分类（已用标签 + 用户创建的空收藏夹）。 */
    suspend fun favoriteCategories(): List<String> {
        val used = db.paperDao().getFavoriteTagsOnce()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val folders = settings.favoriteFolders.first()
        return (used + folders).distinct()
    }
}
