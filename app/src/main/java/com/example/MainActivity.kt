package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.GoMemoAppNavigation
import com.example.ui.theme.GoMemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.data.local.LocalSessionManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            GoMemoTheme {
                GoMemoAppNavigation()
            }
        }
    }
}
