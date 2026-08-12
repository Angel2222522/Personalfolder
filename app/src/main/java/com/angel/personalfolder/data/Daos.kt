package com.angel.personalfolder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("""
        SELECT d.* FROM documents AS d
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
    ): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL ORDER BY expiryDate ASC")
    fun observeExpiring(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("""
        UPDATE documents SET title = :title, category = :category, tags = :tags,
        provider = :provider, issuedDate = :issuedDate, expiryDate = :expiryDate,
        protocolNumber = :protocolNumber, metadataManuallyEdited = 1, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateMetadata(
        id: String,
        title: String,
        category: String,
        tags: String,
        provider: String,
        issuedDate: String?,
        expiryDate: String?,
        protocolNumber: String?,
        updatedAt: Long
    )

    @Query("UPDATE documents SET category = :category, ocrText = :ocrText, provider = :provider, issuedDate = :issuedDate, expiryDate = :expiryDate, protocolNumber = :protocolNumber, extractedMetadataJson = :metadataJson, processingState = :state, processingError = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProcessing(
        id: String,
        category: String,
        ocrText: String,
        provider: String,
        issuedDate: String?,
        expiryDate: String?,
        protocolNumber: String?,
        metadataJson: String,
        state: String,
        error: String?,
        updatedAt: Long
    )

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

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getForDocumentOrdered(documentId: String): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<DocumentPageEntity>)

    @Query("UPDATE document_pages SET ocrText = :ocrText WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun updateOcr(documentId: String, pageIndex: Int, ocrText: String)

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

    @Query("SELECT documents.* FROM documents INNER JOIN case_documents ON documents.id = case_documents.documentId WHERE case_documents.caseId = :caseId ORDER BY documents.updatedAt DESC")
    fun observeDocumentsForCase(caseId: String): Flow<List<DocumentEntity>>

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
