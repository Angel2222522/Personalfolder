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
import com.angel.personalfolder.data.FolderRepository
import com.angel.personalfolder.data.TimelineEventEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FolderRepository(application)
    private val backupService = BackupService(application)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val documents: StateFlow<List<DocumentEntity>> = _query
        .flatMapLatest(repository::documents)
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

    fun importUris(uris: List<Uri>) = viewModelScope.launch {
        _busy.value = true
        runCatching { repository.importUris(uris) }
            .onSuccess { if (it != null) _message.emit("Το έγγραφο εισήχθη και επεξεργάζεται τοπικά.") }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η εισαγωγή.") }
        _busy.value = false
    }

    suspend fun getDocument(id: String): DocumentEntity? = repository.document(id)

    fun updateDocument(id: String, title: String, category: String, expiryDate: String?) = viewModelScope.launch {
        runCatching { repository.updateDocumentBasics(id, title, category, expiryDate) }
            .onFailure { _message.emit(it.message ?: "Δεν ήταν δυνατή η αποθήκευση.") }
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

    fun updateCaseStatus(id: String, status: String) = viewModelScope.launch {
        repository.updateCaseStatus(id, status)
    }

    fun addTimelineEvent(caseId: String, title: String, note: String) = viewModelScope.launch {
        if (title.isNotBlank()) repository.addTimelineEvent(caseId, title, note)
    }

    fun addChecklistItem(caseId: String, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repository.addChecklistItem(caseId, title)
    }

    fun setChecklistComplete(item: ChecklistItemEntity, complete: Boolean) = viewModelScope.launch {
        repository.setChecklistComplete(item.id, complete)
    }

    fun timeline(caseId: String) = repository.timeline(caseId)
    fun checklist(caseId: String) = repository.checklist(caseId)

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

    companion object {
        val caseStatuses = listOf(
            CaseStatus.NEW, CaseStatus.IN_PROGRESS, CaseStatus.WAITING,
            CaseStatus.ACTION, CaseStatus.COMPLETED, CaseStatus.ARCHIVED
        )
    }
}
