package com.academicmorning.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机完成接收器：设备重启后恢复每日精准闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("push_enabled", true)
        val minutes = prefs.getInt("push_minutes", 480)
        if (enabled) {
            WorkerScheduler.scheduleExactAlarm(context, minutes)
        }
    }
}
