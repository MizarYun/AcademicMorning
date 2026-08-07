package com.academicmorning.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextTertiary

/**
 * 收藏分类选择弹窗：从已有分类中多选，或输入新分类。
 * @param onConfirm 确认（返回选中的分类列表，可为空 = 未分类）
 * @param extraAction 额外操作按钮（如"取消收藏"），null 则不显示
 */
@Composable
fun FavoriteCategoryDialog(
    title: String = "选择收藏分类",
    existing: List<String>,
    initial: List<String> = emptyList(),
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    extraAction: (Pair<String, () -> Unit>)? = null
) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    var newCategory by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column {
                if (existing.isNotEmpty()) {
                    Text("已有分类（可多选）", fontSize = 12.sp, color = AmTextTertiary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        existing.forEach { c ->
                            FilterChip(
                                selected = selected.contains(c),
                                onClick = {
                                    selected = if (selected.contains(c)) selected - c else selected + c
                                },
                                label = { Text(c, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it.take(12) },
                    label = { Text("新建分类（可选）", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val all = selected.toMutableList()
                val n = newCategory.trim()
                if (n.isNotBlank() && !all.contains(n)) all.add(n)
                onConfirm(all)
            }) { Text("确定", color = AmPrimary) }
        },
        dismissButton = {
            Row {
                extraAction?.let { (label, action) ->
                    TextButton(onClick = action) { Text(label, color = Color(0xFFDC2626)) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
