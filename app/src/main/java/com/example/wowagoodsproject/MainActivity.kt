package com.example.wowagoodsproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.wowagoodsproject.navigation.MainScreen
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var themeMode by remember { mutableStateOf(App.getThemeMode()) }

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> systemDarkTheme
            }

            WowaGoodsProjectTheme(darkTheme = darkTheme) {
                val context = LocalContext.current
                var isReady by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val needsUpdate = remember {
                    val prefs = context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
                    val lastUpdate = prefs.getLong("last_update", 0L)
                    System.currentTimeMillis() - lastUpdate >= 24 * 60 * 60 * 1000L
                }

                LaunchedEffect(Unit) {
                    scope.launch {
                        UpdateManager.checkAndUpdate(context)
                        isReady = true
                    }
                }

                if (isReady) {
                    MainScreen(
                        onThemeChange = { mode ->
                            App.setThemeMode(mode)
                            themeMode = mode
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                            if (needsUpdate) {
                                Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                                Text(text = "데이터 업데이트 중...")
                            }
                        }
                    }
                }
            }
        }
    }
}