package com.academicmorning.app.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.repository.AiConfigRepository
import com.academicmorning.app.ui.components.AmCard
import com.academicmorning.app.ui.theme.AmAccent
import com.academicmorning.app.ui.theme.AmAccentContainer
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import com.academicmorning.app.work.WorkerScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademicMorningApp
    private val settings = app.container.settings
    private val aiRepo = app.container.aiConfigRepository

    val pushEnabled = settings.pushEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val pushMinutes = settings.pushTimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 480)
    val providers = aiRepo.providers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val followedDisciplines = settings.followedDisciplines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val translationEngine = settings.translationEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ask")

    fun setTranslationEngine(v: String) = viewModelScope.launch { settings.setTranslationEngine(v) }

    fun toggleDiscipline(key: String) = viewModelScope.launch {
        val cur = followedDisciplines.value
        val next = when {
            cur.contains(key) -> cur - key
            cur.size >= 3 -> cur          // 最多 3 个
            else -> cur + key
        }
        settings.setFollowedDisciplines(next)
    }

    /** 立即执行一次抓取+测试推送。 */
    fun testPush() = WorkerScheduler.runNow(getApplication())

    fun setPushEnabled(v: Boolean) = viewModelScope.launch {
        settings.setPushEnabled(v)
        if (v) {
            WorkerScheduler.scheduleDaily(getApplication(), pushMinutes.value)
        } else {
            WorkerScheduler.cancel(getApplication())
        }
    }

    fun setPushMinutes(v: Int) = viewModelScope.launch {
        settings.setPushTimeMinutes(v)
        if (pushEnabled.value) WorkerScheduler.scheduleDaily(getApplication(), v)
    }

    fun dbSizeMb(): String {
        val f = getApplication<AcademicMorningApp>().getDatabasePath("academic_morning.db")
        val bytes = if (f.exists()) f.length() else 0L
        return "%.1f MB".format(bytes / 1024f / 1024f)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFF1F2937))
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = AmTextTertiary)
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, tint = AmTextTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenApiConfig: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val pushEnabled by vm.pushEnabled.collectAsStateWithLifecycle()
    val pushMinutes by vm.pushMinutes.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val followedDisciplines by vm.followedDisciplines.collectAsStateWithLifecycle()
    var showTimeDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 电池优化白名单状态
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE)
        as android.os.PowerManager
    var ignoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // 大模型已配置但 TMT 未配置 → 提示配置 TMT 可降低 token 消耗
    val llmConfigured = providers.any { it.provider != "tencent_tmt" && it.configured }
    val tmtConfigured = providers.any { it.provider == "tencent_tmt" && it.configured }
    val showTmtHint = llmConfigured && !tmtConfigured

    Column(
        Modifier
            .fillMaxSize()
            .background(AmBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        // 每日推送
        AmCard(Modifier.fillMaxWidth()) {
            Text("每日推送", style = MaterialTheme.typography.titleSmall)
            SettingRow(
                "推送开关",
                "每天定时抓取并推送晨报",
                trailing = {
                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { vm.setPushEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AmPrimary)
                    )
                }
            )
            Divider(color = AmDivider)
            SettingRow(
                "推送时间",
                "自定义每日晨报推送时间",
                onClick = { showTimeDialog = true },
                trailing = {
                    Text(
                        "%02d:%02d".format(pushMinutes / 60, pushMinutes % 60),
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = AmPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = AmTextTertiary)
                }
            )
        }

        // 推送时间选择对话框
        if (showTimeDialog) {
            val timeState = rememberTimePickerState(
                initialHour = pushMinutes / 60,
                initialMinute = pushMinutes % 60,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimeDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setPushMinutes(timeState.hour * 60 + timeState.minute)
                        showTimeDialog = false
                    }) { Text("确定", color = AmPrimary) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimeDialog = false }) { Text("取消") }
                },
                title = { Text("选择每日推送时间") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                    }
                }
            )
        }

        // 关注学科（可变更）
        AmCard(Modifier.fillMaxWidth()) {
            Text("关注学科", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text("最多选择 3 个，变更后可到「发现期刊」调整关注期刊",
                fontSize = 12.sp, color = AmTextTertiary)
            Spacer(Modifier.height(8.dp))
            com.academicmorning.app.data.model.Discipline.entries.toList()
                .chunked(3)
                .forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { d ->
                            val selected = followedDisciplines.contains(d.key)
                            androidx.compose.material3.FilterChip(
                                selected = selected,
                                onClick = { vm.toggleDiscipline(d.key) },
                                label = { Text(d.label, fontSize = 12.sp) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmPrimary,
                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
        }

        // 通知送达保障
        AmCard(Modifier.fillMaxWidth()) {
            Text("通知送达保障", style = MaterialTheme.typography.titleSmall)
            SettingRow(
                "电池优化白名单",
                if (ignoringBattery) "已加入白名单，系统不会限制定时推送"
                else "加入白名单可确保每日定时推送不被系统休眠拦截",
                onClick = if (ignoringBattery) null else ({
                    try {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                    ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }),
                trailing = {
                    Text(
                        if (ignoringBattery) "已加入" else "去开启",
                        fontSize = 12.sp,
                        color = if (ignoringBattery) AmAccent else AmPrimary
                    )
                }
            )
            Divider(color = AmDivider)
            SettingRow(
                "自启动与后台运行",
                "允许应用自启动，防止系统清理后台后定时推送失效",
                onClick = {
                    com.academicmorning.app.util.OemSettings.openAutoStart(context)
                    android.widget.Toast.makeText(
                        context,
                        "请在系统设置中允许「学术晨报」自启动；并在最近任务界面下拉锁定本应用",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            )
            Divider(color = AmDivider)
            SettingRow(
                "后台锁定指引",
                "最近任务界面长按/下拉本应用卡片，点击 🔒 锁定，防误杀",
                onClick = {
                    android.widget.Toast.makeText(
                        context,
                        "打开系统「最近任务」→ 找到「学术晨报」→ 长按或下拉卡片 → 点击 🔒 锁定",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            )
            Divider(color = AmDivider)
            run {
                val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE)
                    as android.app.AlarmManager
                val canExact = android.os.Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
                SettingRow(
                    "精准闹钟权限",
                    if (canExact) "已允许，推送将准点触发"
                    else "Android 14+ 需手动允许，否则推送可能延迟",
                    onClick = if (canExact) null else ({
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (e: Exception) {
                            com.academicmorning.app.util.OemSettings.openAppDetails(context)
                        }
                    }),
                    trailing = {
                        Text(
                            if (canExact) "已允许" else "去开启",
                            fontSize = 12.sp,
                            color = if (canExact) AmAccent else AmPrimary
                        )
                    }
                )
            }
            Divider(color = AmDivider)
            SettingRow(
                "测试推送",
                "立即抓取一次并发送测试通知，验证通知能否送达",
                onClick = {
                    vm.testPush()
                    android.widget.Toast.makeText(
                        context, "已开始测试推送，请稍候查看通知栏", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // AI 服务配置
        AmCard(Modifier.fillMaxWidth()) {
            Text("AI 服务配置", style = MaterialTheme.typography.titleSmall)
            if (showTmtHint) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(AmAccentContainer, RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenApiConfig)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 当前由大模型进行总结和翻译。配置腾讯云 TMT 机器翻译后，" +
                            "标题翻译将分流至机翻，可降低大模型 tokens 消耗 →",
                        fontSize = 12.sp,
                        color = AmAccent,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            providers.forEachIndexed { i, p ->
                SettingRow(
                    p.label,
                    if (p.configured) "已配置" + if (p.active) " · 已启用" else "" else "未配置",
                    onClick = onOpenApiConfig,
                    trailing = {
                        Text(
                            if (p.active) "已启用" else if (p.configured) "已配置" else "未配置",
                            fontSize = 12.sp,
                            color = if (p.active) AmAccent else AmTextTertiary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = AmTextTertiary)
                    }
                )
                if (i < providers.size - 1) Divider(color = AmDivider)
            }
            Divider(color = AmDivider)
            val translationEngine by vm.translationEngine.collectAsStateWithLifecycle()
            var showEngineDialog by remember { mutableStateOf(false) }
            SettingRow(
                "翻译引擎偏好",
                "详情页点击翻译时使用的默认方式",
                onClick = { showEngineDialog = true },
                trailing = {
                    Text(
                        when (translationEngine) {
                            "tmt" -> "机器翻译"
                            "llm" -> "大模型翻译"
                            else -> "每次询问"
                        },
                        fontSize = 12.sp, color = AmPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = AmTextTertiary)
                }
            )
            if (showEngineDialog) {
                AlertDialog(
                    onDismissRequest = { showEngineDialog = false },
                    title = { Text("翻译引擎偏好") },
                    text = {
                        Column {
                            listOf(
                                "ask" to "每次询问（双 API 已配置时弹窗选择）",
                                "tmt" to "机器翻译（速度快，准确度较低，不耗 token）",
                                "llm" to "大模型翻译（含核心发现，消耗 token）"
                            ).forEach { (k, label) ->
                                TextButton(onClick = {
                                    vm.setTranslationEngine(k)
                                    showEngineDialog = false
                                }) {
                                    Text(
                                        (if (translationEngine == k) "● " else "○ ") + label,
                                        fontSize = 13.sp,
                                        color = if (translationEngine == k) AmPrimary
                                        else androidx.compose.ui.graphics.Color(0xFF1F2937)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEngineDialog = false }) { Text("关闭", color = AmPrimary) }
                    }
                )
            }
        }

        // 数据与缓存
        AmCard(Modifier.fillMaxWidth()) {
            Text("数据与缓存", style = MaterialTheme.typography.titleSmall)
            SettingRow("本地数据库", vm.dbSizeMb())
            Divider(color = AmDivider)
            SettingRow("翻译缓存", "DOI 缓存有效期 30 天，自动去重")
            Divider(color = AmDivider)
            SettingRow("备份与恢复", "后续版本提供")
        }

        // 关于
        AmCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.academicmorning.app.ui.components.AppLogo(size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("学术晨报", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 17.sp)
                    Text("Academic Morning · Every Morning, One Step Ahead.",
                        fontSize = 11.sp, color = AmTextTertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = AmDivider)
            SettingRow("开源项目", "学术晨报 Academic Morning")
            Divider(color = AmDivider)
            SettingRow("版本", "Beta 2.3.0")
            Divider(color = AmDivider)
            SettingRow("许可证", "GPL-3.0 · 本地优先 · 零账号 · 数据不出手机")
        }
        Spacer(Modifier.height(24.dp))
    }
}
