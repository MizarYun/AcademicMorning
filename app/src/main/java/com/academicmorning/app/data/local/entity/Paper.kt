package com.academicmorning.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "papers")
data class Paper(
    @PrimaryKey val id: String,
    val doi: String?,
    val title: String,
    val titleZh: String?,
    val abstractText: String?,
    val abstractZh: String?,
    val oneLinerZh: String?,
    val journalName: String,
    val journalIssn: String,
    val authors: String,
    val publishedDate: String,   // ISO "2026-08-03"
    val url: String,
    val isOpenAccess: Boolean,
    val isPreprint: Boolean,
    val fetchedDate: String,     // 属于哪一天的晨报
    val fetchedAt: Long,
    val translatedAt: Long?,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val tags: String = ""           // 收藏自定义分类（逗号分隔）
)
