package com.academicmorning.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.academicmorning.app.AcademicMorningApp
import kotlinx.coroutines.flow.first

/** 每日定时抓取晨报并推送通知。 */
class MorningFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as AcademicMorningApp
            val repo = app.container.paperRepository
            val settings = app.container.settings
            val isTest = inputData.getBoolean(KEY_TEST, false)

            // 清理 30 天前的非收藏论文（收藏永久保留）
            try { repo.purgeOld() } catch (_: Exception) {}

            val added = repo.refreshAuto()   // 当日起近三日范围
            val papers = repo.papersForDate(repo.todayString()).first()
            val counts = papers.groupBy { it.journalName }
                .mapValues { it.value.size }
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }
            val aiEnabled = settings.activeLlmProvider.first() != null ||
                settings.tmtEnabled.first()
            if (added > 0 || isTest) {
                NotificationHelper.showMorningNotification(
                    applicationContext, added, counts, aiEnabled, isTest
                )
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_TEST = "from_test"
    }
}
