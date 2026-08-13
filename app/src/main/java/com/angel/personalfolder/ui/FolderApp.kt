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
import com.angel.personalfolder.data.DocumentSelectionPolicy
import com.angel.personalfolder.data.MetadataConfidence
import com.angel.personalfolder.data.MetadataFieldConfirmations
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
    val allDocuments by viewModel.allDocuments.collectAsStateWithLifecycle()
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
                    val document = allDocuments.firstOrNull { it.id == selectedDocumentId }
                    if (document != null) DocumentDetailScreen(document, viewModel, onOpenDocument, onShareDocument, onBack = { selectedDocumentId = null })
                }
                selectedCaseId != null -> {
                    val caseEntity = cases.firstOrNull { it.id == selectedCaseId }
                    if (caseEntity != null) CaseDetailScreen(caseEntity, allDocuments, viewModel, onOpenDocument)
                }
                section == MainSection.HOME.name -> HomeScreen(allDocuments, cases, reminders, onImport, onCamera, { section = MainSection.DOCUMENTS.name }, { section = MainSection.CASES.name }, { selectedDocumentId = it }, { selectedCaseId = it }, viewModel::markReminderDone)
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
        val confirmed = MetadataConfidence.isConfirmed(document.expiryDate, document.expiryDateConfidence, document.expiryDateManuallyEdited)
        confirmed && document.expiryDate?.let { runCatching { LocalDate.parse(it).isBefore(LocalDate.now().plusDays(31)) }.getOrDefault(false) } == true
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
    LaunchedEffect(documents) {
        val visibleIds = documents.asSequence().map { it.id }.toSet()
        selectedIds = DocumentSelectionPolicy.retainVisible(selectedIds, visibleIds)
    }
    val hasActiveFilter = query.isNotBlank() || category.isNotBlank() || processingState.isNotBlank() || caseId != null || expiringSoon
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(if (hasActiveFilter) "Δεν βρέθηκαν έγγραφα με αυτά τα φίλτρα." else "Δεν έχεις προσθέσει ακόμη έγγραφα.", Icons.Default.Search) }
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
        val confirmedExpiry = document.expiryDate?.takeIf {
            MetadataConfidence.isConfirmed(it, document.expiryDateConfidence, document.expiryDateManuallyEdited)
        }
        val unconfirmedExpiry = document.expiryDateSuggestion ?: document.expiryDate?.takeUnless {
            MetadataConfidence.isConfirmed(it, document.expiryDateConfidence, document.expiryDateManuallyEdited)
        }
        if (confirmedExpiry != null) item { InfoCard("Ημερομηνία λήξης", confirmedExpiry, Icons.Default.CalendarMonth) }
        if (unconfirmedExpiry != null) item {
            InfoCard(
                "Πρόταση ημερομηνίας λήξης — δεν έχει επιβεβαιωθεί",
                "${unconfirmedExpiry} (${confidenceLabel(document.expiryDateSuggestionConfidence.ifBlank { document.expiryDateConfidence })})",
                Icons.Default.WarningAmber
            )
        }
        if (document.provider.isNotBlank() || document.protocolNumber != null || document.issuedDate != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Στοιχεία", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (document.provider.isNotBlank()) InfoRow("Φορέας (${confidenceLabel(document.providerConfidence)})", document.provider)
                document.protocolNumber?.let { InfoRow("Αριθμός πρωτοκόλλου (${confidenceLabel(document.protocolNumberConfidence)})", it) }
                document.issuedDate?.let { InfoRow("Ημερομηνία έκδοσης (${confidenceLabel(document.issuedDateConfidence)})", it) }
                InfoRow("Βεβαιότητα κατηγορίας", confidenceLabel(document.categoryConfidence))
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
                }
            }
        }
        item { Text("Έκδοση 2.0.1 · Ελληνικό περιβάλλον · Λειτουργία χωρίς σύνδεση", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold) } } }

@Composable
private fun <T> FilterMenuChip(label: String, selectedLabel: String, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selectedLabel != "Όλες", onClick = { expanded = true }, label = { Text("$label: $selectedLabel", maxLines = 1, overflow = TextOverflow.Ellipsis) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = { expanded = false; onSelected(value) }) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun DocumentCard(document: DocumentEntity, onClick: () -> Unit, selected: Boolean = false, onToggleSelection: (() -> Unit)? = null) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(document.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); if (document.processingState == ProcessingState.PROCESSING) Text("Γίνεται επεξεργασία…", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }; document.expiryDate?.takeIf { MetadataConfidence.isConfirmed(it, document.expiryDateConfidence, document.expiryDateManuallyEdited) }?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }; onToggleSelection?.let { toggle -> androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { toggle() }) } } } }

@Composable
private fun CaseCard(caseEntity: CaseEntity, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(caseEntity.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(caseEntity.nextStep.ifBlank { caseEntity.description.ifBlank { "Χωρίς επόμενο βήμα" } }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; AssistChip(onClick = {}, label = { Text(caseEntity.status, fontSize = 11.sp) }) } } }

private fun confidenceLabel(value: String): String = when (value) {
    MetadataConfidence.HIGH -> "υψηλή"
    MetadataConfidence.MEDIUM -> "μεσαία"
    MetadataConfidence.LOW -> "χαμηλή"
    MetadataConfidence.MANUAL -> "χειροκίνητη"
    else -> "άγνωστη"
}

@Composable
private fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(10.dp)); Column { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer); Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) } } } }

@Composable
private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(180.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable
private fun StatusChip(state: String) { val label = when (state) { ProcessingState.PROCESSED -> "Έτοιμο"; ProcessingState.PROCESSING -> "Επεξεργασία"; ProcessingState.FAILED -> "Αποτυχία"; else -> "Σε αναμονή" }; AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(if (state == ProcessingState.PROCESSED) Icons.Default.Check else Icons.Default.MoreVert, null, Modifier.size(16.dp)) }) }

@Composable
private fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(30.dp)) { Icon(icon, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .65f)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun LockedScreen() {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Ο φάκελος είναι κλειδωμένος", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Επιβεβαίωσε την ταυτότητά σου για να συνεχίσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ReminderCard(reminder: ReminderEntity, onDone: () -> Unit) {
    val deadline = remember(reminder.deadlineAt, reminder.dueAt, reminder.leadDays) {
        Instant.ofEpochMilli(reminder.deadlineAt.takeIf { it > 0L } ?: (reminder.dueAt + reminder.leadDays * 86_400_000L))
            .atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    val trigger = remember(reminder.dueAt) {
        Instant.ofEpochMilli(reminder.dueAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Πραγματική προθεσμία: $deadline", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Ειδοποίηση: $trigger", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onDone) { Icon(Icons.Default.Check, "Ολοκληρώθηκε") }
        }
    }
}

@Composable
private fun LinearProcessing() { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }

@Composable
private fun ChecklistRow(item: ChecklistItemEntity, linkedDocument: DocumentEntity?, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = item.isComplete, onCheckedChange = onChecked)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = if (item.isComplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            linkedDocument?.let { Text("Έγγραφο: ${it.title}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TimelineRow(event: com.angel.personalfolder.data.TimelineEventEntity) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.height(4.dp)); Divider(Modifier.height(45.dp).width(1.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(event.title, fontWeight = FontWeight.SemiBold); Text(event.eventDate, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp); if (event.note.isNotBlank()) Text(event.note, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
private fun CreateCaseDialog(onDismiss: () -> Unit, onSave: (String, String, String?, String?, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var nextStep by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Νέα υπόθεση") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Τίτλος") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Περιγραφή") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(startDate, { startDate = it }, label = { Text("Ημερομηνία έναρξης (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(deadline, { deadline = it }, label = { Text("Προθεσμία (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(nextStep, { nextStep = it }, label = { Text("Επόμενο βήμα") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Σημειώσεις") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, startDate.trim().ifBlank { null }, deadline.trim().ifBlank { null }, nextStep, notes) }, enabled = title.isNotBlank()) { Text("Αποθήκευση") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
private fun CaseEditDialog(
    caseEntity: CaseEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String?, String, String) -> Unit
) {
    var title by remember { mutableStateOf(caseEntity.title) }
    var description by remember { mutableStateOf(caseEntity.description) }
    var status by remember { mutableStateOf(caseEntity.status) }
    var startDate by remember { mutableStateOf(caseEntity.startDate.orEmpty()) }
    var deadline by remember { mutableStateOf(caseEntity.deadline.orEmpty()) }
    var nextStep by remember { mutableStateOf(caseEntity.nextStep) }
    var notes by remember { mutableStateOf(caseEntity.notes) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Επεξεργασία υπόθεσης") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Τίτλος") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Περιγραφή") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                item {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(status, modifier = Modifier.weight(1f)); Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { FolderViewModel.caseStatuses.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { status = option; menu = false }) } }
                    }
                }
                item { OutlinedTextField(startDate, { startDate = it }, label = { Text("Ημερομηνία έναρξης (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(deadline, { deadline = it }, label = { Text("Προθεσμία (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(nextStep, { nextStep = it }, label = { Text("Επόμενο βήμα") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Σημειώσεις") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, status, startDate.trim().ifBlank { null }, deadline.trim().ifBlank { null }, nextStep, notes) }, enabled = title.isNotBlank()) { Text("Αποθήκευση") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
private fun AttachDocumentDialog(documents: List<DocumentEntity>, onDismiss: () -> Unit, onAttach: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Σύνδεση εγγράφου") },
        text = {
            if (documents.isEmpty()) Text("Όλα τα έγγραφα είναι ήδη συνδεδεμένα ή δεν υπάρχουν διαθέσιμα έγγραφα.")
            else LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(documents, key = { it.id }) { document ->
                    TextButton(onClick = { onAttach(document.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(document.title, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Κλείσιμο") } }
    )
}

@Composable
private fun ChecklistDialog(
    documents: List<DocumentEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val selectedTitle = documents.firstOrNull { it.id == selectedDocumentId }?.title ?: "Χωρίς σύνδεση εγγράφου"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Νέο δικαιολογητικό") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Τι χρειάζεται;") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Χωρίς σύνδεση") }, onClick = { selectedDocumentId = null; menu = false })
                        documents.forEach { document -> DropdownMenuItem(text = { Text(document.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = { selectedDocumentId = document.id; menu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, selectedDocumentId) }) { Text("Προσθήκη") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
private fun DocumentEditDialog(document: DocumentEntity, viewModel: FolderViewModel, onDismiss: () -> Unit) {
    var title by remember(document.id, document.updatedAt) { mutableStateOf(document.title) }
    var tags by remember(document.id, document.updatedAt) { mutableStateOf(document.tags) }
    var provider by remember(document.id, document.updatedAt) { mutableStateOf(document.provider) }
    var issuedDate by remember(document.id, document.updatedAt) { mutableStateOf(document.issuedDate.orEmpty()) }
    var expiryDate by remember(document.id, document.updatedAt) { mutableStateOf(document.expiryDate.orEmpty()) }
    var protocolNumber by remember(document.id, document.updatedAt) { mutableStateOf(document.protocolNumber.orEmpty()) }
    var category by remember(document.id, document.updatedAt) { mutableStateOf(document.category) }
    var titleConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.titleManuallyEdited) }
    var categoryConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.categoryManuallyEdited) }
    var providerConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.providerManuallyEdited) }
    var issuedConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.issuedDateManuallyEdited) }
    var expiryConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.expiryDateManuallyEdited) }
    var protocolConfirmed by remember(document.id, document.updatedAt) { mutableStateOf(document.protocolNumberManuallyEdited) }
    var menu by remember { mutableStateOf(false) }
    val categories = listOf("Ταυτότητα / προσωπικά", "Μετανάστευση / άδειες", "Κατοικία", "Δημόσιες υπηρεσίες", "Εργασία", "Οικονομικά", "Λογαριασμοί", "Υγεία", "Συμβόλαια", "Άλλα")
    val expirySuggestion = document.expiryDateSuggestion
        ?: document.expiryDate?.takeUnless { MetadataConfidence.isConfirmed(it, document.expiryDateConfidence, document.expiryDateManuallyEdited) }
    val expirySuggestionConfidence = document.expiryDateSuggestionConfidence
        .takeUnless { it == MetadataConfidence.NONE }
        ?: document.expiryDateConfidence
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Διόρθωση στοιχείων") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Οι τιμές OCR είναι προτάσεις. Οι αλλαγές σου διατηρούνται σε επόμενη επεξεργασία.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                item {
                    MetadataTextField("Τίτλος", title, document.titleConfidence, titleConfirmed, { title = it; titleConfirmed = true }, { titleConfirmed = true })
                }
                item {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(category, modifier = Modifier.weight(1f)); Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { categories.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryConfirmed = true; menu = false }) } }
                    }
                    MetadataConfirmationRow("Κατηγορία", document.categoryConfidence, categoryConfirmed) { categoryConfirmed = true }
                }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Ετικέτες (με κόμμα)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    MetadataTextField("Φορέας", provider, document.providerConfidence, providerConfirmed, { provider = it; providerConfirmed = true }, { providerConfirmed = true })
                }
                item {
                    MetadataTextField("Αριθμός πρωτοκόλλου", protocolNumber, document.protocolNumberConfidence, protocolConfirmed, { protocolNumber = it; protocolConfirmed = true }, { protocolConfirmed = true })
                }
                item {
                    MetadataTextField("Ημερομηνία έκδοσης (YYYY-MM-DD)", issuedDate, document.issuedDateConfidence, issuedConfirmed, { issuedDate = it; issuedConfirmed = true }, { issuedConfirmed = true })
                }
                item {
                    MetadataTextField(
                        label = "Ημερομηνία λήξης (YYYY-MM-DD)",
                        value = expiryDate,
                        confidence = expirySuggestionConfidence,
                        confirmed = expiryConfirmed,
                        onValueChange = { expiryDate = it; expiryConfirmed = true },
                        onConfirm = { expiryConfirmed = true },
                        suggestion = expirySuggestion,
                        onUseSuggestion = { suggestion -> expiryDate = suggestion; expiryConfirmed = true }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateDocument(
                    document.id,
                    title,
                    category,
                    tags,
                    provider,
                    issuedDate.trim().ifBlank { null },
                    expiryDate.trim().ifBlank { null },
                    protocolNumber.trim().ifBlank { null },
                    MetadataFieldConfirmations(titleConfirmed, categoryConfirmed, providerConfirmed, issuedConfirmed, expiryConfirmed, protocolConfirmed)
                )
                onDismiss()
            }) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
private fun MetadataTextField(
    label: String,
    value: String,
    confidence: String,
    confirmed: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    suggestion: String? = null,
    onUseSuggestion: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        suggestion?.takeIf { value.isBlank() }?.let { candidate ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Πρόταση OCR: $candidate (${confidenceLabel(confidence)})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                onUseSuggestion?.let { use -> TextButton(onClick = { use(candidate) }) { Text("Χρήση") } }
            }
        }
        MetadataConfirmationRow(label, confidence, confirmed, onConfirm)
    }
}

@Composable
private fun MetadataConfirmationRow(label: String, confidence: String, confirmed: Boolean, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (confirmed) "$label: χειροκίνητα επιβεβαιωμένο" else "$label: πρόταση OCR (${confidenceLabel(confidence)})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        if (!confirmed) TextButton(onClick = onConfirm) { Text("Επιβεβαίωση") }
    }
}

@Composable
private fun EventDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) { var title by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Νέο γεγονός") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Τίτλος γεγονότος") }, singleLine = true); OutlinedTextField(note, { note = it }, label = { Text("Σημείωση") }, minLines = 2) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, note) }) { Text("Αποθήκευση") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }) }

@Composable
private fun PasswordDialog(title: String, minimumLength: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ο κωδικός δεν αποθηκεύεται. Χρησιμοποίησέ τον ξανά για επαναφορά. Ελάχιστο μήκος: $minimumLength χαρακτήρες.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            OutlinedTextField(
                password, { password = it }, label = { Text("Κωδικός") }, singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Προβολή κωδικού") } }
            )
            OutlinedTextField(
                confirmation, { confirmation = it }, label = { Text("Επιβεβαίωση κωδικού") }, singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
            )
        } },
        confirmButton = { TextButton(enabled = password.length >= minimumLength && password == confirmation, onClick = { onConfirm(password) }) { Text("Συνέχεια") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}
