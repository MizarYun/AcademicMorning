package com.academicmorning.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journals")
data class Journal(
    @PrimaryKey val issn: String,
    val name: String,
    val discipline: String,
    val quartile: String,
    val impactFactor: Double,
    val source: String,      // "crossref" | "pubmed" | "arxiv"
    val sourceRef: String,
    val publisher: String,
    val isFollowed: Boolean = false,
    val yesterdayCount: Int = 0,
    /** 识别出的出刊周期（天）：7 周刊 / 15 半月刊 / 31 月刊 / 62 双月刊 / 183 半年刊；
     *  0 = 未识别；-1 = 识别失败或无法识别（不再重试）。 */
    val freqDays: Int = 0
)
