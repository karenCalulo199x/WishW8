package com.karencalulo.wishwait.data.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google sign-in for Drive backup, using Google's Authorization API.
 * Only asks for the hidden app folder (`drive.appdata`): WishWait can't see any other Drive files.
 * Sign-in is tied to the app's package name and signing key (set up in Google Cloud Console),
 * so there is no client secret in the app.
 */
class DriveAuth(context: Context) {

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    /**
     * Asks Google for access. If the person already agreed, the result has a token right away;
     * otherwise [AuthorizationResult.hasResolution] is true and its PendingIntent shows Google's consent screen.
     * Null when Google Play services can't be reached or the app isn't set up in Google Cloud yet.
     */
    suspend fun authorize(): AuthorizationResult? = runCatching { client.authorize(request).await() }.getOrNull()

    /** A fresh token without showing anything, or null when the person must sign in again. */
    suspend fun silentToken(): String? = authorize()?.takeIf { !it.hasResolution() }?.accessToken

    /** Reads the result after Google's consent screen closes. */
    fun resultFrom(data: Intent?): AuthorizationResult? =
        runCatching { client.getAuthorizationResultFromIntent(data) }.getOrNull()

    /** The Google account shown in Settings, when Google shares it. */
    @Suppress("DEPRECATION")
    fun accountEmail(result: AuthorizationResult): String? =
        runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

/** Waits for a Play services Task without an extra library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { e -> if (cont.isActive) cont.resumeWith(Result.failure(e)) }
    addOnCanceledListener { cont.cancel() }
}
