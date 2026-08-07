package com.academicmorning.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * 精准闹钟接收器：在设定推送时间前 2 分钟触发，
 * 立即（加急）执行抓取 Worker，确保推送时间前发出通知；
 * 随后排定下一天的闹钟。
 */
class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 立即加急执行抓取（WorkManager 加急任务，无额度时降级为普通任务）
        val request = OneTimeWorkRequestBuilder<MorningFetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(request)

        // 排定次日闹钟
        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
        val minutes = prefs.getInt("push_minutes", 480)
        val enabled = prefs.getBoolean("push_enabled", true)
        if (enabled) {
            WorkerScheduler.scheduleExactAlarm(context, minutes)
        }
    }
}
