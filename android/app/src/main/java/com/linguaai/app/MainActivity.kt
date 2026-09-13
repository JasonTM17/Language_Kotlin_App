package com.linguaai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.ui.navigation.LinguaNavHost
import com.linguaai.app.ui.theme.LinguaAiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The stored theme preference was previously written but never read,
            // so choosing Light or Dark did nothing. Resolving it here is what
            // makes the setting real.
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(
                initialValue = SettingsDataStore.THEME_SYSTEM,
            )
            val darkTheme =
                when (themeMode) {
                    SettingsDataStore.THEME_DARK -> true
                    SettingsDataStore.THEME_LIGHT -> false
                    else -> isSystemInDarkTheme()
                }

            LinguaAiTheme(darkTheme = darkTheme) {
                LinguaApp()
            }
        }
    }
}

@Composable
private fun LinguaApp() {
    LinguaNavHost(navController = rememberNavController())
}
