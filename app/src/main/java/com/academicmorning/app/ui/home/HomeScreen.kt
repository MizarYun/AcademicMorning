package com.academicmorning.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.ui.components.AmCard
import com.academicmorning.app.ui.components.EmptyState
import com.academicmorning.app.ui.components.FavoriteCategoryDialog
import com.academicmorning.app.ui.components.PaperCard
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmPrimaryContainer
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
private fun AmFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmPrimary,
            selectedLabelColor = Color.White
        )
    )
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPaperClick: (String) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val papers by vm.papers.collectAsStateWithLifecycle()
    val todayCount by vm.todayCount.collectAsStateWithLifecycle()
    val journals by vm.followedJournals.collectAsStateWithLifecycle()
    val journalFilter by vm.journalFilter.collectAsStateWithLifecycle()
    val readFilter by vm.readFilter.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.refreshMessage.collectAsStateWithLifecycle()
    val startupBanner by vm.startupBanner.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showRangeDialog by remember { mutableStateOf(false) }
    var favoriteTarget by remember { mutableStateOf<Paper?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // 按日期分组（发表日期降序）
    val grouped = papers.groupBy { it.publishedDate.ifBlank { it.fetchedDate } }
        .toSortedMap(compareByDescending { it })

    Box(Modifier.fillMaxSize().background(AmBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                // 顶部问候 + 发表时间选择入口（设置入口已移至底部 Tab）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val greeting = when (hour) {
                            in 5..11 -> "早上好 ☀️"
                            in 12..13 -> "中午好 🌤"
                            in 14..17 -> "下午好 ☕"
                            else -> "晚上好 🌙"
                        }
                        Text(greeting, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AmPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showRangeDialog = true }, enabled = !refreshing) {
                        Text("发表时间选择 ›", color = AmPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 启动抓取完成横幅
                startupBanner?.let { banner ->
                    Spacer(Modifier.height(6.dp))
                    AmCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                banner,
                                fontSize = 12.sp,
                                color = AmTextSecondary,
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.dismissStartupBanner() }) {
                                Icon(Icons.Rounded.Close, "关闭", tint = AmTextTertiary,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 今日新增统计卡
                AmCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("今日新增论文", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$todayCount",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmPrimary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("篇", color = AmTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        Box(
                            Modifier
                                .size(56.dp)
                                .background(AmPrimaryContainer, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Notifications, null,
                                tint = AmPrimary, modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 已读状态筛选：全部 / 未读 / 已读
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmFilterChip("全部(${papers.size})", readFilter == ReadFilter.ALL) {
                        vm.setReadFilter(ReadFilter.ALL)
                    }
                    AmFilterChip("未读", readFilter == ReadFilter.UNREAD) {
                        vm.setReadFilter(ReadFilter.UNREAD)
                    }
                    AmFilterChip("已读", readFilter == ReadFilter.READ) {
                        vm.setReadFilter(ReadFilter.READ)
                    }
                }
                Spacer(Modifier.height(4.dp))

                // 关注期刊分类筛选
                if (journals.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AmFilterChip("全部期刊", journalFilter == null) { vm.setJournalFilter(null) }
                        journals.forEach { j ->
                            AmFilterChip(j.name, journalFilter == j.issn) {
                                vm.setJournalFilter(if (journalFilter == j.issn) null else j.issn)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (papers.isEmpty()) {
                item {
                    val selectedJournal = journals.firstOrNull { it.issn == journalFilter }
                    if (selectedJournal != null) {
                        // 指定期刊下无内容（如月刊当月未更新）→ 提供"检索上一期"入口
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "《${selectedJournal.name}》暂无近期论文\n（可能为月刊/双月刊，尚未到新一期更新时间）",
                                fontSize = 13.sp, color = AmTextTertiary, lineHeight = 19.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.fetchLastIssue(selectedJournal.issn, selectedJournal.name)
                                },
                                enabled = !refreshing,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (refreshing) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp), strokeWidth = 2.dp, color = AmPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    if (refreshing) "检索中…"
                                    else "检索《${selectedJournal.name}》上一期发布的内容",
                                    color = AmPrimary, fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        EmptyState(
                            "暂无符合条件的论文\n\n点击右上角「发表时间选择」，\n自选期刊与日期范围（最多 31 天）检索新发布，\n自动与本地记录去重"
                        )
                    }
                }
            } else {
                grouped.forEach { (date, list) ->
                    item(key = "header_$date") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatDateHeader(date),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmTextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${list.size} 篇", fontSize = 12.sp, color = AmTextTertiary)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(AmDivider)
                            )
                        }
                    }
                    items(list, key = { it.id }) { paper ->
                        PaperCard(
                            paper = paper,
                            onClick = { onPaperClick(paper.id) },
                            onFavorite = {
                                if (paper.isFavorite) vm.unfavorite(paper)
                                else {
                                    vm.loadCategories()
                                    favoriteTarget = paper
                                }
                            },
                            onShare = { vm.share(paper) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // 收藏分类弹窗
    favoriteTarget?.let { target ->
        FavoriteCategoryDialog(
            existing = categories,
            onConfirm = { tags ->
                vm.favoriteWithTags(target, tags)
                favoriteTarget = null
            },
            onDismiss = { favoriteTarget = null }
        )
    }

    // 发表时间选择对话框：目标期刊 + 起止日期（跨度≤7天）
    if (showRangeDialog) {
        val yesterday = LocalDate.now().minusDays(1)
        var selectedIssn by remember { mutableStateOf<String?>(null) }
        var fromDate by remember { mutableStateOf(yesterday) }
        var toDate by remember { mutableStateOf(yesterday) }
        var pickingFrom by remember { mutableStateOf(false) }
        var pickingTo by remember { mutableStateOf(false) }
        val spanDays = (toDate.toEpochDay() - fromDate.toEpochDay() + 1).toInt()
        val rangeError = when {
            toDate.isBefore(fromDate) -> "结束日期不能早于开始日期"
            spanDays > 31 -> "时间跨度最多 31 天，约一个月（当前 $spanDays 天）"
            toDate.isAfter(LocalDate.now()) -> "结束日期不能晚于今天"
            else -> null
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text("发表时间选择") },
            text = {
                Column {
                    Text("目标期刊", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AmFilterChip("全部关注", selectedIssn == null) { selectedIssn = null }
                        journals.forEach { j ->
                            AmFilterChip(j.name, selectedIssn == j.issn) { selectedIssn = j.issn }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("日期范围（最多 31 天，约一个月）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                            Text("${fromDate.year}年${fromDate.monthValue}月${fromDate.dayOfMonth}日", fontSize = 12.sp)
                        }
                        Text("至", modifier = Modifier.align(Alignment.CenterVertically))
                        OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
                            Text("${toDate.year}年${toDate.monthValue}月${toDate.dayOfMonth}日", fontSize = 12.sp)
                        }
                    }
                    rangeError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rangeError == null,
                    onClick = {
                        vm.reviewRange(
                            selectedIssn,
                            fmt.format(Date(fromDate.toPickerMillis())),
                            fmt.format(Date(toDate.toPickerMillis()))
                        )
                        showRangeDialog = false
                    }
                ) { Text("开始检索", color = if (rangeError == null) AmPrimary else AmTextTertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) { Text("取消") }
            }
        )

        if (pickingFrom) {
            val state = rememberDatePickerState(initialSelectedDateMillis = fromDate.toPickerMillis())
            DatePickerDialog(
                onDismissRequest = { pickingFrom = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { fromDate = it.toLocalDateUtc() }
                        pickingFrom = false
                    }) { Text("确定", color = AmPrimary) }
                },
                dismissButton = { TextButton(onClick = { pickingFrom = false }) { Text("取消") } }
            ) { DatePicker(state = state) }
        }
        if (pickingTo) {
            val state = rememberDatePickerState(initialSelectedDateMillis = toDate.toPickerMillis())
            DatePickerDialog(
                onDismissRequest = { pickingTo = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { toDate = it.toLocalDateUtc() }
                        pickingTo = false
                    }) { Text("确定", color = AmPrimary) }
                },
                dismissButton = { TextButton(onClick = { pickingTo = false }) { Text("取消") } }
            ) { DatePicker(state = state) }
        }
    }
}

private fun formatDateHeader(date: String): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = fmt.parse(date) ?: return date
        val today = fmt.parse(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        val cal = Calendar.getInstance().apply { time = today!!; add(Calendar.DAY_OF_YEAR, -1) }
        val label = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(d)
        when {
            d == today -> "今天 · $label"
            d == cal.time -> "昨天 · $label"
            else -> label
        }
    } catch (e: Exception) {
        date
    }
}
