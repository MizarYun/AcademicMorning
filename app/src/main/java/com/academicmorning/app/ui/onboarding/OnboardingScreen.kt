package com.academicmorning.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.data.model.Discipline
import com.academicmorning.app.data.repository.AiConfigRepository
import com.academicmorning.app.ui.components.AmCard
import com.academicmorning.app.ui.components.JournalRow
import com.academicmorning.app.ui.theme.AmAccent
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmPrimaryContainer
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import kotlinx.coroutines.launch

private val disciplineIcons: Map<String, ImageVector> = mapOf(
    "biology" to Icons.Rounded.Biotech,
    "environment" to Icons.Rounded.Eco,
    "cs" to Icons.Rounded.Computer,
    "medicine" to Icons.Rounded.LocalHospital,
    "chemistry" to Icons.Rounded.Science,
    "physics" to Icons.Rounded.Bolt,
    "materials" to Icons.Rounded.Layers,
    "earth" to Icons.Rounded.Public,
    "math" to Icons.Rounded.Functions
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AmBackground)
            .padding(20.dp)
    ) {
        // 步骤指示
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) AmPrimary else AmDivider)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            listOf("① 选择学科", "② 精选期刊", "③ 智能增强（可跳过）", "④ 权限与偏好")[step],
            style = MaterialTheme.typography.titleSmall,
            color = AmTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> Step1Disciplines(vm)
                1 -> Step2Journals(vm)
                2 -> Step3Api(vm)
                3 -> Step4Permission()
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (step < 3) step++ else Unit
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmPrimary),
            shape = RoundedCornerShape(12.dp),
            enabled = step < 3
        ) {
            Text(if (step < 3) "下一步" else "", fontWeight = FontWeight.Bold)
        }
        if (step == 3) {
            FinishButton(vm, onFinish)
        }
    }
}

// ================= Step 1：选择学科 =================
@Composable
private fun Step1Disciplines(vm: OnboardingViewModel) {
    val selected by vm.selectedDisciplines.collectAsStateWithLifecycle()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.academicmorning.app.ui.components.AppLogo(size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("学术晨报", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Every Morning, One Step Ahead.", fontSize = 11.sp, color = AmTextTertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("选择你的研究领域", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("请选择 1–3 个研究领域，系统将为你推荐高影响力期刊", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Discipline.entries.toList()) { d ->
                val isSel = selected.contains(d.key)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(
                            2.dp,
                            if (isSel) AmPrimary else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { vm.toggleDiscipline(d.key) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        disciplineIcons[d.key] ?: Icons.Rounded.Science,
                        d.label,
                        tint = if (isSel) AmPrimary else AmTextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        d.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) AmPrimary else Color(0xFF1F2937)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "已选择 ${selected.size}/3",
            color = AmTextSecondary, fontSize = 13.sp
        )
    }
}

// ================= Step 2：精选期刊 =================
@Composable
private fun Step2Journals(vm: OnboardingViewModel) {
    val recommendations by vm.recommendations.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val followed by vm.followedCount.collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()
    var limitTip by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索期刊、学科或关键词", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AmTextTertiary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (limitTip) {
            Text("关注期刊已达 20 个上限", color = Color(0xFFDC2626), fontSize = 12.sp)
        }
        Text(
            "已关注 $followed/20 · 以下为按你的学科推荐的高影响力期刊（JCR Q2 及以上）",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        val list = if (query.isBlank()) recommendations else searchResults
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.issn }) { j ->
                JournalRow(journal = j) {
                    scope.launch {
                        val ok = vm.toggleFollow(j)
                        limitTip = !ok
                    }
                }
                Divider(color = AmDivider)
            }
        }
    }
}

// ================= Step 3：API 配置 =================
@Composable
private fun Step3Api(vm: OnboardingViewModel) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val testResults by vm.testResults.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn {
        item {
            Text("让 AI 帮你理解文献", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "配置你自己的 API Key 后，晨报将提供中文翻译与一句话核心发现。\n" +
                    "Key 仅加密存储于本机。不配置也可正常使用（纯英文模式）。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            val llmConfigured = providers.any { it.provider != "tencent_tmt" && it.configured }
            val tmtConfigured = providers.any { it.provider == "tencent_tmt" && it.configured }
            if (llmConfigured && !tmtConfigured) {
                AmCard(Modifier.fillMaxWidth()) {
                    Text(
                        "💡 当前由大模型进行总结和翻译。建议再配置腾讯云 TMT 机器翻译，" +
                            "标题翻译将分流至机翻，可降低大模型 tokens 消耗。",
                        fontSize = 12.sp, color = AmAccent, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        items(providers, key = { it.provider }) { p ->
            ProviderConfigCard(p, testResults[p.provider], vm)
            Spacer(Modifier.height(10.dp))
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：点击供应商名称可展开配置。所有 Key 均存储于 Android Keystore 加密区域。",
                style = MaterialTheme.typography.bodySmall,
                color = AmTextTertiary
            )
        }
    }
}

@Composable
private fun ProviderConfigCard(
    status: AiConfigRepository.ProviderStatus,
    testResult: String?,
    vm: OnboardingViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var secretId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    val context = LocalContext.current

    AmCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(status.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (status.configured) "已配置" + if (status.active) " · 已启用" else "" else "未配置",
                    fontSize = 12.sp,
                    color = if (status.configured) AmAccent else AmTextTertiary
                )
            }
            TextButton(onClick = {
                val url = AiConfigRepository.APPLY_URLS[status.provider]
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(14.dp), tint = AmPrimary)
                Spacer(Modifier.width(4.dp))
                Text("一键申请", color = AmPrimary, fontSize = 12.sp)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (status.provider == "tencent_tmt") {
                OutlinedTextField(
                    value = secretId, onValueChange = { secretId = it },
                    label = { Text("SecretId") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = secretKey, onValueChange = { secretKey = it },
                    label = { Text("SecretKey") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = keyInput, onValueChange = { keyInput = it },
                    label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = {
                        if (status.provider == "tencent_tmt") vm.saveTencentKeys(secretId, secretKey)
                        else vm.saveKey(status.provider, keyInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("保存并启用", fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.testConnection(status.provider) }) {
                    Text("测试连接", color = AmTextSecondary, fontSize = 13.sp)
                }
            }
            if (!testResult.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(testResult, fontSize = 12.sp, color = AmTextSecondary)
            }
        }
    }
}

// ================= Step 4：权限与偏好 =================
@Composable
private fun Step4Permission() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    Column {
        Text("开启每日晨报通知", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("每天 08:00，为你送上关注期刊的最新论文，不错过任何重要进展。", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        AmCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, null, tint = AmPrimary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("每日推送时间", fontWeight = FontWeight.SemiBold)
                    Text("08:00 · 时区 (UTC+08:00) 北京", fontSize = 13.sp, color = AmTextSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else granted = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (granted) AmAccent else AmPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("通知权限已开启", fontWeight = FontWeight.Bold)
            } else {
                Text("开启通知权限", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 国产 ROM 保活引导：电池白名单 + 自启动 + 后台锁定
        AmCard(Modifier.fillMaxWidth()) {
            Text("确保通知准时送达", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "部分 ${com.academicmorning.app.util.OemSettings.manufacturer()} 机型会限制后台任务，" +
                    "建议完成以下三项设置（随时可在「设置 → 通知送达保障」中找到）：",
                fontSize = 12.sp, color = AmTextSecondary, lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { com.academicmorning.app.util.OemSettings.requestIgnoreBattery(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("① 加入电池优化白名单", fontSize = 13.sp) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { com.academicmorning.app.util.OemSettings.openAutoStart(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("② 允许自启动（跳转系统设置）", fontSize = 13.sp) }
            Spacer(Modifier.height(6.dp))
            Text(
                "③ 后台锁定：打开系统「最近任务」界面，长按或下拉「学术晨报」卡片，点击 🔒 锁定",
                fontSize = 12.sp, color = AmTextSecondary, lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun FinishButton(vm: OnboardingViewModel, onFinish: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    Column {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                loading = true
                vm.finish(pushEnabled = true, minutes = 480, onDone = onFinish)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmPrimary),
            shape = RoundedCornerShape(12.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("进入应用", fontWeight = FontWeight.Bold)
            }
        }
    }
}
