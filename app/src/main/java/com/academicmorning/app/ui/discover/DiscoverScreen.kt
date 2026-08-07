package com.academicmorning.app.ui.discover

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.model.Discipline
import com.academicmorning.app.ui.components.JournalRow
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademicMorningApp
    private val repo = app.container.journalRepository

    val query = MutableStateFlow("")
    val disciplineFilter = MutableStateFlow<String?>(null)   // null = 全部
    val quartileFilter = MutableStateFlow<String?>(null)     // null = 全部

    val journals: StateFlow<List<Journal>> = combine(
        query.flatMapLatest { q -> if (q.isBlank()) repo.allJournals() else repo.search(q) },
        disciplineFilter,
        quartileFilter
    ) { list, disc, quart ->
        list.filter { (disc == null || it.discipline == disc) && (quart == null || it.quartile == quart) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedCount = repo.followedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val message = MutableStateFlow<String?>(null)

    fun toggleFollow(j: Journal) = viewModelScope.launch {
        val ok = repo.setFollowed(j.issn, !j.isFollowed)
        if (!ok) message.value = "关注期刊已达 20 个上限，请先取消部分关注"
    }

    fun clearMessage() { message.value = null }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AmPrimary else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text, fontSize = 12.sp,
            color = if (selected) Color.White else AmTextSecondary
        )
    }
}

@Composable
fun DiscoverScreen(vm: DiscoverViewModel = viewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val discFilter by vm.disciplineFilter.collectAsStateWithLifecycle()
    val quartFilter by vm.quartileFilter.collectAsStateWithLifecycle()
    val journals by vm.journals.collectAsStateWithLifecycle()
    val followedCount by vm.followedCount.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Box(Modifier.fillMaxSize().background(AmBackground)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索期刊名称、ISSN 或关键词", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AmTextTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            // 学科筛选
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip("全部学科", discFilter == null) { vm.disciplineFilter.value = null }
                Spacer(Modifier.width(6.dp))
                Discipline.entries.forEach { d ->
                    FilterChip(d.label, discFilter == d.key) {
                        vm.disciplineFilter.value = if (discFilter == d.key) null else d.key
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            // 分区筛选
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip("全部分区", quartFilter == null) { vm.quartileFilter.value = null }
                Spacer(Modifier.width(6.dp))
                listOf("Q1", "Q2").forEach { q ->
                    FilterChip(q, quartFilter == q) {
                        vm.quartileFilter.value = if (quartFilter == q) null else q
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "已关注 $followedCount/20 · 共 ${journals.size} 本期刊",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn {
                items(journals, key = { it.issn }) { j ->
                    JournalRow(journal = j) { vm.toggleFollow(j) }
                    Divider(color = AmDivider)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
