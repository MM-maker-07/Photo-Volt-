package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.VaultDatabase
import com.example.data.repository.VaultRepository
import com.example.security.SecurityPreferences
import com.example.ui.screens.gallery.VaultGalleryScreen
import com.example.ui.screens.gallery.VaultViewModel
import com.example.ui.screens.pin.PinScreen
import com.example.ui.theme.MyApplicationTheme

/**
 * Main entry point for the Secure Photo Vault application.
 *
 * Security Enhancements:
 * 1. Extends [FragmentActivity] to support AndroidX BiometricPrompt API.
 * 2. Applies [WindowManager.LayoutParams.FLAG_SECURE] to the window to prevent screenshots,
 *    screen recording, and leaking sensitive decrypted previews to the Android Recents/Task Switcher.
 * 3. Enforces an automatic session lock when the application is backgrounded ([onStop]).
 */
class MainActivity : FragmentActivity() {

    private lateinit var securityPrefs: SecurityPreferences
    private var isVaultUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Anti-Leak Security: Prevent screenshots and task switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        securityPrefs = SecurityPreferences(this)

        // Initialize Database & Repository
        val database = VaultDatabase.getDatabase(applicationContext)
        val repository = VaultRepository(applicationContext, database.photoDao())

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vaultViewModel: VaultViewModel = viewModel(
                        factory = VaultViewModel.Factory(repository)
                    )

                    Crossfade(
                        targetState = isVaultUnlocked,
                        animationSpec = tween(400),
                        label = "VaultScreenCrossfade"
                    ) { unlocked ->
                        if (unlocked) {
                            VaultGalleryScreen(
                                viewModel = vaultViewModel,
                                onLockVault = {
                                    isVaultUnlocked = false
                                }
                            )
                        } else {
                            PinScreen(
                                securityPrefs = securityPrefs,
                                onAuthenticated = {
                                    isVaultUnlocked = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Auto-Lock on Backgrounding:
     * When the user leaves the application or puts the device to sleep,
     * immediately revoke the unlocked session state for security.
     */
    override fun onStop() {
        super.onStop()
        isVaultUnlocked = false
    }
}
