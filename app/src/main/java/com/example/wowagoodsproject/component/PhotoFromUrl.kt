package com.example.wowagoodsproject.component

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * 사진을 URL 로 넣기. 사진 주소를 적으면 그 사진을 받아 임시 파일로 두고, 카메라·갤러리로 고른 사진과 똑같이 넘긴다.
 * 주소를 그대로 저장하지 않는 이유: 백업 zip 이 사진 파일을 담고, 아이콘 자르기도 파일이 있어야 해서다.
 */

/** 받을 수 있는 사진 크기 상한 */
private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

/** https 주소인지. 암호화하지 않은 http 는 안드로이드가 기본으로 막아 받을 수 없다. */
fun isImageUrl(text: String): Boolean = text.trim().startsWith("https://", true)

/**
 * [url] 의 사진을 앱 캐시에 받아 그 파일의 Uri 를 돌려준다. 사진이 아니거나 받지 못하면 예외(이유를 담은 메시지).
 * 받은 임시 파일(url_temp_*)은 '용량 정리'의 임시 파일로도 지울 수 있다.
 */
suspend fun downloadImageToCache(context: Context, url: String): Uri = withContext(Dispatchers.IO) {
    // 저장한 뒤에도 남는 예전 임시 사진(최대 20MB)이 쌓이지 않게, 하루 넘은 것은 받기 전에 지운다.
    // 방금 받은 것은 아직 등록 화면에서 쓰고 있을 수 있어 남긴다.
    val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    context.cacheDir.listFiles { f -> f.name.startsWith("url_temp_") && f.lastModified() < dayAgo }?.forEach { it.delete() }
    val target = File(context.cacheDir, "url_temp_${System.currentTimeMillis()}.img")
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WuwaGoods")
        }
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("사진을 받지 못했습니다 (응답 $code)")
        if (conn.contentLengthLong > MAX_IMAGE_BYTES) throw IllegalStateException("사진이 너무 큽니다 (20MB 까지)")
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMAGE_BYTES) throw IllegalStateException("사진이 너무 큽니다 (20MB 까지)")
                    out.write(buf, 0, n)
                }
            }
        }
        // 웹페이지 주소처럼 사진이 아닌 것을 받았으면 알린다.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("사진 주소가 아닙니다. 사진을 길게 눌러 '이미지 주소 복사'로 받은 주소를 넣어 주세요")
        Uri.fromFile(target)
    } catch (e: Exception) {
        target.delete()
        throw if (e is IllegalStateException) e else IllegalStateException("사진을 받지 못했습니다 (${e.message ?: "인터넷 연결을 확인해 주세요"})")
    } finally {
        // 성공·실패 모두 연결을 끊는다(실패 응답이면 열린 채로 남지 않게).
        conn?.disconnect()
    }
}

/**
 * 사진 URL 입력 창. 주소를 적으면 아래에 미리보기가 뜨고, '불러오기'를 누르면 받아서 [onPicked] 로 넘긴다.
 */
@Composable
fun PhotoUrlDialog(onPicked: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // '미리보기'를 누른 주소. 글자를 칠 때마다 받지 않고, 누를 때만 받는다. 주소를 고치면 다시 눌러야 한다.
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val valid = isImageUrl(url)
    fun load() {
        if (!valid || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val uri = downloadImageToCache(context, url)
                loading = false
                onPicked(uri)
            } catch (e: Exception) {
                loading = false
                error = e.message
            }
        }
    }
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("URL 로 사진 넣기") },
        text = {
            // 가로 화면처럼 낮으면 미리보기를 줄이고, 그래도 넘치면 창 안에서 스크롤(버튼이 밀려나지 않게)
            val previewHeight = minOf(180f, androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.3f).dp
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.example.wowagoodsproject.component.UnderlineTextField(
                    value = url,
                    onValueChange = { url = it; error = null; previewUrl = null },
                    label = { Text("사진 주소") },
                    placeholder = { Text("https://…") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    isError = error != null || (url.isNotBlank() && !valid),
                    supportingText = {
                        when {
                            error != null -> Text(error!!)
                            url.isNotBlank() && !valid -> Text("https:// 로 시작하는 주소를 넣어 주세요")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { load() }),
                    modifier = Modifier.fillMaxWidth()
                )
                // 미리보기: '미리보기'를 눌렀을 때만 받아 보여 준다(불러오기 전에 맞는 사진인지 확인).
                if (valid && previewUrl == null) {
                    OutlinedButton(
                        onClick = { previewUrl = url.trim() },
                        enabled = !loading,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("미리보기") }
                }
                if (valid && previewUrl != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(previewHeight).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(previewUrl),
                            contentDescription = "사진 미리보기",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(6.dp)
                        )
                        if (loading) CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { load() }, enabled = valid && !loading) { Text("불러오기") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("취소") } }
    )
}
