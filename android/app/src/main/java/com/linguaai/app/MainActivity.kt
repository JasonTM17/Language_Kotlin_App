package com.linguaai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.linguaai.app.ui.navigation.LinguaNavHost
import com.linguaai.app.ui.theme.LinguaAiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinguaAiTheme {
                LinguaApp()
            }
        }
    }
}

@Composable
private fun LinguaApp() {
    LinguaNavHost(navController = rememberNavController())
}
