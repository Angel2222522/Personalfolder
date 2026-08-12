package com.angel.personalfolder.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

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
    @ColumnInfo(defaultValue = "0") val metadataManuallyEdited: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Room's schema model for the local full-text index. The row is maintained by
 * the database triggers in [AppDatabase] so writes remain atomic with the
 * document row while Room can validate and migrate FTS queries safely.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    val documentId: String,
    val title: String,
    val originalFileName: String,
    val ocrText: String,
    val provider: String,
    val category: String,
    val tags: String,
    val protocolNumber: String?
)

@Entity(
    tableName = "document_pages",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentPageEntity(
    val documentId: String,
    val pageIndex: Int,
    val encryptedPath: String,
    val ocrText: String = "",
    @ColumnInfo(defaultValue = "''") val sourceFileName: String = "",
    @ColumnInfo(defaultValue = "'application/octet-stream'") val mimeType: String = "application/octet-stream"
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
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("caseId"), Index("documentId")]
)
data class CaseDocumentCrossRef(
    val caseId: String,
    val documentId: String
)

@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedDocumentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("caseId"), Index("linkedDocumentId")]
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
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
