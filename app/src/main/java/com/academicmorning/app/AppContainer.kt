package com.academicmorning.app

import android.content.Context
import androidx.room.Room
import com.academicmorning.app.data.local.AppDatabase
import com.academicmorning.app.data.local.JournalSeedLoader
import com.academicmorning.app.data.prefs.ApiKeyStore
import com.academicmorning.app.data.prefs.SettingsManager
import com.academicmorning.app.data.repository.AiConfigRepository
import com.academicmorning.app.data.repository.JournalRepository
import com.academicmorning.app.data.repository.PaperRepository
import com.academicmorning.app.data.repository.StatsRepository
import com.academicmorning.app.work.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 手工 DI 容器。所有仓库单例在此装配。
 */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "academic_morning.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    val settings: SettingsManager by lazy { SettingsManager(context) }
    val keyStore: ApiKeyStore by lazy { ApiKeyStore(context) }

    val journalRepository: JournalRepository by lazy { JournalRepository(database, settings) }
    val paperRepository: PaperRepository by lazy {
        PaperRepository(database, settings, keyStore, context, journalRepository)
    }
    val statsRepository: StatsRepository by lazy { StatsRepository(database) }
    val aiConfigRepository: AiConfigRepository by lazy { AiConfigRepository(keyStore, settings) }

    fun init() {
        appScope.launch {
            // 首次运行导入预置期刊数据库
            JournalSeedLoader.seedIfNeeded(context, database)
            // 恢复每日推送调度
            val enabled = settings.pushEnabled.first()
            if (enabled) {
                WorkerScheduler.scheduleDaily(context, settings.pushTimeMinutes.first())
            }
        }
    }
}
