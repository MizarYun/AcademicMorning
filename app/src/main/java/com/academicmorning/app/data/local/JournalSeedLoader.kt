package com.academicmorning.app.data.local

import android.content.Context
import com.academicmorning.app.data.local.entity.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 首次启动将 assets/journals.json 预置期刊库导入 Room。 */
object JournalSeedLoader {

    @Serializable
    private data class SeedJournal(
        val issn: String,
        val name: String,
        val discipline: String,
        val quartile: String,
        val impactFactor: Double,
        val source: String,
        val sourceRef: String,
        val publisher: String,
        val yesterdayCount: Int = 0
    )

    suspend fun seedIfNeeded(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
        if (prefs.getBoolean("journals_seeded", false)) return@withContext
        // 防御：数据库已有数据则只打标记
        if (db.journalDao().count() > 0) {
            prefs.edit().putBoolean("journals_seeded", true).apply()
            return@withContext
        }
        val text = context.assets.open("journals.json").bufferedReader().use { it.readText() }
        val seeds = Json { ignoreUnknownKeys = true }.decodeFromString<List<SeedJournal>>(text)
        db.journalDao().upsertAll(
            seeds.map {
                Journal(
                    issn = it.issn,
                    name = it.name,
                    discipline = it.discipline,
                    quartile = it.quartile,
                    impactFactor = it.impactFactor,
                    source = it.source,
                    sourceRef = it.sourceRef,
                    publisher = it.publisher,
                    yesterdayCount = it.yesterdayCount
                )
            }
        )
        prefs.edit().putBoolean("journals_seeded", true).apply()
    }
}
