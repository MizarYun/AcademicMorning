package com.academicmorning.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_events")
data class ReadingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paperId: String,
    val timestamp: Long,
    val durationSec: Int = 0
)
