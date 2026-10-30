package com.hsib.sib.social.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds cannot pass Play Integrity, so they use App Check's debug provider. It prints a token to the log the first
 * time; the owner adds that token in the Firebase console (App Check, Apps, Manage debug tokens).
 */
internal object AppCheckSetup {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
