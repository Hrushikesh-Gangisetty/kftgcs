package com.example.kftgcs.authentication

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.kftgcs.R
import com.example.kftgcs.navigation.Screen
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 3-step Forgot Password screen:
 *  Step 1 – Enter email → request OTP   (action = send_otp)
 *  Step 2 – Enter OTP  → verify OTP     (action = verify_otp)
 *  Step 3 – New password form           (action = reset_password)
 */
@Composable
fun ForgotPasswordPage(
    modifier: Modifier = Modifier,
    navController: NavController,
    authViewModel: AuthViewModel
) {
    // ── Internal step state (1 = Email, 2 = OTP, 3 = New Password) ──
    var step by remember { mutableStateOf(1) }

    // ── Field state ───────────────────────────────────────────────────
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // ── OTP timer (2 minutes) ─────────────────────────────────────────
    var timerSeconds by remember { mutableStateOf(120) }
    var timerRunning by remember { mutableStateOf(false) }

    val authState = authViewModel.authState.observeAsState()
    val context = LocalContext.current

    // Start / restart countdown whenever timerRunning is toggled on
    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timerSeconds > 0) {
                delay(1000L)
                timerSeconds--
            }
            timerRunning = false
        }
    }

    // ── Auth-state side-effects ────────────────────────────────────────
    LaunchedEffect(authState.value) {
        Timber.d("ForgotPasswordPage state=${authState.value}, step=$step")
        when (val state = authState.value) {
            is AuthState.ResetOtpSent -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                // Advance to OTP step & start timer
                step = 2
                timerSeconds = 120
                timerRunning = true
                authViewModel.resetAuthState()
            }
            is AuthState.ResetOtpVerified -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                step = 3
                authViewModel.resetAuthState()
            }
            is AuthState.PasswordResetSuccess -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                }
            }
            is AuthState.Error -> {
                Timber.e("ForgotPasswordPage error: ${state.message}")
                // If OTP expired the backend returns a message containing "expired"
                // snap back to email step so user requests a fresh OTP
                if (state.message.contains("expired", ignoreCase = true) ||
                    state.message.contains("resend", ignoreCase = true)
                ) {
                    step = 1
                    otp = ""
                    timerRunning = false
                }
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                authViewModel.resetAuthState()
            }
            else -> Unit
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.logbag),
            contentDescription = "Forgot Password Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (step) {
                    1 -> Step1EmailContent(
                        email = email,
                        onEmailChange = { email = it },
                        isLoading = authState.value is AuthState.Loading,
                        onSendOtp = { authViewModel.sendResetOtp(email) },
                        onBack = { navController.navigateUp() }
                    )
                    2 -> Step2OtpContent(
                        email = email,
                        otp = otp,
                        onOtpChange = { otp = it.filter(Char::isDigit) },
                        timerSeconds = timerSeconds,
                        timerRunning = timerRunning,
                        isLoading = authState.value is AuthState.Loading,
                        onVerify = { authViewModel.verifyResetOtp(email, otp) },
                        onResend = {
                            otp = ""
                            authViewModel.sendResetOtp(email)
                        },
                        onBack = { step = 1 }
                    )
                    3 -> Step3NewPasswordContent(
                        newPassword = newPassword,
                        onNewPasswordChange = { newPassword = it },
                        confirmPassword = confirmPassword,
                        onConfirmPasswordChange = { confirmPassword = it },
                        newPasswordVisible = newPasswordVisible,
                        onToggleNewPassword = { newPasswordVisible = !newPasswordVisible },
                        confirmPasswordVisible = confirmPasswordVisible,
                        onToggleConfirmPassword = { confirmPasswordVisible = !confirmPasswordVisible },
                        isLoading = authState.value is AuthState.Loading,
                        onReset = { authViewModel.resetPassword(email, newPassword, confirmPassword) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 1 – Email entry
// ─────────────────────────────────────────────────────────────
@Composable
private fun Step1EmailContent(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    onSendOtp: () -> Unit,
    onBack: () -> Unit
) {
    Text(text = "Forgot Password", fontSize = 26.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Enter your registered email to receive a reset OTP",
        fontSize = 12.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors()
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (isLoading) {
        CircularProgressIndicator(color = Color.Black)
    } else {
        Button(
            onClick = onSendOtp,
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank()
        ) {
            Text(text = "Send OTP", color = if (email.isNotBlank()) Color.Black else Color.Gray)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onBack) {
        Text(text = "Back to Login", color = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────
// Step 2 – OTP verification
// ─────────────────────────────────────────────────────────────
@Composable
private fun Step2OtpContent(
    email: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    timerSeconds: Int,
    timerRunning: Boolean,
    isLoading: Boolean,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    Text(text = "Enter OTP", fontSize = 26.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "An OTP was sent to\n$email",
        fontSize = 12.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = otp,
        onValueChange = onOtpChange,
        label = { Text("Enter OTP") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "OTP expires in",
            fontSize = 12.sp,
            color = Color.Gray
        )
        if (timerRunning && timerSeconds > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${timerSeconds}s",
                fontSize = 12.sp,
                color = Color.Red
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (isLoading) {
        CircularProgressIndicator(color = Color.Black)
    } else {
        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = otp.isNotBlank()
        ) {
            Text(text = "Verify OTP", color = if (otp.isNotBlank()) Color.Black else Color.Gray)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        onClick = onResend,
        enabled = !timerRunning || timerSeconds == 0
    ) {
        Text(
            text = if (timerRunning && timerSeconds > 0) "Resend OTP (${timerSeconds}s)" else "Resend OTP",
            color = if (!timerRunning || timerSeconds == 0) Color.Black else Color.Gray
        )
    }
    TextButton(onClick = onBack) {
        Text(text = "Back", color = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────
// Step 3 – New password
// ─────────────────────────────────────────────────────────────
@Composable
private fun Step3NewPasswordContent(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    newPasswordVisible: Boolean,
    onToggleNewPassword: () -> Unit,
    confirmPasswordVisible: Boolean,
    onToggleConfirmPassword: () -> Unit,
    isLoading: Boolean,
    onReset: () -> Unit
) {
    Text(text = "Reset Password", fontSize = 26.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Enter your new password",
        fontSize = 12.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = newPassword,
        onValueChange = onNewPasswordChange,
        label = { Text("New Password") },
        singleLine = true,
        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleNewPassword) {
                Icon(
                    imageVector = if (newPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (newPasswordVisible) "Hide password" else "Show password",
                    tint = Color.Gray
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors()
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text("Confirm Password") },
        singleLine = true,
        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleConfirmPassword) {
                Icon(
                    imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                    tint = Color.Gray
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors()
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (isLoading) {
        CircularProgressIndicator(color = Color.Black)
    } else {
        val enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank()
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(text = "Reset Password", color = if (enabled) Color.Black else Color.Gray)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared TextField colour helper
// ─────────────────────────────────────────────────────────────
@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    cursorColor = Color.Black,
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color.Black,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black
)

