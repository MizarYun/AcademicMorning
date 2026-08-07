package com.academicmorning.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.academicmorning.app.data.local.dao.JournalDao
import com.academicmorning.app.data.local.dao.PaperDao
import com.academicmorning.app.data.local.dao.ReadingEventDao
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.local.entity.Paper
import com.academicmorning.app.data.local.entity.ReadingEvent

@Database(
    entities = [Journal::class, Paper::class, ReadingEvent::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun paperDao(): PaperDao
    abstract fun readingEventDao(): ReadingEventDao

    companion object {
        /** v1 → v2：papers 表新增收藏自定义分类字段 tags。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE papers ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
