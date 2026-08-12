package com.angel.personalfolder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class MainSection { HOME, DOCUMENTS, CASES, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderApp(
    viewModel: FolderViewModel,
    onImport: () -> Unit,
    onCamera: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onShareDocument: (String) -> Unit,
    onEnableLock: () -> Unit,
    onDisableLock: () -> Unit,
    onCreateBackup: (String) -> Unit,
    onRestoreBackup: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onExportDocuments: (List<String>) -> Unit,
    onExportPdf: (List<String>) -> Unit,
    scannerOpen: Boolean,
    scannerPageUris: List<android.net.Uri>,
    onScannerAddPage: () -> Unit,
    onScannerRetryLast: () -> Unit,
    onScannerFinish: () -> Unit,
    onScannerCancel: () -> Unit,
    lockEnabled: Boolean,
    locked: Boolean
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val cases by viewModel.cases.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.category.collectAsStateWithLifecycle()
    val processingFilter by viewModel.processingState.collectAsStateWithLifecycle()
    val caseFilter by viewModel.caseId.collectAsStateWithLifecycle()
    val expiringSoon by viewModel.expiringSoon.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val reminders by viewModel.pendingReminders.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(MainSection.HOME.name) }
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateCase by remember { mutableStateOf(false) }
    var backupAction by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (locked) {
        LockedScreen()
        return
    }

    val detail = selectedDocumentId != null || selectedCaseId != null
    Scaffold(
        topBar = {
            if (selectedDocumentId != null) {
                TopAppBar(
                    title = { Text("Στοιχεία εγγράφου") },
                    navigationIcon = { IconButton(onClick = { selectedDocumentId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            } else if (selectedCaseId != null) {
                TopAppBar(
                    title = { Text("Υπόθεση") },
                    navigationIcon = { IconButton(onClick = { selectedCaseId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            } else {
                TopAppBar(title = { Text("Προσωπικός Φάκελος", fontWeight = FontWeight.SemiBold) })
            }
        },
        bottomBar = {
            if (!detail) {
                NavigationBar {
                    NavigationBarItem(selected = section == MainSection.HOME.name, onClick = { section = MainSection.HOME.name }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Αρχική") })
                    NavigationBarItem(selected = section == MainSection.DOCUMENTS.name, onClick = { section = MainSection.DOCUMENTS.name }, icon = { Icon(Icons.Default.Description, null) }, label = { Text("Έγγραφα") })
                    NavigationBarItem(selected = section == MainSection.CASES.name, onClick = { section = MainSection.CASES.name }, icon = { Icon(Icons.Default.Assignment, null) }, label = { Text("Υποθέσεις") })
                    NavigationBarItem(selected = section == MainSection.SETTINGS.name, onClick = { section = MainSection.SETTINGS.name }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ρυθμίσεις") })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!detail && section == MainSection.DOCUMENTS.name) {
                FloatingActionButton(onClick = onImport) { Icon(Icons.Default.Add, contentDescription = "Εισαγωγή") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selectedDocumentId != null -> {
                    val document = documents.firstOrNull { it.id == selectedDocumentId }
                    if (document != null) DocumentDetailScreen(document, viewModel, onOpenDocument, onShareDocument, onBack = { selectedDocumentId = null })
                }
                selectedCaseId != null -> {
                    val caseEntity = cases.firstOrNull { it.id == selectedCaseId }
                    if (caseEntity != null) CaseDetailScreen(caseEntity, documents, viewModel, onOpenDocument)
                }
                section == MainSection.HOME.name -> HomeScreen(documents, cases, reminders, onImport, onCamera, { section = MainSection.DOCUMENTS.name }, { section = MainSection.CASES.name }, { selectedDocumentId = it }, { selectedCaseId = it }, viewModel::markReminderDone)
                section == MainSection.DOCUMENTS.name -> DocumentsScreen(
                    documents = documents,
                    cases = cases,
                    query = query,
                    category = categoryFilter,
                    processingState = processingFilter,
                    caseId = caseFilter,
                    expiringSoon = expiringSoon,
                    busy = busy,
                    onQuery = viewModel::setQuery,
                    onCategory = viewModel::setCategory,
                    onProcessingState = viewModel::setProcessingState,
                    onCaseId = viewModel::setCaseFilter,
                    onExpiringSoon = viewModel::setExpiringSoon,
                    onDocument = { selectedDocumentId = it },
                    onImport = onImport,
                    onExportDocuments = onExportDocuments,
                    onExportPdf = onExportPdf
                )
                section == MainSection.CASES.name -> CasesScreen(cases, { selectedCaseId = it }, { showCreateCase = true })
                else -> SettingsScreen(lockEnabled, onEnableLock, onDisableLock, onRequestNotifications, onBackup = { backupAction = "create" }, onRestore = { backupAction = "restore" })
            }
            if (showCreateCase) {
                CreateCaseDialog(
                    onDismiss = { showCreateCase = false },
                    onSave = { title, description, startDate, deadline, nextStep, notes ->
                        showCreateCase = false
                        viewModel.createCase(title, description, startDate, deadline, nextStep, notes)
                    }
                )
            }
            backupAction?.let { action ->
                PasswordDialog(
                    title = if (action == "create") "Δημιουργία αντιγράφου" else "Επαναφορά αντιγράφου",
                    minimumLength = if (action == "create") 12 else 8,
                    onDismiss = { backupAction = null },
                    onConfirm = { password ->
                        backupAction = null
                        if (action == "create") onCreateBackup(password) else onRestoreBackup(password)
                    }
                )
            }
            if (scannerOpen) {
                ScannerSessionDialog(
                    pageUris = scannerPageUris,
                    onAddPage = onScannerAddPage,
                    onRetryLast = onScannerRetryLast,
                    onFinish = onScannerFinish,
                    onCancel = onScannerCancel
                )
            }
        }
    }
}
