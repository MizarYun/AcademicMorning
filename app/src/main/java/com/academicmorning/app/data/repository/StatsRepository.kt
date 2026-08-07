package com.academicmorning.app.data.repository

import com.academicmorning.app.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 阅读统计仓库。 */
class StatsRepository(private val db: AppDatabase) {

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun readCountToday(): Flow<Int> = db.paperDao().readSince(startOfToday())

    fun totalRead(): Flow<Int> = db.paperDao().totalRead()

    fun readDurationTodaySec(): Flow<Int> =
        db.readingEventDao().totalDurationSince(startOfToday()).map { it ?: 0 }

    fun monthReadCount(): Flow<Int> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return db.paperDao().readSince(cal.timeInMillis)
    }

    /** 连续阅读天数：以阅读事件所在自然日计算。 */
    fun currentStreakDays(): Flow<Int> {
        val since = startOfToday() - 400L * 24 * 3600 * 1000
        return db.readingEventDao().getSince(since).map { events ->
            val days = events.map { it.timestamp / (24 * 3600 * 1000) }.toSet()
            if (days.isEmpty()) return@map 0
            val today = System.currentTimeMillis() / (24 * 3600 * 1000)
            var cursor = if (days.contains(today)) today else today - 1
            if (!days.contains(cursor)) return@map 0
            var streak = 0
            while (days.contains(cursor)) {
                streak++
                cursor--
            }
            streak
        }
    }

    fun topJournals(limit: Int = 5): Flow<List<Pair<String, Int>>> =
        db.paperDao().topReadJournals(limit).map { list -> list.map { it.journalName to it.cnt } }

    /** 近 30 天每日阅读数："MM-dd" to count。 */
    fun dailyCountsLast30(): Flow<List<Pair<String, Int>>> {
        val since = startOfToday() - 29L * 24 * 3600 * 1000
        val fmt = SimpleDateFormat("MM-dd", Locale.US)
        return db.readingEventDao().getSince(since).map { events ->
            val map = events.groupBy { fmt.format(Date(it.timestamp)) }
                .mapValues { it.value.size }
            (0..29).map { i ->
                val key = fmt.format(Date(since + i * 24L * 3600 * 1000))
                key to (map[key] ?: 0)
            }
        }
    }
}
