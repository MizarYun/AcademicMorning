package com.academicmorning.app.data.repository

import com.academicmorning.app.data.local.AppDatabase
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.prefs.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class JournalRepository(
    private val db: AppDatabase,
    private val settings: SettingsManager
) {
    companion object {
        const val MAX_FOLLOW = 20
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
}
