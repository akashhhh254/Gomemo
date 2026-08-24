package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.example.data.crypto.CryptoManager
import com.example.data.firebase.FirebaseManager
import com.example.data.local.LocalSessionManager
import com.example.data.model.ActivityType
import com.example.data.model.TimelineActivity
import com.example.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed class PhoneAuthResult {
    data class CodeSent(val verificationId: String, val token: PhoneAuthProvider.ForceResendingToken) : PhoneAuthResult()
    data class VerificationCompleted(val userProfile: UserProfile) : PhoneAuthResult()
    data class VerificationFailed(val error: String) : PhoneAuthResult()
}

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseManager.auth,
    private val firestore: FirebaseFirestore = FirebaseManager.firestore
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    fun getAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUpWithEmail(
        fullName: String,
        email: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            val cleanName = fullName.trim()
            val baseUsername = cleanEmail.substringBefore("@").replace(Regex("[^a-zA-Z0-9_]"), "").lowercase()

            val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = authResult.user ?: throw Exception("Authentication returned empty user")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .build()
            user.updateProfile(profileUpdates).await()

            val uniqueUsername = if (baseUsername.length < 3) "user_${user.uid.take(6)}" else baseUsername
            val publicKey = CryptoManager.getPublicKeyBase64()

            val userProfile = UserProfile(
                uid = user.uid,
                fullName = cleanName,
                email = cleanEmail,
                username = uniqueUsername,
                profilePhoto = "",
                bio = "Exploring the world, one memory at a time.",
                publicKey = publicKey,
                followersCount = 0,
                followingCount = 0,
                memoriesCount = 0,
                locationTrackingEnabled = true,
                isPrivateAccount = false
            )

            // Save in Firestore
            firestore.collection("users").document(user.uid).set(userProfile).await()

            // Welcome activity in real timeline
            val welcomeActivity = TimelineActivity(
                userId = user.uid,
                userName = cleanName,
                userPhoto = "",
                type = ActivityType.ADDED_MEMORY.name,
                title = "Joined GoMemo",
                description = "Created an account on GoMemo. Ready to capture places and memories!"
            )
            firestore.collection("timeline").add(welcomeActivity).await()

            LocalSessionManager.saveUserSession(userProfile)
            Result.success(userProfile)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign up error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = authResult.user ?: throw Exception("Sign in returned empty user")

            val profile = ensureUserProfileExists(user)
            LocalSessionManager.saveUserSession(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign in error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)

            // Dynamically resolve server client id from google-services.json generated resources
            val serverClientId = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else ""
            } catch (e: Exception) {
                ""
            }

            if (serverClientId.isBlank()) {
                throw Exception("Google Web Client ID is not configured. Please ensure google-services.json is added and has OAuth client credentials configured.")
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw Exception("Google sign in returned empty user")

                val profile = ensureUserProfileExists(
                    user,
                    googleIdTokenCredential.displayName,
                    googleIdTokenCredential.profilePictureUri?.toString()
                )
                LocalSessionManager.saveUserSession(profile)
                Result.success(profile)
            } else {
                throw Exception("Unexpected credential format received from Credential Manager.")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign in error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a real Firebase SMS verification code to the given E.164 phone number.
     */
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onResult: (PhoneAuthResult) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant auto-verification (e.g. on real SIM devices)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        if (user != null) {
                            val profile = ensureUserProfileExistsBlocking(user, null, null)
                            LocalSessionManager.saveUserSession(profile)
                            onResult(PhoneAuthResult.VerificationCompleted(profile))
                        } else {
                            onResult(PhoneAuthResult.VerificationFailed("Authentication completed but user is null."))
                        }
                    }
                    .addOnFailureListener { err ->
                        onResult(PhoneAuthResult.VerificationFailed(translateAuthError(err)))
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("AuthRepository", "Phone verification failed: ${e.message}", e)
                onResult(PhoneAuthResult.VerificationFailed(translateAuthError(e)))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d("AuthRepository", "Phone OTP sent successfully with verificationId: $verificationId")
                onResult(PhoneAuthResult.CodeSent(verificationId, token))
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /**
     * Verifies the SMS OTP code and signs the user into Firebase.
     */
    suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Authentication returned empty user.")

            val profile = ensureUserProfileExists(user)
            LocalSessionManager.saveUserSession(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Verify OTP error: ${e.message}", e)
            Result.failure(Exception(translateAuthError(e)))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank()) {
                throw Exception("Please enter your email address.")
            }
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Password reset failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Sign out error", e)
        }
        LocalSessionManager.clearUserSession()
    }

    private suspend fun ensureUserProfileExists(
        user: FirebaseUser,
        displayName: String? = null,
        photoUrl: String? = null
    ): UserProfile {
        val docRef = firestore.collection("users").document(user.uid)
        val snapshot = docRef.get().await()

        val publicKey = CryptoManager.getPublicKeyBase64()

        if (snapshot.exists()) {
            val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile(uid = user.uid)
            if (profile.publicKey.isBlank()) {
                docRef.update("publicKey", publicKey).await()
                return profile.copy(publicKey = publicKey)
            }
            return profile
        }

        val name = displayName ?: user.displayName ?: if (!user.phoneNumber.isNullOrBlank()) "Explorer ${user.phoneNumber?.takeLast(4)}" else "GoMemo User"
        val email = user.email ?: ""
        val baseUsername = if (email.isNotBlank()) {
            email.substringBefore("@").lowercase().replace(Regex("[^a-zA-Z0-9_]"), "")
        } else if (!user.phoneNumber.isNullOrBlank()) {
            "explorer_${user.uid.take(6).lowercase()}"
        } else {
            "user_${user.uid.take(6).lowercase()}"
        }
        val photo = photoUrl ?: user.photoUrl?.toString() ?: ""

        val profile = UserProfile(
            uid = user.uid,
            fullName = name,
            email = email,
            username = if (baseUsername.length < 3) "user_${user.uid.take(6)}" else baseUsername,
            profilePhoto = photo,
            bio = "Exploring the world, one memory at a time.",
            publicKey = publicKey,
            followersCount = 0,
            followingCount = 0,
            memoriesCount = 0,
            locationTrackingEnabled = true,
            isPrivateAccount = false
        )
        docRef.set(profile).await()
        return profile
    }

    private fun ensureUserProfileExistsBlocking(
        user: FirebaseUser,
        displayName: String?,
        photoUrl: String?
    ): UserProfile {
        val publicKey = CryptoManager.getPublicKeyBase64()
        val name = displayName ?: user.displayName ?: if (!user.phoneNumber.isNullOrBlank()) "Explorer ${user.phoneNumber?.takeLast(4)}" else "GoMemo User"
        val email = user.email ?: ""
        val baseUsername = "user_${user.uid.take(6).lowercase()}"
        val photo = photoUrl ?: user.photoUrl?.toString() ?: ""

        val profile = UserProfile(
            uid = user.uid,
            fullName = name,
            email = email,
            username = baseUsername,
            profilePhoto = photo,
            bio = "Exploring the world, one memory at a time.",
            publicKey = publicKey,
            followersCount = 0,
            followingCount = 0,
            memoriesCount = 0,
            locationTrackingEnabled = true,
            isPrivateAccount = false
        )
        firestore.collection("users").document(user.uid).set(profile)
        return profile
    }

    fun translateAuthError(error: Throwable): String {
        val msg = error.message ?: ""
        return when {
            error is FirebaseAuthInvalidCredentialsException || msg.contains("invalid-credential", ignoreCase = true) || msg.contains("invalid verification code", ignoreCase = true) || msg.contains("code has expired", ignoreCase = true) ->
                "Invalid or expired verification code. Please check the code and try again."
            error is FirebaseTooManyRequestsException || msg.contains("too-many-requests", ignoreCase = true) || msg.contains("quota", ignoreCase = true) ->
                "Too many verification attempts from this device. Please wait a few minutes before trying again."
            msg.contains("API key not valid", ignoreCase = true) ->
                "Firebase API Key configuration error: Please check that your new google-services.json is active and that Identity Toolkit API is enabled in Google Cloud Console."
            msg.contains("28444", ignoreCase = true) || msg.contains("Developer console is not set up correctly", ignoreCase = true) ->
                "Google Sign-In configuration error [28444]: Add your build's SHA-1 certificate fingerprint and matching package name to your Android app in Firebase Console."
            msg.contains("network", ignoreCase = true) ->
                "Network connection error. Please check your internet connection."
            else -> error.localizedMessage ?: "Authentication failed. Please try again."
        }
    }
}

