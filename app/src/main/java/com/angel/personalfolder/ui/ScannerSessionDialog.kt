package com.angel.personalfolder.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScannerSessionDialog(
    pageUris: List<Uri>,
    onAddPage: () -> Unit,
    onRetryLast: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Σάρωση εγγράφου") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Οι σελίδες θα ενωθούν σε ένα έγγραφο. Προσανατολισμός, βασικό αυτόματο περιθώριο και βελτίωση αντίθεσης εφαρμόζονται πριν την αποθήκευση.")
                if (pageUris.isEmpty()) {
                    Text("Δεν έχεις φωτογραφίσει ακόμη σελίδα.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(pageUris) { index, uri ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScannerThumbnail(uri)
                                Text("Σελίδα ${index + 1}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCancel) { Text("Ακύρωση") }
                Button(onClick = onFinish, enabled = pageUris.isNotEmpty()) { Text("Ολοκλήρωση") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onRetryLast, enabled = pageUris.isNotEmpty()) { Text("Επανάληψη") }
                OutlinedButton(onClick = onAddPage) { Text("+ Σελίδα") }
            }
        }
    )
}

@Composable
private fun ScannerThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = calculateSample(bounds.outWidth, bounds.outHeight)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(72.dp)) }
}

private fun calculateSample(width: Int, height: Int): Int {
    var sample = 1
    while (kotlin.math.max(width / sample, height / sample) > 320) sample *= 2
    return sample
}
