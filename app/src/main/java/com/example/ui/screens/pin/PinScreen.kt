package com.example.ui.screens.pin

import android.os.CountDownTimer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricHelper
import com.example.security.PinVerificationResult
import com.example.security.SecurityPreferences
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.OnPrimaryDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PinStage {
    LOGIN,
    SETUP_ENTER_NEW,
    SETUP_CONFIRM
}

/**
 * Production-ready PIN authentication and setup screen with:
 * - 4-digit animated dot indicators with error shake
 * - High-contrast tactile keypad
 * - BiometricPrompt integration for Fingerprint & Face unlock
 * - Brute-force rate limiting lockout with timer
 * - Initial PIN creation & confirmation flow
 */
@Composable
fun PinScreen(
    securityPrefs: SecurityPreferences,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isInitialSetup = remember { !securityPrefs.isPinSet() }
    var currentStage by remember {
        mutableStateOf(if (isInitialSetup) PinStage.SETUP_ENTER_NEW else PinStage.LOGIN)
    }

    var enteredPin by remember { mutableStateOf("") }
    var firstPinDraft by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }
    var isBiometricAvailable by remember { mutableStateOf(false) }

    // Shake animation for incorrect PIN
    val shakeOffset = remember { Animatable(0f) }

    // Check biometric availability
    LaunchedEffect(Unit) {
        val status = BiometricHelper.checkBiometricAvailability(context)
        isBiometricAvailable = (status == BiometricHelper.BiometricStatus.AVAILABLE) && securityPrefs.isBiometricEnabled

        // Auto-prompt biometric on login if available and not locked out
        if (currentStage == PinStage.LOGIN && isBiometricAvailable && activity != null && lockoutSeconds == 0) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Unlock Photo Vault",
                subtitle = "Verify fingerprint or face",
                onSuccess = onAuthenticated,
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    // Trigger error shake animation
    fun triggerErrorShake(msg: String) {
        errorMessage = msg
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -20f at 50
                    20f at 100
                    -15f at 150
                    15f at 200
                    -8f at 250
                    8f at 300
                    0f at 400
                }
            )
        }
    }

    // Handle 4-digit PIN completion
    fun onPinFilled(pin: String) {
        when (currentStage) {
            PinStage.LOGIN -> {
                when (val result = securityPrefs.verifyPin(pin)) {
                    is PinVerificationResult.Success -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAuthenticated()
                    }
                    is PinVerificationResult.Incorrect -> {
                        enteredPin = ""
                        triggerErrorShake("Incorrect PIN. ${result.remainingAttempts} attempts left.")
                    }
                    is PinVerificationResult.LockedOut -> {
                        enteredPin = ""
                        lockoutSeconds = result.remainingSeconds
                        triggerErrorShake("Vault locked for ${result.remainingSeconds}s due to failed attempts.")
                    }
                    is PinVerificationResult.PinNotSet -> {
                        currentStage = PinStage.SETUP_ENTER_NEW
                    }
                }
            }
            PinStage.SETUP_ENTER_NEW -> {
                firstPinDraft = pin
                enteredPin = ""
                errorMessage = null
                currentStage = PinStage.SETUP_CONFIRM
            }
            PinStage.SETUP_CONFIRM -> {
                if (pin == firstPinDraft) {
                    securityPrefs.setPin(pin)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAuthenticated()
                } else {
                    enteredPin = ""
                    firstPinDraft = ""
                    currentStage = PinStage.SETUP_ENTER_NEW
                    triggerErrorShake("PINs did not match. Please try again.")
                }
            }
        }
    }

    // Keypad click handler
    fun onKeyPressed(digit: String) {
        if (lockoutSeconds > 0) return
        if (enteredPin.length < 4) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            errorMessage = null
            val newPin = enteredPin + digit
            enteredPin = newPin
            if (newPin.length == 4) {
                onPinFilled(newPin)
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    // Lockout countdown timer
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            lockoutSeconds -= 1
            if (lockoutSeconds == 0) {
                errorMessage = null
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("pin_screen"),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar Header in Elegant Dark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = OnPrimaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Photo Vault",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = TextPrimary
                    )
                }

                // AES-256 Pill Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkBorder)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentPurple)
                        )
                        Text(
                            text = "AES-256",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = AccentPurple
                        )
                    }
                }
            }

            // Center Content: Lock Icon, Title, Subtitle, and PIN Dots
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (currentStage == PinStage.LOGIN) Icons.Default.Shield else Icons.Default.LockOpen,
                        contentDescription = "Security Vault Shield",
                        tint = AccentPurple,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = when (currentStage) {
                        PinStage.LOGIN -> "Enter Security PIN"
                        PinStage.SETUP_ENTER_NEW -> "Create Master PIN"
                        PinStage.SETUP_CONFIRM -> "Confirm Master PIN"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 24.sp
                    ),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (currentStage) {
                        PinStage.LOGIN -> if (lockoutSeconds > 0) "Locked out for $lockoutSeconds seconds" else "Your photos are encrypted with hardware AES-256 key"
                        PinStage.SETUP_ENTER_NEW -> "Set a 4-digit PIN for hardware AES-256 encryption"
                        PinStage.SETUP_CONFIRM -> "Re-enter your 4-digit PIN to confirm"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lockoutSeconds > 0) AccentRed else TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN 4-Digit Dot Indicators (Elegant Dark Glowing Rings)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("pin_dots_row")
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        if (errorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed)
                                    .border(4.dp, AccentRed.copy(alpha = 0.25f), CircleShape)
                            )
                        } else if (isFilled) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(AccentPurple)
                                    .border(4.dp, AccentPurple.copy(alpha = 0.25f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, TextSecondary, CircleShape)
                            )
                        }
                    }
                }

                // Error / Status Message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = AccentRed,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag("pin_error_text")
                    )
                }
            }

            // 3x4 Numerical Keypad in Elegant Dark
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (currentStage == PinStage.LOGIN && isBiometricAvailable && activity != null) {
                                        KeypadActionIconButton(
                                            icon = Icons.Default.Fingerprint,
                                            contentDescription = "Unlock with Biometrics",
                                            tint = AccentPurple,
                                            onClick = {
                                                BiometricHelper.showBiometricPrompt(
                                                    activity = activity,
                                                    title = "Unlock Photo Vault",
                                                    subtitle = "Verify fingerprint or face",
                                                    onSuccess = onAuthenticated,
                                                    onError = { err -> errorMessage = err }
                                                )
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }
                                "DEL" -> {
                                    KeypadActionIconButton(
                                        icon = Icons.Default.Backspace,
                                        contentDescription = "Backspace",
                                        tint = AccentRed,
                                        onClick = { onBackspace() }
                                    )
                                }
                                else -> {
                                    KeypadNumberButton(
                                        digit = key,
                                        enabled = lockoutSeconds == 0,
                                        onClick = { onKeyPressed(key) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom decorative home bar pill
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(DarkBorder.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun KeypadNumberButton(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DarkSurfaceVariant.copy(alpha = if (enabled) 0.6f else 0.2f))
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .testTag("keypad_btn_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (enabled) TextPrimary else TextMuted
        )
    }
}

@Composable
private fun KeypadActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DarkSurfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .testTag("keypad_action_${contentDescription.replace(" ", "_").lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
