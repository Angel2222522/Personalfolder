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

@Composable
fun CreateCaseDialog(onDismiss: () -> Unit, onSave: (String, String, String?, String?, String, String) -> Unit) {
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
fun CaseEditDialog(
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
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { CaseStatus.all.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { status = option; menu = false }) } }
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
fun AttachDocumentDialog(documents: List<DocumentEntity>, onDismiss: () -> Unit, onAttach: (String) -> Unit) {
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
fun ChecklistDialog(
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
fun DocumentEditDialog(document: DocumentEntity, viewModel: FolderViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(document.title) }
    var tags by remember { mutableStateOf(document.tags) }
    var provider by remember { mutableStateOf(document.provider) }
    var issuedDate by remember { mutableStateOf(document.issuedDate.orEmpty()) }
    var expiryDate by remember { mutableStateOf(document.expiryDate.orEmpty()) }
    var protocolNumber by remember { mutableStateOf(document.protocolNumber.orEmpty()) }
    var category by remember { mutableStateOf(document.category) }
    var menu by remember { mutableStateOf(false) }
    val categories = listOf("Ταυτότητα / προσωπικά", "Μετανάστευση / άδειες", "Κατοικία", "Δημόσιες υπηρεσίες", "Εργασία", "Οικονομικά", "Λογαριασμοί", "Υγεία", "Συμβόλαια", "Άλλα")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Διόρθωση στοιχείων") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Οι τιμές OCR είναι προτάσεις. Οι αλλαγές σου διατηρούνται σε επόμενη επεξεργασία.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                item { OutlinedTextField(title, { title = it }, label = { Text("Τίτλος") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(category, modifier = Modifier.weight(1f)); Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { categories.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { category = option; menu = false }) } }
                    }
                }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Ετικέτες (με κόμμα)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(provider, { provider = it }, label = { Text("Φορέας") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(protocolNumber, { protocolNumber = it }, label = { Text("Αριθμός πρωτοκόλλου") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(issuedDate, { issuedDate = it }, label = { Text("Ημερομηνία έκδοσης (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(expiryDate, { expiryDate = it }, label = { Text("Ημερομηνία λήξης (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateDocument(document.id, title, category, tags, provider, issuedDate.trim().ifBlank { null }, expiryDate.trim().ifBlank { null }, protocolNumber.trim().ifBlank { null })
                onDismiss()
            }) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
fun EventDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) { var title by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Νέο γεγονός") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Τίτλος γεγονότος") }, singleLine = true); OutlinedTextField(note, { note = it }, label = { Text("Σημείωση") }, minLines = 2) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, note) }) { Text("Αποθήκευση") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }) }

@Composable
fun PasswordDialog(title: String, minimumLength: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
