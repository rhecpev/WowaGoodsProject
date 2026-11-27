package com.example.wowagoodsproject.component

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 캐릭터 사진에서 뽑은 색 후보를 저장해 둔다(마이페이지 보유 굿즈 게이지 등).
 *
 * 왜: 색은 사진을 받아 Palette 로 뽑아야 해서, 화면을 열 때마다 하면 실기에서 늦게 뜰 때가 있다.
 * 어떻게:
 * - 사진 주소(경로·URL)를 열쇠로, 후보 색 목록과 기기 사진의 수정 시각을 함께 저장한다.
 *   열쇠가 주소 하나라 같은 경로의 사진을 바꿔도 줄이 늘지 않고 그 줄을 덮어쓴다.
 * - 화면을 그릴 때([cached])는 메모리만 본다(디스크를 읽지 않는다). 수정 시각 확인은 [candidates] 가 IO 에서 하고,
 *   사진이 바뀌었으면 새로 뽑는다.
 * - 임시 사진(앱 캐시 폴더, 예: 불러오기 미리보기)은 메모리에만 두고 저장하지 않는다.
 * - 저장값을 읽을 때 없어진 기기 사진의 줄과 예전 버전 줄은 지운다.
 * - 캐릭터마다 최종 색은 함께 보이는 캐릭터끼리 겹치지 않게 그때그때 고르므로([assignDistinctColors]) 후보만 저장한다.
 * - 앱을 켤 때 명조 캐릭터 사진의 후보를 미리 뽑아 둔다([prewarm]). 배신 굿즈 캐릭터(직접 넣은 사진)는 처음 볼 때 뽑아 저장한다.
 * - 뽑는 방법이나 저장 형식을 바꾸면 [VERSION] 을 올린다(예전 저장값은 지운다).
 * 저장소: SharedPreferences "chara_colors" (열쇠 "v2|주소" → "수정시각;rgb,rgb,..."). 앱 데이터라 백업 zip 에는 들어가지 않는다.
 */
object CharaColorCache {
    private const val PREFS = "chara_colors"
    private const val VERSION = "v2"

    /** 수정 시각(인터넷 사진은 0)과 후보 색 */
    private class Entry(val modified: Long, val colors: List<Int>)

    private val memory = ConcurrentHashMap<String, Entry>()
    @Volatile
    private var loaded = false

    private fun isRemote(path: String) = path.startsWith("http")

    /** 기기 사진의 수정 시각(없으면 0). 디스크를 읽으므로 IO 에서만 부른다. */
    private fun modifiedOf(path: String): Long = if (isRemote(path)) 0L else File(path).lastModified()

    /** 앱 캐시 폴더의 사진(임시)인지. 저장해 봐야 곧 지워진다. */
    private fun isTemporary(context: Context, path: String): Boolean =
        !isRemote(path) && runCatching { File(path).canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator) }.getOrDefault(false)

    /** 저장값을 읽어 메모리에 둔다. 없어진 기기 사진·예전 버전 줄은 지운다. 디스크를 읽으므로 IO 에서 부른다. */
    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stale = mutableListOf<String>()
            prefs.all.forEach { (k, v) ->
                if (!k.startsWith("$VERSION|") || v !is String) { stale += k; return@forEach }
                val path = k.removePrefix("$VERSION|")
                val modified = v.substringBefore(';', "").toLongOrNull()
                val colors = v.substringAfter(';', "").split(',').mapNotNull { it.toIntOrNull() }
                if (modified == null || colors.isEmpty() || (!isRemote(path) && !File(path).isFile)) { stale += k; return@forEach }
                memory[path] = Entry(modified, colors)
            }
            if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
            loaded = true
        }
    }

    /** 저장해 둔 후보(화면을 그릴 때 바로 쓴다, 디스크는 읽지 않는다). 아직 읽지 않았거나 없으면 null */
    fun cached(path: String): List<Int>? = if (!loaded || path.isEmpty()) null else memory[path]?.colors

    /**
     * 후보 색. 저장해 둔 것이 있고 사진이 그대로면 그것, 아니면 사진에서 뽑아 저장한다.
     * 사진을 못 받으면 빈 목록(저장하지 않아 다음에 다시 뽑는다)
     */
    suspend fun candidates(context: Context, path: String): List<Int> = withContext(Dispatchers.IO) {
        if (path.isEmpty()) return@withContext emptyList()
        load(context)
        val modified = modifiedOf(path)
        memory[path]?.takeIf { it.modified == modified }?.let { return@withContext it.colors }
        val colors = extract(context, path)
        if (colors.isNotEmpty()) {
            memory[path] = Entry(modified, colors)
            if (!isTemporary(context, path)) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("$VERSION|$path", "$modified;${colors.joinToString(",")}")
                    .apply()
            }
        }
        colors
    }

    /** 사진을 작게 받아 Palette 로 색을 뽑는다. 많이 쓰인 색이 먼저, 회색 계열은 색이 있는 후보 뒤로. */
    private suspend fun extract(context: Context, path: String): List<Int> = runCatching {
        val request = ImageRequest.Builder(context)
            .data(if (isRemote(path)) path else File(path))
            .size(96)
            .allowHardware(false) // Palette 가 픽셀을 읽으려면 소프트웨어 비트맵이어야 한다
            .build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching emptyList()
        Palette.from(bitmap).generate().swatches
            .sortedWith(
                compareBy<Palette.Swatch> { argbToHsl(it.rgb).s < GREY_SATURATION }
                    .thenByDescending { it.population }
            )
            .map { it.rgb }
            .distinct()
    }.getOrDefault(emptyList())

    /**
     * 앱을 켤 때 부른다. 저장값을 읽어 두고, 명조 캐릭터 사진 중 아직 없는 것을 뒤에서 하나씩 뽑는다.
     * 실패해도(오프라인 등) 화면에서 처음 볼 때 다시 뽑으므로 넘어간다.
     */
    fun prewarm(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                load(app)
                // 선호 캐릭터를 먼저(마이페이지에 먼저 보인다)
                val charas = com.example.wowagoodsproject.App.charaDatabase.charaDao().getAll()
                    .sortedWith(compareByDescending<com.example.wowagoodsproject.db.character.CharaEntity> { it.charaIsFavorite }.thenBy { it.charaFavoriteOrder })
                charas.map { it.charaUrl }.filter { it.isNotEmpty() }.distinct().forEach { candidates(app, it) }
            }
        }
    }
}
