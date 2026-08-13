package com.angel.personalfolder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.personalfolder.data.CaseEntity
import com.angel.personalfolder.data.CaseStatus
import com.angel.personalfolder.data.ChecklistItemEntity
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.BackupService
import com.angel.personalfolder.data.ExportService
import com.angel.personalfolder.data.FolderRepository
import com.angel.personalfolder.data.IndependentImportBatchRunner
import com.angel.personalfolder.data.MetadataFieldConfirmations
import com.angel.personalfolder.data.TimelineEventEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cases: StateFlow<List<CaseEntity>> = repository.cases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingReminders = repository.pendingReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _message.asSharedFlow()

    fun setQuery(value: String) { _query.value = value }
    fun setCategory(value: String) { _category.value = value }
    fun setProcessingState(value: String) { _processingState.value = value }
    fun setCaseFilter(value: String?) { _caseId.value = value }
    fun setExpiringSoon(value: Boolean) { _expiringSoon.value = value }

    /**
     * Multi-selection from the system picker/share sheet means independent
     * documents. Each source gets its own DocumentEntity and its own OCR job.
     *
     * Scanner pages are the one deliberate exception: the in-app scanner already
     * represents one document composed of multiple photographed pages, identified
     * by our private FileProvider scanner path.
     */
    fun importUris(uris: List<Uri>, onFinished: () -> Unit = {}) = viewModelScope.launch {
        _busy.value = true
        try {
            val distinctUris = uris.distinct()
            if (distinctUris.isEmpty()) return@launch

            if (isScannerPageBatch(distinctUris)) {
                runCatching { repository.importUris(distinctUris) }
                    .onSuccess {
                        if (it != null) _message.emit("Η σάρωση εισήχθη ως ένα έγγραφο και μπήκε στην ουρά επεξεργασίας.")
                    }
                    .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η εισαγωγή της σάρωσης.") }
            } else {
                val result = IndependentImportBatchRunner.run(distinctUris) { uri ->
                    requireNotNull(repository.importUris(listOf(uri))) {
                        "Η μεμονωμένη εισαγωγή δεν δημιούργησε έγγραφο."
                    }
                }
                when {
                    result.totalCount == 1 && result.successCount == 1 ->
                        _message.emit("Το έγγραφο εισήχθη και μπήκε στην ουρά επεξεργασίας.")
                    result.failureCount == 0 ->
                        _message.emit("Εισήχθησαν ${result.successCount} ανεξάρτητα έγγραφα και μπήκαν στην ουρά επεξεργασίας.")
                    result.successCount > 0 ->
                        _message.emit("Εισήχθησαν ${result.successCount} από ${result.totalCount} έγγραφα. ${result.failureCount} απέτυχαν, αλλά τα υπόλοιπα συνεχίζουν κανονικά.")
                    else ->
                        _message.emit("Δεν ήταν δυνατή η εισαγωγή κανενός από τα ${result.totalCount} έγγραφα.")
                }
            }
        } finally {
            _busy.value = false
            runCatching { onFinished() }
        }
    }

    private fun isScannerPageBatch(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        val expectedAuthority = "${getApplication<Application>().packageName}.fileprovider"
        return uris.all { uri ->
            uri.authority == expectedAuthority && uri.pathSegments.firstOrNull() == SCANNER_FILE_PROVIDER_ROOT
        }
    }

    suspend fun getDocument(id: String): DocumentEntity? = repository.document(id)

    fun updateDocument(id: String, title: String, category: String, tags: String, provider: String, issuedDate: String?, expiryDate: String?, protocolNumber: String?, confirmedFields: MetadataFieldConfirmations? = null) = viewModelScope.launch {
        runCatching { repository.updateDocumentMetadata(id, title, category, tags, provider, issuedDate, expiryDate, protocolNumber, confirmedFields) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η αποθήκευση.") }
    }

    fun rerunOcr(id: String) = viewModelScope.launch {
        runCatching { repository.retryOcr(id) }
            .onSuccess { _message.emit("Η επεξεργασία OCR ξεκίνησε ξανά.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η επανάληψη OCR.") }
    }

    fun deleteDocument(id: String) = viewModelScope.launch {
        runCatching { repository.deleteDocument(id) }
            .onSuccess { _message.emit("Το έγγραφο διαγράφηκε.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η διαγραφή.") }
    }

    fun createCase(title: String, description: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        runCatching { repository.createCase(title, description) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η δημιουργία υπόθεσης.") }
    }

    fun createCase(title: String, description: String, startDate: String?, deadline: String?, nextStep: String, notes: String) = viewModelScope.launch {
        runCatching { repository.createCase(title, description, startDate, deadline, nextStep, notes) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η δημιουργία υπόθεσης.") }
    }

    fun updateCase(id: String, title: String, description: String, status: String, startDate: String?, deadline: String?, nextStep: String, notes: String) = viewModelScope.launch {
        runCatching { repository.updateCase(id, title, description, status, startDate, deadline, nextStep, notes) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η αποθήκευση της υπόθεσης.") }
    }

    fun deleteCase(id: String) = viewModelScope.launch {
        runCatching { repository.deleteCase(id) }
            .onSuccess { _message.emit("Η υπόθεση διαγράφηκε.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η διαγραφή της υπόθεσης.") }
    }

    fun updateCaseStatus(id: String, status: String) = viewModelScope.launch {
        runCatching { repository.updateCaseStatus(id, status) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η αλλαγή κατάστασης.") }
    }

    fun addTimelineEvent(caseId: String, title: String, note: String) = viewModelScope.launch {
        if (title.isNotBlank()) runCatching { repository.addTimelineEvent(caseId, title, note) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η προσθήκη γεγονότος.") }
    }

    fun addChecklistItem(caseId: String, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) runCatching { repository.addChecklistItem(caseId, title) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η προσθήκη δικαιολογητικού.") }
    }

    fun addChecklistItem(caseId: String, title: String, linkedDocumentId: String?) = viewModelScope.launch {
        if (title.isNotBlank()) runCatching { repository.addChecklistItem(caseId, title, linkedDocumentId) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η προσθήκη.") }
    }

    fun setChecklistComplete(item: ChecklistItemEntity, complete: Boolean) = viewModelScope.launch {
        runCatching { repository.setChecklistComplete(item.id, complete) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η ενημέρωση.") }
    }

    fun linkChecklistDocument(item: ChecklistItemEntity, documentId: String?) = viewModelScope.launch {
        runCatching { repository.linkChecklistDocument(item.id, documentId) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η σύνδεση.") }
    }

    fun deleteChecklistItem(item: ChecklistItemEntity) = viewModelScope.launch {
        runCatching { repository.deleteChecklistItem(item.id) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η διαγραφή.") }
    }

    fun timeline(caseId: String) = repository.timeline(caseId)
    fun checklist(caseId: String) = repository.checklist(caseId)
    fun caseDocuments(caseId: String) = repository.caseDocuments(caseId)

    fun attachDocumentToCase(caseId: String, documentId: String) = viewModelScope.launch {
        runCatching { repository.attachDocumentToCase(caseId, documentId) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η σύνδεση.") }
    }

    fun detachDocumentFromCase(caseId: String, documentId: String) = viewModelScope.launch {
        runCatching { repository.detachDocumentFromCase(caseId, documentId) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η αποσύνδεση.") }
    }

    fun markReminderDone(id: String) = viewModelScope.launch {
        runCatching { repository.markReminderDone(id) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η ενημέρωση της υπενθύμισης.") }
    }

    fun reportError(message: String) { _message.tryEmit(message) }

    fun clearSearch() { _query.value = "" }

    fun createBackup(destination: Uri, password: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { backupService.create(destination, password) }
            .onSuccess { _message.emit("Το κρυπτογραφημένο αντίγραφο δημιουργήθηκε.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η δημιουργία του αντιγράφου.") }
        _busy.value = false
    }

    fun restoreBackup(source: Uri, password: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { backupService.restore(source, password) }
            .onSuccess { _message.emit("Η επαναφορά ολοκληρώθηκε.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η επαναφορά του αντιγράφου.") }
        _busy.value = false
    }

    fun exportDocuments(destination: Uri, documentIds: List<String>) = viewModelScope.launch {
        _busy.value = true
        runCatching { exportService.exportDocuments(destination, documentIds) }
            .onSuccess { _message.emit("Η εξαγωγή ZIP ολοκληρώθηκε. Το αρχείο δεν είναι κρυπτογραφημένο.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η εξαγωγή.") }
        _busy.value = false
    }

    fun exportPdf(destination: Uri, documentIds: List<String>) = viewModelScope.launch {
        _busy.value = true
        runCatching { exportService.exportPdf(destination, documentIds) }
            .onSuccess { _message.emit("Το ενιαίο PDF δημιουργήθηκε. Το αρχείο δεν είναι κρυπτογραφημένο.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η δημιουργία PDF.") }
        _busy.value = false
    }

    companion object {
        private const val SCANNER_FILE_PROVIDER_ROOT = "scanner_cache"

        val caseStatuses = listOf(
            CaseStatus.NEW, CaseStatus.IN_PROGRESS, CaseStatus.WAITING,
            CaseStatus.ACTION, CaseStatus.COMPLETED, CaseStatus.ARCHIVED
        )
    }

    private data class DocumentFilters(
        val query: String,
        val category: String,
        val processingState: String,
        val caseId: String?,
        val expiringSoon: Boolean
    )
}
