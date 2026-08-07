package com.academicmorning.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.academicmorning.app.R

/** 每日晨报本地通知。 */
object NotificationHelper {

    // v2：高重要性渠道（旧渠道重要性无法升级，必须换新 id）
    const val CHANNEL_ID = "morning_papers_v2"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "每日晨报", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "每天清晨推送关注期刊的最新论文"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param total 今日新增总数
     * @param journalCounts 期刊名 → 篇数（已按篇数降序，取前几名展示）
     * @param aiEnabled 是否已配置 AI（决定文案）
     * @param isTest 测试推送：即使 0 篇也弹通知
     */
    fun showMorningNotification(
        context: Context,
        total: Int,
        journalCounts: List<Pair<String, Int>>,
        aiEnabled: Boolean,
        isTest: Boolean = false
    ) {
        if (!hasPermission(context)) return

        createChannel(context)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { action = "OPEN_TODAY" }
            ?: Intent().setClassName(context, "com.academicmorning.app.ui.MainActivity")
                .apply { action = "OPEN_TODAY" }
        val pending = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 文案格式：你关注的《Nature》《Science》等期刊有新发表
        val quoted = journalCounts.take(2).joinToString("") { "《${it.first}》" }
        val contentText = when {
            total > 0 && quoted.isNotBlank() ->
                "你关注的$quoted${if (journalCounts.size > 2) "等期刊" else "期刊"}有新发表"
            total > 0 -> "你关注的期刊有新发表"
            else -> "今日暂无新论文"
        }

        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle(contentText)
            .setSummaryText("今日新增 $total 篇 · 来自 ${journalCounts.size} 个期刊")
        journalCounts.take(6).forEach { (name, cnt) -> inbox.addLine("《$name》 $cnt 篇") }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("学术晨报")
            .setContentText(contentText)
            .setStyle(inbox)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (aiEnabled && total > 0) {
            builder.setTicker("今日 $total 篇新论文，点击查看 AI 摘要")
        }

        context.getSystemService(NotificationManager::class.java)
            ?.notify(1001, builder.build())
    }
}
