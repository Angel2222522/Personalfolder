package com.angel.personalfolder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Keeps the screenshot permission discoverable without weakening the default
 * privacy posture. The activity still enforces FLAG_SECURE while the app is
 * locked or backgrounded, even when the user has allowed screenshots.
 */
@Composable
fun ScreenshotPrivacyShell(
    screenshotsAllowed: Boolean,
    locked: Boolean,
    onScreenshotsAllowedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var dialogVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        content()

        if (!locked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 2.dp, end = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                IconButton(onClick = { dialogVisible = true }) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Ρύθμιση στιγμιοτύπων οθόνης",
                        tint = if (screenshotsAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (dialogVisible && !locked) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("Στιγμιότυπα οθόνης") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Να επιτρέπονται στιγμιότυπα οθόνης", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (screenshotsAllowed) "Ενεργό" else "Απενεργοποιημένο",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = screenshotsAllowed,
                            onCheckedChange = onScreenshotsAllowedChange
                        )
                    }
                    Text(
                        "Όταν είναι ενεργό, μπορείς να τραβάς στιγμιότυπα και να καταγράφεις την οθόνη όσο ο φάκελος είναι ξεκλείδωτος. Η προστασία ενεργοποιείται ξανά αυτόματα όταν η εφαρμογή κλειδώνει ή πηγαίνει στο παρασκήνιο.",
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }) { Text("Κλείσιμο") }
            }
        )
    }
}
