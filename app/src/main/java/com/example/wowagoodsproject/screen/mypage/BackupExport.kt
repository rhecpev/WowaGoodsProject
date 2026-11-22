package com.example.wowagoodsproject.screen.mypage

import com.example.wowagoodsproject.component.withoutEmoji

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 백업 파일 추출. 명조 마이페이지(전체 또는 장르를 골라)와 배신 굿즈 장르 마이페이지(그 장르만)가 같이 쓴다.
 *
 * 1. [selection] 범위로 zip 을 만든다.
 * 2. 만든 파일을 다시 열어 센 것이 기기 데이터(같은 범위)와 같은지 확인한다. 다르면 지우고 실패로 알린다.
 * 3. 안드로이드 10 이상은 다운로드 폴더에 저장하고, 그 전 버전은 공유 창으로 내보낸다.
 */
object BackupExport {

    /** 추출 파일 이름 앞부분. '용량 정리'가 앱 폴더에 남은 백업을 찾을 때도 쓴다. */
    const val FILE_PREFIX = "wuwaGoods_"

    /**
     * 항목 이름을 이만큼(UTF-8 바이트)까지만 잇고, 넘으면 '외N' 으로 줄인다.
     * 파일 이름은 255바이트까지라 한글(글자당 3바이트)도 넉넉히 들어가게 바이트로 센다.
     */
    private const val MAX_ITEMS_BYTES = 150

    /**
     * 추출 파일 이름: wuwaGoods_버전_yyyyMMdd_(전체 | 고른 항목을 _ 로 이은 것).zip
     * @param items null 이면 전체(명조 + 모든 장르). 아니면 담은 항목 이름(명조, 장르 이름…) 순서대로
     */
    fun fileName(items: List<String>?, version: String, date: java.util.Date = java.util.Date()): String {
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(date)
        // 예전에 이모지를 넣어 저장한 이름도 파일 이름에는 글자만 쓴다(자를 때 반만 남지 않게).
        fun clean(s: String) = s.withoutEmoji().trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        val names = items?.map(::clean)?.filter { it.isNotEmpty() }
        val tail = when {
            names == null -> "전체"
            names.isEmpty() -> "빈백업"
            else -> {
                fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size
                val kept = mutableListOf<String>()
                for (n in names) {
                    if (kept.isEmpty()) {
                        // 첫 항목도 너무 길면 잘라서 넣는다(이름이 아주 긴 장르 하나만 담아도 추출이 되게).
                        var first = n
                        while (bytes(first) > MAX_ITEMS_BYTES) first = first.dropLast(1)
                        kept += first
                        continue
                    }
                    if (bytes((kept + n).joinToString("_")) > MAX_ITEMS_BYTES) break
                    kept += n
                }
                kept.joinToString("_") + if (kept.size < names.size) "_외${names.size - kept.size}" else ""
            }
        }
        return "$FILE_PREFIX${clean(version)}_${day}_$tail.zip"
    }

    /** [selection] 에 맞는 파일 이름. 명조와 모든 장르를 담으면 '전체' */
    suspend fun fileNameFor(selection: ExportSelection): String {
        val genres = com.example.wowagoodsproject.App.betrayDatabase.betrayDao().genres()
        val ids = selection.genreIds
        val picked = if (ids == null) genres else genres.filter { it.genreId in ids }
        val all = selection.includeWuwa && picked.size == genres.size
        val items = if (all) null else (if (selection.includeWuwa) listOf("명조") else emptyList()) + picked.map { it.genreNm }
        return fileName(items, com.example.wowagoodsproject.BuildConfig.VERSION_NAME)
    }

    /** 명조와 모든 장르를 고른 선택은 전체 백업([ExportSelection.ALL])으로 본다. 이름이 '전체'인데 부분 백업이 되지 않게. */
    internal suspend fun normalized(selection: ExportSelection): ExportSelection {
        val ids = selection.genreIds ?: return selection
        if (!selection.includeWuwa) return selection
        val all = com.example.wowagoodsproject.App.betrayDatabase.betrayDao().genres().map { it.genreId }
        // 장르가 하나도 없는 기기의 '명조만'(빈 선택)을 전체로 바꾸면, 빈 전체 배신 굿즈 파일이 들어가
        // 다른 기기에서 불러올 때 그 기기의 장르를 모두 지운다. 장르가 있고 모두 골랐을 때만 전체로 본다.
        return if (all.isNotEmpty() && ids.containsAll(all)) ExportSelection.ALL else selection
    }

    /** @return 만든 파일을 다시 읽어 센 내용(추출 완료 알림에 쓴다) */
    suspend fun export(
        context: Context,
        requested: ExportSelection = ExportSelection.ALL,
        /** null 이면 [fileNameFor] 로 정한다 */
        fileName: String? = null,
        onProgress: (Float) -> Unit = {}
    ): ExportPreview = withContext(Dispatchers.IO) {
        val selection = normalized(requested)
        // 다운로드 폴더에 같은 이름이 있으면 안드로이드가 알아서 '이름 (1).zip' 처럼 붙여 따로 남긴다(지난 추출 파일은 그대로).
        val displayName = fileName ?: fileNameFor(selection)
        // 앱 전용 폴더의 지난 공유용 복사본(안드로이드 9 이하)은 그 공유가 이미 끝났으니 지운다. 다운로드 폴더는 건드리지 않는다.
        removeLeftoverCopies(context)
        val zipFile = File(context.filesDir, displayName)
        try {
            exportWith(context, selection, displayName, zipFile, onProgress)
        } catch (e: Throwable) {
            // 어디서 멈추든(만들기·확인·다운로드 폴더로 옮기기) 앱 전용 폴더에 반쯤 만든 파일을 남기지 않는다.
            zipFile.delete()
            throw e
        }
    }

    /** 앱 전용 폴더에 남은 추출 파일(옛 이름 wowa_backup*.zip 포함) */
    internal fun leftoverCopies(context: Context): List<File> =
        context.filesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip") && (it.name.startsWith(FILE_PREFIX) || it.name.startsWith("wowa_backup")) }
            .orEmpty()

    /** 다운로드 폴더에 쓴 파일의 실제 크기. 열지 못하면 -1(검증 실패로 본다). */
    internal fun copiedSize(resolver: android.content.ContentResolver, uri: android.net.Uri): Long =
        runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L

    private fun removeLeftoverCopies(context: Context) = leftoverCopies(context).forEach { it.delete() }

    private suspend fun exportWith(
        context: Context,
        selection: ExportSelection,
        displayName: String,
        zipFile: File,
        onProgress: (Float) -> Unit
    ): ExportPreview {
        val expected = ExportPreview.load(selection)
        // zip 만들기가 대부분이라 0.9 까지, 다운로드 폴더로 옮기기가 나머지.
        BackupZip.write(zipFile, { onProgress((it * 0.9f).coerceIn(0f, 1f)) }, selection)
        val written = ExportPreview.fromZip(zipFile)
        // 세고 나서 파일을 쓰는 사이에 데이터가 바뀌었을 수 있다(오전 5시 자동 업데이트 등). 다르면 한 번 다시 센다.
        val diffs = expected.differencesFrom(written).let { first ->
            if (first.isEmpty()) first else ExportPreview.load(selection).differencesFrom(written)
        }
        if (diffs.isNotEmpty()) {
            throw IllegalStateException("만든 파일이 기기 데이터와 다릅니다(${diffs.joinToString()}). 다시 추출해 주세요")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
            }
            val resolver = context.contentResolver
            val downloadUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("다운로드 폴더에 파일을 만들지 못했습니다")
            try {
                val output = resolver.openOutputStream(downloadUri) ?: throw IllegalStateException("다운로드 폴더에 쓰지 못했습니다")
                output.use { FileInputStream(zipFile).use { input -> input.copyTo(it) } }
                // 오류 없이 덜 복사되는 경우도 잡도록, 다운로드 폴더에 생긴 파일 크기를 원본과 맞춰 본다.
                val copied = copiedSize(resolver, downloadUri)
                if (copied != zipFile.length()) {
                    throw IllegalStateException("다운로드 폴더에 저장한 파일 크기가 다릅니다(${copied} / ${zipFile.length()}바이트). 다시 추출해 주세요")
                }
            } catch (e: Exception) {
                // 반쯤 쓴 파일이 다운로드 폴더에 남으면 나중에 백업으로 착각해 불러올 수 있다.
                resolver.delete(downloadUri, null, null)
                throw e
            }
            // 다운로드 폴더로 옮겼으니 앱 안의 임시 파일은 지운다.
            zipFile.delete()
            onProgress(1f)
        } else {
            // 공유받는 앱이 언제 다 읽을지 알 수 없어 여기서는 지우지 않는다. 다음 추출 때 지우고, '용량 정리'로도 지울 수 있다.
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "백업 파일 내보내기").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        return written
    }
}
