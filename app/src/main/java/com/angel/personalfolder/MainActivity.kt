package com.angel.personalfolder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.ui.FolderApp
import com.angel.personalfolder.ui.FolderViewModel
import com.angel.personalfolder.ui.PersonalFolderTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private val viewModel by lazy { androidx.lifecycle.ViewModelProvider(this)[FolderViewModel::class.java] }
    private val settings by lazy { getSharedPreferences("personal_folder_settings", MODE_PRIVATE) }
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null
    private var sessionUnlocked = false
    private var lockPromptVisible = false
    private var pendingBackupPassword: String? = null
    private val biometricExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        viewModel.importUris(uris)
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            viewModel.importUris(listOf(uri)) { file.delete() }
        }
        else file?.delete()
        cameraFile = null
        cameraUri = null
    }

    private val backupCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && password != null) viewModel.createBackup(uri, password)
    }

    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && password != null) viewModel.restoreBackup(uri, password)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(cacheDir, "share").deleteRecursively()
        handleIncomingIntent(intent)
        setContent {
            PersonalFolderTheme {
                FolderApp(
                    viewModel = viewModel,
                    onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                    onCamera = ::takePhoto,
                    onOpenDocument = ::openDocument,
                    onShareDocument = ::shareDocument,
                    onEnableLock = { authenticate { settings.edit().putBoolean(KEY_LOCK, true).apply() } },
                    onDisableLock = { authenticate { settings.edit().putBoolean(KEY_LOCK, false).apply() } },
                    onCreateBackup = { password -> pendingBackupPassword = password; backupCreator.launch("personal-folder-backup.pfb") },
                    onRestoreBackup = { password -> pendingBackupPassword = password; backupPicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                    onRequestNotifications = ::requestNotificationPermission,
                    lockEnabled = settings.getBoolean(KEY_LOCK, false)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (settings.getBoolean(KEY_LOCK, false) && !sessionUnlocked && !lockPromptVisible) authenticate { sessionUnlocked = true }
    }

    override fun onStop() {
        super.onStop()
        sessionUnlocked = false
    }

    private fun takePhoto() {
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
        val uri = incoming?.data ?: incoming?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (incoming?.action == Intent.ACTION_SEND && uri != null) viewModel.importUris(listOf(uri))
    }

    private fun openDocument(documentId: String) {
        lifecycleScope.launch {
            val document = viewModel.getDocument(documentId) ?: return@launch
            val source = File(document.encryptedPath)
            val extension = document.originalFileName.substringAfterLast('.', "bin")
            val shareFile = File(cacheDir, "share/${document.id}.$extension").apply { parentFile?.mkdirs() }
            runCatching {
                FileCrypto.decryptToTemp(source, shareFile)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", shareFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, document.mimeType.ifBlank { "application/octet-stream" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.open_original)))
            }
        }
    }

    private fun shareDocument(documentId: String) {
        lifecycleScope.launch {
            val document = viewModel.getDocument(documentId) ?: return@launch
            val extension = document.originalFileName.substringAfterLast('.', "bin")
            val shareFile = File(cacheDir, "share/${document.id}.$extension").apply { parentFile?.mkdirs() }
            runCatching {
                FileCrypto.decryptToTemp(File(document.encryptedPath), shareFile)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", shareFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = document.mimeType.ifBlank { "application/octet-stream" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_document)))
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        if (lockPromptVisible) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
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

    companion object { private const val KEY_LOCK = "biometric_lock" }
}
