package com.hsib.sib

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.platform.Notifier
import com.hsib.sib.ui.ImportActions
import com.hsib.sib.ui.SibRoot
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val auth by lazy { PhoneAuthenticator(this) }
    private var route by mutableStateOf<String?>(null)

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris, SourceType.SHARE_IMAGE)
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }

    private val scan = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isNotEmpty()) vm.importUris(pages, SourceType.SCAN)
        }
    }

    private val importActions = ImportActions(
        paste = ::pasteFromClipboard,
        photo = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        scan = ::startScan,
        file = { pickFile.launch(arrayOf("*/*")) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        route = routeOf(intent)
        setContent {
            SibRoot(
                vm, importActions, auth, route,
                onRouteHandled = { route = null },
                onSecureChange = ::setSecure,
            )
        }
        // Remember which hours the app is opened in, so the daily summary can learn a time that suits the user.
        lifecycleScope.launch { vm.container.awaitReady()?.recordAppOpen(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route = routeOf(intent)
    }

    override fun onStop() {
        vm.lock.onLeft()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        vm.lock.onReturned()
    }

    /** Reads the requested screen from an intent that may come from any app; malformed or unknown values are ignored. */
    private fun routeOf(intent: Intent?): String? = Notifier.safeRoute(runCatching { intent?.getStringExtra(Notifier.EXTRA_ROUTE) }.getOrNull())

    /** Hides the app from screenshots and the recents list when the user asked for that. */
    private fun setSecure(secure: Boolean) {
        val flagged = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (secure && !flagged) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!secure && flagged) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun pasteFromClipboard() {
        val clip: ClipData? = getSystemService<ClipboardManager>()?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing is copied yet.", Toast.LENGTH_SHORT).show()
        } else {
            vm.importText(text.take(MAX_PASTE), SourceType.PASTE, "Pasted text")
        }
    }

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(MAX_PHOTOS)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender -> scan.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { Toast.makeText(this, "Scanning is not available on this phone. Try Photo instead.", Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val MAX_PHOTOS = 5
        const val MAX_PASTE = 200_000
    }
}
