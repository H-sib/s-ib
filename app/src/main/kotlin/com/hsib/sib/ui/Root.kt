package com.hsib.sib.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hsib.sib.Authenticator
import com.hsib.sib.BuildConfig
import com.hsib.sib.ImportUi
import com.hsib.sib.MainViewModel
import com.hsib.sib.Phase
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.ThemeMode
import com.hsib.sib.domain.AppState
import com.hsib.sib.social.SocialSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val MOTION_MS = 220
private const val UI_PREFS = "ui"

private fun cachedTheme(context: Context): ThemeMode =
    runCatching { ThemeMode.valueOf(context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)

@Composable
private fun Themed(mode: ThemeMode, space: Space, reduceMotion: Boolean, content: @Composable () -> Unit) {
    SibTheme(mode, space) {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            SystemBars()
            content()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps the status and navigation bar icons readable against the theme the user chose, not just the phone's. */
@Composable
private fun SystemBars() {
    val dark = SibTheme.colors.dark
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** How long the opening plays, from what the phone remembered: long the first time, short after, or as chosen. 0 skips it. */
private fun introDuration(prefs: android.content.SharedPreferences): Int {
    val speed = runCatching { com.hsib.sib.data.IntroSpeed.valueOf(prefs.getString("introSpeed", "AUTO").orEmpty()) }.getOrDefault(com.hsib.sib.data.IntroSpeed.AUTO)
    return when (speed) {
        com.hsib.sib.data.IntroSpeed.OFF -> 0
        com.hsib.sib.data.IntroSpeed.LONG -> 4200
        com.hsib.sib.data.IntroSpeed.SHORT -> 1600
        com.hsib.sib.data.IntroSpeed.AUTO -> if (prefs.getBoolean("introSeen", false)) 1600 else 4200
    }
}

@Composable
fun SibRoot(
    vm: MainViewModel,
    importActions: ImportActions,
    auth: Authenticator,
    route: String?,
    onRouteHandled: () -> Unit,
    onSecureChange: (Boolean) -> Unit,
) {
    val phase by vm.phase.collectAsState()
    val st by vm.state.collectAsState()
    val running = phase as? Phase.Running
    val settingsKnown = running != null && st.loaded
    // The chosen theme is remembered in plain preferences (it is not sensitive) so the intro already wears it.
    val context = LocalContext.current
    val cachedTheme = remember { cachedTheme(context) }
    val mode = if (settingsKnown) st.settings.theme else cachedTheme
    LaunchedEffect(settingsKnown, st.settings.theme) {
        if (settingsKnown) context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit().putString("theme", st.settings.theme.name).putString("introSpeed", st.settings.introSpeed.name).apply()
    }

    SideEffect { onSecureChange(settingsKnown && st.settings.hideInRecents) }

    // The intro plays on a cold start. Opening from a notification goes straight to the item, and a tap skips it.
    val prefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val introMs = remember { introDuration(prefs) }
    var introDone by rememberSaveable { mutableStateOf(route != null || introMs == 0) }

    Themed(mode, Space.WORK, settingsKnown && st.settings.reduceMotion) {
        val motion = rememberMotionEnabled()
        Crossfade(introDone, animationSpec = tween(if (motion) 520 else 0), label = "intro") { done ->
            when {
                !done -> IntroScreen(introMs) { prefs.edit().putBoolean("introSeen", true).apply(); introDone = true }
                phase is Phase.VaultLost -> VaultLostScreen(vm::resetVault)
                running == null || !st.loaded -> Splash()
                else -> Running(running.ready, st, vm, importActions, auth, route, onRouteHandled)
            }
        }
    }
}

// ---- the quiet screens -----------------------------------------------------------------------

@Composable
private fun Splash() {
    val c = SibTheme.colors
    Box(Modifier.fillMaxSize().background(c.paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppName()
            Text("Start It Before", style = SibType.small, color = c.inkSoft)
        }
    }
}

@Composable
private fun VaultLostScreen(onReset: () -> Unit) {
    val c = SibTheme.colors
    var confirm by remember { mutableStateOf(false) }
    Page("Storage could not be opened", null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "H-sib Reminder keeps your deadlines in storage that is locked with a key held by your phone. That key is gone, which can happen after a phone restore, a reset, or a change to the phone's security.",
            style = SibType.body, color = c.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "The data on this phone cannot be opened again, and nobody can recover it, including the developer. If you made an encrypted backup, start fresh and choose \"I have a backup\" on the first screen.",
            style = SibType.body, color = c.inkSoft,
        )
        Spacer(Modifier.height(20.dp))
        SibButton("Start fresh", { confirm = true }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = c.raised,
            title = { Text("Start fresh?", style = SibType.title, color = c.ink) },
            text = { Text("The unreadable data on this phone is erased and H-sib Reminder starts empty.", style = SibType.body, color = c.inkSoft) },
            confirmButton = { TextButton({ confirm = false; onReset() }) { Text("Start fresh", style = SibType.bodyStrong, color = c.vermilion) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel", style = SibType.body, color = c.ink) } },
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = SibTheme.colors
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(c.paper).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AppName()
        Text("Locked", style = SibType.body, color = c.inkSoft)
        Spacer(Modifier.height(24.dp))
        SibButton("Unlock", onUnlock)
    }
}

// ---- running ---------------------------------------------------------------------------------

@Composable
private fun Running(
    ready: Ready,
    st: AppState,
    vm: MainViewModel,
    importActions: ImportActions,
    auth: Authenticator,
    route: String?,
    onRouteHandled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRestore by remember { mutableStateOf(false) }

    // Nothing is stored and nothing is asked until the terms have been read and accepted (again, if they change).
    if (st.settings.termsVersion < Terms.VERSION) {
        TermsScreen(
            online = BuildConfig.FIREBASE_READY, acceptedOn = null, onBack = null,
            onAccept = { scope.launch { ready.settings.putRaw("termsAcceptedOn", java.time.LocalDate.now().toString()); ready.settings.putRaw("termsAcceptedAt", java.time.Instant.now().toString() + " v" + Terms.VERSION); ready.settings.update { it.copy(termsVersion = Terms.VERSION) } } },
        )
        return
    }

    if (!st.settings.onboardingDone) {
        if (showRestore) {
            RestoreScreen(st, ready, onBack = { showRestore = false }, onRestored = { showRestore = false })
        } else {
            Onboarding(
                st.today, vm::demo, onRestore = { showRestore = true },
                onFinish = { scope.launch { ready.settings.update { it.copy(onboardingDone = true, lastSeenVersion = BuildConfig.VERSION_CODE) } } },
            )
        }
        return
    }

    // If the phone no longer has a screen lock, there is nothing to ask for, so the app must not trap its owner.
    if (st.settings.appLock && auth.available() && vm.lock.locked) {
        LockScreen { auth.prompt("Unlock H-sib Reminder") { ok -> if (ok) vm.lock.locked = false } }
        return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var askNotifications by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    val mode = st.settings.theme

    // Shared deadlines catch up when the app opens and then every ten minutes while it is open. Chat uses a lighter refresh.
    val socialState = rememberSocial(ready)
    LaunchedEffect(socialState.user?.uid) {
        val social = ready.social
        if (social != null && socialState.user != null) {
            // Also while the app is closed, a few times a day, so a changed date or a new message is waiting.
            if (!social.practice) SocialSyncWorker.schedule(context)
            while (true) {
                social.sync()
                delay(10 * 60_000L)
            }
        } else if (social != null) {
            SocialSyncWorker.cancel(context)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { ready.reminders.sync(vm.state.value) }
    }

    // The first time something real is saved is the right moment to explain why reminders need permission.
    val afterSave: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !st.settings.notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications = true
    }

    val toggleLock: (Boolean) -> Unit = { enable ->
        if (enable && !auth.available()) {
            Toast.makeText(context, "Set a screen lock, fingerprint or face on your phone first.", Toast.LENGTH_LONG).show()
        } else {
            auth.prompt(if (enable) "Turn on app lock" else "Turn off app lock") { ok ->
                if (ok) {
                    vm.lock.locked = false
                    scope.launch { ready.settings.update { it.copy(appLock = enable) } }
                }
            }
        }
    }

    LaunchedEffect(route) {
        val r = route ?: return@LaunchedEffect
        when {
            r == "review" -> { tab = Tab.REVIEW; nav.navigate("home") { popUpTo("home") } }
            r.startsWith("item/") || r.startsWith("close/item/") -> nav.navigate(r) { launchSingleTop = true }
        }
        onRouteHandled()
    }

    BackHandler(enabled = tab != Tab.TODAY && entry?.destination?.route == "home") { tab = Tab.TODAY }

    val back: () -> Unit = { nav.popBackStack() }
    val ms = if (reduce) 0 else MOTION_MS

    NavHost(
        nav, startDestination = "home",
        enterTransition = { fadeIn(tween(ms)) + slideInHorizontally(tween(ms)) { it / 12 } },
        exitTransition = { fadeOut(tween(ms)) },
        popEnterTransition = { fadeIn(tween(ms)) },
        popExitTransition = { fadeOut(tween(ms)) + slideOutHorizontally(tween(ms)) { it / 12 } },
    ) {
        composable("home") { HomeTabs(tab, { tab = it }, st, nav, ready, importActions, afterSave) }
        composable("all") { AllScreen(st, onOpen = { nav.navigate("item/$it") }, onBack = back, sharedIds = socialState.rooms.keys) }
        composable("item/{id}") { e -> DetailScreen(e.arguments?.getString("id").orEmpty(), st, ready, back, back, onPeople = { nav.navigate("room/$it") }) }
        composable("add") {
            AddScreen(st, ready, importActions, back, onJoin = if (ready.social != null) ({ nav.navigate("friends/join") }) else null, onSaved = { id ->
                afterSave()
                nav.popBackStack()
                nav.navigate("item/$id")
            })
        }

        composable("friends") {
            FriendsScreen(
                st, ready, back,
                onOpenRoom = { nav.navigate("room/$it") },
                onJoin = { nav.navigate("friends/join") },
                onAccount = { nav.navigate("friends/account") },
            )
        }
        composable("friends/account") { AccountScreen(ready, back, onSignedOut = { nav.popBackStack("home", inclusive = false) }) }
        composable("friends/join") { JoinScreen(ready, back, onJoined = { id -> nav.popBackStack(); nav.navigate("item/$id") }) }
        composable("room/{id}") { e ->
            val id = e.arguments?.getString("id").orEmpty()
            RoomScreen(id, st, ready, back, onChat = { nav.navigate("room/$id/chat") })
        }
        composable("room/{id}/chat") { e -> ChatScreen(e.arguments?.getString("id").orEmpty(), st, ready, back) }

        composable("settings/reminders") { RemindersSettings(st, ready, back) }
        composable("settings/notifications") { NotificationSettings(st, ready, back) }
        composable("settings/sources") { SourcesSettings(st, ready, back) }
        composable("settings/backup") { BackupScreen(st, ready, back, onRestore = { nav.navigate("restore") }) }
        composable("settings/privacy") { PrivacyScreen(st, ready, back, onWiped = { nav.popBackStack("home", inclusive = false) }) }
        composable("settings/security") { SecuritySettings(st, ready, back, toggleLock) }
        composable("settings/appearance") { AppearanceSettings(st, ready, back) }
        composable("settings/health") { HealthScreen(st, ready, back) }
        composable("settings/about") { AboutScreen(back, onTerms = { nav.navigate("settings/terms") }) }
        composable("settings/close") { CloseOnesSettings(st, ready, back) }
        composable("settings/reader") { ReaderScreen(st, ready, back) }
        composable("settings/feedback") { FeedbackScreen(back) }
        composable("settings/guide") { GuideScreen(online = ready.social?.practice == false, onBack = back) }
        composable("settings/terms") {
            val on by produceState<String?>(null) { value = ready.settings.raw("termsAcceptedOn") }
            TermsScreen(online = BuildConfig.FIREBASE_READY, acceptedOn = on, onAccept = null, onBack = back)
        }
        composable("restore") { RestoreScreen(st, ready, back, onRestored = back) }

        composable("close") {
            Themed(mode, Space.CLOSE, reduce) {
                CloseHomeScreen(
                    st, back,
                    onOpen = { nav.navigate("close/item/$it") },
                    onAdd = { template -> nav.navigate(if (template == null) "close/edit" else "close/edit?template=$template") },
                )
            }
        }
        composable("close/item/{id}") { e ->
            Themed(mode, Space.CLOSE, reduce) {
                CloseDetailScreen(e.arguments?.getString("id").orEmpty(), st, ready, back, onEdit = { nav.navigate("close/edit?id=$it") }, onDeleted = back)
            }
        }
        composable(
            "close/edit?id={id}&template={template}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("template") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { e ->
            val id = e.arguments?.getString("id")
            Themed(mode, Space.CLOSE, reduce) {
                CloseEditorScreen(id, e.arguments?.getString("template"), st, ready, back) { saved ->
                    if (id == null) {
                        afterSave()
                        nav.popBackStack()
                        nav.navigate("close/item/$saved")
                    } else {
                        nav.popBackStack()
                    }
                }
            }
        }
    }

    val importUi by vm.importUi.collectAsState()
    ImportDialog(importUi, onDismiss = vm::dismissImport) {
        vm.dismissImport()
        tab = Tab.REVIEW
        nav.popBackStack("home", inclusive = false)
    }

    if (askNotifications) {
        val c = SibTheme.colors
        val asked: () -> Unit = { askNotifications = false; scope.launch { ready.settings.update { it.copy(notificationPermissionAsked = true) } } }
        AlertDialog(
            onDismissRequest = asked,
            containerColor = c.raised,
            title = { Text("Allow reminders?", style = SibType.title, color = c.ink) },
            text = {
                Text(
                    "Reminders are the point of H-sib Reminder, and Android only lets an app show them if you allow notifications. They are made and shown on this phone, and nothing is sent anywhere.",
                    style = SibType.body, color = c.inkSoft,
                )
            },
            confirmButton = { TextButton({ asked(); notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow", style = SibType.bodyStrong, color = c.ink) } },
            dismissButton = { TextButton(asked) { Text("Not now", style = SibType.body, color = c.inkSoft) } },
        )
    }
}

@Composable
private fun HomeTabs(tab: Tab, onTab: (Tab) -> Unit, st: AppState, nav: NavController, ready: Ready, importActions: ImportActions, afterSave: () -> Unit) {
    val c = SibTheme.colors
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    // Null when this build has no sharing, so the entries for it simply do not appear.
    val social = if (ready.social != null) rememberSocial(ready) else null
    Column(Modifier.fillMaxSize().background(c.paper)) {
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Crossfade(tab, animationSpec = tween(if (reduce) 0 else 180), label = "tab") { t ->
                when (t) {
                    Tab.TODAY -> TodayScreen(
                        st, importActions,
                        onOpen = { nav.navigate("item/$it") },
                        onAdd = { nav.navigate("add") },
                        onReview = { onTab(Tab.REVIEW) },
                        onPlan = { onTab(Tab.PLAN) },
                        onAll = { nav.navigate("all") },
                        onLog = { id, minutes -> scope.launch { ready.store.addProgress(id, minutes) } },
                        onCloseOnes = { nav.navigate("close") },
                        social = social,
                        onFriends = { nav.navigate("friends") },
                        whatsNew = WhatsNew.unseen(st.settings.lastSeenVersion, BuildConfig.VERSION_CODE),
                        onWhatsNewSeen = { scope.launch { ready.settings.update { it.copy(lastSeenVersion = BuildConfig.VERSION_CODE) } } },
                        onPrompt = { p ->
                            scope.launch {
                                when (p) {
                                    Prompt.DASHBOARD_ON -> { ready.settings.update { it.copy(todayNotification = true, dashboardPromptDismissed = true) }; afterSave() }
                                    Prompt.DASHBOARD_LATER -> ready.settings.update { it.copy(dashboardPromptDismissed = true) }
                                    Prompt.READER_SETUP -> { ready.settings.update { it.copy(readerPromptDismissed = true) }; nav.navigate("settings/reader") }
                                    Prompt.READER_LATER -> ready.settings.update { it.copy(readerPromptDismissed = true) }
                                }
                            }
                        },
                    )
                    Tab.PLAN -> PlanScreen(st, onOpen = { nav.navigate("item/$it") }, onAdd = { nav.navigate("add") })
                    Tab.REVIEW -> ReviewScreen(
                        st,
                        onConfirm = { cand, edit -> scope.launch { ready.store.accept(cand.id, edit); afterSave() } },
                        onDismiss = { id -> scope.launch { ready.store.dismiss(id) } },
                        onSnooze = { id -> scope.launch { ready.store.snooze(id, Instant.now().plus(Duration.ofDays(3))) } },
                        onAdd = { nav.navigate("add") },
                    )
                    Tab.SETTINGS -> SettingsHome(st, social = social, onNav = { nav.navigate(it) })
                }
            }
        }
        Box(Modifier.background(c.paper).windowInsetsPadding(WindowInsets.navigationBars)) {
            SibBottomBar(tab, onTab, st.pending.size)
        }
    }
}

// ---- import results --------------------------------------------------------------------------

@Composable
private fun ImportDialog(ui: ImportUi, onDismiss: () -> Unit, onReview: () -> Unit) {
    val c = SibTheme.colors
    when (ui) {
        ImportUi.Idle -> Unit
        is ImportUi.Working -> AlertDialog(
            onDismissRequest = {}, containerColor = c.raised,
            title = { Text(ui.label + "...", style = SibType.title, color = c.ink) },
            text = { Text("This happens on your phone.", style = SibType.body, color = c.inkSoft) },
            confirmButton = {},
        )
        is ImportUi.Done -> {
            val r = ui.result
            val found = r.saved.size
            AlertDialog(
                onDismissRequest = onDismiss, containerColor = c.raised,
                title = {
                    Text(
                        when {
                            r.error != null -> "That could not be read"
                            found == 0 -> "No dates found"
                            found == 1 -> "1 possible deadline found"
                            else -> "$found possible deadlines found"
                        },
                        style = SibType.title, color = c.ink,
                    )
                },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when {
                            r.error != null -> Text(r.error, style = SibType.body, color = c.inkSoft)
                            r.messagesRead > 0 -> Text("Read ${r.messagesRead} messages. Nothing is added until you confirm it in Review.", style = SibType.body, color = c.inkSoft)
                            else -> {
                                Text(
                                    if (found > 0) "They are waiting in Review. Nothing is added until you confirm it. Here is what was read and why:" else "Here is what was read and why nothing was taken:",
                                    style = SibType.body, color = c.inkSoft,
                                )
                                r.extraction?.let { Spacer(Modifier.height(10.dp)); ExplainedText(it) }
                            }
                        }
                    }
                },
                confirmButton = { if (found > 0 && r.error == null) TextButton(onReview) { Text("Review now", style = SibType.bodyStrong, color = c.ink) } else TextButton(onDismiss) { Text("Close", style = SibType.bodyStrong, color = c.ink) } },
                dismissButton = { if (found > 0 && r.error == null) TextButton(onDismiss) { Text("Later", style = SibType.body, color = c.inkSoft) } },
            )
        }
    }
}
