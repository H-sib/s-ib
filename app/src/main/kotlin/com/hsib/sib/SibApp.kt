package com.hsib.sib

import android.app.Application
import com.hsib.sib.platform.MaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Objects

class SibApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, scope)
        container.start()
        MaintenanceWorker.schedule(this)
        scope.launch {
            val ready = container.awaitReady() ?: return@launch
            ready.notifier.createChannels()
            // Keep reminders, the ongoing summary, the tile and the widget in step with the data.
            ready.engine.state
                .filter { it.loaded }
                .map { st ->
                    Objects.hash(
                        st.items.map { Triple(it.o.id, it.o.updatedAt, it.urgency) },
                        st.settings, st.pending.size, st.plan.feasible, st.today,
                    )
                }
                .distinctUntilChanged()
                .debounce(600)
                .collect {
                    val st = ready.engine.state.value
                    // A repeating occasion whose day has passed moves on; the change triggers another round of this loop.
                    if (runCatching { ready.store.rollRepeats(st.today, st.zone) }.getOrDefault(0) > 0) return@collect
                    runCatching { ready.reminders.sync(st) }
                    runCatching { ready.surfaces.refresh(st) }
                }
        }
    }
}
