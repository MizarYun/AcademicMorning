package com.academicmorning.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.academicmorning.app.data.local.entity.Paper
import kotlinx.coroutines.flow.Flow

data class JournalCount(val journalName: String, val cnt: Int)

@Dao
interface PaperDao {

    @Query("SELECT * FROM papers WHERE fetchedDate = :date ORDER BY publishedDate DESC, fetchedAt DESC")
    fun getByDate(date: String): Flow<List<Paper>>

    @Query("SELECT * FROM papers WHERE fetchedDate = :date ORDER BY publishedDate DESC, fetchedAt DESC")
    suspend fun getByDateOnce(date: String): List<Paper>

    @Query("SELECT * FROM papers WHERE id = :id")
    fun getById(id: String): Flow<Paper?>

    @Query("SELECT * FROM papers WHERE id = :id")
    suspend fun getByIdOnce(id: String): Paper?

    @Query("SELECT * FROM papers WHERE isFavorite = 1 ORDER BY fetchedAt DESC")
    fun getFavorites(): Flow<List<Paper>>

    @Query("SELECT * FROM papers ORDER BY publishedDate DESC, fetchedAt DESC")
    fun getAll(): Flow<List<Paper>>

    @Query("SELECT * FROM papers WHERE translatedAt IS NULL ORDER BY fetchedAt DESC LIMIT :limit")
    suspend fun getUntranslatedOnce(limit: Int): List<Paper>

    @Query("DELETE FROM papers WHERE isFavorite = 0 AND fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM papers")
    fun totalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Paper>): List<Long>

    @Query(
        "UPDATE papers SET titleZh = :titleZh, abstractZh = :abstractZh, " +
            "oneLinerZh = :oneLinerZh, translatedAt = :translatedAt WHERE id = :id"
    )
    suspend fun updateTranslation(
        id: String, titleZh: String?, abstractZh: String?, oneLinerZh: String?, translatedAt: Long
    )

    @Query("UPDATE papers SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE papers SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE papers SET isFavorite = :fav, tags = :tags WHERE id = :id")
    suspend fun setFavoriteWithTags(id: String, fav: Boolean, tags: String)

    @Query("UPDATE papers SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: String)

    @Query("SELECT DISTINCT tags FROM papers WHERE isFavorite = 1 AND tags != ''")
    suspend fun getFavoriteTagsOnce(): List<String>

    @Query("SELECT COUNT(*) FROM papers WHERE isRead = 1")
    fun totalRead(): Flow<Int>

    @Query("SELECT COUNT(*) FROM papers WHERE isRead = 1 AND fetchedAt >= :since")
    fun readSince(since: Long): Flow<Int>

    @Query("SELECT journalName, COUNT(*) AS cnt FROM papers WHERE isRead = 1 GROUP BY journalName ORDER BY cnt DESC LIMIT :limit")
    fun topReadJournals(limit: Int): Flow<List<JournalCount>>
}
