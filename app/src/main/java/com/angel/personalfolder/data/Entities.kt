package com.angel.personalfolder.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "documents",
    indices = [Index("updatedAt"), Index("expiryDate"), Index("category")]
)
data class DocumentEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val originalFileName: String,
    val mimeType: String,
    val encryptedPath: String,
    val pageCount: Int,
    val category: String = "Άλλα",
    val tags: String = "",
    val provider: String = "",
    val issuedDate: String? = null,
    val expiryDate: String? = null,
    val protocolNumber: String? = null,
    val ocrText: String = "",
    val extractedMetadataJson: String = "",
    val processingState: String = ProcessingState.QUEUED,
    val processingError: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "document_pages",
    primaryKeys = ["documentId", "pageIndex"],
    indices = [Index("documentId")]
)
data class DocumentPageEntity(
    val documentId: String,
    val pageIndex: Int,
    val encryptedPath: String,
    val ocrText: String = ""
)

@Entity(tableName = "cases", indices = [Index("updatedAt"), Index("status")])
data class CaseEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val status: String = CaseStatus.NEW,
    val startDate: String? = null,
    val deadline: String? = null,
    val nextStep: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "case_documents",
    primaryKeys = ["caseId", "documentId"],
    indices = [Index("caseId"), Index("documentId")]
)
data class CaseDocumentCrossRef(
    val caseId: String,
    val documentId: String
)

@Entity(
    tableName = "timeline_events",
    indices = [Index("caseId"), Index("createdAt")]
)
data class TimelineEventEntity(
    @androidx.room.PrimaryKey val id: String,
    val caseId: String,
    val title: String,
    val note: String = "",
    val eventType: String = "manual",
    val eventDate: String,
    val createdAt: Long
)

@Entity(
    tableName = "checklist_items",
    indices = [Index("caseId")]
)
data class ChecklistItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val caseId: String,
    val title: String,
    val isComplete: Boolean = false,
    val linkedDocumentId: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "reminders",
    indices = [Index("dueAt"), Index("documentId"), Index("caseId")]
)
data class ReminderEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val dueAt: Long,
    val documentId: String? = null,
    val caseId: String? = null,
    val leadDays: Int = 0,
    val isDone: Boolean = false
)

object ProcessingState {
    const val QUEUED = "queued"
    const val PROCESSING = "processing"
    const val PROCESSED = "processed"
    const val FAILED = "failed"
}

object CaseStatus {
    const val NEW = "Νέα"
    const val IN_PROGRESS = "Σε εξέλιξη"
    const val WAITING = "Περιμένω απάντηση"
    const val ACTION = "Χρειάζεται ενέργεια"
    const val COMPLETED = "Ολοκληρώθηκε"
    const val ARCHIVED = "Αρχειοθετήθηκε"
}
