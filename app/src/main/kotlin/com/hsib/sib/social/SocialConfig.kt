package com.hsib.sib.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.hsib.sib.BuildConfig
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.SibDatabase
import com.hsib.sib.data.Store
import com.hsib.sib.social.firebase.FirebaseSetup
import com.hsib.sib.social.firebase.FirebaseSocialAuth
import com.hsib.sib.social.firebase.FirestoreBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Builds the sharing engine once the vault is open, or says there is none. */
fun interface SocialBuilder {
    fun build(db: SibDatabase, settings: SettingsStore, store: Store, scope: CoroutineScope): SocialRepository
}

/**
 * Where sharing comes from.
 *  - With a Firebase connection (app/google-services.json at build time): the real thing. Google sign in tells accounts
 *    apart, Cloud Firestore holds the sealed data, and the build keeps its network permissions.
 *  - Without one, in a debug build: a practice mode, a server in memory with a pretend friend, so the screens can be tried.
 *  - Without one, in a release build: no sharing at all. No screen offers it and the app has no network permission.
 */
object SocialConfig {
    fun builder(context: Context): SocialBuilder? = when {
        FirebaseSetup.available -> firebase(context)
        BuildConfig.DEBUG -> PracticeSocial.builder(context)
        else -> null
    }

    private fun firebase(context: Context) = SocialBuilder { db, settings, store, scope ->
        val app = checkNotNull(FirebaseSetup.app(context)) { "Firebase is configured but could not start" }
        val auth = FirebaseAuth.getInstance(app)
        SocialRepository(
            db, settings, store,
            FirestoreBackend(FirebaseSetup.firestore(app), auth),
            FirebaseSocialAuth(auth, BuildConfig.GOOGLE_WEB_CLIENT_ID),
            scope,
        )
    }
}

/** The practice server lives as long as the process. Nothing in it is saved, and nothing leaves the phone. */
internal object PracticeSocial {
    private val server = FakeServer()

    fun builder(context: Context) = SocialBuilder { db, settings, store, scope ->
        val me = SocialUser("practice-me", "You", "you@practice.invalid")
        val repository = SocialRepository(db, settings, store, FakeBackend(server, me.uid), FakeAuth(me), scope, practice = true)
        scope.launch(Dispatchers.Default) {
            val friend = PracticeFriend.create(context, server, repository, me.uid, scope)
            while (true) {
                runCatching { friend.step() }
                delay(2_000)
            }
        }
        repository
    }
}
