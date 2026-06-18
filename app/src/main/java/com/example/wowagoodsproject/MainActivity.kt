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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import com.example.wowagoodsproject.navigation.MainScreen
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
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
                var isReady by rememberSaveable { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var latestVersion by remember { mutableStateOf("") }
                var releaseNote by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()


                val needsUpdate = remember {
                    val prefs = context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
                    val lastUpdate = prefs.getLong("last_update", 0L)
                    System.currentTimeMillis() - lastUpdate >= 24 * 60 * 60 * 1000L
                }

                LaunchedEffect(Unit) {
                    scope.launch {
                        val prefs = context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
                        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                        val lastUpdateDate = prefs.getString("last_data_update_date", "")
                        if (lastUpdateDate != today) {
                            prefs.edit().putString("last_data_update_date", today).apply()
                            val charaResult = UpdateManager.updateCharacters()
                            val seriesResult = UpdateManager.updateSeries()
                            val goodsResult = UpdateManager.updateGoods()

                            val total = charaResult.first + charaResult.second + charaResult.third +
                                    seriesResult.first + seriesResult.second + seriesResult.third +
                                    goodsResult.first + goodsResult.second + goodsResult.third

                            val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
                            else "데이터가 최신 상태입니다"

                            val channelId = "update_channel"
                            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val channel = android.app.NotificationChannel(
                                    channelId,
                                    "데이터 업데이트",
                                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                                )
                                notificationManager.createNotificationChannel(channel)
                            }

                            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle("WowaGoods 업데이트")
                                .setContentText(msg)
                                .setAutoCancel(true)
                                .build()

                            notificationManager.notify(1001, notification)
                        }
                        val result = UpdateManager.checkAppUpdate()
                        if (result != null) {
                            latestVersion = result.first
                            releaseNote = result.second
                            showUpdateDialog = true
                        }
                        isReady = true
                    }
                }
                if (showUpdateDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text("업데이트 가능") },
                        text = {
                            Column {
                                Text("새 버전이 출시되었습니다!")
                                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                Text("${BuildConfig.VERSION_NAME} → $latestVersion")
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/rhecpev/WowaGoodsProject/releases/latest")
                                )
                                context.startActivity(intent)
                                showUpdateDialog = false
                            }) { Text("다운로드") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) {
                                Text("나중에")
                            }
                        }
                    )
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
                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                            Text(text = "데이터 업데이트 중...")
                        }
                    }
                }
            }
        }
    }
}