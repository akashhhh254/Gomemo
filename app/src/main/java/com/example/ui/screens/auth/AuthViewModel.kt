package com.example.ui.screens.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.PhoneAuthResult
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    EMAIL_SIGN_IN,
    EMAIL_SIGN_UP,
    PHONE_OTP
}

enum class PhoneStep {
    ENTER_PHONE,
    ENTER_OTP
}

data class AuthUiState(
    val authMode: AuthMode = AuthMode.EMAIL_SIGN_IN,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    // Phone Auth
    val countryCode: String = "+1",
    val phoneNumber: String = "",
    val phoneStep: PhoneStep = PhoneStep.ENTER_PHONE,
    val verificationId: String = "",
    val otpCode: String = "",
    val resendCountdown: Int = 0,
    val canResend: Boolean = true,
    // Username / Profile Setup
    val showProfileSetup: Boolean = false,
    val setupFullName: String = "",
    val setupUsername: String = "",
    val isCheckingUsername: Boolean = false,
    val usernameError: String? = null,
    val isUsernameAvailable: Boolean? = null,
    // Status
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetEmailSent: Boolean = false,
    val showResetDialog: Boolean = false,
    val resetEmail: String = ""
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdownJob: Job? = null

    val currentUser: FirebaseUser?
        get() = authRepository.currentUser

    fun setAuthMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                authMode = mode,
                errorMessage = null,
                phoneStep = PhoneStep.ENTER_PHONE,
                otpCode = ""
            )
        }
    }

    fun onFullNameChange(name: String) = _uiState.update { it.copy(fullName = name, errorMessage = null) }
    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, errorMessage = null) }
    fun onPasswordChange(pass: String) = _uiState.update { it.copy(password = pass, errorMessage = null) }
    fun onConfirmPasswordChange(pass: String) = _uiState.update { it.copy(confirmPassword = pass, errorMessage = null) }
    fun onCountryCodeChange(code: String) = _uiState.update { it.copy(countryCode = code) }
    fun onPhoneNumberChange(phone: String) = _uiState.update { it.copy(phoneNumber = phone, errorMessage = null) }
    fun onOtpCodeChange(otp: String) = _uiState.update { it.copy(otpCode = otp.take(6), errorMessage = null) }
    fun onResetEmailChange(email: String) = _uiState.update { it.copy(resetEmail = email) }
    fun setResetDialogVisible(show: Boolean) = _uiState.update { it.copy(showResetDialog = show, resetEmail = it.email) }

    fun onSetupFullNameChange(name: String) = _uiState.update { it.copy(setupFullName = name) }
    fun onSetupUsernameChange(rawUsername: String) {
        val clean = rawUsername.trim().lowercase().removePrefix("@").replace(Regex("[^a-z0-9_]"), "")
        _uiState.update { it.copy(setupUsername = clean, usernameError = null, isUsernameAvailable = null) }
        checkUsernameAvailability(clean)
    }

    private fun checkUsernameAvailability(username: String) {
        if (username.length < 3) {
            _uiState.update { it.copy(usernameError = "Username must be at least 3 characters", isUsernameAvailable = false) }
            return
        }
        if (username.length > 30) {
            _uiState.update { it.copy(usernameError = "Username must be 30 characters or fewer", isUsernameAvailable = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUsername = true) }
            val currentUid = authRepository.currentUserId
            val available = userRepository.isUsernameAvailable(username, currentUid)
            _uiState.update {
                it.copy(
                    isCheckingUsername = false,
                    isUsernameAvailable = available,
                    usernameError = if (available) null else "Username '@$username' is already taken."
                )
            }
        }
    }

    fun submitProfileSetup(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.setupFullName.trim()
        val username = state.setupUsername.trim().lowercase().removePrefix("@")
        val currentUid = authRepository.currentUserId

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }

        if (username.length < 3 || username.length > 30) {
            _uiState.update { it.copy(errorMessage = "Username must be between 3 and 30 characters.") }
            return
        }

        if (state.isUsernameAvailable == false) {
            _uiState.update { it.copy(errorMessage = "Please choose an available username.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = userRepository.updateProfile(
                userId = currentUid,
                fullName = name,
                username = username,
                bio = "Exploring the world, one memory at a time."
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, showProfileSetup = false) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun authenticate(onSuccess: () -> Unit) {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password.trim()

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        if (state.authMode == AuthMode.EMAIL_SIGN_UP) {
            if (state.fullName.trim().isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
                return
            }
            if (password != state.confirmPassword.trim()) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
                return
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = authRepository.signUpWithEmail(
                    fullName = state.fullName.trim(),
                    email = email,
                    password = password
                )
                result.fold(
                    onSuccess = { profile ->
                        _uiState.update { it.copy(isLoading = false) }
                        if (profile.username.startsWith("user_")) {
                            _uiState.update {
                                it.copy(
                                    showProfileSetup = true,
                                    setupFullName = profile.fullName,
                                    setupUsername = profile.username
                                )
                            }
                        } else {
                            onSuccess()
                        }
                    },
                    onFailure = { error ->
                        val friendlyMessage = authRepository.translateAuthError(error)
                        _uiState.update { it.copy(isLoading = false, errorMessage = friendlyMessage) }
                    }
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = authRepository.signInWithEmail(email, password)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    },
                    onFailure = { error ->
                        val friendlyMessage = authRepository.translateAuthError(error)
                        _uiState.update { it.copy(isLoading = false, errorMessage = friendlyMessage) }
                    }
                )
            }
        }
    }

    fun sendPhoneOtp(activity: Activity) {
        val state = _uiState.value
        val digits = state.phoneNumber.replace(Regex("[^0-9]"), "")
        if (digits.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid phone number.") }
            return
        }

        val fullPhoneNumber = if (state.phoneNumber.startsWith("+")) state.phoneNumber else "${state.countryCode}$digits"

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        authRepository.sendPhoneOtp(
            activity = activity,
            phoneNumber = fullPhoneNumber,
            resendToken = resendToken
        ) { result ->
            when (result) {
                is PhoneAuthResult.CodeSent -> {
                    resendToken = result.token
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            phoneStep = PhoneStep.ENTER_OTP,
                            verificationId = result.verificationId,
                            errorMessage = null
                        )
                    }
                    startCountdownTimer()
                }
                is PhoneAuthResult.VerificationCompleted -> {
                    _uiState.update { it.copy(isLoading = false) }
                    checkProfileAndFinish(result.userProfile, onComplete = {})
                }
                is PhoneAuthResult.VerificationFailed -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error) }
                }
            }
        }
    }

    fun verifyPhoneOtp(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.otpCode.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter the 6-digit verification code.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.verifyPhoneOtp(state.verificationId, state.otpCode)
            result.fold(
                onSuccess = { profile ->
                    _uiState.update { it.copy(isLoading = false) }
                    checkProfileAndFinish(profile, onSuccess)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    private fun checkProfileAndFinish(profile: UserProfile, onComplete: () -> Unit) {
        if (profile.username.isBlank() || profile.username.startsWith("user_") || profile.fullName == "GoMemo User" || profile.fullName.startsWith("Explorer ")) {
            _uiState.update {
                it.copy(
                    showProfileSetup = true,
                    setupFullName = profile.fullName,
                    setupUsername = if (profile.username.startsWith("user_")) "" else profile.username
                )
            }
        } else {
            onComplete()
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(resendCountdown = 60, canResend = false) }
            for (i in 59 downTo 0) {
                delay(1000)
                _uiState.update { it.copy(resendCountdown = i) }
            }
            _uiState.update { it.copy(canResend = true) }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            result.fold(
                onSuccess = { profile ->
                    _uiState.update { it.copy(isLoading = false) }
                    checkProfileAndFinish(profile, onSuccess)
                },
                onFailure = { error ->
                    val message = authRepository.translateAuthError(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (error.message?.contains("cancelled", ignoreCase = true) == true)
                                null
                            else message
                        )
                    }
                }
            )
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.resetEmail.trim()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email for reset.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetEmailSent = true,
                            showResetDialog = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = authRepository.translateAuthError(error)
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

