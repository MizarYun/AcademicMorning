package com.academicmorning.app.ui.favorites

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.ui.components.EmptyState
import com.academicmorning.app.ui.components.FavoriteCategoryDialog
import com.academicmorning.app.ui.components.PaperCard
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademicMorningApp
    private val repo = app.container.paperRepository
    private val settings = app.container.settings

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category

    fun setCategory(c: String?) { _category.value = c }

    /** 新建收藏夹（允许空收藏夹）。 */
    fun addFolder(name: String) = viewModelScope.launch {
        val n = name.trim().take(12)
        if (n.isNotBlank()) settings.addFavoriteFolder(n)
    }

    /** 全部收藏分类 = 已用标签 + 用户创建的收藏夹（"未分类"由 UI 单独提供）。 */
    val categories: StateFlow<List<String>> =
        combine(repo.favorites(), settings.favoriteFolders) { list, folders ->
            val used = list.flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
            (used + folders).distinct()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Paper>> =
        combine(repo.favorites(), _category) { list, c ->
            when (c) {
                null -> list
                "未分类" -> list.filter { it.tags.isBlank() }
                else -> list.filter { it.tags.split(",").map { t -> t.trim() }.contains(c) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unfavorite(p: Paper) = viewModelScope.launch {
        repo.setFavorite(p.id, false)
    }

    fun updateTags(p: Paper, tags: List<String>) = viewModelScope.launch {
        repo.updateTags(p.id, tags.joinToString(","))
    }
}

@Composable
fun FavoritesScreen(onPaperClick: (String) -> Unit, vm: FavoritesViewModel = viewModel()) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<Paper?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AmBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("收藏", style = MaterialTheme.typography.headlineMedium)
                Text("共 ${favorites.size} 篇 · 收藏永久保存，链接随时可访问", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { showNewFolder = true }) {
                Text("+ 新建收藏夹", color = AmPrimary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        // 分类筛选
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(null to "全部", "未分类" to "未分类").forEach { (k, label) ->
                FilterChip(
                    selected = category == k,
                    onClick = { vm.setCategory(k) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
            categories.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { vm.setCategory(if (category == c) null else c) },
                    label = { Text(c, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (favorites.isEmpty()) {
            EmptyState("该分类下暂无收藏论文\n在论文卡片上点击 ☆ 并选择分类即可收藏")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(favorites, key = { it.id }) { p ->
                    PaperCard(
                        paper = p,
                        onClick = { onPaperClick(p.id) },
                        onFavorite = { editTarget = p },
                        onShare = {}
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // 编辑分类 / 取消收藏
    editTarget?.let { target ->
        FavoriteCategoryDialog(
            title = "编辑收藏分类",
            existing = categories,
            initial = target.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
            onConfirm = { tags ->
                vm.updateTags(target, tags)
                editTarget = null
            },
            onDismiss = { editTarget = null },
            extraAction = "取消收藏" to {
                vm.unfavorite(target)
                editTarget = null
            }
        )
    }

    // 新建收藏夹
    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建收藏夹", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("收藏夹名称", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addFolder(name)
                    showNewFolder = false
                }) { Text("创建", color = AmPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("取消") }
            }
        )
    }
}
