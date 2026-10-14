package com.hsib.sib

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The one question the interface asks of the phone: is this the person who owns it? */
interface Authenticator {
    /** False when the phone has no fingerprint, face or screen lock to ask with. */
    fun available(): Boolean

    fun prompt(title: String, onResult: (Boolean) -> Unit)
}

/**
 * Fingerprint or face, falling back to the screen lock. Weak biometrics are accepted on purpose: this lock only keeps
 * a casual person out of the app. The data stays encrypted whether it is on or off.
 */
class PhoneAuthenticator(private val activity: FragmentActivity) : Authenticator {
    private val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private var showing = false

    override fun available(): Boolean = BiometricManager.from(activity).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    override fun prompt(title: String, onResult: (Boolean) -> Unit) {
        if (showing || !available()) return
        showing = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                showing = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                showing = false
                onResult(false)
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(allowed).build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
