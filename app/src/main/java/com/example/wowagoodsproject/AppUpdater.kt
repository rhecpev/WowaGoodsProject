package com.example.wowagoodsproject

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.example.wowagoodsproject.ui.theme.AppStyles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 앱 자체 업데이트(깃허브 릴리즈).
 *  1. 최신 릴리즈와 거기 붙은 APK 를 확인한다([fetchLatest]).
 *  2. APK 를 앱 캐시에 받는다. 같은 버전을 이미 다 받아 두었으면 다시 받지 않는다(깃허브 다운로드 수가 두 번 세지지 않게).
 *  3. 안드로이드 설치 화면을 띄운다. 마지막 '설치'는 안드로이드가 늘 사람에게 묻는다(자동 설치는 안 된다).
 *     처음 한 번은 이 앱의 '출처를 알 수 없는 앱 설치' 허용이 필요하다.
 * 릴리즈에 APK 가 없으면 예전처럼 릴리즈 페이지를 연다.
 * 업데이트 APK 는 기기에 깔린 앱과 같은 키로 서명돼 있어야 덮어 설치된다.
 */
object AppUpdater {

    private const val RELEASE_API = "https://api.github.com/repos/rhecpev/WowaGoodsProject/releases/latest"
    const val RELEASE_PAGE = "https://github.com/rhecpev/WowaGoodsProject/releases/latest"
    private const val CHANNEL_ID = "app_update"
    // 굿즈 알림(0~7,000,000 대)·소식 알림과 겹치지 않는 번호. PendingIntent 요청 코드로도 쓴다.
    private const val NOTIFICATION_ID = Int.MAX_VALUE - 1
    private const val PREF_NOTIFIED = "app_update_notified_version"
    /** 설치 허용을 기다리는 버전. 허용을 바꾸면 안드로이드가 앱 프로세스를 다시 띄워 메모리 상태가 사라지므로 적어 둔다. */
    private const val PREF_PENDING_INSTALL = "app_update_pending_install"

    /** 최신 릴리즈. [apkUrl] 이 null 이면 릴리즈에 APK 가 붙어 있지 않다. */
    data class Release(val tag: String, val notes: String, val apkUrl: String?, val apkName: String, val apkSize: Long) {
        val isNewer: Boolean get() = isNewerVersion(tag, BuildConfig.VERSION_NAME)
        /** 화면에 보일 버전: 태그에 v 가 붙었든 안 붙었든 'v3.0.1' 로 맞춘다 */
        val display: String get() = "v" + versionOf(tag)
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        /** 새 버전이 있다(받을지 묻는다) */
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val fraction: Float) : State
        /** 받았는데 '출처를 알 수 없는 앱 설치' 허용이 필요하다 */
        data class NeedPermission(val release: Release, val file: File) : State
        /** 다 받았는데 앱이 뒤에 있어 설치 화면을 띄우지 못했다(안드로이드 10+ 는 백그라운드에서 화면을 못 띄운다) */
        data class ReadyToInstall(val release: Release, val file: File) : State
        /** [file] 은 이미 받아 둔 APK(있으면 '다시 시도'가 다시 받지 않고 설치한다) */
        data class Failed(val release: Release?, val message: String, val file: File? = null) : State
    }

    /** 앱 화면이 앞에 있는지. MainActivity 의 onResume/onPause 가 적는다. */
    @Volatile var inForeground: Boolean = false

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    // 한 작업이 예외로 끝나도 scope 전체가 취소되어 다음 확인이 멈추지 않게 SupervisorJob 을 둔다.
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** 깃허브에서 최신 릴리즈를 읽는다(하루 한 번 제한 없음). */
    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val json = try {
            val code = conn.responseCode
            if (code == 403 || code == 429) throw IllegalStateException("확인 횟수가 많아 잠시 막혔습니다. 조금 뒤 다시 시도해 주세요")
            if (code !in 200..299) throw IllegalStateException("서버 응답 $code")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val tag = json.getString("tag_name")
        val assets = json.optJSONArray("assets")
        var apk: JSONObject? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a; break }
        }
        App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE).edit()
            .putString("cached_latest_version", tag).apply()
        Release(
            tag = tag,
            notes = json.optString("body", ""),
            apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
            apkName = apk?.optString("name").orEmpty(),
            apkSize = apk?.optLong("size", -1L) ?: -1L
        )
    }

    /**
     * 지금 확인한다(마이페이지 '현재 버전', 알림). 최신이면 [toastIfLatest] 일 때 토스트로 알리고,
     * 새 버전이면 받을지 묻는 창을 띄운다. [onChecked] 는 확인한 최신 버전 이름.
     */
    fun checkNow(activityContext: Context, toastIfLatest: Boolean = true, onChecked: (String) -> Unit = {}) {
        // 앱 수명 scope 에서 쓰므로 액티비티를 붙잡지 않게 앱 context 로 토스트를 띄운다.
        val context = activityContext.applicationContext
        // 이미 창이 떠 있으면(받을지 묻는 중·설치 허용 안내 등) 다시 확인해 덮어쓰지 않는다. 실패 창에서는 다시 확인해도 된다.
        if (_state.value !is State.Idle && _state.value !is State.Failed) return
        _state.value = State.Checking
        scope.launch {
            try {
                // 앱 시작 확인과 같은 결과를 나눠 쓰게 UpdateManager 를 거친다(GitHub API 를 두 번 부르지 않게).
                val r = UpdateManager.refreshAppUpdate()
                offeredTag = r.tag
                onChecked(r.tag)
                if (r.isNewer) _state.value = State.Available(r)
                else {
                    _state.value = State.Idle
                    if (toastIfLatest) Toast.makeText(context, "최신 버전입니다 (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                // 화면을 떠나 확인이 취소된 것은 실패가 아니다. 알리지 않고 멈춘다.
                _state.value = State.Idle
                throw e
            } catch (e: Exception) {
                _state.value = State.Idle
                Toast.makeText(context, "버전을 확인하지 못했습니다: ${e.message ?: "인터넷 연결을 확인해 주세요"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 앱을 켤 때 이미 찾은 새 버전으로 받을지 묻는 창을 띄운다. */
    /** 이번 실행에서 이미 안내한 버전. '나중에'로 닫으면 화면이 다시 만들어져도 그날은 다시 묻지 않는다. */
    private var offeredTag: String? = null

    fun offer(release: Release) {
        if (release.tag == offeredTag) return
        if (_state.value is State.Idle && release.isNewer) {
            offeredTag = release.tag
            _state.value = State.Available(release)
        }
    }

    /** 테스트에서 창을 그려 보려고 상태를 바로 넣는다 */
    @androidx.annotation.VisibleForTesting
    internal fun setState(s: State) { _state.value = s }

    fun dismiss() {
        job?.cancel()
        setPendingInstall(App.appContext, null)
        _state.value = State.Idle
    }

    /** 받을 곳(버전마다 이름이 다르다) */
    internal fun apkFile(context: Context, release: Release): File =
        File(File(context.cacheDir, "update"), "wowa_${versionOf(release.tag)}.apk")

    /** 태그에서 버전 숫자만(앞의 v·V 와 공백을 뺀다). 파일 이름과 정리 규칙이 같은 값을 쓴다. */
    internal fun versionOf(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** 받기 → 설치 화면. APK 가 없는 릴리즈면 릴리즈 페이지를 연다. */
    fun downloadAndInstall(context: Context, release: Release) {
        val url = release.apkUrl
        if (url == null) {
            openReleasePage(context)
            _state.value = State.Idle
            return
        }
        val appContext = context.applicationContext
        job?.cancel()
        job = scope.launch {
            try {
                val file = apkFile(appContext, release)
                // 같은 버전을 이미 다 받아 두었으면 다시 받지 않는다.
                if (!(file.isFile && (release.apkSize <= 0 || file.length() == release.apkSize))) {
                    _state.value = State.Downloading(release, 0f)
                    download(url, file, release.apkSize) { f -> _state.value = State.Downloading(release, f) }
                }
                install(appContext, release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(release, e.message ?: "받지 못했습니다")
            }
        }
    }

    /** 받아 둔 [file] 로 설치 화면을 연다(실패 창의 '다시 시도') */
    fun installDownloaded(context: Context, release: Release, file: File) {
        _state.value = State.ReadyToInstall(release, file)
        retryInstall(context)
    }

    /** 허용하고 돌아온 뒤 다시 설치 */
    fun retryInstall(context: Context) {
        val (release, file) = when (val s = _state.value) {
            is State.NeedPermission -> s.release to s.file
            is State.ReadyToInstall -> s.release to s.file
            else -> return
        }
        try {
            install(context.applicationContext, release, file)
        } catch (e: Exception) {
            _state.value = State.Failed(release, e.message ?: "설치 화면을 열지 못했습니다", file)
        }
    }

    /**
     * 설치 허용을 바꾸고 돌아오면 앱이 새로 시작되어 '설치' 창이 사라진다. 받아 둔 APK 가 그대로 있으면 창을 다시 띄운다.
     * 앱을 켤 때 [cleanup] 뒤에 부른다.
     */
    fun resumePendingInstall(context: Context) {
        if (_state.value !is State.Idle) return
        val prefs = context.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        val tag = prefs.getString(PREF_PENDING_INSTALL, null) ?: return
        val release = Release(tag, "", apkUrl = null, apkName = "", apkSize = -1L)
        val file = apkFile(context, release)
        if (!release.isNewer || !file.isFile) {
            prefs.edit().remove(PREF_PENDING_INSTALL).apply()
            return
        }
        val r = release.copy(apkName = file.name, apkSize = file.length())
        _state.value = if (canInstall(context)) State.ReadyToInstall(r, file) else State.NeedPermission(r, file)
    }

    private fun setPendingInstall(context: Context, tag: String?) {
        val e = context.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE).edit()
        (if (tag == null) e.remove(PREF_PENDING_INSTALL) else e.putString(PREF_PENDING_INSTALL, tag)).apply()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openReleasePage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun install(context: Context, release: Release, file: File) {
        if (!canInstall(context)) {
            setPendingInstall(context, release.tag)
            _state.value = State.NeedPermission(release, file)
            return
        }
        if (!inForeground) {
            // 뒤에서 띄우면 안드로이드가 조용히 막는다. 돌아왔을 때 '설치' 창을 보여 준다(앱이 꺼져도 다시 묻게 적어 둔다).
            setPendingInstall(context, release.tag)
            _state.value = State.ReadyToInstall(release, file)
            return
        }
        setPendingInstall(context, null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        _state.value = State.Idle
    }

    /** [url] 을 [target] 에 받는다. 다 받은 뒤에야 이름을 바꿔, 반쯤 받은 파일을 완성본으로 착각하지 않게 한다. */
    private suspend fun download(url: String, target: File, expected: Long, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        // 받을 때마다 다른 임시 이름을 쓴다. 취소한 앞 작업이 아직 끝나지 않았을 때 다시 받으면,
        // 앞 작업의 finally 가 새 작업의 임시 파일을 지우지 않게 한다.
        val part = File(target.parentFile, target.name + "." + java.util.UUID.randomUUID().toString().take(8) + ".part")
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                if (code == 403 || code == 429) throw IllegalStateException("받기 횟수가 많아 잠시 막혔습니다. 조금 뒤 다시 시도해 주세요")
                if (code !in 200..299) throw IllegalStateException("받지 못했습니다 (서버 응답 $code)")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: expected
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (!isActive) throw CancellationException()
            if (expected > 0 && part.length() != expected) throw IllegalStateException("받은 파일 크기가 맞지 않습니다")
            target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("받은 파일을 저장하지 못했습니다")
        } finally {
            part.delete()
        }
    }

    /** 다른 버전(이미 설치했거나 지난 것)의 받은 APK 를 지운다. 앱을 켤 때 부른다. */
    fun cleanup(context: Context) {
        // 받는 중에 액티비티만 다시 만들어진 경우(다크 모드·언어 변경 등) 쓰고 있는 .part 를 지우면 안 된다.
        if (_state.value is State.Downloading) return
        val dir = File(context.cacheDir, "update")
        dir.listFiles()?.forEach { f ->
            val version = f.name.removePrefix("wowa_").removeSuffix(".apk").removeSuffix(".part")
            if (f.name.endsWith(".part") || !isNewerVersion(version, BuildConfig.VERSION_NAME)) f.delete()
        }
    }

    /** 하루 한 번(오전 5시 업데이트) 새 버전이면 알린다. 같은 버전은 한 번만. */
    // 알림 권한은 위에서 PendingGoodsNotifier.hasPermission 으로 확인하고, notify 는 runCatching 으로 감싼다.
    @SuppressLint("MissingPermission")
    suspend fun notifyIfNew(context: Context) {
        val r = try {
            fetchLatest()
        } catch (e: CancellationException) {
            throw e // 작업이 멈추는 중이면 그대로 알린다(성공으로 넘기지 않게)
        } catch (e: Exception) {
            return
        }
        if (!r.isNewer) return
        val prefs = context.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
        if (prefs.getString(PREF_NOTIFIED, null) == r.tag) return
        if (!PendingGoodsNotifier.hasPermission(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "앱 업데이트", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "새 버전이 나오면 알려 줍니다" }
            )
        }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, OpenTarget.intentFor(context, OpenTarget.AppUpdate),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("새 버전 ${r.display}이 나왔어요")
            .setContentText("눌러서 바로 받아 설치하세요 (지금 v${BuildConfig.VERSION_NAME})")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // 띄우지 못했으면 다음 날 다시 알리도록 기록하지 않는다.
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onSuccess { prefs.edit().putString(PREF_NOTIFIED, r.tag).apply() }
    }
}

/** 앱 업데이트 창들(새 버전 안내 · 받는 중 · 설치 허용 · 실패). 앱 맨 위에 하나만 둔다. */
@Composable
fun AppUpdateDialogs() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsState()
    when (val s = state) {
        is AppUpdater.State.Available -> AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("업데이트 가능") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("v${BuildConfig.VERSION_NAME} → ${s.release.display}")
                    Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                    Text(
                        if (s.release.apkUrl != null) "받아서 바로 설치 화면을 띄울게요." else "이 버전은 릴리즈 페이지에서 받아 주세요.",
                        style = AppStyles.textCardSmall
                    )
                    if (s.release.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                        Text(s.release.notes.trim(), style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { AppUpdater.downloadAndInstall(context, s.release) }) {
                    Text(if (s.release.apkUrl != null) "업데이트" else "릴리즈 페이지")
                }
            },
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("나중에") } }
        )
        is AppUpdater.State.Downloading -> AlertDialog(
            shape = RectangleShape,
            onDismissRequest = {},
            title = { Text("${s.release.display} 받는 중") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                    Text("${(s.fraction * 100).toInt()}%", style = AppStyles.textCardSmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("취소") } }
        )
        is AppUpdater.State.NeedPermission -> AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("설치 허용이 필요해요") },
            text = { Text("처음 한 번, 설정에서 이 앱의 '출처를 알 수 없는 앱 설치'를 허용해 주세요. 허용하고 돌아와 '설치'를 누르면 됩니다.") },
            confirmButton = { TextButton(onClick = { AppUpdater.retryInstall(context) }) { Text("설치") } },
            dismissButton = { TextButton(onClick = { AppUpdater.openInstallPermissionSettings(context) }) { Text("설정 열기") } }
        )
        is AppUpdater.State.ReadyToInstall -> AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("${s.release.display} 받기 완료") },
            text = { Text("'설치'를 누르면 설치 화면이 열려요.") },
            confirmButton = { TextButton(onClick = { AppUpdater.retryInstall(context) }) { Text("설치") } },
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("나중에") } }
        )
        is AppUpdater.State.Failed -> AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("업데이트하지 못했어요") },
            text = { Text("${s.message}\n릴리즈 페이지에서 직접 받을 수도 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    val r = s.release
                    val f = s.file
                    when {
                        r != null && f != null && f.isFile -> AppUpdater.installDownloaded(context, r, f)
                        r != null -> AppUpdater.downloadAndInstall(context, r)
                        else -> AppUpdater.dismiss()
                    }
                }) { Text("다시 시도") }
            },
            dismissButton = { TextButton(onClick = { AppUpdater.openReleasePage(context); AppUpdater.dismiss() }) { Text("릴리즈 페이지") } }
        )
        else -> Unit
    }
}
