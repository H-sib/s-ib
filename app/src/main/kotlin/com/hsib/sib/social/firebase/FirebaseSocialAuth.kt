package com.hsib.sib.social.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.hsib.sib.social.SignInResult
import com.hsib.sib.social.SocialAuth
import com.hsib.sib.social.SocialError
import com.hsib.sib.social.SocialException
import com.hsib.sib.social.SocialUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Who the person is. Google sign in through Android's Credential Manager gives an ID token; Firebase Authentication turns
 * it into an account with a stable user id, which is what tells one person's data from another's everywhere in the server
 * rules. The web client id (not the Android one) is what the token is made for.
 */
internal class FirebaseSocialAuth(private val auth: FirebaseAuth, private val webClientId: String) : SocialAuth {
    private val state = MutableStateFlow(auth.currentUser?.toSocialUser())
    override val user: StateFlow<SocialUser?> = state

    init {
        auth.addAuthStateListener { state.value = it.currentUser?.toSocialUser() }
    }

    private fun FirebaseUser.toSocialUser() = SocialUser(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Me",
        email = email,
    )

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnCompleteListener { t ->
            if (t.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                c.resume(t.result as T)
            } else {
                c.resumeWithException(t.exception ?: IllegalStateException("task failed"))
            }
        }
    }

    /** Asks Android to show the Google account chooser. Null if the person cancelled. Throws a [SocialException] otherwise. */
    private suspend fun googleCredential(context: Context): AuthCredential? {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw SocialException(SocialError.NO_GOOGLE_ACCOUNT)
        } catch (e: GetCredentialException) {
            throw SocialException(SocialError.SIGN_IN_FAILED)
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SocialException(SocialError.SIGN_IN_FAILED)
        }
        val token = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw SocialException(SocialError.SIGN_IN_FAILED)
        }
        return GoogleAuthProvider.getCredential(token, null)
    }

    override suspend fun signIn(context: Context): SignInResult = try {
        val credential = googleCredential(context)
        if (credential == null) {
            SignInResult.Cancelled
        } else {
            val account = auth.signInWithCredential(credential).await().user ?: throw SocialException(SocialError.SIGN_IN_FAILED)
            SignInResult.Ok(account.toSocialUser())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocialException) {
        SignInResult.Failed(e.error)
    } catch (e: FirebaseNetworkException) {
        SignInResult.Failed(SocialError.OFFLINE)
    } catch (e: Exception) {
        SignInResult.Failed(SocialError.SIGN_IN_FAILED)
    }

    override suspend fun signOut() {
        auth.signOut()
        // Forget which Google account was chosen, so the next sign in shows the chooser again.
        runCatching { CredentialManager.create(auth.app.applicationContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun deleteAccount(context: Context) {
        val account = auth.currentUser ?: return
        try {
            account.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            // Deleting an account is allowed only soon after signing in, so ask for the Google account once more.
            val credential = googleCredential(context) ?: throw SocialException(SocialError.NOT_SIGNED_IN)
            account.reauthenticate(credential).await()
            account.delete().await()
        } catch (e: FirebaseNetworkException) {
            throw SocialException(SocialError.OFFLINE)
        }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
