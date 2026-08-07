package com.academicmorning.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.academicmorning.app.data.local.entity.ReadingEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingEventDao {

    @Insert
    suspend fun insert(event: ReadingEvent)

    @Query("SELECT * FROM reading_events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp")
    fun getBetween(start: Long, end: Long): Flow<List<ReadingEvent>>

    @Query("SELECT COUNT(*) FROM reading_events WHERE timestamp BETWEEN :start AND :end")
    suspend fun countBetween(start: Long, end: Long): Int

    @Query("SELECT * FROM reading_events WHERE timestamp >= :since ORDER BY timestamp")
    fun getSince(since: Long): Flow<List<ReadingEvent>>

    @Query("SELECT SUM(durationSec) FROM reading_events WHERE timestamp >= :since")
    fun totalDurationSince(since: Long): Flow<Int?>
}
