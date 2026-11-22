package com.example.wowagoodsproject.screen.betray

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Event
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.PhotoStore
import com.example.wowagoodsproject.component.withoutEmoji
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.copyBetrayImage
import com.example.wowagoodsproject.db.betray.deleteUnusedImages
import com.example.wowagoodsproject.db.betray.updateGenreInfo
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.screen.fan.FormSection
import com.example.wowagoodsproject.ui.theme.AppStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 장르 만들기·고치기. 이름은 꼭, 사진은 없어도 된다(나중에 장르 메뉴의 '편집'에서 넣을 수 있다).
 * 장르 사진은 두 가지다.
 *   배경(직사각형) - 장르 목록 그리드 보기
 *   아이콘(원)     - 장르 목록 원 보기. 사진에서 원 범위를 골라 정사각형으로 잘라 둔다.
 * '사진 하나로'는 올린 사진을 배경으로 그대로 쓰고 같은 사진에서 아이콘 범위를 고른다.
 * '사진 두 개'는 배경과 아이콘을 따로 올린다.
 */

/** 화면에 보이는 사진 칸의 값. 저장할 때 원래 값과 견줘 무엇을 바꿀지 정한다. */
sealed interface GenrePhoto {
    /** 사진 없음 */
    data object None : GenrePhoto
    /** 이미 저장된 사진(앱 폴더 경로) */
    data class Saved(val path: String) : GenrePhoto
    /** 이번에 고른 사진 */
    data class Picked(val uri: Uri) : GenrePhoto

    /** Coil 에 넘길 값 */
    val model: Any? get() = when (this) {
        None -> null
        is Saved -> File(path)
        is Picked -> uri
    }

    /** 원 범위를 고를 때 읽을 주소 */
    val sourceUri: Uri? get() = when (this) {
        None -> null
        is Saved -> Uri.fromFile(File(path))
        is Picked -> uri
    }
}

private fun photoOf(path: String): GenrePhoto = if (path.isEmpty()) GenrePhoto.None else GenrePhoto.Saved(path)


/**
 * 이름 + 아이콘(원) + 배경(직사각형) 편집. 장르·시리즈(배신 굿즈, 명조 2차창작) 편집 화면이 함께 쓴다.
 * 사진을 넣으면 배경으로 쓰고 가운데 원을 아이콘으로 자동으로 정한다. 아이콘은 따로 바꾸기 전까지 배경을 따라간다.
 */
open class IconBannerEditViewModel : ViewModel() {
    /** 아이콘(원)도 쓰는지. 시리즈는 배경 사진만 쓴다. */
    protected open val withIcon: Boolean get() = true

    val name = MutableStateFlow("")
    val banner = MutableStateFlow<GenrePhoto>(GenrePhoto.None)
    val icon = MutableStateFlow<GenrePhoto>(GenrePhoto.None)
    /** 원 범위를 고르는 중이면 그 사진(돌림까지 맞춘 것). null 이면 편집 화면 */
    val cropBitmap = MutableStateFlow<Bitmap?>(null)
    val isSaving = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    /** 열어 둔 대상. null 이면 아직 열지 않음 */
    var openedKey: String? = null
        private set
    /**
     * 아이콘을 따로 고른 사진(아이콘의 '사진 변경'). null 이면 아이콘은 배경 사진을 따라간다:
     * 배경을 바꾸면 아이콘도 새 배경 가운데로 다시 정하고, 위치·크기 변경도 배경 사진에서 고른다.
     */
    private var iconSource: Uri? = null
    /** 지금 고르고 있는 원 범위가 어느 사진에서 나왔는지(확인하면 그 사진이 아이콘 원본이 된다) */
    private var cropSource: Uri? = null
    private var cropFromIconPhoto = false
    /** 잘라 만든 아이콘 임시 파일. 저장하거나 닫을 때 지운다. */
    private val tempFiles = mutableListOf<File>()

    /** [key] 가 같으면(화면을 돌려 다시 그릴 때) 입력을 지우지 않는다. */
    fun openWith(key: String, initialName: String, imgPath: String, iconPath: String) {
        if (openedKey == key) return
        clearTemps()
        openedKey = key
        name.value = initialName
        banner.value = photoOf(imgPath)
        icon.value = photoOf(iconPath)
        iconSource = null
        cropBitmap.value = null
    }

    fun close() {
        clearTemps()
        openedKey = null
        cropBitmap.value = null
    }

    /** 배경 사진을 넣거나 바꾼다. 아이콘이 배경을 따라가면(또는 아직 없으면) 새 사진 가운데로 아이콘을 자동으로 정한다. */
    fun setBanner(context: Context, uri: Uri) {
        banner.value = GenrePhoto.Picked(uri)
        if (withIcon && (iconSource == null || icon.value == GenrePhoto.None)) {
            iconSource = null
            autoIcon(context, uri)
        }
    }

    /** 아이콘만 다른 사진으로 바꾼다. 고르면 원 범위 화면이 열린다(취소하면 아이콘은 그대로). */
    fun setIconPhoto(context: Context, uri: Uri) {
        startCrop(context, uri, fromIconPhoto = true)
    }

    /** 아이콘 위치·크기 변경: 아이콘을 따로 골랐으면 그 사진, 아니면 배경 사진에서 원 범위를 다시 고른다. */
    fun recropIcon(context: Context) {
        val source = iconSource ?: banner.value.sourceUri ?: icon.value.sourceUri ?: return
        startCrop(context, source, fromIconPhoto = iconSource != null)
    }

    fun canRecrop(bannerPhoto: GenrePhoto, iconPhoto: GenrePhoto): Boolean =
        iconSource != null || bannerPhoto != GenrePhoto.None || iconPhoto != GenrePhoto.None

    /** 배경 사진을 뺀다. 아이콘이 배경을 따라가던 것이면 아이콘도 함께 뺀다. */
    fun removeBanner() {
        banner.value = GenrePhoto.None
        if (iconSource == null) icon.value = GenrePhoto.None
    }

    fun removeIcon() {
        icon.value = GenrePhoto.None
        iconSource = null
    }

    /** 사진 가운데의 가장 큰 원을 아이콘으로 */
    private fun autoIcon(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        isLoading.value = true
        viewModelScope.launch {
            try {
                val bitmap = PhotoStore.loadUpright(appContext, uri)
                val r = min(bitmap.width, bitmap.height) / 2f
                icon.value = GenrePhoto.Picked(saveCrop(appContext, bitmap, bitmap.width / 2f, bitmap.height / 2f, r))
                bitmap.recycle()
            } catch (e: Exception) {
                Toast.makeText(appContext, "사진을 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun startCrop(context: Context, uri: Uri, fromIconPhoto: Boolean) {
        val appContext = context.applicationContext
        isLoading.value = true
        viewModelScope.launch {
            try {
                val bitmap = PhotoStore.loadUpright(appContext, uri)
                cropSource = uri
                cropFromIconPhoto = fromIconPhoto
                cropBitmap.value = bitmap
            } catch (e: Exception) {
                Toast.makeText(appContext, "사진을 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading.value = false
            }
        }
    }

    /** 원 범위를 정했다. 원의 중심(cx, cy)과 반지름 r 은 사진 픽셀 기준이다. */
    fun confirmCrop(context: Context, cx: Float, cy: Float, r: Float) {
        val bitmap = cropBitmap.value ?: return
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                icon.value = GenrePhoto.Picked(saveCrop(appContext, bitmap, cx, cy, r))
                // 아이콘을 따로 고른 사진이면 그 사진이 아이콘 원본이 된다(배경을 바꿔도 아이콘은 그대로).
                if (cropFromIconPhoto) iconSource = cropSource
            } catch (e: Exception) {
                Toast.makeText(appContext, "아이콘을 만들지 못했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            }
            cropBitmap.value = null
        }
    }

    fun cancelCrop() {
        cropBitmap.value = null
    }

    private suspend fun saveCrop(context: Context, bitmap: Bitmap, cx: Float, cy: Float, r: Float): Uri {
        val file = cropToSquareFile(context, bitmap, cx, cy, r)
        tempFiles += file
        return Uri.fromFile(file)
    }

    /**
     * 저장한다. 새로 고른 사진은 [prefix] 이름으로 앱 폴더에 WebP 로 복사하고, [persist] 에 이름·배경·아이콘을 넘긴다
     * (사진 값: null 그대로, 빈 문자열 빼기, 그 밖에는 새 경로). [persist] 가 false 면(이름 겹침 등) 복사한 사진을 지운다.
     */
    fun saveWith(
        context: Context,
        prefix: String,
        persist: suspend (name: String, banner: String?, icon: String?) -> Boolean,
        onDone: (Boolean) -> Unit
    ) {
        if (isSaving.value) return
        val trimmed = name.value.trim()
        if (trimmed.isEmpty()) { onDone(false); return }
        isSaving.value = true
        viewModelScope.launch {
            val copied = mutableListOf<String>()
            suspend fun dropCopies() { if (copied.isNotEmpty()) App.betrayDatabase.betrayDao().deleteUnusedImages(copied) }
            try {
                suspend fun resolve(photo: GenrePhoto): String? = when (photo) {
                    GenrePhoto.None -> ""
                    is GenrePhoto.Saved -> null // 그대로
                    is GenrePhoto.Picked -> copyBetrayImage(context, photo.uri, prefix = prefix).also { copied += it }
                }
                val newBanner = resolve(banner.value)
                val newIcon = resolve(icon.value)
                if (persist(trimmed, newBanner, newIcon)) {
                    close()
                    onDone(true)
                } else {
                    dropCopies()
                    onDone(false)
                }
            } catch (e: Exception) {
                dropCopies()
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving.value = false
            }
        }
    }

    private fun clearTemps() {
        val files = tempFiles.toList()
        tempFiles.clear()
        if (files.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { files.forEach { it.delete() } }
    }

    override fun onCleared() {
        tempFiles.forEach { it.delete() }
    }
}

/** 장르 만들기·고치기 */
class BetrayGenreEditViewModel : IconBannerEditViewModel() {
    /** 열어 둔 장르(0 이면 새 장르) */
    var openedFor: Int? = null
        private set

    fun open(genre: BetrayGenreEntity?) {
        val id = genre?.genreId ?: 0
        openWith("genre:$id", genre?.genreNm.orEmpty(), genre?.genreImgPath.orEmpty(), genre?.genreIconPath.orEmpty())
        openedFor = id
    }

    /** 같은 이름의 다른 장르가 있으면 false. */
    fun save(context: Context, onDone: (Boolean) -> Unit) {
        val genreId = openedFor ?: return
        saveWith(context, "betray_genre_", { name, newBanner, newIcon ->
            val dao = App.betrayDatabase.betrayDao()
            val genres = dao.genres()
            if (genres.any { it.genreId != genreId && it.genreNm.equals(name, ignoreCase = true) }) false
            else {
                val id = if (genreId == 0) {
                    dao.insertGenre(BetrayGenreEntity(genreNm = name, genreOrder = (genres.maxOfOrNull { it.genreOrder } ?: 0) + 1)).toInt()
                } else genreId
                dao.updateGenreInfo(id, name, newBanner, newIcon)
                true
            }
        }) { ok -> if (ok) openedFor = null; onDone(ok) }
    }
}

/** 시리즈 만들기·고치기(배신 굿즈·명조 2차창작 공통). 배경 사진만 쓴다. 저장할 곳은 화면이 [IconBannerEditViewModel.saveWith] 로 정한다. */
class SeriesEditViewModel : IconBannerEditViewModel() {
    override val withIcon: Boolean get() = false
    /** 시리즈 날짜 "yyyy-MM-dd" */
    val date = MutableStateFlow("")
}

/** (cx, cy) 를 중심으로 반지름 r 인 원을 감싸는 정사각형을 잘라 [outSize] 이하로 줄여 임시 파일에 쓴다. */
internal suspend fun cropToSquareFile(
    context: Context,
    bitmap: Bitmap,
    cx: Float,
    cy: Float,
    r: Float,
    outSize: Int = 512
): File = withContext(Dispatchers.IO) {
    val side = (2 * r).roundToInt().coerceIn(1, min(bitmap.width, bitmap.height))
    val left = (cx - side / 2f).roundToInt().coerceIn(0, bitmap.width - side)
    val top = (cy - side / 2f).roundToInt().coerceIn(0, bitmap.height - side)
    val cropped = Bitmap.createBitmap(bitmap, left, top, side, side)
    val out = if (side > outSize) Bitmap.createScaledBitmap(cropped, outSize, outSize, true) else cropped
    // 저장할 때 WebP 로 한 번만 압축하도록 임시 파일은 화질 손실 없는 PNG 로 둔다.
    // camera_temp_ 로 시작하면 남더라도 '용량 정리'가 지운다.
    val file = File(context.cacheDir, "camera_temp_icon_${UUID.randomUUID()}.png")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (out !== cropped) out.recycle()
    if (cropped !== bitmap) cropped.recycle()
    file
}

/** 장르 만들기·고치기 화면. [genre] 가 null 이면 새 장르. */
@Composable
fun BetrayGenreEditScreen(
    genre: BetrayGenreEntity?,
    viewModel: BetrayGenreEditViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // 처음 그릴 때 바로 연다(LaunchedEffect 로 미루면 한 번은 이전 입력이 보인다). 같은 장르면 다시 열어도 그대로다.
    remember(genre?.genreId) { viewModel.open(genre); true }
    IconBannerEditScreen(
        title = if (genre == null) "새 장르" else "장르 편집",
        subtitle = genre?.genreNm,
        nameLabel = "이름",
        nameIcon = Icons.Outlined.Category,
        revealKey = genre?.genreId,
        viewModel = viewModel,
        onSave = {
            viewModel.save(context) { ok ->
                if (ok) onClose() else Toast.makeText(context, "이미 있거나 빈 이름입니다", Toast.LENGTH_SHORT).show()
            }
        },
        onClose = onClose
    )
}

/**
 * 시리즈 만들기·고치기 화면(배신 굿즈·명조 2차창작 공통). 장르 편집 화면과 같은 구성: 이름 → 카메라/갤러리 → 아이콘 | 배경.
 * @param target 고칠 시리즈 이름(null 이면 새 시리즈, 그때 이름 칸은 [prefill])
 * @param persist 저장(이름, 배경, 아이콘 → 성공 여부). 사진 값: null 그대로, 빈 문자열 빼기
 * @param onDelete 등록한 시리즈를 지우기(null 이면 지우기 단추 없음)
 */
@Composable
fun SeriesEditScreen(
    key: String,
    target: String?,
    prefill: String = "",
    imgPath: String = "",
    iconPath: String = "",
    /** 고칠 시리즈의 날짜. 새 시리즈는 무시하고 오늘로 시작한다 */
    date: String = "",
    subtitle: String? = null,
    photoPrefix: String,
    viewModel: SeriesEditViewModel,
    persist: suspend (name: String, banner: String?, icon: String?, date: String) -> Boolean,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    remember(key) {
        // 처음 열 때만 날짜를 채운다(돌려도 사용자가 고른 날짜를 덮지 않게). 새 시리즈는 등록하는 날.
        if (viewModel.openedKey != key) viewModel.date.value = if (target == null) com.example.wowagoodsproject.component.todayDateString() else date
        viewModel.openWith(key, target ?: prefill, imgPath, iconPath)
        true
    }
    val seriesDate by viewModel.date.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { confirmDelete = false },
            title = { Text("'$target' 시리즈를 지울까요?") },
            text = { Text("이름과 사진만 지워집니다. 굿즈는 그대로이고, 굿즈에 이 시리즈 이름이 있으면 계속 나옵니다.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.close(); onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }
    IconBannerEditScreen(
        title = if (target == null) "시리즈 추가" else "시리즈 편집",
        subtitle = subtitle,
        nameLabel = "이름",
        nameIcon = Icons.Default.Collections,
        revealKey = key,
        viewModel = viewModel,
        withIcon = false,
        // 이름 아래에 날짜(다른 입력 칸과 같은 밑줄 칸, 누르면 달력)
        extraField = {
            com.example.wowagoodsproject.component.DatePickField(
                label = "날짜",
                icon = Icons.Default.Event,
                date = seriesDate,
                onDateChange = { viewModel.date.value = it },
                labelUnderIcon = true
            )
        },
        onSave = {
            val name = viewModel.name.value.trim()
            viewModel.saveWith(context, photoPrefix, { n, b, i -> persist(n, b, i, viewModel.date.value) }) { ok ->
                if (ok) onSaved(name) else Toast.makeText(context, "이미 있거나 빈 이름입니다", Toast.LENGTH_SHORT).show()
            }
        },
        onClose = onClose,
        onDelete = if (onDelete != null) ({ confirmDelete = true }) else null
    )
}

/**
 * 이름 + 아이콘 | 배경 편집 화면 본체.
 * 이름을 적으면 카메라/갤러리 버튼이 서서히 나타나고, 사진을 넣으면 배경(직사각형)과 아이콘(가운데 원)이 자동으로 정해진다.
 * 아이콘 오른쪽 위 연필 표시를 누르면 사진 변경 / 위치·크기 변경.
 */
@Composable
internal fun IconBannerEditScreen(
    title: String,
    subtitle: String?,
    nameLabel: String,
    revealKey: Any?,
    viewModel: IconBannerEditViewModel,
    onSave: () -> Unit,
    onClose: () -> Unit,
    /** 있으면 사진 아래 단추가 [삭제 | 저장] 으로 나뉜다 */
    onDelete: (() -> Unit)? = null,
    /** false 면 배경 사진만(시리즈) */
    withIcon: Boolean = true,
    /** 이름 칸 왼쪽 아이콘(굿즈 등록 칸들과 같은 자리) */
    nameIcon: ImageVector? = null,
    /** 이름 칸 바로 아래에 더 둘 칸(시리즈 날짜) */
    extraField: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val name by viewModel.name.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val icon by viewModel.icon.collectAsState()
    val cropBitmap by viewModel.cropBitmap.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val close = { viewModel.close(); onClose() }
    // 사진 런처는 원 범위 화면을 열고 닫아도 같아야 결과를 받으므로 화면을 나누기 전에 만든다.
    val bannerLaunch = rememberPhotoLaunch { viewModel.setBanner(context, it) }
    val iconLaunch = rememberPhotoLaunch { viewModel.setIconPhoto(context, it) }

    val crop = cropBitmap
    if (crop != null) {
        CircleCropScreen(
            bitmap = crop,
            onCancel = { viewModel.cancelCrop() },
            onConfirm = { cx, cy, r -> viewModel.confirmCrop(context, cx, cy, r) }
        )
        return
    }
    BackHandler(onBack = close)

    val nameFields: @Composable () -> Unit = {
        UnderlineNameField(
            value = name,
            // 이름은 글자만 받는다(이모지는 입력해도 들어가지 않는다).
            onValueChange = { viewModel.name.value = it.withoutEmoji() },
            label = nameLabel,
            leadingIcon = nameIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } }
        )
        if (extraField != null) {
            Spacer(modifier = Modifier.height(16.dp))
            extraField()
        }
    }
    val saveButtons: @Composable () -> Unit = {
        SaveDeleteButtons(
            saveEnabled = name.isNotBlank() && !isLoading,
            isSaving = isSaving,
            onSave = onSave,
            onDelete = onDelete
        )
    }
    /** 사진 칸(아이콘 | 배경) + 사진이 없을 때 카메라/갤러리/URL + 불러오는 중 막대 */
    val photoArea: @Composable () -> Unit = {
        // 사진을 넣기 전에도 넣으면 보일 자리(미리보기)를 먼저 보여 준다.
        if (!withIcon) {
            EditableBanner(
                photo = banner,
                onCamera = bannerLaunch.camera,
                onGallery = bannerLaunch.gallery,
                onUrl = bannerLaunch.url,
                onRemove = if (banner != GenrePhoto.None) ({ viewModel.removeBanner() }) else null
            )
        } else {
            // 왼쪽 아이콘 | 구분선 | 오른쪽 배경(직사각형)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                EditableIcon(
                    photo = icon,
                    size = 96,
                    description = "아이콘 편집",
                    onCamera = iconLaunch.camera,
                    onGallery = iconLaunch.gallery,
                    onUrl = iconLaunch.url,
                    onRecrop = if (viewModel.canRecrop(banner, icon)) ({ viewModel.recropIcon(context) }) else null,
                    onRemove = if (icon != GenrePhoto.None) ({ viewModel.removeIcon() }) else null
                )
                Spacer(modifier = Modifier.width(16.dp))
                VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    EditableBanner(
                        photo = banner,
                        onCamera = bannerLaunch.camera,
                        onGallery = bannerLaunch.gallery,
                        onUrl = bannerLaunch.url,
                        onRemove = if (banner != GenrePhoto.None) ({ viewModel.removeBanner() }) else null
                    )
                }
            }
        }
        if (banner == GenrePhoto.None && icon == GenrePhoto.None) {
            Spacer(modifier = Modifier.height(16.dp))
            PhotoSourceButtons(onCamera = bannerLaunch.camera, onGallery = bannerLaunch.gallery, onUrl = bannerLaunch.url)
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = title,
            subtitle = subtitle,
            onBack = close
        )
        if (com.example.wowagoodsproject.component.isSidePanel()) {
            // 가로: 왼쪽 사진 | 오른쪽 입력(이름·날짜·저장). 사진 칸은 처음부터 보인다.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) { photoArea() }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp)
                ) {
                    nameFields()
                    Spacer(modifier = Modifier.height(28.dp))
                    saveButtons()
                }
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .widthIn(max = 560.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            nameFields()
            // 이름을 적으면 사진 칸이 서서히 나타난다. 한 번 나타나면 이름을 잠깐 지워도 그대로 둔다.
            var photoShown by rememberSaveable(revealKey) { mutableStateOf(name.isNotBlank()) }
            if (name.isNotBlank()) photoShown = true
            AnimatedVisibility(visible = photoShown, enter = fadeIn(tween(400)), exit = fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    photoArea()
                    // 저장(·삭제)은 사진 아래
                    Spacer(modifier = Modifier.height(28.dp))
                    saveButtons()
                }
            }
        }
    }
}

/**
 * 원 범위 고르기. 사진은 화면에 맞춰 고정하고, 원을 옮기고 크기를 바꾼다(마우스 하나로도 된다).
 *  - 원 안(또는 둘레 네모 안)을 끌면 원이 옮겨진다.
 *  - 둘레 네모의 네 모서리 손잡이를 끌면 반대쪽 모서리를 기준으로 크기가 바뀐다.
 * 원은 늘 사진 안에 있다. 원의 위치·크기는 사진 픽셀 기준으로 들고 있어 화면을 돌려도 같은 범위다.
 * [onConfirm] 은 사진 픽셀 기준 원의 중심과 반지름을 준다.
 */
@Composable
internal fun CircleCropScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (cx: Float, cy: Float, r: Float) -> Unit
) {
    BackHandler(onBack = onCancel)
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    // 처음에는 사진 가운데의 가장 큰 원
    var cx by remember(bitmap) { mutableFloatStateOf(bw / 2f) }
    var cy by remember(bitmap) { mutableFloatStateOf(bh / 2f) }
    var r by remember(bitmap) { mutableFloatStateOf(min(bw, bh) / 2f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleTouch = with(density) { 32.dp.toPx() }
    val minOnScreen = with(density) { 48.dp.toPx() }

    /** 화면에 사진을 맞춰 놓는 배율과 왼쪽 위 위치 */
    fun fit(): Triple<Float, Float, Float> {
        val pad = with(density) { 16.dp.toPx() }
        val s = min((viewport.width - 2 * pad) / bw, (viewport.height - 2 * pad) / bh).coerceAtLeast(0.0001f)
        return Triple(s, (viewport.width - bw * s) / 2f, (viewport.height - bh * s) / 2f)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("취소") }
            Text(
                "아이콘 범위",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(
                onClick = { onConfirm(cx, cy, r) },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) { Text("확인", fontWeight = FontWeight.Bold) }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("circleCropArea")
                .onSizeChanged { viewport = it }
                .pointerInput(bitmap) {
                    // 누른 곳으로 정한다(끌기 시작 판정 거리만큼 밀린 곳이 아니라). 손잡이를 놓치지 않게 하려는 것.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val (s0, left, top) = fit()
                        val corners = listOf(
                            Offset(cx - r, cy - r), Offset(cx + r, cy - r),
                            Offset(cx - r, cy + r), Offset(cx + r, cy + r)
                        ).map { Offset(left + it.x * s0, top + it.y * s0) }
                        // 0 옮기기, 1~4 모서리(왼위·오위·왼아래·오아래).
                        // 원 안을 누르면 늘 옮기기다. 원을 작게 줄이면 원 안 거의 전부가 손잡이 범위에 들어가 옮길 수 없게 되기 때문.
                        // 모서리는 원 밖에 있으므로 원 밖에서 가장 가까운 손잡이를 고른다.
                        val center = Offset(left + cx * s0, top + cy * s0)
                        val insideCircle = (start - center).getDistance() <= r * s0
                        val hit = if (insideCircle) -1 else corners.withIndex()
                            .filter { (it.value - start).getDistance() <= handleTouch }
                            .minByOrNull { (it.value - start).getDistance() }?.index ?: -1
                        val mode = when {
                            insideCircle -> 0
                            hit >= 0 -> hit + 1
                            start.x in (left + (cx - r) * s0)..(left + (cx + r) * s0) &&
                                start.y in (top + (cy - r) * s0)..(top + (cy + r) * s0) -> 0
                            else -> return@awaitEachGesture
                        }
                        down.consume()
                        val signX = if (mode == 2 || mode == 4) 1f else -1f
                        val signY = if (mode >= 3) 1f else -1f
                        // 크기를 바꿀 때 고정되는 반대쪽 모서리와 지금 손가락 위치(사진 픽셀)
                        val anchorX = cx - signX * r
                        val anchorY = cy - signY * r
                        var pointerX = cx + signX * r
                        var pointerY = cy + signY * r
                        drag(down.id) { change ->
                            val d = change.positionChange()
                            change.consume()
                            val (s, _, _) = fit()
                            val dx = d.x / s
                            val dy = d.y / s
                            if (mode == 0) {
                                cx = (cx + dx).coerceIn(r, bw - r)
                                cy = (cy + dy).coerceIn(r, bh - r)
                            } else {
                                pointerX += dx; pointerY += dy
                                // 반대쪽 모서리에서 사진 끝까지가 가장 큰 크기
                                val maxSide = min(
                                    if (signX > 0) bw - anchorX else anchorX,
                                    if (signY > 0) bh - anchorY else anchorY
                                )
                                val minSide = min(minOnScreen / s, maxSide)
                                // 손가락이 반대쪽을 넘어가도 뒤집히지 않게 바깥 방향 거리만 쓴다
                                val side = max((pointerX - anchorX) * signX, (pointerY - anchorY) * signY)
                                    .coerceIn(minSide, maxSide)
                                r = side / 2f
                                cx = anchorX + signX * r
                                cy = anchorY + signY * r
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (viewport == IntSize.Zero) return@Canvas
                val (s, left, top) = fit()
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize((bw * s).roundToInt().coerceAtLeast(1), (bh * s).roundToInt().coerceAtLeast(1))
                )
                val c = Offset(left + cx * s, top + cy * s)
                val radius = r * s
                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(c, radius))
                }
                drawPath(mask, Color.Black.copy(alpha = 0.6f))
                drawCircle(Color.White, radius = radius, center = c, style = Stroke(width = 2.dp.toPx()))
                // 둘레 네모와 모서리 손잡이(ㄱ자)
                val box = Rect(c, radius)
                drawRect(Color.White.copy(alpha = 0.5f), topLeft = box.topLeft, size = box.size, style = Stroke(width = 1.dp.toPx()))
                val arm = min(18.dp.toPx(), radius)
                val thick = 4.dp.toPx()
                listOf(
                    box.topLeft to Offset(1f, 1f), box.topRight to Offset(-1f, 1f),
                    box.bottomLeft to Offset(1f, -1f), box.bottomRight to Offset(-1f, -1f)
                ).forEach { (p, dir) ->
                    drawLine(Color.White, p, p + Offset(arm * dir.x, 0f), strokeWidth = thick)
                    drawLine(Color.White, p, p + Offset(0f, arm * dir.y), strokeWidth = thick)
                }
            }
        }
        Text(
            "원 안을 끌어 옮기고, 모서리를 끌어 크기를 바꾸세요",
            color = Color.White.copy(alpha = 0.8f),
            style = AppStyles.textCardSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
