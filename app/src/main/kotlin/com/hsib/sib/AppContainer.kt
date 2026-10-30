package com.hsib.sib

import android.content.Context
import com.hsib.sib.core.backup.KeyHierarchy
import com.hsib.sib.core.extract.Extractor
import com.hsib.sib.core.extract.HmacFingerprinter
import com.hsib.sib.core.plan.RhythmTracker
import com.hsib.sib.data.BlobFiles
import com.hsib.sib.data.HistoryKeys
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.SibDatabase
import com.hsib.sib.data.Store
import com.hsib.sib.data.Vault
import com.hsib.sib.domain.Engine
import com.hsib.sib.platform.BackupService
import com.hsib.sib.platform.Importer
import com.hsib.sib.platform.NotificationReader
import com.hsib.sib.platform.Notifier
import com.hsib.sib.platform.ReminderSync
import com.hsib.sib.platform.Surfaces
import com.hsib.sib.social.SocialBuilder
import com.hsib.sib.social.SocialConfig
import com.hsib.sib.social.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/** Everything that needs the unlocked vault. Only exists once the vault has been opened. */
class Ready(val context: Context, val db: SibDatabase, master: ByteArray, scope: CoroutineScope, socialBuilder: SocialBuilder? = null) {
    val settings = SettingsStore(db.settings())
    val keys = HistoryKeys(settings)
    val blobs = BlobFiles(File(context.filesDir, "receipts"), KeyHierarchy.blobKey(master))
    val store = Store(db, keys, blobs)
    val engine = Engine(store, settings, scope)
    val extractor = Extractor(fingerprinter = HmacFingerprinter(KeyHierarchy.fingerprintKey(master)))
    val importer = Importer(context, store, settings, extractor)
    val notifier = Notifier(context)
    val reminders = ReminderSync(context, db, store, settings, engine, notifier)
    val reader = NotificationReader(context, settings, importer, notifier)
    val surfaces = Surfaces(context, notifier)
    val backup = BackupService(context, store, settings, keys)

    /** Sharing and chat. Null when this build does not offer them. */
    val social: SocialRepository? = socialBuilder?.build(db, settings, store, scope)

    suspend fun init() {
        keys.load()
        engine.refreshCalibration()
    }

    suspend fun recordAppOpen(hour: Int) {
        val tracker = RhythmTracker.parse(settings.raw("rhythm"))
        tracker.record(hour)
        settings.putRaw("rhythm", tracker.serialize())
    }

    suspend fun digestHour(): Int {
        val s = settings.current()
        return if (s.learnDigestTime) RhythmTracker.parse(settings.raw("rhythm")).bestHour(s.digestHour) else s.digestHour
    }
}

sealed interface Phase {
    data object Loading : Phase
    class Running(val ready: Ready) : Phase
    data object VaultLost : Phase
}

class AppContainer(private val app: Context, private val scope: CoroutineScope) {
    private val vault = Vault(app)
    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase
    val clock: Clock = Clock.systemDefaultZone()

    fun start() {
        scope.launch(Dispatchers.IO) {
            when (val s = vault.open()) {
                is Vault.State.Ready -> {
                    val ready = Ready(app, SibDatabase.open(app, s.master), s.master, scope, SocialConfig.builder(app))
                    ready.init()
                    _phase.value = Phase.Running(ready)
                }
                Vault.State.Lost -> _phase.value = Phase.VaultLost
            }
        }
    }

    /** Receivers and workers wait for the vault here. Returns null when it is lost. */
    suspend fun awaitReady(): Ready? {
        val p = _phase.first { it !is Phase.Loading }
        return (p as? Phase.Running)?.ready
    }

    /** The user chose to start over after the vault could not be opened. */
    suspend fun resetVault() = withContext(Dispatchers.IO) {
        vault.reset()
        _phase.value = Phase.Loading
        start()
    }
}
