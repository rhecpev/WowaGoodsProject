package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.WhatsNewDialog
import com.example.wowagoodsproject.component.ReleaseNotes
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
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
    // 구매예정/소식 알림을 띄우려면 Android 13 이상에서 알림 권한이 필요하다.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 알림을 눌러 들어왔을 때 열어야 할 화면(굿즈/소식). 화면이 열고 나면 null 로 비운다. */
    private val openTarget = MutableStateFlow<OpenTarget?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        OpenTarget.takeFrom(intent)?.let { openTarget.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTarget.value = OpenTarget.takeFrom(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PendingGoodsNotifier.hasPermission(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableIntStateOf(App.getThemeMode()) }
            val appTheme = AppTheme.fromId(themeMode)

            WowaGoodsProjectTheme(theme = appTheme) {
                var isReady by remember { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var showWhatsNew by remember { mutableStateOf(false) }
                var latestVersion by remember { mutableStateOf("") }
                var releaseNote by remember { mutableStateOf("") }
                val updateProgress by UpdateManager.progress.collectAsState()
                val isUpdating by UpdateManager.showProgress.collectAsState()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    scope.launch {
                        // 데이터 업데이트는 매일 오전 5시에 백그라운드에서 돈다(DailySchedule).
                        // 앱을 열 때는 받아 둔 데이터가 하나도 없을 때(첫 설치)만 받는다.
                        val isFirstInstall = App.database.goodsDao().count() == 0
                        // 첫 설치면 '달라진 점' 을 보여 줄 필요가 없으니 본 것으로 둔다.
                        if (isFirstInstall) ReleaseNotes.markSeen(context)
                        else showWhatsNew = ReleaseNotes.shouldShow(context)
                        if (isFirstInstall) {
                            try {
                                UpdateManager.runFullUpdate()
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "첫 데이터 받기 실패: ${e.message}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "데이터를 받지 못했습니다. 마이페이지에서 수동 업데이트를 눌러 주세요", Toast.LENGTH_LONG).show()
                                }
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

                // 업데이트 후 처음 열 때 한 번만 변경점을 보여 준다. 새 버전 안내 팝업이 있으면 그걸 먼저 닫은 뒤에 띄운다.
                if (isReady && showWhatsNew && !showUpdateDialog) {
                    ReleaseNotes.forCurrentVersion()?.let { sections ->
                        WhatsNewDialog(
                            version = ReleaseNotes.currentVersion,
                            sections = sections,
                            onDismiss = {
                                ReleaseNotes.markSeen(context)
                                showWhatsNew = false
                            }
                        )
                    }
                }

                if (isReady) {
                    val target by openTarget.collectAsState()
                    MainScreen(
                        onThemeChange = { mode ->
                            App.setThemeMode(mode)
                            themeMode = mode
                        },
                        openTarget = target,
                        onOpenTargetHandled = { openTarget.value = null }
                    )
                    // 수동 업데이트(UpdateWorker)가 도는 동안에는 화면을 막고 진행률을 띄운다. 오전 5시 업데이트는 띄우지 않는다.
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
