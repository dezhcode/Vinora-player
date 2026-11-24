package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PlinthApp
import com.example.ui.theme.PlinthTheme
import com.example.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(isDark = false)

        setContent {
            val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
            PlinthTheme(
                themeMode = uiState.preferences.themeMode,
                language = uiState.preferences.language,
            ) {
                PlinthApp(
                    viewModel = playerViewModel,
                    onDarkModeResolved = { isDark -> applySystemBars(isDark) },
                )
            }
        }
    }

    private fun applySystemBars(isDark: Boolean) {
        val barStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle,
        )
    }
}
