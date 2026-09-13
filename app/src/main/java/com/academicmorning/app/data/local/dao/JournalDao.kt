package com.academicmorning.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.academicmorning.app.data.local.entity.Journal
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journals ORDER BY impactFactor DESC")
    fun getAll(): Flow<List<Journal>>

    @Query("SELECT * FROM journals WHERE isFollowed = 1 ORDER BY impactFactor DESC")
    fun getFollowed(): Flow<List<Journal>>

    @Query("SELECT * FROM journals WHERE isFollowed = 1 ORDER BY impactFactor DESC")
    suspend fun getFollowedOnce(): List<Journal>

    @Query("SELECT * FROM journals WHERE name LIKE '%' || :query || '%' OR issn LIKE '%' || :query || '%' ORDER BY impactFactor DESC")
    fun search(query: String): Flow<List<Journal>>

    @Query("SELECT * FROM journals WHERE discipline = :key ORDER BY impactFactor DESC")
    fun getByDiscipline(key: String): Flow<List<Journal>>

    @Query("UPDATE journals SET isFollowed = :followed WHERE issn = :issn")
    suspend fun setFollowed(issn: String, followed: Boolean)

    @Query("SELECT COUNT(*) FROM journals WHERE isFollowed = 1")
    fun followedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(list: List<Journal>)

    @Query("SELECT * FROM journals WHERE issn = :issn")
    suspend fun getByIssn(issn: String): Journal?

    /** 缓存识别出的出刊周期（天）。 */
    @Query("UPDATE journals SET freqDays = :freqDays WHERE issn = :issn")
    suspend fun updateFreqDays(issn: String, freqDays: Int)

    @Query("SELECT COUNT(*) FROM journals")
    suspend fun count(): Int
}
