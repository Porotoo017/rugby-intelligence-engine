package com.rugbypro.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.screens.RugbyProRoot
import com.rugbypro.app.ui.theme.RugbyProTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RugbyViewModel>()

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled dynamically by settings UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            RugbyProTheme(themeMode = state.config.themeMode) {
                RugbyProRoot(
                    state = state,
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}
