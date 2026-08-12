package com.angel.personalfolder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angel.personalfolder.data.CaseEntity
import com.angel.personalfolder.data.ChecklistItemEntity
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderEntity
import com.angel.personalfolder.data.TimelineEventEntity

@Composable
fun QuickAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) { Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold) } } }

@Composable
fun <T> FilterMenuChip(label: String, selectedLabel: String, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selectedLabel != "Όλες", onClick = { expanded = true }, label = { Text("$label: $selectedLabel", maxLines = 1, overflow = TextOverflow.Ellipsis) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = { expanded = false; onSelected(value) }) }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
fun DocumentCard(document: DocumentEntity, onClick: () -> Unit, selected: Boolean = false, onToggleSelection: (() -> Unit)? = null) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(document.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); if (document.processingState == ProcessingState.PROCESSING) Text("Γίνεται επεξεργασία…", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }; document.expiryDate?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }; onToggleSelection?.let { toggle -> androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { toggle() }) } } } }

@Composable
fun CaseCard(caseEntity: CaseEntity, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(caseEntity.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(caseEntity.nextStep.ifBlank { caseEntity.description.ifBlank { "Χωρίς επόμενο βήμα" } }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; AssistChip(onClick = {}, label = { Text(caseEntity.status, fontSize = 11.sp) }) } } }

@Composable
fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(10.dp)); Column { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer); Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) } } } }

@Composable
fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(180.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable
fun StatusChip(state: String) { val label = when (state) { ProcessingState.PROCESSED -> "Έτοιμο"; ProcessingState.PROCESSING -> "Επεξεργασία"; ProcessingState.FAILED -> "Αποτυχία"; else -> "Σε αναμονή" }; AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(if (state == ProcessingState.PROCESSED) Icons.Default.Check else Icons.Default.MoreVert, null, Modifier.size(16.dp)) }) }

@Composable
fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(30.dp)) { Icon(icon, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .65f)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
fun LockedScreen() {
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
fun ReminderCard(reminder: ReminderEntity, onDone: () -> Unit) {
    val date = remember(reminder.dueAt) {
        Instant.ofEpochMilli(reminder.dueAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Προθεσμία: $date", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(onClick = onDone) { Icon(Icons.Default.Check, "Ολοκληρώθηκε") }
        }
    }
}

@Composable
fun LinearProcessing() { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }

@Composable
fun ChecklistRow(item: ChecklistItemEntity, linkedDocument: DocumentEntity?, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = item.isComplete, onCheckedChange = onChecked)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = if (item.isComplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            linkedDocument?.let { Text("Έγγραφο: ${it.title}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun TimelineRow(event: com.angel.personalfolder.data.TimelineEventEntity) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.height(4.dp)); Divider(Modifier.height(45.dp).width(1.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(event.title, fontWeight = FontWeight.SemiBold); Text(event.eventDate, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp); if (event.note.isNotBlank()) Text(event.note, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
