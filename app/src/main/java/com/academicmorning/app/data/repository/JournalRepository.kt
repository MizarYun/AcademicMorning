package com.academicmorning.app.data.repository

import com.academicmorning.app.data.local.AppDatabase
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.prefs.SettingsManager
import com.academicmorning.app.data.remote.crossref.CrossrefClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class JournalRepository(
    private val db: AppDatabase,
    private val settings: SettingsManager
) {
    private val crossref = CrossrefClient()

    companion object {
        const val MAX_FOLLOW = 20

        const val FREQ_WEEKLY = 7
        const val FREQ_SEMI_MONTHLY = 15
        const val FREQ_MONTHLY = 31
        const val FREQ_BIMONTHLY = 62
        const val FREQ_SEMI_ANNUAL = 183

        /** 出刊周期（天）→ 类型标签；0/-1 返回 null（不标注）。 */
        fun typeLabelOf(freqDays: Int): String? = when {
            freqDays <= 0 -> null
            freqDays <= 10 -> "周刊"
            freqDays <= 18 -> "半月刊"
            freqDays <= 45 -> "月刊"
            freqDays <= 100 -> "双月刊"
            else -> "半年刊"
        }

        /**
         * 是否需要放宽检索窗口：仅识别为双月刊（放宽到 62 天）
         * 或半年刊（放宽到 183 天）的期刊；其余一律用用户/默认窗口，
         * 避免高频刊（如 J. Hazardous Materials）被拉到几个月前的旧文。
         */
        fun widenedLookbackDays(freqDays: Int): Int? = when {
            freqDays in 46..100 -> FREQ_BIMONTHLY
            freqDays > 100 -> FREQ_SEMI_ANNUAL
            else -> null
        }
    }

    fun allJournals(): Flow<List<Journal>> = db.journalDao().getAll()

    fun followedJournals(): Flow<List<Journal>> = db.journalDao().getFollowed()

    fun followedCount(): Flow<Int> = db.journalDao().followedCount()

    fun search(query: String): Flow<List<Journal>> = db.journalDao().search(query)

    /**
     * 按所选学科生成推荐期刊：各学科 IF 阈值过滤 + Q2 及以上 + IF 降序。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun recommendationsFor(disciplineKeys: List<String>): Flow<List<Journal>> {
        if (disciplineKeys.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val flows = disciplineKeys.map { key ->
            combine(db.journalDao().getByDiscipline(key), settings.ifThreshold(key)) { list, threshold ->
                list.filter { it.impactFactor >= threshold && it.quartile in listOf("Q1", "Q2") }
            }
        }
        return combine(flows) { arrays ->
            arrays.flatMap { it }.sortedByDescending { it.impactFactor }
        }
    }

    /** 设置关注状态；关注时若已达上限返回 false。 */
    suspend fun setFollowed(issn: String, followed: Boolean): Boolean {
        if (followed) {
            val count = db.journalDao().followedCount().first()
            val current = db.journalDao().getByIssn(issn)
            if (current?.isFollowed != true && count >= MAX_FOLLOW) return false
        }
        db.journalDao().setFollowed(issn, followed)
        return true
    }

    /**
     * 依据本地已抓取论文的实际发表日期推断刊物更新周期：
     * 中位间隔 ≤18 天→半月刊，≤40 天→月刊，否则→双月刊；
     * 并给出最常见的更新日（每月几号）。数据不足返回 null。
     */
    suspend fun frequencyLabel(issn: String): String? {
        val dates = db.paperDao().publishedDatesForJournal(issn)
            .mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            .distinct()
            .sortedDescending()
        if (dates.size < 3) return null
        val gaps = dates.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(b, a) }
            .filter { it in 1..120 }
        if (gaps.isEmpty()) return null
        val median = gaps.sorted().let { it[it.size / 2] }
        val freq = when {
            median <= 18 -> "半月刊"
            median <= 40 -> "月刊"
            else -> "双月刊"
        }
        // 最常见的更新日（每月几号）
        val commonDay = dates.groupingBy { it.dayOfMonth }.eachCount()
            .maxByOrNull { it.value }?.key
        return if (commonDay != null) "$freq · 多在每月${commonDay}日前后更新" else freq
    }

    /**
     * 自动识别期刊出刊周期并写入 journals.freqDays（幂等：已识别直接返回缓存值）。
     * 依据 Crossref 最近 14 篇论文的发表日期间隔中位数分类：
     * ≤10 天→周刊(7)，≤18→半月刊(15)，≤45→月刊(31)，≤100→双月刊(62)，否则→半年刊(183)。
     * arXiv 分类源不是期刊，跳过；网络失败/数据不足记为 -1（本次安装内不再重试）。
     */
    suspend fun detectFrequency(issn: String): Int {
        val journal = db.journalDao().getByIssn(issn) ?: return 0
        if (journal.freqDays != 0) return journal.freqDays
        if (journal.source == "arxiv") {
            db.journalDao().updateFreqDays(issn, -1)
            return -1
        }
        val dates = crossref.fetchRecentDates(issn)
        if (dates.size < 3) {
            db.journalDao().updateFreqDays(issn, -1)
            return -1
        }
        val gaps = dates.zipWithNext { a, b ->
            java.time.temporal.ChronoUnit.DAYS.between(b, a)
        }.filter { it in 1..200 }
        if (gaps.isEmpty()) {
            db.journalDao().updateFreqDays(issn, -1)
            return -1
        }
        val median = gaps.sorted().let { it[it.size / 2] }
        val freqDays = when {
            median <= 10 -> FREQ_WEEKLY
            median <= 18 -> FREQ_SEMI_MONTHLY
            median <= 45 -> FREQ_MONTHLY
            median <= 100 -> FREQ_BIMONTHLY
            else -> FREQ_SEMI_ANNUAL
        }
        db.journalDao().updateFreqDays(issn, freqDays)
        return freqDays
    }
}
