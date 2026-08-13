package com.angel.personalfolder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val DOCUMENT_SUMMARY_COLUMNS = """
    id, title, originalFileName, mimeType, encryptedPath, pageCount,
    category, tags, provider, issuedDate, expiryDate, protocolNumber,
    processingState, processingError, metadataManuallyEdited,
    expiryDateSuggestion, expiryDateSuggestionConfidence,
    titleConfidence, categoryConfidence, providerConfidence,
    issuedDateConfidence, expiryDateConfidence, protocolNumberConfidence,
    titleManuallyEdited, categoryManuallyEdited, providerManuallyEdited,
    issuedDateManuallyEdited, expiryDateManuallyEdited,
    protocolNumberManuallyEdited, createdAt, updatedAt
"""

@Dao
interface DocumentDao {
    @Query("SELECT $DOCUMENT_SUMMARY_COLUMNS FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentSummary>>

    @Query("""
        SELECT d.id, d.title, d.originalFileName, d.mimeType, d.encryptedPath, d.pageCount,
            d.category, d.tags, d.provider, d.issuedDate, d.expiryDate, d.protocolNumber,
            d.processingState, d.processingError, d.metadataManuallyEdited,
            d.expiryDateSuggestion, d.expiryDateSuggestionConfidence,
            d.titleConfidence, d.categoryConfidence, d.providerConfidence,
            d.issuedDateConfidence, d.expiryDateConfidence, d.protocolNumberConfidence,
            d.titleManuallyEdited, d.categoryManuallyEdited, d.providerManuallyEdited,
            d.issuedDateManuallyEdited, d.expiryDateManuallyEdited,
            d.protocolNumberManuallyEdited, d.createdAt, d.updatedAt
        FROM documents AS d
        WHERE (:ftsQuery = '' OR d.id IN (
            SELECT documentId FROM documents_fts WHERE documents_fts MATCH :ftsQuery
        ))
          AND (:category = '' OR d.category = :category)
          AND (:processingState = '' OR d.processingState = :processingState)
          AND (:caseId IS NULL OR EXISTS (
              SELECT 1 FROM case_documents AS cd
              WHERE cd.documentId = d.id AND cd.caseId = :caseId
          ))
          AND (:expiryBefore IS NULL OR (
              d.expiryDate IS NOT NULL AND d.expiryDate >= :today AND d.expiryDate <= :expiryBefore
          ))
        ORDER BY d.updatedAt DESC
    """)
    fun search(
        ftsQuery: String,
        category: String,
        processingState: String,
        caseId: String?,
        today: String,
        expiryBefore: String?
    ): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DocumentEntity?>

    @Query("SELECT $DOCUMENT_SUMMARY_COLUMNS FROM documents WHERE expiryDate IS NOT NULL ORDER BY expiryDate ASC")
    fun observeExpiring(): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Query("SELECT id FROM documents")
    suspend fun getAllIds(): List<String>

    @Query("SELECT encryptedPath FROM documents")
    suspend fun getAllEncryptedPaths(): List<String>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(pageCount), 0) FROM documents")
    suspend fun totalLogicalPageCount(): Long

    @Query("SELECT COALESCE(SUM(LENGTH(ocrText)), 0) FROM documents")
    suspend fun totalDocumentOcrChars(): Long

    @Query("SELECT COALESCE(SUM(LENGTH(extractedMetadataJson)), 0) FROM documents")
    suspend fun totalMetadataJsonChars(): Long

    @Query("SELECT * FROM documents WHERE processingState = :state")
    suspend fun getByProcessingState(state: String): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()
}

@Dao
interface DocumentPageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getForDocument(documentId: String): List<DocumentPageEntity>

    @Query("SELECT * FROM document_pages")
    suspend fun getAll(): List<DocumentPageEntity>

    @Query("SELECT COUNT(*) FROM document_pages")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(ocrText)), 0) FROM document_pages")
    suspend fun totalOcrChars(): Long

    @Query("SELECT encryptedPath FROM document_pages")
    suspend fun getAllEncryptedPaths(): List<String>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getForDocumentOrdered(documentId: String): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<DocumentPageEntity>)

    @Query("UPDATE document_pages SET ocrText = :ocrText WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun updateOcr(documentId: String, pageIndex: Int, ocrText: String)

    @Query("UPDATE document_pages SET ocrText = '' WHERE documentId = :documentId")
    suspend fun clearOcrForDocument(documentId: String)

    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("DELETE FROM document_pages")
    suspend fun deleteAll()
}

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CaseEntity?

    @Query("SELECT * FROM cases")
    suspend fun getAll(): List<CaseEntity>

    @Query("SELECT COUNT(*) FROM cases")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(caseEntity: CaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cases: List<CaseEntity>)

    @Query("UPDATE cases SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("""
        UPDATE cases SET title = :title, description = :description, status = :status,
        startDate = :startDate, deadline = :deadline, nextStep = :nextStep, notes = :notes,
        updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun update(
        id: String,
        title: String,
        description: String,
        status: String,
        startDate: String?,
        deadline: String?,
        nextStep: String,
        notes: String,
        updatedAt: Long
    )

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cases")
    suspend fun deleteAll()
}

@Dao
interface CaseDocumentDao {
    @Query("SELECT * FROM case_documents")
    suspend fun getAll(): List<CaseDocumentCrossRef>

    @Query("""
        SELECT d.id, d.title, d.originalFileName, d.mimeType, d.encryptedPath, d.pageCount,
            d.category, d.tags, d.provider, d.issuedDate, d.expiryDate, d.protocolNumber,
            d.processingState, d.processingError, d.metadataManuallyEdited,
            d.expiryDateSuggestion, d.expiryDateSuggestionConfidence,
            d.titleConfidence, d.categoryConfidence, d.providerConfidence,
            d.issuedDateConfidence, d.expiryDateConfidence, d.protocolNumberConfidence,
            d.titleManuallyEdited, d.categoryManuallyEdited, d.providerManuallyEdited,
            d.issuedDateManuallyEdited, d.expiryDateManuallyEdited,
            d.protocolNumberManuallyEdited, d.createdAt, d.updatedAt
        FROM documents AS d
        INNER JOIN case_documents ON d.id = case_documents.documentId
        WHERE case_documents.caseId = :caseId
        ORDER BY d.updatedAt DESC
    """)
    fun observeDocumentsForCase(caseId: String): Flow<List<DocumentSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: CaseDocumentCrossRef)

    @Query("DELETE FROM case_documents WHERE caseId = :caseId AND documentId = :documentId")
    suspend fun delete(caseId: String, documentId: String)

    @Query("DELETE FROM case_documents WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("DELETE FROM case_documents")
    suspend fun deleteAll()
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_events WHERE caseId = :caseId ORDER BY eventDate DESC, createdAt DESC")
    fun observeForCase(caseId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events")
    suspend fun getAll(): List<TimelineEventEntity>

    @Query("SELECT COUNT(*) FROM timeline_events")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TimelineEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TimelineEventEntity>)

    @Query("DELETE FROM timeline_events")
    suspend fun deleteAll()
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE caseId = :caseId ORDER BY createdAt ASC")
    fun observeForCase(caseId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items")
    suspend fun getAll(): List<ChecklistItemEntity>

    @Query("SELECT COUNT(*) FROM checklist_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    @Query("UPDATE checklist_items SET isComplete = :complete WHERE id = :id")
    suspend fun setComplete(id: String, complete: Boolean)

    @Query("UPDATE checklist_items SET linkedDocumentId = :documentId WHERE id = :id")
    suspend fun linkDocument(id: String, documentId: String?)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY dueAt ASC")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun count(): Int

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE documentId = :documentId")
    suspend fun getForDocument(documentId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE caseId = :caseId")
    suspend fun getForCase(caseId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("DELETE FROM reminders WHERE caseId = :caseId")
    suspend fun deleteForCase(caseId: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE reminders SET isDone = 1 WHERE id = :id")
    suspend fun markDone(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
