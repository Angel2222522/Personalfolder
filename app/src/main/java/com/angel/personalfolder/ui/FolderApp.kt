package com.angel.personalfolder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.angel.personalfolder.data.CaseEntity
import com.angel.personalfolder.data.CaseStatus
import com.angel.personalfolder.data.ChecklistItemEntity
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderEntity
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

@Composable
private fun HomeScreen(
    documents: List<DocumentEntity>,
    cases: List<CaseEntity>,
    reminders: List<ReminderEntity>,
    onImport: () -> Unit,
    onCamera: () -> Unit,
    onDocuments: () -> Unit,
    onCases: () -> Unit,
    onDocument: (String) -> Unit,
    onCase: (String) -> Unit,
    onReminderDone: (String) -> Unit
) {
    val attention = documents.filter { document ->
        document.expiryDate?.let { runCatching { LocalDate.parse(it).isBefore(LocalDate.now().plusDays(31)) }.getOrDefault(false) } == true
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Οργάνωσε ό,τι έχει σημασία", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Έγγραφα, προθεσμίες και υποθέσεις — ιδιωτικά στη συσκευή σου.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction(Modifier.weight(1f), Icons.Default.Add, "Εισαγωγή", onImport)
                QuickAction(Modifier.weight(1f), Icons.Default.CameraAlt, "Φωτογράφιση", onCamera)
            }
        }
        if (attention.isNotEmpty()) {
            item {
                SectionHeader("Χρειάζονται προσοχή", Icons.Default.WarningAmber, onDocuments)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attention.take(3).forEach { document ->
                            Row(Modifier.fillMaxWidth().clickable { onDocument(document.id) }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(document.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Λήξη: ${document.expiryDate}", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                                }
                                Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
        if (reminders.isNotEmpty()) {
            item { SectionHeader("Υπενθυμίσεις", Icons.Default.CalendarMonth, {}) }
            items(reminders.take(3), key = { it.id }) { reminder ->
                ReminderCard(reminder, onDone = { onReminderDone(reminder.id) })
            }
        }
        item { SectionHeader("Πρόσφατα έγγραφα", Icons.Default.Description, onDocuments) }
        if (documents.isEmpty()) {
            item { EmptyState("Δεν έχεις προσθέσει ακόμη έγγραφα.", Icons.Default.Description) }
        } else {
            items(documents.take(5), key = { it.id }) { document -> DocumentCard(document, onClick = { onDocument(document.id) }) }
        }
        item { SectionHeader("Ενεργές υποθέσεις", Icons.Default.Assignment, onCases) }
        if (cases.isEmpty()) {
            item { EmptyState("Δεν υπάρχουν ακόμη υποθέσεις.", Icons.Default.Assignment) }
        } else {
            items(cases.filter { it.status != CaseStatus.ARCHIVED }.take(3), key = { it.id }) { caseEntity -> CaseCard(caseEntity) { onCase(caseEntity.id) } }
        }
    }
}

@Composable
private fun DocumentsScreen(
    documents: List<DocumentEntity>,
    cases: List<CaseEntity>,
    query: String,
    category: String,
    processingState: String,
    caseId: String?,
    expiringSoon: Boolean,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onCategory: (String) -> Unit,
    onProcessingState: (String) -> Unit,
    onCaseId: (String?) -> Unit,
    onExpiringSoon: (Boolean) -> Unit,
    onDocument: (String) -> Unit,
    onImport: () -> Unit,
    onExportDocuments: (List<String>) -> Unit,
    onExportPdf: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("Αναζήτηση σε τίτλους, OCR και στοιχεία") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Καθαρισμός") } })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterMenuChip("Κατηγορία", category.ifBlank { "Όλες" }, listOf("" to "Όλες") + listOf("Ταυτότητα / προσωπικά", "Μετανάστευση / άδειες", "Κατοικία", "Δημόσιες υπηρεσίες", "Εργασία", "Οικονομικά", "Λογαριασμοί", "Υγεία", "Συμβόλαια", "Άλλα").map { it to it }, onCategory)
            FilterMenuChip("Κατάσταση", processingState.ifBlank { "Όλες" }, listOf("" to "Όλες", ProcessingState.PROCESSED to "Έτοιμα", ProcessingState.PROCESSING to "Επεξεργασία", ProcessingState.FAILED to "Αποτυχία"), onProcessingState)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterMenuChip("Υπόθεση", cases.firstOrNull { it.id == caseId }?.title ?: "Όλες", listOf(null to "Όλες") + cases.map { it.id to it.title }, onCaseId)
            FilterChip(selected = expiringSoon, onClick = { onExpiringSoon(!expiringSoon) }, label = { Text("Λήγουν σύντομα") })
        }
        if (selectedIds.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Επιλεγμένα: ${selectedIds.size}", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExportDocuments(selectedIds.toList()) }, modifier = Modifier.weight(1f)) { Text("Εξαγωγή ZIP") }
                    OutlinedButton(onClick = { onExportPdf(selectedIds.toList()) }, modifier = Modifier.weight(1f)) { Text("Ενιαίο PDF") }
                }
                TextButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.align(Alignment.End)) { Text("Καθαρισμός επιλογής") }
            }
        }
        if (busy) LinearProcessing()
        if (documents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(if (query.isBlank()) "Δεν έχεις προσθέσει ακόμη έγγραφα." else "Δεν βρέθηκαν αποτελέσματα.", Icons.Default.Search) }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(documents, key = { it.id }) { document ->
                    DocumentCard(
                        document = document,
                        onClick = { onDocument(document.id) },
                        selected = document.id in selectedIds,
                        onToggleSelection = { selectedIds = if (document.id in selectedIds) selectedIds - document.id else selectedIds + document.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun CasesScreen(cases: List<CaseEntity>, onCase: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Υποθέσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Νέα υπόθεση") }
        }
        Spacer(Modifier.height(12.dp))
        if (cases.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("Δεν υπάρχουν ακόμη υποθέσεις.", Icons.Default.Assignment) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) { items(cases, key = { it.id }) { CaseCard(it) { onCase(it.id) } } }
    }
}

@Composable
private fun DocumentDetailScreen(document: DocumentEntity, viewModel: FolderViewModel, onOpen: (String) -> Unit, onShare: (String) -> Unit, onBack: () -> Unit) {
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf(false) }
    if (viewer) {
        DocumentViewerScreen(document, onClose = { viewer = false })
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(document.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(document.originalFileName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { edit = true }) { Icon(Icons.Default.MoreVert, "Επεξεργασία") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(document.processingState)
                AssistChip(onClick = {}, label = { Text(document.category) }, leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) })
            }
            if (document.tags.isNotBlank()) Text("Ετικέτες: ${document.tags}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        item { Button(onClick = { viewer = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text("Προβολή ολόκληρου εγγράφου") } }
        item { OutlinedButton(onClick = { onOpen(document.id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Άνοιγμα ως PDF σε άλλη εφαρμογή") } }
        item { OutlinedButton(onClick = { onShare(document.id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Κοινοποίηση ολόκληρου εγγράφου") } }
        if (document.expiryDate != null) item { InfoCard("Ημερομηνία λήξης", document.expiryDate, Icons.Default.CalendarMonth) }
        if (document.provider.isNotBlank() || document.protocolNumber != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Στοιχεία", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (document.provider.isNotBlank()) InfoRow("Φορέας", document.provider)
                document.protocolNumber?.let { InfoRow("Αριθμός πρωτοκόλλου", it) }
                document.issuedDate?.let { InfoRow("Ημερομηνία έκδοσης", it) }
            }
        }
        item {
            Text("Αναγνωρισμένο κείμενο", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Text(document.ocrText.ifBlank { "Δεν αναγνωρίστηκε ακόμη κείμενο." }, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (document.processingState == ProcessingState.FAILED) {
                Text(document.processingError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                OutlinedButton(onClick = { viewModel.rerunOcr(document.id) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Επανάληψη OCR") }
            }
        }
        item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Διαγραφή εγγράφου") } }
    }
    if (edit) DocumentEditDialog(document, viewModel) { edit = false }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Διαγραφή εγγράφου;") }, text = { Text("Το αρχείο και τα τοπικά δεδομένα του θα διαγραφούν από τη συσκευή.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteDocument(document.id); onBack() }) { Text("Διαγραφή") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ακύρωση") } })
}

@Composable
private fun CaseDetailScreen(caseEntity: CaseEntity, documents: List<DocumentEntity>, viewModel: FolderViewModel, onOpenDocument: (String) -> Unit) {
    val timeline by viewModel.timeline(caseEntity.id).collectAsState(initial = emptyList())
    val checklist by viewModel.checklist(caseEntity.id).collectAsState(initial = emptyList())
    val attachedDocuments by viewModel.caseDocuments(caseEntity.id).collectAsState(initial = emptyList())
    var statusMenu by remember { mutableStateOf(false) }
    var addItem by remember { mutableStateOf(false) }
    var addEvent by remember { mutableStateOf(false) }
    var addDocument by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (edit) {
        CaseEditDialog(
            caseEntity = caseEntity,
            onDismiss = { edit = false },
            onSave = { title, description, status, startDate, deadline, nextStep, notes ->
                viewModel.updateCase(caseEntity.id, title, description, status, startDate, deadline, nextStep, notes)
                edit = false
            }
        )
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(caseEntity.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (caseEntity.description.isNotBlank()) Text(caseEntity.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { edit = true }) { Icon(Icons.Default.MoreVert, "Επεξεργασία υπόθεσης") }
            }
        }
        item {
            Box {
                AssistChip(onClick = { statusMenu = true }, label = { Text(caseEntity.status) }, leadingIcon = { Icon(Icons.Default.MoreVert, null, Modifier.size(16.dp)) })
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) { FolderViewModel.caseStatuses.forEach { status -> DropdownMenuItem(text = { Text(status) }, onClick = { statusMenu = false; viewModel.updateCaseStatus(caseEntity.id, status) }) } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Συνδεδεμένα έγγραφα", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { addDocument = true }) { Icon(Icons.Default.Add, "Σύνδεση εγγράφου") }
            }
            if (attachedDocuments.isEmpty()) Text("Δεν έχεις συνδέσει έγγραφα.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else for (document in attachedDocuments) {
                Row(Modifier.fillMaxWidth().clickable { onOpenDocument(document.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(document.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { viewModel.detachDocumentFromCase(caseEntity.id, document.id) }) { Icon(Icons.Default.Delete, "Αποσύνδεση") }
                }
            }
        }
        if (caseEntity.startDate != null) item { InfoRow("Έναρξη", caseEntity.startDate) }
        if (caseEntity.deadline != null) item { InfoCard("Προθεσμία", caseEntity.deadline, Icons.Default.CalendarMonth) }
        if (caseEntity.nextStep.isNotBlank()) item { InfoRow("Επόμενο βήμα", caseEntity.nextStep) }
        if (caseEntity.notes.isNotBlank()) item { Text(caseEntity.notes, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Λίστα δικαιολογητικών", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); IconButton(onClick = { addItem = true }) { Icon(Icons.Default.Add, "Προσθήκη") } }
            if (checklist.isEmpty()) Text("Δεν έχεις προσθέσει στοιχεία.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else for (checkItem in checklist) {
                ChecklistRow(checkItem, documents.firstOrNull { it.id == checkItem.linkedDocumentId }) { complete -> viewModel.setChecklistComplete(checkItem, complete) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Χρονολόγιο", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); IconButton(onClick = { addEvent = true }) { Icon(Icons.Default.Add, "Νέο γεγονός") } }
            if (timeline.isEmpty()) Text("Δεν υπάρχουν γεγονότα.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else for (event in timeline) { TimelineRow(event) }
        }
        item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Διαγραφή υπόθεσης") } }
    }
    if (addItem) ChecklistDialog(
        documents = documents,
        onDismiss = { addItem = false },
        onSave = { title, linkedDocumentId -> viewModel.addChecklistItem(caseEntity.id, title, linkedDocumentId); addItem = false }
    )
    if (addEvent) EventDialog(onDismiss = { addEvent = false }) { title, note -> viewModel.addTimelineEvent(caseEntity.id, title, note); addEvent = false }
    if (addDocument) AttachDocumentDialog(
        documents = documents.filterNot { candidate -> attachedDocuments.any { it.id == candidate.id } },
        onDismiss = { addDocument = false },
        onAttach = { documentId -> viewModel.attachDocumentToCase(caseEntity.id, documentId); addDocument = false }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Διαγραφή υπόθεσης;") },
        text = { Text("Η υπόθεση, το χρονολόγιο και η λίστα της θα διαγραφούν. Τα έγγραφα δεν διαγράφονται.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteCase(caseEntity.id) }) { Text("Διαγραφή") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ακύρωση") } }
    )
}

@Composable
private fun SettingsScreen(lockEnabled: Boolean, onEnableLock: () -> Unit, onDisableLock: () -> Unit, onRequestNotifications: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Ρυθμίσεις", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Ιδιωτικότητα και ασφάλεια", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text("Τα έγγραφα αποθηκεύονται ιδιωτικά και κρυπτογραφημένα στη συσκευή. Δεν χρησιμοποιείται λογαριασμός, διαφημίσεις ή διακομιστής.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = if (lockEnabled) onDisableLock else onEnableLock, modifier = Modifier.fillMaxWidth()) { Text(if (lockEnabled) "Απενεργοποίηση κλειδώματος" else "Ενεργοποίηση κλειδώματος") }
                    OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Ενεργοποίηση ειδοποιήσεων λήξης") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Αντίγραφα ασφαλείας", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text("Το αντίγραφο περιλαμβάνει έγγραφα, OCR, υποθέσεις, checklist, χρονολόγιο και υπενθυμίσεις. Οι ρυθμίσεις ασφαλείας δεν μεταφέρονται και προστατεύεται με δικό σου κωδικό.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackup, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(6.dp)); Text("Δημιουργία") }
                        OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(6.dp)); Text("Επαναφορά") }
                    }
