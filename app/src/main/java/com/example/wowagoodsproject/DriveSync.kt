package com.example.wowagoodsproject

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 구글 드라이브 연동. 마이페이지에서 켜 두면 보유 현황·2차창작·선호 캐릭터가 바뀔 때마다
 * 데이터 추출과 같은 백업 zip 을 이용자 드라이브의 앱 전용 숨김 폴더(appDataFolder)에 올린다.
 *
 * 웹(wuwa-goods-web)도 같은 Google Cloud 프로젝트의 같은 파일(wuwa-goods-userdata.zip)을 읽으므로,
 * 웹에서 같은 구글 계정으로 연결하면 앱에서 바꾼 내용이 보인다. 파일 형식은 앱 백업 형식 그대로다.
 *
 * - 권한은 drive.appdata 하나뿐이라 이 앱이 만든 파일 말고는 드라이브를 볼 수 없다.
 * - 바뀐 뒤 [UPLOAD_DELAY_SEC]초 동안 조용하면 올린다(WorkManager 라 앱을 닫아도 마저 올린다).
 * - 처음 연결할 때 드라이브에 이미 파일(웹에서 쓰던 기록 등)이 있으면 덮기 전에 날짜를 붙인 사본으로 남긴다.
 * - 열쇠(access token)는 구글 플레이 서비스가 맡아 조용히 새로 받는다. 이용자가 허락을 거뒀을 때만 다시 연결한다.
 */
object DriveSync {

    private const val TAG = "DriveSync"
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "wuwa-goods-userdata.zip"
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val WORK_NAME = "drive_sync"
    private const val UPLOAD_DELAY_SEC = 5L

    private const val KEY_LINKED = "drive_linked"
    private const val KEY_NEEDS_AUTH = "drive_needs_auth"
    private const val KEY_LAST_SYNC = "drive_last_sync"
    private const val KEY_ERROR = "drive_last_error"
    private const val KEY_FINGERPRINT = "drive_fingerprint"
    private const val KEY_FILE_ID = "drive_file_id"
    /** 처음 연결한 뒤 아직 드라이브에 있던 파일을 사본으로 남기지 않았는지 */
    private const val KEY_KEEP_EXISTING = "drive_keep_existing"

    data class State(
        val linked: Boolean = false,
        /** 허락이 풀려 사람이 다시 연결해야 한다 */
        val needsAuth: Boolean = false,
        val syncing: Boolean = false,
        val lastSync: String? = null,
        val error: String? = null
    )

    enum class Upload { DONE, UNCHANGED, NEEDS_AUTH, FAILED }

    private val prefs get() = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
    private val uploadMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun reloadState(syncing: Boolean = _state.value.syncing) {
        val p = prefs
        _state.value = State(
            linked = p.getBoolean(KEY_LINKED, false),
            needsAuth = p.getBoolean(KEY_NEEDS_AUTH, false),
            syncing = syncing,
            lastSync = p.getString(KEY_LAST_SYNC, null),
            error = p.getString(KEY_ERROR, null)
        )
    }

    val isLinked get() = prefs.getBoolean(KEY_LINKED, false)

    /** 앱 시작 때 한 번. 유저 데이터가 담긴 표가 바뀌면 올리기를 예약한다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        reloadState(syncing = false)
        fun observe(db: RoomDatabase, table: String) {
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf(table)) {
                override fun onInvalidated(tables: Set<String>) {
                    if (isLinked) schedule(app)
                }
            })
        }
        observe(App.database, "tb_goods")
        observe(App.fanDatabase, "tb_fan_goods")
        observe(App.charaDatabase, "tb_character")
    }

    /** 올리기를 예약한다. 이미 예약돼 있으면 새로 바꿔 끼워 마지막 변경에서 [delaySec]초 뒤에 돈다. */
    fun schedule(context: Context, delaySec: Long = UPLOAD_DELAY_SEC, force: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(DriveSyncWorker.KEY_FORCE to force))
                .build()
        )
    }

    // ── 연결 ────────────────────────────────────────────────────────────────

    private fun authorizationRequest() =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /**
     * 연결을 시작한다. 이미 허락한 계정이면 바로 연결하고 null 을,
     * 계정을 고르거나 허락을 받아야 하면 띄울 화면(PendingIntent)을 돌려준다. 그 결과는 [finishConnect] 로 넘긴다.
     */
    suspend fun connect(context: Context): PendingIntent? {
        val result = Identity.getAuthorizationClient(context).authorize(authorizationRequest()).await()
        if (result.hasResolution()) return result.pendingIntent
        link(context, result)
        return null
    }

    /** [connect] 가 띄운 화면의 결과로 연결을 마친다. 취소했거나 실패하면 예외를 던진다. */
    fun finishConnect(context: Context, data: Intent?) {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        link(context, result)
    }

    private fun link(context: Context, result: AuthorizationResult) {
        if (result.accessToken == null) throw IllegalStateException("구글 드라이브 권한을 받지 못했습니다")
        val editor = prefs.edit()
            .putBoolean(KEY_NEEDS_AUTH, false)
            .remove(KEY_ERROR)
        // 다시 연결(허락이 풀렸던 경우)이 아니라 처음 켜는 것이면 드라이브의 파일을 새로 찾고, 있던 것은 사본으로 남긴다.
        if (!isLinked) {
            editor.putBoolean(KEY_KEEP_EXISTING, true)
                .remove(KEY_FILE_ID)
                .remove(KEY_FINGERPRINT)
        }
        editor.putBoolean(KEY_LINKED, true).apply()
        reloadState()
        schedule(context, delaySec = 0, force = true)
    }

    /** 연동을 끈다. 드라이브의 파일은 그대로 둔다(웹에서는 마지막으로 올린 기록이 계속 보인다). */
    fun unlink(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs.edit()
            .remove(KEY_LINKED)
            .remove(KEY_NEEDS_AUTH)
            .remove(KEY_ERROR)
            .remove(KEY_FILE_ID)
            .remove(KEY_FINGERPRINT)
            .remove(KEY_KEEP_EXISTING)
            .apply()
        reloadState(syncing = false)
    }

    // ── 올리기 ──────────────────────────────────────────────────────────────

    /** 사람이 보지 않는 곳(작업)에서 열쇠를 받는다. 다시 허락받아야 하면 null */
    private suspend fun silentToken(context: Context): String? {
        val result = Identity.getAuthorizationClient(context).authorize(authorizationRequest()).await()
        return if (result.hasResolution()) null else result.accessToken
    }

    /**
     * 지금 내용을 드라이브에 올린다. [force] 가 아니면 지난번에 올린 것과 내용이 같을 때 건너뛴다.
     * 굿즈 데이터 업데이트(오전 5시)처럼 유저 기록이 그대로인 변경에도 불리기 때문이다.
     */
    suspend fun upload(context: Context, force: Boolean): Upload = uploadMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!isLinked) return@withContext Upload.UNCHANGED
            // 굿즈 목록을 아직 받지 못했으면 보유 기록이 하나도 없는 파일을 올려 웹 쪽 기록을 지우게 된다.
            if (App.database.goodsDao().count() == 0) return@withContext Upload.UNCHANGED
            reloadState(syncing = true)
            val zipFile = File(context.cacheDir, "drive_upload.zip")
            try {
                BackupZip.write(zipFile)
                val fingerprint = fingerprint(zipFile)
                if (!force && fingerprint == prefs.getString(KEY_FINGERPRINT, null)) return@withContext Upload.UNCHANGED

                var token = silentToken(context) ?: return@withContext needsAuth()
                try {
                    send(token, zipFile)
                } catch (e: AuthExpired) {
                    // 플레이 서비스가 들고 있던 열쇠가 끝났다. 버리고 한 번 새로 받는다.
                    GoogleAuthUtil.clearToken(context, token)
                    token = silentToken(context) ?: return@withContext needsAuth()
                    send(token, zipFile)
                }

                prefs.edit()
                    .putString(KEY_FINGERPRINT, fingerprint)
                    .putString(KEY_LAST_SYNC, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
                    .remove(KEY_ERROR)
                    .apply()
                Upload.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "드라이브에 올리지 못함", e)
                prefs.edit().putString(KEY_ERROR, e.message ?: e.javaClass.simpleName).apply()
                Upload.FAILED
            } finally {
                zipFile.delete()
                reloadState(syncing = false)
            }
        }
    }

    private fun needsAuth(): Upload {
        prefs.edit().putBoolean(KEY_NEEDS_AUTH, true).apply()
        return Upload.NEEDS_AUTH
    }

    /** zip 안의 항목 이름과 내용으로 만든 지문. zip 파일 자체는 만든 시각이 들어가 매번 달라서 쓰지 않는다. */
    private fun fingerprint(zipFile: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            val buffer = ByteArray(64 * 1024)
            var entry = zip.nextEntry
            while (entry != null) {
                digest.update(entry.name.toByteArray(Charsets.UTF_8))
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                entry = zip.nextEntry
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun send(token: String, zipFile: File) {
        var fileId = prefs.getString(KEY_FILE_ID, null) ?: findFileId(token)

        // 처음 연결: 드라이브에 이미 있던 기록(웹에서 쓰던 것 등)은 덮기 전에 사본으로 남긴다.
        if (fileId != null && prefs.getBoolean(KEY_KEEP_EXISTING, false)) {
            val existing = request(token, "$FILES/$fileId?alt=media").use { it.inputStream.readBytes() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            createFile(token, FILE_NAME.removeSuffix(".zip") + "-before-app-$stamp.zip", existing)
            Log.d(TAG, "드라이브에 있던 기록을 사본으로 남김 (${existing.size}바이트)")
        }
        prefs.edit().remove(KEY_KEEP_EXISTING).apply()

        val bytes = zipFile.readBytes()
        if (fileId != null) {
            try {
                request(token, "$UPLOAD/$fileId?uploadType=media&fields=id", "PATCH", "application/zip", bytes).close()
            } catch (e: NotFound) {
                // 웹에서 드라이브 기록을 지웠다. 새로 만든다.
                fileId = null
            }
        }
        if (fileId == null) fileId = createFile(token, FILE_NAME, bytes)
        prefs.edit().putString(KEY_FILE_ID, fileId).apply()
    }

    /** 앱 전용 폴더에서 백업 파일을 찾는다. 여럿이면 가장 최근에 바뀐 것 */
    private fun findFileId(token: String): String? {
        val q = URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val json = request(token, "$FILES?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&fields=files(id)&pageSize=10")
            .use { it.inputStream.bufferedReader().readText() }
        val files = JSONObject(json).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    /** 앱 전용 폴더에 새 파일을 만든다(정보 + 내용을 한 번에 보내는 multipart). 새 파일 id 를 돌려준다. */
    private fun createFile(token: String, name: String, bytes: ByteArray): String {
        val boundary = "wuwa" + System.nanoTime()
        val meta = JSONObject()
            .put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", "application/zip")
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/zip\r\n\r\n"
        val body = head.toByteArray(Charsets.UTF_8) + bytes + "\r\n--$boundary--".toByteArray(Charsets.UTF_8)
        val json = request(token, "$UPLOAD?uploadType=multipart&fields=id", "POST", "multipart/related; boundary=$boundary", body)
            .use { it.inputStream.bufferedReader().readText() }
        return JSONObject(json).getString("id")
    }

    private class AuthExpired : IOException("구글 드라이브 연결이 만료되었습니다")
    private class NotFound : IOException("드라이브 파일이 없습니다")

    private class Response(val connection: HttpURLConnection) : AutoCloseable {
        val inputStream get() = connection.inputStream
        override fun close() = connection.disconnect()
    }

    private fun request(
        token: String,
        url: String,
        method: String = "GET",
        contentType: String? = null,
        body: ByteArray? = null
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        // HttpURLConnection 은 PATCH 를 모르므로 POST 로 보내고 덮어쓴다(드라이브가 받아 준다).
        connection.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        if (code in 200..299) return Response(connection)
        val detail = connection.errorStream?.bufferedReader()?.readText().orEmpty()
        connection.disconnect()
        Log.w(TAG, "$method $url → HTTP $code $detail")
        when (code) {
            401 -> throw AuthExpired()
            404 -> throw NotFound()
            else -> throw IOException("구글 드라이브 오류 (HTTP $code)")
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}

/** [DriveSync.schedule] 이 거는 올리기 작업. 인터넷 오류면 몇 번 다시 해 본다. */
class DriveSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (DriveSync.upload(applicationContext, force = inputData.getBoolean(KEY_FORCE, false))) {
            DriveSync.Upload.FAILED -> if (runAttemptCount < 5) Result.retry() else Result.failure()
            else -> Result.success()
        }

    companion object {
        const val KEY_FORCE = "force"
    }
}
