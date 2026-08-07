package com.academicmorning.app.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ApiConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademicMorningApp
    private val aiRepo = app.container.aiConfigRepository

    val providers = aiRepo.providers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testResults = MutableStateFlow<Map<String, String>>(emptyMap())

    fun saveKey(provider: String, key: String) = viewModelScope.launch {
        aiRepo.saveKey(provider, key)
    }

    fun saveTencentKeys(id: String, key: String) = viewModelScope.launch {
        aiRepo.saveTencentKeys(id, key)
    }

    fun removeKey(provider: String) = viewModelScope.launch {
        aiRepo.removeKey(provider)
    }

    fun setActive(provider: String?, active: Boolean) = viewModelScope.launch {
        aiRepo.setActive(if (active) provider else null)
    }

    fun test(provider: String) = viewModelScope.launch {
        testResults.value = testResults.value + (provider to "测试中…")
        val r = aiRepo.testConnection(provider)
        testResults.value = testResults.value +
            (provider to (r.getOrNull() ?: r.exceptionOrNull()?.message ?: "未知错误"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(onBack: () -> Unit, vm: ApiConfigViewModel = viewModel()) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val testResults by vm.testResults.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(AmBackground)) {
        TopAppBar(
            title = { Text("AI 服务配置", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "返回", tint = Color(0xFF1F2937))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "配置你自己的 API Key 获得中文翻译与 AI 提炼。Key 仅加密存储于本机（Android Keystore），不随云备份导出。" +
                    "全部停用即为纯英文模式，核心功能不受影响。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            // 大模型已配置但 TMT 未配置 → token 节省提示
            val llmConfigured = providers.any { it.provider != "tencent_tmt" && it.configured }
            val tmtConfigured = providers.any { it.provider == "tencent_tmt" && it.configured }
            if (llmConfigured && !tmtConfigured) {
                AmCard(Modifier.fillMaxWidth()) {
                    Text(
                        "💡 提示：当前由大模型进行总结和翻译。配置腾讯云 TMT 机器翻译后，" +
                            "论文标题翻译将分流至机翻通道，大模型只处理摘要与核心提炼，" +
                            "可显著降低大模型 tokens 消耗。",
                        fontSize = 12.sp,
                        color = AmAccent,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            if (llmConfigured && tmtConfigured) {
                AmCard(Modifier.fillMaxWidth()) {
                    Text(
                        "✅ 混合模式已启用：标题翻译走 TMT 机翻，摘要与核心提炼走大模型，token 消耗最优。",
                        fontSize = 12.sp,
                        color = AmAccent,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(2.dp))
            providers.forEach { p ->
                ProviderCard(p, testResults[p.provider], vm)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ProviderCard(
    status: AiConfigRepository.ProviderStatus,
    testResult: String?,
    vm: ApiConfigViewModel
) {
    var keyInput by remember { mutableStateOf("") }
    var secretId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    val context = LocalContext.current

    AmCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(status.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (status.configured) "已配置" else "未配置",
                    fontSize = 12.sp,
                    color = if (status.configured) AmAccent else AmTextTertiary
                )
            }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(AiConfigRepository.APPLY_URLS[status.provider]))
                )
            }) {
                Icon(Icons.Rounded.OpenInNew, null, tint = AmPrimary, modifier = Modifier.width(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("一键申请", color = AmPrimary, fontSize = 12.sp)
            }
            if (status.configured) {
                Switch(
                    checked = status.active,
                    onCheckedChange = { vm.setActive(status.provider, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AmPrimary)
                )
            }
        }
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
                label = { Text(if (status.configured) "更新 API Key" else "API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (status.provider == "tencent_tmt") {
                        if (secretId.isNotBlank() && secretKey.isNotBlank()) {
                            vm.saveTencentKeys(secretId, secretKey)
                        }
                    } else if (keyInput.isNotBlank()) {
                        vm.saveKey(status.provider, keyInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmPrimary),
                shape = RoundedCornerShape(8.dp)
            ) { Text("保存", fontSize = 13.sp) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.test(status.provider) }, enabled = status.configured) {
                Text("测试连接", color = AmTextSecondary, fontSize = 13.sp)
            }
            if (status.configured) {
                TextButton(onClick = { vm.removeKey(status.provider) }) {
                    Text("删除", color = Color(0xFFDC2626), fontSize = 13.sp)
                }
            }
        }
        if (!testResult.isNullOrBlank()) {
            Text(testResult, fontSize = 12.sp, color = AmTextSecondary)
        }
    }
}
