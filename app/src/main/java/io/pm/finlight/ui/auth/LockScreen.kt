package io.pm.finlight.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import io.pm.finlight.ui.navigation.resolveActivityViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.findFragmentActivity
import java.util.concurrent.Executor

@SuppressLint("NewApi")
@Composable
fun FinanceAppWithLockScreen(
    shortcutAction: String? = null,
    settingsViewModel: SettingsViewModel? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = settingsViewModel ?: resolveActivityViewModel()
    val appLockEnabled by vm.appLockEnabled.collectAsState()
    var isUnlocked by remember { mutableStateOf(false) }

    val permissionsToRequest =
        remember {
            val list =
                mutableListOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            list.toTypedArray()
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { perms ->
            val allPermissionsGranted = perms.all { it.value }
            if (!allPermissionsGranted) {
                Toast.makeText(context, "Some permissions were denied. The app may not function fully.", Toast.LENGTH_LONG).show()
            }
        }

    LaunchedEffect(Unit) {
        val areAllPermissionsGranted =
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        if (!areAllPermissionsGranted) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val biometricManager = remember(context) { BiometricManager.from(context) }
    val canAuthenticate =
        remember(biometricManager) {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            try {
                biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
            } catch (_: Exception) {
                false
            }
        }

    Box(modifier = modifier) {
        if (appLockEnabled == null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        } else if (appLockEnabled == true && canAuthenticate && !isUnlocked) {
            LockScreen(onUnlock = { isUnlocked = true })
        } else {
            content()
        }
    }
}

@Suppress("kotlin:S5324")
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val executor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }

    val promptInfo =
        remember {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("App Locked")
                .setSubtitle("Unlock using biometrics, PIN, pattern, or password")
                .setAllowedAuthenticators(authenticators)
                .build()
        }

    val biometricPrompt =
        remember(activity, executor) {
            activity?.let { fragmentActivity ->
                BiometricPrompt(
                    fragmentActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onUnlock()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_CANCELED
                            ) {
                                Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, biometricPrompt, promptInfo) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    try {
                        biometricPrompt?.authenticate(promptInfo)
                    } catch (_: Exception) {
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(biometricPrompt) {
        try {
            biometricPrompt?.authenticate(promptInfo)
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = {
                try {
                    biometricPrompt?.authenticate(promptInfo)
                } catch (_: Exception) {
                }
            },
            enabled = biometricPrompt != null,
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Unlock App")
        }
    }
}
