package com.angel.personalfolder.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        DocumentPageEntity::class,
        CaseEntity::class,
        CaseDocumentCrossRef::class,
        TimelineEventEntity::class,
        ChecklistItemEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun caseDao(): CaseDao
    abstract fun caseDocumentDao(): CaseDocumentDao
    abstract fun timelineDao(): TimelineDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Version 2 keeps the same tables and columns; this migration records the safe schema transition.
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "personal_folder.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
