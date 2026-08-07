package com.academicmorning.app.ui.stats

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.ui.components.AmCard
import com.academicmorning.app.ui.theme.AmBackground
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary
import com.academicmorning.app.ui.theme.AmTextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AcademicMorningApp).container.statsRepository

    val readToday = repo.readCountToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val monthRead = repo.monthReadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalRead = repo.totalRead()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val streak = repo.currentStreakDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val topJournals = repo.topJournals(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val daily30 = repo.dailyCountsLast30()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    AmCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AmPrimary)
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 12.sp, color = AmTextSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val readToday by vm.readToday.collectAsStateWithLifecycle()
    val monthRead by vm.monthRead.collectAsStateWithLifecycle()
    val totalRead by vm.totalRead.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val topJournals by vm.topJournals.collectAsStateWithLifecycle()
    val daily30 by vm.daily30.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(AmBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("阅读统计", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("连续阅读", "$streak", "天", Modifier.weight(1f))
            StatCard("本月阅读", "$monthRead", "篇", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("累计阅读", "$totalRead", "篇", Modifier.weight(1f))
            StatCard("今日阅读", "$readToday", "篇", Modifier.weight(1f))
        }

        // Top5 期刊条形图
        AmCard(Modifier.fillMaxWidth()) {
            Text("最常阅读期刊 Top 5", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (topJournals.isEmpty()) {
                Text("暂无阅读记录", style = MaterialTheme.typography.bodyMedium)
            } else {
                val max = topJournals.maxOf { it.second }.coerceAtLeast(1)
                topJournals.forEach { (name, cnt) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name, fontSize = 11.sp, color = AmTextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(110.dp)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(cnt.toFloat() / max)
                                    .height(14.dp)
                                    .background(AmPrimary, RoundedCornerShape(7.dp))
                            )
                        }
                        Text("$cnt", fontSize = 11.sp, color = AmTextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 近 30 天趋势折线
        AmCard(Modifier.fillMaxWidth()) {
            Text("近 30 天阅读趋势", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (daily30.isEmpty() || daily30.all { it.second == 0 }) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium)
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val maxV = daily30.maxOf { it.second }.coerceAtLeast(1)
                    val stepX = size.width / (daily30.size - 1).coerceAtLeast(1)
                    val path = Path()
                    daily30.forEachIndexed { i, (_, v) ->
                        val x = i * stepX
                        val y = size.height - (v.toFloat() / maxV) * (size.height - 16.dp.toPx())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    // 网格基线
                    drawLine(AmDivider, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                    drawPath(path, AmPrimary, style = Stroke(width = 4f))
                    daily30.forEachIndexed { i, (_, v) ->
                        if (v > 0) {
                            val x = i * stepX
                            val y = size.height - (v.toFloat() / maxV) * (size.height - 16.dp.toPx())
                            drawCircle(AmPrimary, 5f, Offset(x, y))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(daily30.first().first, fontSize = 10.sp, color = AmTextTertiary)
                    Text(daily30.last().first, fontSize = 10.sp, color = AmTextTertiary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
