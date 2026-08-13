package com.angel.personalfolder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.angel.personalfolder.data.ExportService
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.processing.ScannerImageProcessor
import com.angel.personalfolder.security.PendingActivityStateStore
import com.angel.personalfolder.ui.FolderApp
import com.angel.personalfolder.ui.FolderViewModel
import com.angel.personalfolder.ui.PersonalFolderTheme
import com.angel.personalfolder.ui.ScreenshotPrivacyShell
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private val viewModel by lazy { androidx.lifecycle.ViewModelProvider(this)[FolderViewModel::class.java] }
    private val settings by lazy { getSharedPreferences("personal_folder_settings", MODE_PRIVATE) }
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null
    private var sessionUnlocked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var screenshotsAllowed by mutableStateOf(false)
    private var lockPromptVisible = false
    private var pendingBackupPassword: String? = null
    private var pendingExportDocumentIds: List<String> = emptyList()
    private var pendingPdfDocumentIds: List<String> = emptyList()
    private var scannerFiles by mutableStateOf<List<File>>(emptyList())
    private var scannerOpen by mutableStateOf(false)
    private var lastIncomingIntentKey: String? = null
    private var pendingIncomingUris: List<Uri> = emptyList()
    private val biometricExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }
    private val exportService by lazy { ExportService(this) }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        viewModel.importUris(uris) {
            uris.forEach { uri -> runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }
    }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null) {
            scannerFiles = scannerFiles + file
            scannerOpen = true
        }
        else file?.delete()
        cameraFile = null
        cameraUri = null
    }

    private val backupCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword ?: PendingActivityStateStore.consumePassword(this)
        PendingActivityStateStore.clear(this)
        pendingBackupPassword = null
        if (uri != null && password != null) viewModel.createBackup(uri, password)
    }

    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword ?: PendingActivityStateStore.consumePassword(this)
        PendingActivityStateStore.clear(this)
        pendingBackupPassword = null
        if (uri != null && password != null) viewModel.restoreBackup(uri, password)
    }

    private val exportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val documentIds = pendingExportDocumentIds.ifEmpty { PendingActivityStateStore.consumeList(this, STATE_EXPORT_IDS) }
        PendingActivityStateStore.clearList(this, STATE_EXPORT_IDS)
        pendingExportDocumentIds = emptyList()
        if (uri != null && documentIds.isNotEmpty()) viewModel.exportDocuments(uri, documentIds)
    }

    private val pdfExportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val documentIds = pendingPdfDocumentIds.ifEmpty { PendingActivityStateStore.consumeList(this, STATE_PDF_IDS) }
        PendingActivityStateStore.clearList(this, STATE_PDF_IDS)
        pendingPdfDocumentIds = emptyList()
        if (uri != null && documentIds.isNotEmpty()) viewModel.exportPdf(uri, documentIds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restorePendingState(savedInstanceState)
        if (savedInstanceState == null) {
            pendingExportDocumentIds = PendingActivityStateStore.peekList(this, STATE_EXPORT_IDS)
            pendingPdfDocumentIds = PendingActivityStateStore.peekList(this, STATE_PDF_IDS)
            pendingIncomingUris = PendingActivityStateStore.peekList(this, STATE_INCOMING_URIS).map(Uri::parse)
        }
        lockEnabled = settings.getBoolean(KEY_LOCK, false)
        screenshotsAllowed = settings.getBoolean(KEY_SCREENSHOTS_ALLOWED, false)
        applyScreenCapturePolicy()
        if (lockEnabled && !canAuthenticate()) {
            // An already-enabled lock must fail closed. Do not silently weaken the
            // persisted policy when a credential is temporarily unavailable.
            Toast.makeText(this, "Το κλείδωμα παραμένει ενεργό, αλλά δεν υπάρχει διαθέσιμη ασφαλής ταυτοποίηση στη συσκευή.", Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            PersonalFolderTheme {
                ScreenshotPrivacyShell(
                    screenshotsAllowed = screenshotsAllowed,
                    locked = lockEnabled && !sessionUnlocked,
                    onScreenshotsAllowedChange = ::setScreenshotsAllowed
                ) {
                    FolderApp(
                        viewModel = viewModel,
                        onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                        onCamera = ::takePhoto,
                        onOpenDocument = ::openDocument,
                        onShareDocument = ::shareDocument,
                        onEnableLock = { authenticate(
                            onSuccess = {
                                settings.edit().putBoolean(KEY_LOCK, true).apply()
                                lockEnabled = true
                                sessionUnlocked = true
                                applyScreenCapturePolicy()
                            },
                            onFailure = ::showAuthMessage
                        ) },
                        onDisableLock = { authenticate(
                            onSuccess = {
                                settings.edit().putBoolean(KEY_LOCK, false).apply()
                                lockEnabled = false
                                sessionUnlocked = true
                                applyScreenCapturePolicy()
                            },
                            onFailure = ::showAuthMessage
                        ) },
                        onCreateBackup = { password -> pendingBackupPassword = password; PendingActivityStateStore.savePassword(this, password); backupCreator.launch("personal-folder-backup.pfb") },
                        onRestoreBackup = { password -> pendingBackupPassword = password; PendingActivityStateStore.savePassword(this, password); backupPicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                        onRequestNotifications = ::requestNotificationPermission,
                        onExportDocuments = { documentIds -> pendingExportDocumentIds = documentIds; PendingActivityStateStore.saveList(this, STATE_EXPORT_IDS, documentIds); exportCreator.launch("personal-folder-export.zip") },
                        onExportPdf = { documentIds -> pendingPdfDocumentIds = documentIds; PendingActivityStateStore.saveList(this, STATE_PDF_IDS, documentIds); pdfExportCreator.launch("personal-folder-export.pdf") },
                        scannerOpen = scannerOpen,
                        scannerPageUris = scannerFiles.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) },
                        onScannerAddPage = { launchCamera() },
                        onScannerRetryLast = ::retryLastScanPage,
                        onScannerFinish = ::finishScanner,
                        onScannerCancel = ::cancelScanner,
                        lockEnabled = lockEnabled,
                        locked = lockEnabled && !sessionUnlocked
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyScreenCapturePolicy()
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }
        if (lockEnabled && !canAuthenticate()) {
            sessionUnlocked = false
            applyScreenCapturePolicy()
            showAuthMessage("Το κλείδωμα παραμένει ενεργό. Ενεργοποίησε ξανά μια ασφαλή συσκευή ταυτοποίησης για να ξεκλειδώσεις.")
        } else if (lockEnabled && !sessionUnlocked && !lockPromptVisible) {
            authenticate(
                onSuccess = {
                    sessionUnlocked = true
                    applyScreenCapturePolicy()
                    flushPendingIncoming()
                },
                onFailure = ::showAuthMessage
            )
        }
    }

    override fun onStop() {
        // Always protect the task preview/background frame, even when the user
        // allows screenshots while actively using the unlocked application.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        sessionUnlocked = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(KEY_PENDING_EXPORT_IDS, ArrayList(pendingExportDocumentIds))
        outState.putStringArrayList(KEY_PENDING_PDF_IDS, ArrayList(pendingPdfDocumentIds))
        outState.putParcelableArrayList(KEY_PENDING_INCOMING_URIS, ArrayList(pendingIncomingUris))
        outState.putString(KEY_LAST_INCOMING_KEY, lastIncomingIntentKey)
        outState.putStringArrayList(KEY_SCANNER_FILES, ArrayList(scannerFiles.map(File::getAbsolutePath)))
        outState.putBoolean(KEY_SCANNER_OPEN, scannerOpen)
        super.onSaveInstanceState(outState)
    }

    private fun setScreenshotsAllowed(allowed: Boolean) {
        settings.edit().putBoolean(KEY_SCREENSHOTS_ALLOWED, allowed).apply()
        screenshotsAllowed = allowed
        applyScreenCapturePolicy()
    }

    private fun applyScreenCapturePolicy() {
        val mustRemainSecure = !screenshotsAllowed || (lockEnabled && !sessionUnlocked)
        if (mustRemainSecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun takePhoto() {
        scannerOpen = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun launchCamera() {
        val file = File(cacheDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = file
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraCapture.launch(cameraUri!!)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val uris = when (incoming?.action) {
            Intent.ACTION_SEND -> listOfNotNull(incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: incoming.data)
            Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }.distinct()
        if (uris.size > MAX_INCOMING_URIS) {
            showAuthMessage("Η εισαγωγή από share sheet περιορίζεται σε $MAX_INCOMING_URIS αρχεία ανά αποστολή.")
            return
        }
        val key = incoming?.action.orEmpty() + ":" + uris.joinToString("|")
        if (uris.isNotEmpty() && key != lastIncomingIntentKey) {
            lastIncomingIntentKey = key
            if (lockEnabled && !sessionUnlocked) {
                pendingIncomingUris = (pendingIncomingUris + uris).distinct().take(MAX_PENDING_INCOMING_URIS)
                PendingActivityStateStore.saveList(this, STATE_INCOMING_URIS, pendingIncomingUris.map(Uri::toString))
                if (pendingIncomingUris.size >= MAX_PENDING_INCOMING_URIS) showAuthMessage("Υπάρχουν ήδη πολλές εισαγωγές σε αναμονή για ξεκλείδωμα.")
            }
            else viewModel.importUris(uris)
        }
    }

    private fun flushPendingIncoming() {
        val queued = pendingIncomingUris.ifEmpty { PendingActivityStateStore.consumeList(this, STATE_INCOMING_URIS).map(Uri::parse) }
        PendingActivityStateStore.clearList(this, STATE_INCOMING_URIS)
        pendingIncomingUris = emptyList()
        if (queued.isNotEmpty()) viewModel.importUris(queued)
    }

    private fun openDocument(documentId: String) {
        lifecycleScope.launch {
            var shareFile: File? = null
            runCatching {
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά." }
                val document = viewModel.getDocument(documentId) ?: error("Το έγγραφο δεν βρέθηκε.")
                val file = exportService.createSharePdf(document.id)
                shareFile = file
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε πριν ολοκληρωθεί το άνοιγμα." }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.open_original)))
                shareFile = null
            }.onFailure { showAuthMessage(it.message ?: "Δεν ήταν δυνατό το άνοιγμα του εγγράφου.") }
            shareFile?.delete()
        }
    }

    private fun shareDocument(documentId: String) {
        lifecycleScope.launch {
            var shareFile: File? = null
            runCatching {
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά." }
                val document = viewModel.getDocument(documentId) ?: error("Το έγγραφο δεν βρέθηκε.")
                val file = exportService.createSharePdf(document.id)
                shareFile = file
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε πριν ολοκληρωθεί η κοινοποίηση." }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_document)))
                shareFile = null
            }.onFailure { showAuthMessage(it.message ?: "Δεν ήταν δυνατή η κοινοποίηση.") }
            shareFile?.delete()
        }
    }

    private fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (lockPromptVisible) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure("Δεν υπάρχει διαθέσιμη ασφαλής ταυτοποίηση στη συσκευή. Το κλείδωμα δεν ενεργοποιήθηκε.")
            return
        }
        lockPromptVisible = true
        val prompt = BiometricPrompt(this, biometricExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lockPromptVisible = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lockPromptVisible = false
                onFailure(errString.toString())
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.biometric_lock))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun canAuthenticate(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showAuthMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun retryLastScanPage() {
        scannerFiles.lastOrNull()?.delete()
        scannerFiles = scannerFiles.dropLast(1)
        launchCamera()
    }

    private fun cancelScanner() {
        scannerFiles.forEach(File::delete)
        scannerFiles = emptyList()
        scannerOpen = false
    }

    private fun finishScanner() {
        val rawFiles = scannerFiles.toList()
        if (rawFiles.isEmpty()) {
            scannerOpen = false
            return
        }
        scannerOpen = false
        lifecycleScope.launch(Dispatchers.IO) {
            val processed = mutableListOf<File>()
            try {
                rawFiles.forEachIndexed { index, raw ->
                    val output = File(cacheDir, "scanner/processed_${System.nanoTime()}_$index.jpg")
                    processed += ScannerImageProcessor.enhance(raw, output)
                }
                val uris = processed.map { FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", it) }
                withContext(Dispatchers.Main) {
                    viewModel.importUris(uris) {
                        rawFiles.forEach(File::delete)
                        processed.forEach(File::delete)
                        scannerFiles = emptyList()
                    }
                }
            } catch (error: Throwable) {
                rawFiles.forEach(File::delete)
                processed.forEach(File::delete)
                withContext(Dispatchers.Main) { showAuthMessage(error.message ?: "Δεν ήταν δυνατή η επεξεργασία της σάρωσης."); scannerFiles = emptyList() }
            }
        }
    }

    companion object {
        private const val KEY_LOCK = "biometric_lock"
        private const val KEY_SCREENSHOTS_ALLOWED = "screenshots_allowed"
        private const val MAX_INCOMING_URIS = 100
        private const val MAX_PENDING_INCOMING_URIS = 100
        private const val KEY_PENDING_EXPORT_IDS = "pending_export_ids"
        private const val KEY_PENDING_PDF_IDS = "pending_pdf_ids"
        private const val KEY_PENDING_INCOMING_URIS = "pending_incoming_uris"
        private const val KEY_LAST_INCOMING_KEY = "last_incoming_key"
        private const val KEY_SCANNER_FILES = "scanner_files"
        private const val KEY_SCANNER_OPEN = "scanner_open"
        private const val STATE_EXPORT_IDS = "export_ids"
        private const val STATE_PDF_IDS = "pdf_ids"
        private const val STATE_INCOMING_URIS = "incoming_uris"
    }

    private fun restorePendingState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        pendingExportDocumentIds = savedInstanceState.getStringArrayList(KEY_PENDING_EXPORT_IDS).orEmpty()
        pendingPdfDocumentIds = savedInstanceState.getStringArrayList(KEY_PENDING_PDF_IDS).orEmpty()
        pendingIncomingUris = savedInstanceState.getParcelableArrayList<Uri>(KEY_PENDING_INCOMING_URIS).orEmpty()
        lastIncomingIntentKey = savedInstanceState.getString(KEY_LAST_INCOMING_KEY)
        scannerFiles = savedInstanceState.getStringArrayList(KEY_SCANNER_FILES).orEmpty().map(::File)
        scannerOpen = savedInstanceState.getBoolean(KEY_SCANNER_OPEN, false)
    }
}
