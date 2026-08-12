package com.angel.personalfolder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.DocumentRenderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentViewerScreen(document: DocumentEntity, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val service = remember(context) { DocumentRenderService(context) }
    var pages by remember(document.id, document.updatedAt) { mutableStateOf(emptyList<com.angel.personalfolder.data.LogicalDocumentPage>()) }
    var pageIndex by remember(document.id, document.updatedAt) { mutableStateOf(0) }
    var bitmap by remember(document.id, document.updatedAt) { mutableStateOf<Bitmap?>(null) }
    var bitmapPageIndex by remember(document.id, document.updatedAt) { mutableStateOf<Int?>(null) }
    var error by remember(document.id, document.updatedAt) { mutableStateOf<String?>(null) }

    LaunchedEffect(document.id, document.updatedAt) {
        runCatching { withContext(Dispatchers.IO) { service.logicalPages(document) } }
            .onSuccess { pages = it; pageIndex = 0; error = null }
            .onFailure { error = it.message ?: "Δεν ήταν δυνατή η ανάγνωση του εγγράφου." }
    }
    LaunchedEffect(document.id, pageIndex, pages) {
        val requestedPageIndex = pageIndex
        val page = pages.getOrNull(requestedPageIndex) ?: return@LaunchedEffect
        bitmap?.recycle()
        bitmap = null
        bitmapPageIndex = null
        var loaded: Bitmap? = null
        try {
            loaded = withContext(Dispatchers.IO) { service.renderPage(document, page) }
            if (pageIndex == requestedPageIndex) {
                bitmap = loaded
                bitmapPageIndex = requestedPageIndex
                loaded = null
            }
        } catch (failure: Throwable) {
            error = failure.message ?: "Δεν ήταν δυνατή η εμφάνιση της σελίδας."
        } finally {
            loaded?.recycle()
        }
    }
    val currentBitmap by rememberUpdatedState(bitmap)
    DisposableEffect(document.id, document.updatedAt) { onDispose { currentBitmap?.recycle() } }

    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offsetX += pan.x
        offsetY += pan.y
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω") }
            Text(document.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            if (pages.isNotEmpty()) Text("${pageIndex + 1}/${pages.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClose) { Text("Κλείσιμο") }
                }
                pages.isEmpty() -> CircularProgressIndicator()
                bitmap == null || bitmapPageIndex != pageIndex -> CircularProgressIndicator()
                else -> Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = "Σελίδα ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                        .transformable(transformState)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0); scale = 1f; offsetX = 0f; offsetY = 0f }, enabled = pageIndex > 0) { Icon(Icons.Default.ChevronLeft, "Προηγούμενη") }
            Text("Μεγέθυνση με δύο δάχτυλα", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pages.lastIndex); scale = 1f; offsetX = 0f; offsetY = 0f }, enabled = pageIndex < pages.lastIndex) { Icon(Icons.Default.ChevronRight, "Επόμενη") }
        }
    }
}
