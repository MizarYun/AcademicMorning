package com.academicmorning.app.ui.detail

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.ui.components.AmCard
import com.academicmorning.app.ui.components.TagChip
import com.academicmorning.app.ui.components.extractKeywords
import com.academicmorning.app.ui.theme.AmAccent
import com.academicmorning.app.ui.theme.AmAccentContainer
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmPreprint
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademicMorningApp
    private val paperRepo = app.container.paperRepository
    private val settings = app.container.settings

    private val paperIdFlow = MutableStateFlow("")

    /** 已配置翻译能力：first=大模型，second=TMT 机翻。 */
    private val _engines = MutableStateFlow(false to false)
    val engines: StateFlow<Pair<Boolean, Boolean>> = _engines

    /** 翻译引擎偏好："ask" | "tmt" | "llm"。 */
    val enginePref: StateFlow<String> = settings.translationEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ask")

    init {
        viewModelScope.launch { _engines.value = paperRepo.configuredEngines() }
    }

    fun saveEnginePref(v: String) = viewModelScope.launch { settings.setTranslationEngine(v) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val paper: StateFlow<Paper?> = paperIdFlow
        .flatMapLatest { id -> if (id.isEmpty()) kotlinx.coroutines.flow.flowOf(null) else paperRepo.paper(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating

    private val enterTime = System.currentTimeMillis()
    private var marked = false

    fun load(id: String) {
        paperIdFlow.value = id
    }

    fun markReadOnce(id: String) {
        if (marked) return
        marked = true
        viewModelScope.launch {
            // 记录阅读时长（进入即记录，退出由系统计 0 亦可）
            paperRepo.markRead(id, durationSec = 30)
        }
    }

    fun unfavorite(paper: Paper) = viewModelScope.launch {
        paperRepo.setFavorite(paper.id, false)
    }

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    fun loadCategories() = viewModelScope.launch {
        _categories.value = paperRepo.favoriteCategories()
    }

    fun favoriteWithTags(paper: Paper, tags: List<String>) = viewModelScope.launch {
        paperRepo.setFavoriteWithTags(paper.id, true, tags.joinToString(","))
        _categories.value = paperRepo.favoriteCategories()
    }

    fun translateNow(id: String, engine: String) = viewModelScope.launch {
        _translating.value = true
        paperRepo.translatePaper(id, engine)
        _translating.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailScreen(
    paperId: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel()
) {
    LaunchedEffect(paperId) { vm.load(paperId) }
    val paper by vm.paper.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedTip by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedTip) {
        if (copiedTip != null) { delay(1500); copiedTip = null }
    }

    Column(Modifier.fillMaxSize().background(AmBackground)) {
        TopAppBar(
            title = { Text("论文详情", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "返回", tint = Color(0xFF1F2937))
                }
            },
            actions = {
                paper?.let { p ->
                    var showFavDialog by remember { mutableStateOf(false) }
                    val categories by vm.categories.collectAsStateWithLifecycle()
                    IconButton(onClick = {
                        if (p.isFavorite) vm.unfavorite(p)
                        else {
                            vm.loadCategories()
                            showFavDialog = true
                        }
                    }) {
                        Icon(
                            if (p.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            "收藏", tint = if (p.isFavorite) AmPrimary else AmTextSecondary
                        )
                    }
                    if (showFavDialog) {
                        com.academicmorning.app.ui.components.FavoriteCategoryDialog(
                            existing = categories,
                            onConfirm = { tags ->
                                vm.favoriteWithTags(p, tags)
                                showFavDialog = false
                            },
                            onDismiss = { showFavDialog = false }
                        )
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${p.title}\n${p.url}")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享论文"))
                    }) {
                        Icon(Icons.Rounded.Share, "分享", tint = AmTextSecondary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        paper?.let { p ->
            LaunchedEffect(p.id) { vm.markReadOnce(p.id) }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 期刊信息
                AmCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.journalName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        if (p.isOpenAccess) { TagChip("OA", AmAccent); Spacer(Modifier.width(4.dp)) }
                        if (p.isPreprint) { TagChip("预印本", AmPreprint) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.titleZh ?: p.title,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (p.titleZh != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(p.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(p.authors.ifBlank { "作者信息待更新" }, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${p.publishedDate} · DOI: ${p.doi ?: "无"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 一句话核心发现
                if (!p.oneLinerZh.isNullOrBlank()) {
                    AmCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = AmAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("一句话核心发现", fontWeight = FontWeight.Bold, color = AmAccent, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(AmAccentContainer, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(p.oneLinerZh, fontSize = 13.sp, color = AmAccent, lineHeight = 18.sp)
                        }
                    }
                }

                // 中文摘要
                if (!p.abstractZh.isNullOrBlank()) {
                    AmCard(Modifier.fillMaxWidth()) {
                        Text("中文摘要", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(p.abstractZh, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }

                // 英文摘要（可展开）
                if (!p.abstractText.isNullOrBlank()) {
                    var expanded by remember { mutableStateOf(p.abstractZh == null) }
                    AmCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("英文摘要 (Abstract)", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "收起" else "展开", color = AmPrimary, fontSize = 12.sp)
                            }
                        }
                        if (expanded) {
                            Text(
                                p.abstractText,
                                fontSize = 13.sp, lineHeight = 19.sp, color = AmTextSecondary,
                                maxLines = if (expanded) Int.MAX_VALUE else 3
                            )
                        }
                    }
                }

                // 关键词
                val keywords = extractKeywords(p)
                if (keywords.isNotEmpty()) {
                    AmCard(Modifier.fillMaxWidth()) {
                        Text("关键词", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            keywords.forEach {
                                TagChip(it, AmPrimary)
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                }

                // 操作按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("阅读全文", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            p.doi?.let {
                                clipboard.setText(AnnotatedString(it))
                                copiedTip = "DOI 已复制"
                            }
                        },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("复制 DOI", fontSize = 13.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString("${p.authors}. ${p.title}. ${p.journalName} (${p.publishedDate.take(4)}). https://doi.org/${p.doi ?: ""}")
                            )
                            copiedTip = "引用格式已复制"
                        },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("复制引用", fontSize = 13.sp) }
                    if (p.titleZh == null) {
                        val engines by vm.engines.collectAsStateWithLifecycle()
                        val enginePref by vm.enginePref.collectAsStateWithLifecycle()
                        var showEngineDialog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                val (llm, tmt) = engines
                                when {
                                    // 双 API 且偏好为"每次询问" → 弹窗选择
                                    llm && tmt && enginePref == "ask" -> showEngineDialog = true
                                    llm && tmt -> vm.translateNow(p.id, enginePref)
                                    llm -> vm.translateNow(p.id, "llm")
                                    tmt -> vm.translateNow(p.id, "tmt")
                                    else -> android.widget.Toast.makeText(
                                        context, "请先在「设置 → AI 服务配置」中配置翻译 API",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !translating
                        ) {
                            Text(if (translating) "翻译中…" else "翻译", fontSize = 13.sp, color = AmAccent)
                        }

                        // 双 API 时的引擎选择弹窗（含"不再询问"勾选）
                        if (showEngineDialog) {
                            var dontAsk by remember { mutableStateOf(false) }
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showEngineDialog = false },
                                title = { Text("选择翻译方式", fontSize = 16.sp) },
                                text = {
                                    Column {
                                        androidx.compose.material3.TextButton(onClick = {
                                            if (dontAsk) vm.saveEnginePref("tmt")
                                            vm.translateNow(p.id, "tmt")
                                            showEngineDialog = false
                                        }) {
                                            Text("机器翻译\n速度快，准确度较低，不消耗大模型 token", fontSize = 13.sp)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        androidx.compose.material3.TextButton(onClick = {
                                            if (dontAsk) vm.saveEnginePref("llm")
                                            vm.translateNow(p.id, "llm")
                                            showEngineDialog = false
                                        }) {
                                            Text("大模型翻译\n翻译标题与摘要并生成核心发现，消耗 token", fontSize = 13.sp)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Checkbox(
                                                checked = dontAsk,
                                                onCheckedChange = { dontAsk = it }
                                            )
                                            Text("后续不再询问，均用此方法（可在设置中更改）", fontSize = 12.sp)
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showEngineDialog = false }) { Text("取消") }
                                }
                            )
                        }
                    }
                }
                copiedTip?.let {
                    Text(it, color = AmAccent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        } ?: Column(Modifier.padding(32.dp)) {
            Text("加载中…", color = AmTextTertiary)
        }
    }
}
