package com.academicmorning.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.data.model.Discipline
import com.academicmorning.app.ui.theme.AmAccent
import com.academicmorning.app.ui.theme.AmAccentContainer
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmFavorite
import com.academicmorning.app.ui.theme.AmPreprint
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmPrimaryContainer
import com.academicmorning.app.ui.theme.AmQ2
import com.academicmorning.app.ui.theme.AmTextSecondary

/** 白底圆角卡片容器。 */
@Composable
fun AmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 小标签 chip（Q1/Q2/OA/预印本/AI已总结）。 */
@Composable
fun TagChip(text: String, color: Color, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (filled) Color.White else color
        )
    }
}

/** 期刊行（发现期刊 / 向导 Step2 共用）。 */
@Composable
fun JournalRow(
    journal: Journal,
    freqLabel: String? = null,
    typeLabel: String? = null,
    onToggleFollow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    journal.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 自动识别出的期刊类型标注（周刊/半月刊/月刊/双月刊/半年刊）
                if (!typeLabel.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    TagChip(typeLabel, AmAccent)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Discipline.fromKey(journal.discipline)?.label ?: journal.discipline,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(8.dp))
                TagChip(journal.quartile, if (journal.quartile == "Q1") AmPrimary else AmQ2, filled = true)
                Spacer(Modifier.width(6.dp))
                Text("IF ${journal.impactFactor}", style = MaterialTheme.typography.bodySmall)
                if (journal.yesterdayCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("昨日发文 ${journal.yesterdayCount} 篇", style = MaterialTheme.typography.bodySmall)
                }
            }
            // 更新周期标注（半月刊/月刊/双月刊 + 常见更新日）
            if (!freqLabel.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(freqLabel, fontSize = 11.sp, color = AmAccent)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (journal.isFollowed) {
            TextButton(onClick = onToggleFollow) {
                Icon(
                    Icons.Rounded.CheckCircle, null,
                    tint = AmPrimary, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("已关注", color = AmPrimary, fontSize = 13.sp)
            }
        } else {
            TextButton(onClick = onToggleFollow) {
                Text("关注", color = AmTextSecondary, fontSize = 13.sp)
            }
        }
    }
}

/** 论文卡片（首页/收藏共用）。 */
@Composable
fun PaperCard(
    paper: Paper,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    AmCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // 期刊信息行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(AmPrimary))
            Spacer(Modifier.width(6.dp))
            Text(
                paper.journalName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AmTextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            // 状态标签：未读/已读 · 收藏 · AI 总结 · OA · 预印本
            TagChip(if (paper.isRead) "已读" else "未读", if (paper.isRead) AmAccent else AmPrimary)
            Spacer(Modifier.width(4.dp))
            if (paper.isFavorite) {
                TagChip("收藏", AmFavorite); Spacer(Modifier.width(4.dp))
                paper.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach {
                    TagChip(it, AmFavorite, filled = true); Spacer(Modifier.width(4.dp))
                }
            }
            if (!paper.oneLinerZh.isNullOrBlank()) {
                TagChip("AI 总结", AmAccent); Spacer(Modifier.width(4.dp))
            }
            if (paper.isOpenAccess) {
                TagChip("OA 开放获取", AmAccent, filled = true); Spacer(Modifier.width(4.dp))
            }
            if (paper.isPreprint) {
                TagChip("预印本", AmPreprint)
            }
        }
        Spacer(Modifier.height(8.dp))

        // 标题：中文优先
        val displayTitle = paper.titleZh ?: paper.title
        Text(
            displayTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        if (paper.titleZh != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                paper.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }

        // AI 一句话总结
        if (!paper.oneLinerZh.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmAccentContainer)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome, null,
                    tint = AmAccent, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    paper.oneLinerZh,
                    fontSize = 12.sp, color = AmAccent, lineHeight = 16.sp
                )
            }
        }

        // 底部操作行
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                paper.publishedDate,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (paper.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "收藏",
                    tint = if (paper.isFavorite) AmPrimary else AmTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Share, "分享",
                    tint = AmTextSecondary, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 应用 Logo（太阳+书本，同启动图标）。 */
@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp = 64.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(AmPrimary),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                com.academicmorning.app.R.drawable.ic_launcher_foreground
            ),
            contentDescription = "学术晨报 Logo",
            modifier = Modifier.size(size)
        )
    }
}

/** 空状态。 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📭", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 从标题提取关键词 chips（简单实现：长度>4 的实义英文词）。 */
fun extractKeywords(paper: Paper): List<String> {
    val stop = setOf("which", "where", "their", "there", "these", "those", "through", "between",
        "using", "based", "via", "with", "from", "into", "that", "this", "have", "been")
    return (paper.title + " " + (paper.abstractText ?: ""))
        .split(Regex("[^A-Za-z\\-]+"))
        .filter { it.length > 4 && it.lowercase() !in stop }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
        .take(4).map { it.key }
}

val AmDividerColor = AmDivider
val AmPrimaryColor = AmPrimary
val AmPrimaryContainerColor = AmPrimaryContainer
