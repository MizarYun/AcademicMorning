package com.academicmorning.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 每日定时抓取调度：精准闹钟（主）+ WorkManager 周期任务（备）。 */
object WorkerScheduler {

    const val WORK_NAME = "morning_fetch"
    private const val ALARM_REQUEST_CODE = 20260805

    /** 距设定时间提前 2 分钟触发抓取，确保到点前发出通知。 */
    private const val LEAD_MINUTES = 2

    fun scheduleDaily(context: Context, minutesFromMidnight: Int) {
        // 记录状态供闹钟/开机接收器使用
        context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE).edit()
            .putBoolean("push_enabled", true)
            .putInt("push_minutes", minutesFromMidnight)
            .apply()

        scheduleExactAlarm(context, minutesFromMidnight)

        // WorkManager 周期任务作为备份（防止闹钟被系统清理）
        val delay = computeInitialDelay(minutesFromMidnight - LEAD_MINUTES)
        val request = PeriodicWorkRequestBuilder<MorningFetchWorker>(
            1, TimeUnit.DAYS, 30, TimeUnit.MINUTES
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** 排定下一次精准闹钟（设定时间前 2 分钟）。 */
    fun scheduleExactAlarm(context: Context, minutesFromMidnight: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + computeInitialDelay(minutesFromMidnight - LEAD_MINUTES)
        val pending = alarmPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // 无精准闹钟权限时退化为允许休眠的非精准闹钟（仍在前2分钟左右触发）
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (_: Exception) {
            }
        }
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MorningAlarmReceiver::class.java).apply {
            action = "com.academicmorning.app.ALARM_MORNING_FETCH"
        }
        return PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context) {
        context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE).edit()
            .putBoolean("push_enabled", false)
            .apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 立即执行一次抓取+推送（用于设置页"测试推送"）。 */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MorningFetchWorker>()
            .setInputData(workDataOf(MorningFetchWorker.KEY_TEST to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** 计算到下一个目标时刻（分钟可为负=提前量）的延迟毫秒数。 */
    private fun computeInitialDelay(minutesFromMidnight: Int): Long {
        val target = ((minutesFromMidnight % 1440) + 1440) % 1440
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, target / 60)
            set(Calendar.MINUTE, target % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
