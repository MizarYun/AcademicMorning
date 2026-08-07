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
    val yesterdayCount: Int = 0
)
