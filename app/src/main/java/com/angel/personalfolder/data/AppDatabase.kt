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
    version = 3,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Version 2 keeps the same tables and columns; this migration records the safe schema transition.
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN metadataManuallyEdited INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS documents_new (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        originalFileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        encryptedPath TEXT NOT NULL,
                        pageCount INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        issuedDate TEXT,
                        expiryDate TEXT,
                        protocolNumber TEXT,
                        ocrText TEXT NOT NULL,
                        extractedMetadataJson TEXT NOT NULL,
                        processingState TEXT NOT NULL,
                        processingError TEXT,
                        metadataManuallyEdited INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO documents_new
                    SELECT id, title, originalFileName, mimeType, encryptedPath, pageCount,
                        category, tags, provider, issuedDate, expiryDate, protocolNumber,
                        ocrText, extractedMetadataJson, processingState, processingError,
                        metadataManuallyEdited, createdAt, updatedAt
                    FROM documents
                """.trimIndent())
                database.execSQL("DROP TABLE documents")
                database.execSQL("ALTER TABLE documents_new RENAME TO documents")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_documents_updatedAt ON documents(updatedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_documents_expiryDate ON documents(expiryDate)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_documents_category ON documents(category)")

                database.execSQL("""
                    CREATE TABLE document_pages_new (
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        encryptedPath TEXT NOT NULL,
                        ocrText TEXT NOT NULL,
                        sourceFileName TEXT NOT NULL DEFAULT '',
                        mimeType TEXT NOT NULL DEFAULT 'application/octet-stream',
                        PRIMARY KEY(documentId, pageIndex),
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO document_pages_new(documentId, pageIndex, encryptedPath, ocrText)
                    SELECT documentId, pageIndex, encryptedPath, ocrText FROM document_pages
                    WHERE EXISTS (SELECT 1 FROM documents WHERE documents.id = document_pages.documentId)
                """.trimIndent())
                database.execSQL("DROP TABLE document_pages")
                database.execSQL("ALTER TABLE document_pages_new RENAME TO document_pages")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_document_pages_documentId ON document_pages(documentId)")

                database.execSQL("""
                    CREATE TABLE case_documents_new (
                        caseId TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        PRIMARY KEY(caseId, documentId),
                        FOREIGN KEY(caseId) REFERENCES cases(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO case_documents_new(caseId, documentId)
                    SELECT caseId, documentId FROM case_documents
                    WHERE EXISTS (SELECT 1 FROM cases WHERE cases.id = case_documents.caseId)
                      AND EXISTS (SELECT 1 FROM documents WHERE documents.id = case_documents.documentId)
                """.trimIndent())
                database.execSQL("DROP TABLE case_documents")
                database.execSQL("ALTER TABLE case_documents_new RENAME TO case_documents")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_case_documents_caseId ON case_documents(caseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_case_documents_documentId ON case_documents(documentId)")

                database.execSQL("""
                    CREATE TABLE timeline_events_new (
                        id TEXT NOT NULL,
                        caseId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(caseId) REFERENCES cases(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO timeline_events_new
                    SELECT id, caseId, title, note, eventType, eventDate, createdAt
                    FROM timeline_events
                    WHERE EXISTS (SELECT 1 FROM cases WHERE cases.id = timeline_events.caseId)
                """.trimIndent())
                database.execSQL("DROP TABLE timeline_events")
                database.execSQL("ALTER TABLE timeline_events_new RENAME TO timeline_events")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_caseId ON timeline_events(caseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_createdAt ON timeline_events(createdAt)")

                database.execSQL("""
                    CREATE TABLE checklist_items_new (
                        id TEXT NOT NULL,
                        caseId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        isComplete INTEGER NOT NULL,
                        linkedDocumentId TEXT,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(caseId) REFERENCES cases(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(linkedDocumentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO checklist_items_new
                    SELECT id, caseId, title, isComplete,
                        CASE WHEN EXISTS (
                            SELECT 1 FROM documents WHERE documents.id = checklist_items.linkedDocumentId
                        ) THEN linkedDocumentId ELSE NULL END,
                        createdAt
                    FROM checklist_items
                    WHERE EXISTS (SELECT 1 FROM cases WHERE cases.id = checklist_items.caseId)
                """.trimIndent())
                database.execSQL("DROP TABLE checklist_items")
                database.execSQL("ALTER TABLE checklist_items_new RENAME TO checklist_items")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_caseId ON checklist_items(caseId)")

                database.execSQL("""
                    CREATE TABLE reminders_new (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        dueAt INTEGER NOT NULL,
                        documentId TEXT,
                        caseId TEXT,
                        leadDays INTEGER NOT NULL,
                        isDone INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(caseId) REFERENCES cases(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO reminders_new
                    SELECT id, title, dueAt,
                        CASE WHEN EXISTS (SELECT 1 FROM documents WHERE documents.id = reminders.documentId) THEN documentId ELSE NULL END,
                        CASE WHEN EXISTS (SELECT 1 FROM cases WHERE cases.id = reminders.caseId) THEN caseId ELSE NULL END,
                        leadDays, isDone
                    FROM reminders
                """.trimIndent())
                database.execSQL("DROP TABLE reminders")
                database.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_dueAt ON reminders(dueAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_documentId ON reminders(documentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_caseId ON reminders(caseId)")

                createSearchIndex(database)
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "personal_folder.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        createSearchIndex(database)
                    }

                    override fun onOpen(database: SupportSQLiteDatabase) {
                        createSearchIndex(database)
                    }
                })
                .build().also { instance = it }
        }

        private fun createSearchIndex(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts USING fts4(
                    documentId, title, originalFileName, ocrText, provider, category, tags, protocolNumber
                )
            """.trimIndent())
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS documents_fts_ai AFTER INSERT ON documents BEGIN
                    INSERT INTO documents_fts(documentId, title, originalFileName, ocrText, provider, category, tags, protocolNumber)
                    VALUES (new.id, new.title, new.originalFileName, new.ocrText, new.provider, new.category, new.tags, new.protocolNumber);
                END
            """.trimIndent())
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS documents_fts_ad AFTER DELETE ON documents BEGIN
                    DELETE FROM documents_fts WHERE documentId = old.id;
                END
            """.trimIndent())
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS documents_fts_au AFTER UPDATE ON documents BEGIN
                    DELETE FROM documents_fts WHERE documentId = old.id;
                    INSERT INTO documents_fts(documentId, title, originalFileName, ocrText, provider, category, tags, protocolNumber)
                    VALUES (new.id, new.title, new.originalFileName, new.ocrText, new.provider, new.category, new.tags, new.protocolNumber);
                END
            """.trimIndent())
            val count = database.query("SELECT COUNT(*) FROM documents_fts").use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            val documents = database.query("SELECT id, title, originalFileName, ocrText, provider, category, tags, protocolNumber FROM documents").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(arrayOf(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                            cursor.getString(4), cursor.getString(5), cursor.getString(6),
                            if (cursor.isNull(7)) null else cursor.getString(7)
                        ))
                    }
                }
            }
            if (count == 0L && documents.isNotEmpty()) {
                val statement = database.compileStatement(
                    "INSERT INTO documents_fts(documentId, title, originalFileName, ocrText, provider, category, tags, protocolNumber) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                )
                documents.forEach { values ->
                    statement.clearBindings()
                    values.forEachIndexed { index, value -> if (value == null) statement.bindNull(index + 1) else statement.bindString(index + 1, value as String) }
                    statement.executeInsert()
                }
            }
        }
    }
}
