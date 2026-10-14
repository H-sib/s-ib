package com.hsib.sib

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hsib.sib.core.extract.Extraction
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.domain.AppState
import com.hsib.sib.platform.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the import dialog shows. The text of what was read lives only here, in memory, until the dialog is dismissed. */
sealed interface ImportUi {
    data object Idle : ImportUi
    data class Working(val label: String) : ImportUi
    data class Done(val result: ImportResult) : ImportUi
}

/**
 * Whether the app lock is closed. It starts closed on every cold start and closes again after a short time away,
 * so glancing at a notification and coming straight back does not ask twice.
 */
class LockState {
    var locked by mutableStateOf(true)
    private var leftAt = 0L

    fun onLeft() { leftAt = SystemClock.elapsedRealtime() }

    fun onReturned() {
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > GRACE_MS) locked = true
    }

    private companion object { const val GRACE_MS = 30_000L }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as SibApp).container
    val lock = LockState()
    val phase: StateFlow<Phase> = container.phase

    private val readyFlow: StateFlow<Ready?> =
        phase.map { (it as? Phase.Running)?.ready }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ready: Ready? get() = readyFlow.value

    val state: StateFlow<AppState> = readyFlow
        .flatMapLatest { r -> r?.engine?.state ?: flowOf(AppState.empty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.empty())

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val importUi: StateFlow<ImportUi> = _import

    /** Runs work against the unlocked vault off the main thread. */
    fun run(block: suspend (Ready) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = container.awaitReady() ?: return@launch
            block(r)
        }
    }

    fun importUri(uri: Uri, mime: String? = null) {
        _import.value = ImportUi.Working("Reading")
        viewModelScope.launch(Dispatchers.IO) {
            val r = container.awaitReady() ?: return@launch
            val result = r.importer.fromUri(uri, mime)
            r.countImport(result)
            _import.value = ImportUi.Done(result)
        }
    }

    fun importUris(uris: List<Uri>, source: SourceType) {
        _import.value = ImportUi.Working("Reading")
        viewModelScope.launch(Dispatchers.IO) {
            val r = container.awaitReady() ?: return@launch
            val result = r.importer.fromImages(uris, source)
            r.countImport(result)
            _import.value = ImportUi.Done(result)
        }
    }

    fun importText(text: String, source: SourceType, label: String) {
        _import.value = ImportUi.Working("Reading")
        viewModelScope.launch(Dispatchers.IO) {
            val r = container.awaitReady() ?: return@launch
            val result = r.importer.fromText(text, source, label)
            r.countImport(result)
            _import.value = ImportUi.Done(result)
        }
    }

    fun dismissImport() { _import.value = ImportUi.Idle }

    /** The onboarding demo: runs the real extractor on made-up text and saves nothing. */
    suspend fun demo(text: String): Extraction? = ready?.let { r ->
        r.extractor.extract(text, r.importer.temporalContext(r.settings.current()), SourceType.DEMO)
    }

    fun resetVault() { viewModelScope.launch { container.resetVault() } }
}

suspend fun Ready.countImport(result: ImportResult) {
    if (result.error != null) return
    settings.putRaw("stat.imports", ((settings.raw("stat.imports")?.toLongOrNull() ?: 0) + 1).toString())
    if (result.messagesRead > 0) settings.putRaw("stat.messages", ((settings.raw("stat.messages")?.toLongOrNull() ?: 0) + result.messagesRead).toString())
}
