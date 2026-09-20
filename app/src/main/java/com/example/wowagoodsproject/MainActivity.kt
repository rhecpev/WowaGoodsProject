package com.example.wowagoodsproject

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.ProgressDialog
import com.example.wowagoodsproject.component.ProgressPanel
import com.example.wowagoodsproject.navigation.MainScreen
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.example.wowagoodsproject.ui.theme.AppTheme
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableIntStateOf(App.getThemeMode()) }
            val appTheme = AppTheme.fromId(themeMode)

            WowaGoodsProjectTheme(theme = appTheme) {
                var isReady by remember { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var latestVersion by remember { mutableStateOf("") }
                var releaseNote by remember { mutableStateOf("") }
                val updateProgress by UpdateManager.progress.collectAsState()
                val isUpdating by UpdateManager.isRunning.collectAsState()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    scope.launch {
                        val prefs = context.getSharedPreferences(
                            "wowa_prefs",
                            android.content.Context.MODE_PRIVATE
                        )
                        val today =
                            java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                                .format(java.util.Date())
                        val lastUpdateDate = prefs.getString("last_data_update_date", "")
                        if (lastUpdateDate != today) {
                            try {
                                val total = UpdateManager.runFullUpdate()
                                prefs.edit()
                                    .putString("last_data_update_date", today)
                                    .apply()

                                val msg = if (total > 0) "데이터 업데이트 완료! ${total}개 항목 변경됨"
                                else "데이터가 최신 상태입니다"
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "업데이트 실패: ${e.message}")
                                e.printStackTrace()
                            }
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
                            androidx.compose.material3.TextButton(onClick = {
                                showUpdateDialog = false
                            }) {
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
                    // 수동 업데이트(UpdateWorker)가 도는 동안에는 화면을 막고 진행률을 띄운다.
                    if (isUpdating) {
                        ProgressDialog(
                            title = "데이터 업데이트 중",
                            fraction = updateProgress.fraction,
                            icon = Icons.Default.Sync,
                            label = updateProgress.label
                        )
                    }
                } else {
                    StartupScreen(
                        showProgress = isUpdating || updateProgress.fraction > 0f,
                        fraction = updateProgress.fraction,
                        label = updateProgress.label
                    )
                }
            }
        }

    }
}

/** 앱 시작 시 데이터 동기화/버전 확인이 끝날 때까지 보여주는 화면 */
@Composable
private fun StartupScreen(
    showProgress: Boolean,
    fraction: Float,
    label: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(40.dp))
            if (showProgress) {
                ProgressPanel(
                    title = "데이터 업데이트 중",
                    fraction = fraction,
                    label = label,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // 동기화할 게 없을 때(오늘 이미 받음)는 버전 확인만 하므로 진행률 없이 돌린다.
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "준비 중...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
