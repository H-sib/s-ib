package com.hsib.sib.social.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.hsib.sib.BuildConfig

/**
 * Starts Firebase by hand, and only when the build was given a connection (app/google-services.json). The automatic start is
 * removed in the manifest, so a build without a connection never starts Firebase and never touches the network.
 */
internal object FirebaseSetup {
    val available: Boolean get() = BuildConfig.FIREBASE_READY

    @Synchronized
    fun app(context: Context): FirebaseApp? {
        if (!available) return null
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        val app = FirebaseApp.initializeApp(context.applicationContext, options)
        // Only this app, proven by Play Integrity (or the debug token in debug builds), may use the project.
        AppCheckSetup.install(app)
        return app
    }

    /**
     * Firestore with a memory cache and no offline queue: an action that needs the server fails at once when there is no
     * connection, instead of waiting and then happening later when nobody expects it.
     */
    fun firestore(app: FirebaseApp): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance(app)
        runCatching {
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
        return db
    }
}
