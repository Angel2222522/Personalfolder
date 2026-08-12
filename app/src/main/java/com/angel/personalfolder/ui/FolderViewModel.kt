package com.angel.personalfolder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.personalfolder.data.CaseEntity
import com.angel.personalfolder.data.ChecklistItemEntity
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.BackupService
import com.angel.personalfolder.data.ExportService
import com.angel.personalfolder.data.FolderRepository
import com.angel.personalfolder.data.TimelineEventEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FolderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FolderRepository(application)
    private val backupService = BackupService(application)
    private val exportService = ExportService(application)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _category = MutableStateFlow("")
    private val _processingState = MutableStateFlow("")
    private val _caseId = MutableStateFlow<String?>(null)
    private val _expiringSoon = MutableStateFlow(false)
    val category: StateFlow<String> = _category.asStateFlow()
    val processingState: StateFlow<String> = _processingState.asStateFlow()
    val caseId: StateFlow<String?> = _caseId.asStateFlow()
    val expiringSoon: StateFlow<Boolean> = _expiringSoon.asStateFlow()
    val documents: StateFlow<List<DocumentEntity>> = combine(_query, _category, _processingState, _caseId, _expiringSoon) { query, category, state, caseId, expiring ->
        DocumentFilters(query, category, state, caseId, expiring)
    }.flatMapLatest { filters ->
        repository.documents(filters.query, filters.category, filters.processingState, filters.caseId, filters.expiringSoon)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cases: StateFlow<List<CaseEntity>> = repository.cases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingReminders = repository.pendingReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _activeOperations = MutableStateFlow(0)
    val busy: StateFlow<Boolean> = _activeOperations
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _message.asSharedFlow()

    fun setQuery(value: String) { _query.value = value }
    fun setCategory(value: String) { _category.value = value }
    fun setProcessingState(value: String) { _processingState.value = value }
    fun setCaseFilter(value: String?) { _caseId.value = value }
    fun setExpiringSoon(value: Boolean) { _expiringSoon.value = value }

    fun importUris(uris: List<Uri>, onFinished: () -> Unit = {}) = viewModelScope.launch {
        beginOperation()
        try {
            repository.importUris(uris)?.let { _message.emit("Το έγγραφο εισήχθη και επεξεργάζεται τοπικά.") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _message.emit(error.message ?: "Δεν ήταν δυνατή η εισαγωγή.")
        } finally {
            try {
                onFinished()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _message.tryEmit(error.message ?: "Ο καθαρισμός της εισαγωγής δεν ολοκληρώθηκε.")
            } finally {
                endOperation()
            }
        }
    }

    suspend fun getDocument(id: String): DocumentEntity? = repository.document(id)

    fun updateDocument(id: String, title: String, category: String, tags: String, provider: String, issuedDate: String?, expiryDate: String?, protocolNumber: String?) =
        launchOperation("Δεν ήταν δυνατή η αποθήκευση.") {
            repository.updateDocumentMetadata(id, title, category, tags, provider, issuedDate, expiryDate, protocolNumber)
        }

    fun rerunOcr(id: String) = launchOperation("Δεν ήταν δυνατή η επανάληψη OCR.", "Η επεξεργασία OCR ξεκίνησε ξανά.") {
        repository.retryOcr(id)
    }

    fun deleteDocument(id: String) = launchOperation("Δεν ήταν δυνατή η διαγραφή.", "Το έγγραφο διαγράφηκε.") {
        repository.deleteDocument(id)
    }

    fun createCase(title: String, description: String) {
        if (title.isBlank()) return
        launchOperation("Δεν ήταν δυνατή η δημιουργία υπόθεσης.") { repository.createCase(title, description) }
    }

    fun createCase(title: String, description: String, startDate: String?, deadline: String?, nextStep: String, notes: String) =
        launchOperation("Δεν ήταν δυνατή η δημιουργία υπόθεσης.") {
            repository.createCase(title, description, startDate, deadline, nextStep, notes)
        }

    fun updateCase(id: String, title: String, description: String, status: String, startDate: String?, deadline: String?, nextStep: String, notes: String) =
        launchOperation("Δεν ήταν δυνατή η αποθήκευση της υπόθεσης.") {
            repository.updateCase(id, title, description, status, startDate, deadline, nextStep, notes)
        }

    fun deleteCase(id: String) = launchOperation("Δεν ήταν δυνατή η διαγραφή της υπόθεσης.", "Η υπόθεση διαγράφηκε.") {
        repository.deleteCase(id)
    }

    fun updateCaseStatus(id: String, status: String) = launchOperation("Δεν ήταν δυνατή η αλλαγή κατάστασης.") {
        repository.updateCaseStatus(id, status)
    }

    fun addTimelineEvent(caseId: String, title: String, note: String) {
        if (title.isNotBlank()) launchOperation("Δεν ήταν δυνατή η προσθήκη στο χρονολόγιο.") { repository.addTimelineEvent(caseId, title, note) }
    }

    fun addChecklistItem(caseId: String, title: String) {
        if (title.isNotBlank()) launchOperation("Δεν ήταν δυνατή η προσθήκη.") { repository.addChecklistItem(caseId, title) }
    }

    fun addChecklistItem(caseId: String, title: String, linkedDocumentId: String?) {
        if (title.isNotBlank()) launchOperation("Δεν ήταν δυνατή η προσθήκη.") { repository.addChecklistItem(caseId, title, linkedDocumentId) }
    }

    fun setChecklistComplete(item: ChecklistItemEntity, complete: Boolean) = launchOperation("Δεν ήταν δυνατή η ενημέρωση της λίστας.") {
        repository.setChecklistComplete(item.id, complete)
    }

    fun linkChecklistDocument(item: ChecklistItemEntity, documentId: String?) = launchOperation("Δεν ήταν δυνατή η σύνδεση.") {
        repository.linkChecklistDocument(item.id, documentId)
    }

    fun deleteChecklistItem(item: ChecklistItemEntity) = launchOperation("Δεν ήταν δυνατή η διαγραφή.") {
        repository.deleteChecklistItem(item.id)
    }

    fun timeline(caseId: String) = repository.timeline(caseId)
    fun checklist(caseId: String) = repository.checklist(caseId)
    fun caseDocuments(caseId: String) = repository.caseDocuments(caseId)

    fun attachDocumentToCase(caseId: String, documentId: String) = launchOperation("Δεν ήταν δυνατή η σύνδεση.") {
        repository.attachDocumentToCase(caseId, documentId)
    }

    fun detachDocumentFromCase(caseId: String, documentId: String) = launchOperation("Δεν ήταν δυνατή η αποσύνδεση.") {
        repository.detachDocumentFromCase(caseId, documentId)
    }

    fun markReminderDone(id: String) = launchOperation("Δεν ήταν δυνατή η ενημέρωση της υπενθύμισης.") {
        repository.markReminderDone(id)
    }

    fun reportError(message: String) { _message.tryEmit(message) }

    fun clearSearch() { _query.value = "" }

    fun createBackup(destination: Uri, password: String) = launchOperation("Δεν ήταν δυνατή η δημιουργία του αντιγράφου.", "Το κρυπτογραφημένο αντίγραφο δημιουργήθηκε.") {
        backupService.create(destination, password)
    }

    fun restoreBackup(source: Uri, password: String) = launchOperation("Δεν ήταν δυνατή η επαναφορά του αντιγράφου.", "Η επαναφορά ολοκληρώθηκε.") {
        backupService.restore(source, password)
    }

    fun exportDocuments(destination: Uri, documentIds: List<String>) = launchOperation("Δεν ήταν δυνατή η εξαγωγή.", "Η εξαγωγή ZIP ολοκληρώθηκε. Το αρχείο δεν είναι κρυπτογραφημένο.") {
        exportService.exportDocuments(destination, documentIds)
    }

    fun exportPdf(destination: Uri, documentIds: List<String>) = launchOperation("Δεν ήταν δυνατή η δημιουργία PDF.", "Το ενιαίο PDF δημιουργήθηκε. Το αρχείο δεν είναι κρυπτογραφημένο.") {
        exportService.exportPdf(destination, documentIds)
    }

    private fun launchOperation(errorMessage: String, successMessage: String? = null, operation: suspend () -> Unit) = viewModelScope.launch {
        beginOperation()
        try {
            operation()
            successMessage?.let { _message.emit(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _message.emit(error.message ?: errorMessage)
        } finally {
            endOperation()
        }
    }

    private fun beginOperation() { _activeOperations.update { it + 1 } }

    private fun endOperation() { _activeOperations.update { (it - 1).coerceAtLeast(0) } }

    private data class DocumentFilters(
        val query: String,
        val category: String,
        val processingState: String,
        val caseId: String?,
        val expiringSoon: Boolean
    )
}
