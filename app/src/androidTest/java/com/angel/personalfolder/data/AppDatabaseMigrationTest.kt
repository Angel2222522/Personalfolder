package com.angel.personalfolder.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        databaseName = "migration_${System.nanoTime()}.db"
        createLegacyDatabase(version = 1)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesV1ToV4WithoutLosingRowsOrRelations() = runBlocking {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            assertNotNull(database.documentDao().getById("doc-1"))
            assertEquals(1, database.documentPageDao().getForDocument("doc-1").size)
            assertEquals(1, database.caseDocumentDao().getAll().size)
            assertEquals(1, database.timelineDao().getAll().size)
            assertEquals(1, database.checklistDao().getAll().size)
            assertEquals("2026-12-31", database.documentDao().getById("doc-1")?.expiryDateSuggestion)
            assertEquals(null, database.documentDao().getById("doc-1")?.expiryDate)
            assertEquals("low", database.documentDao().getById("doc-1")?.expiryDateSuggestionConfidence)
            assertEquals(1, database.reminderDao().getAll().size)
            assertEquals(4102444800000L, database.reminderDao().getAll().single().deadlineAt)
            database.openHelper.readableDatabase.query("PRAGMA index_list(checklist_items)").use { cursor ->
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "index_checklist_items_linkedDocumentId") found = true
                assertEquals(true, found)
            }

            database.documentDao().deleteById("doc-1")
            assertEquals(0, database.documentPageDao().getForDocument("doc-1").size)
            assertEquals(0, database.caseDocumentDao().getAll().size)
            assertEquals(1, database.reminderDao().getAll().size)
            assertNull(database.checklistDao().getAll().single().linkedDocumentId)
        } finally {
            database.close()
        }
    }

    private fun createLegacyDatabase(version: Int) {
        val file: File = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE documents (id TEXT NOT NULL, title TEXT NOT NULL, originalFileName TEXT NOT NULL, mimeType TEXT NOT NULL, encryptedPath TEXT NOT NULL, pageCount INTEGER NOT NULL, category TEXT NOT NULL, tags TEXT NOT NULL, provider TEXT NOT NULL, issuedDate TEXT, expiryDate TEXT, protocolNumber TEXT, ocrText TEXT NOT NULL, extractedMetadataJson TEXT NOT NULL, processingState TEXT NOT NULL, processingError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE document_pages (documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL, encryptedPath TEXT NOT NULL, ocrText TEXT NOT NULL, PRIMARY KEY(documentId, pageIndex))")
            database.execSQL("CREATE TABLE cases (id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, startDate TEXT, deadline TEXT, nextStep TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE case_documents (caseId TEXT NOT NULL, documentId TEXT NOT NULL, PRIMARY KEY(caseId, documentId))")
            database.execSQL("CREATE TABLE timeline_events (id TEXT NOT NULL, caseId TEXT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, eventType TEXT NOT NULL, eventDate TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE checklist_items (id TEXT NOT NULL, caseId TEXT NOT NULL, title TEXT NOT NULL, isComplete INTEGER NOT NULL, linkedDocumentId TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE reminders (id TEXT NOT NULL, title TEXT NOT NULL, dueAt INTEGER NOT NULL, documentId TEXT, caseId TEXT, leadDays INTEGER NOT NULL, isDone INTEGER NOT NULL, PRIMARY KEY(id))")
            database.execSQL("CREATE INDEX index_documents_updatedAt ON documents(updatedAt)")
            database.execSQL("CREATE INDEX index_documents_expiryDate ON documents(expiryDate)")
            database.execSQL("CREATE INDEX index_documents_category ON documents(category)")
            database.execSQL("CREATE INDEX index_document_pages_documentId ON document_pages(documentId)")
            database.execSQL("CREATE INDEX index_cases_updatedAt ON cases(updatedAt)")
            database.execSQL("CREATE INDEX index_cases_status ON cases(status)")
            database.execSQL("CREATE INDEX index_case_documents_caseId ON case_documents(caseId)")
            database.execSQL("CREATE INDEX index_case_documents_documentId ON case_documents(documentId)")
            database.execSQL("CREATE INDEX index_timeline_events_caseId ON timeline_events(caseId)")
            database.execSQL("CREATE INDEX index_timeline_events_createdAt ON timeline_events(createdAt)")
            database.execSQL("CREATE INDEX index_checklist_items_caseId ON checklist_items(caseId)")
            database.execSQL("CREATE INDEX index_reminders_dueAt ON reminders(dueAt)")
            database.execSQL("CREATE INDEX index_reminders_documentId ON reminders(documentId)")
            database.execSQL("CREATE INDEX index_reminders_caseId ON reminders(caseId)")
            database.execSQL("INSERT INTO documents (id,title,originalFileName,mimeType,encryptedPath,pageCount,category,tags,provider,issuedDate,expiryDate,protocolNumber,ocrText,extractedMetadataJson,processingState,processingError,createdAt,updatedAt) VALUES ('doc-1','Έγγραφο','doc.pdf','application/pdf','/data/data/app/files/documents/doc-1/page_0.pf',1,'Άλλα','','',NULL,'2026-12-31',NULL,'κείμενο','{}','processed',NULL,1,2)")
            database.execSQL("INSERT INTO document_pages VALUES ('doc-1',0,'/data/data/app/files/documents/doc-1/page_0.pf','κείμενο')")
            database.execSQL("INSERT INTO cases VALUES ('case-1','Υπόθεση','περιγραφή','Νέα','2026-01-01','2026-12-31','Επόμενο','σημείωση',1,2)")
            database.execSQL("INSERT INTO case_documents VALUES ('case-1','doc-1')")
            database.execSQL("INSERT INTO timeline_events VALUES ('event-1','case-1','Γεγονός','','manual','2026-01-02',2)")
            database.execSQL("INSERT INTO checklist_items VALUES ('check-1','case-1','Δικαιολογητικό',0,'doc-1',2)")
            database.execSQL("INSERT INTO reminders VALUES ('rem-doc','Υπενθύμιση εγγράφου',4102444800000,'doc-1',NULL,0,0)")
            database.execSQL("INSERT INTO reminders VALUES ('rem-case','Υπενθύμιση υπόθεσης',4102444800000,NULL,'case-1',0,0)")
            database.execSQL("PRAGMA user_version = $version")
        }
    }
}
