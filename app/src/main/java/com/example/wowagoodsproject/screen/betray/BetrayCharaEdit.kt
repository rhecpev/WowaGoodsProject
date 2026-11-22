package com.example.wowagoodsproject.screen.betray

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.PhotoStore
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.copyBetrayImage
import com.example.wowagoodsproject.db.betray.deleteCharaWithImage
import com.example.wowagoodsproject.db.betray.deleteUnusedImages
import com.example.wowagoodsproject.db.betray.isValidCharaName
import com.example.wowagoodsproject.db.betray.saveChara
import com.example.wowagoodsproject.db.betray.setFavorite
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min

/*
 * 배신 굿즈 캐릭터 추가·고치기. 명조 쪽 캐릭터와는 상관없다.
 * 순서: 캐릭터명 → (서서히) 선호 / 비선호 고르기 → (서서히) 아이콘. 아이콘을 누르면 사진을 넣고,
 * 넣은 사진은 가운데 원으로 자동으로 잘린다. 연필 표시를 누르면 사진 변경 / 위치·크기 변경.
 */

/** 편집할 캐릭터. [name] 이 빈 문자열이면 새 캐릭터이고, 그때 이름 칸을 [prefill] 로 채워 둔다. */
data class CharaEditTarget(
    val name: String,
    val imgPath: String,
    val registered: Boolean,
    val prefill: String = "",
    /** 지금 선호 캐릭터인지(고치기 화면의 선호/비선호 처음 값) */
    val favorite: Boolean = false
)

class BetrayCharaEditViewModel : ViewModel() {
    val name = MutableStateFlow("")
    /** 선호(true) / 비선호(false). 새 캐릭터는 고르기 전이라 null */
    val favorite = MutableStateFlow<Boolean?>(null)
    val photo = MutableStateFlow<GenrePhoto>(GenrePhoto.None)
    val cropBitmap = MutableStateFlow<Bitmap?>(null)
    val isSaving = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    /** 열어 둔 캐릭터(장르 번호, 원래 이름). null 이면 아직 열지 않음 */
    private var openedFor: Pair<Int, String>? = null
    /** 이번에 고른 사진 원본(위치·크기를 다시 고를 때 쓴다) */
    private var photoSource: Uri? = null
    private val tempFiles = mutableListOf<File>()

    fun open(genreId: Int, target: CharaEditTarget) {
        val key = genreId to target.name
        if (openedFor == key) return // 화면을 돌려 다시 그릴 때 입력을 지우지 않는다
        clearTemps()
        openedFor = key
        name.value = target.name.ifEmpty { target.prefill }
        favorite.value = if (target.name.isEmpty()) null else target.favorite
        photo.value = if (target.imgPath.isEmpty()) GenrePhoto.None else GenrePhoto.Saved(target.imgPath)
        photoSource = null
        cropBitmap.value = null
    }

    fun close() {
        clearTemps()
        openedFor = null
        cropBitmap.value = null
    }

    /** 사진을 넣거나 바꾼다. 가운데의 가장 큰 원으로 바로 정해 둔다(연필 → 위치·크기 변경으로 고칠 수 있다). */
    fun onPicked(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        isLoading.value = true
        viewModelScope.launch {
            try {
                val bitmap = PhotoStore.loadUpright(appContext, uri)
                val r = min(bitmap.width, bitmap.height) / 2f
                val file = cropToSquareFile(appContext, bitmap, bitmap.width / 2f, bitmap.height / 2f, r)
                bitmap.recycle()
                tempFiles += file
                photo.value = GenrePhoto.Picked(Uri.fromFile(file))
                photoSource = uri
            } catch (e: Exception) {
                Toast.makeText(appContext, "사진을 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading.value = false
            }
        }
    }

    fun canRecrop(current: GenrePhoto): Boolean = photoSource != null || current != GenrePhoto.None

    /** 위치·크기 변경: 이번에 고른 원본(없으면 지금 사진)에서 원 범위를 다시 고른다. */
    fun recrop(context: Context) {
        val source = photoSource ?: photo.value.sourceUri ?: return
        val appContext = context.applicationContext
        isLoading.value = true
        viewModelScope.launch {
            try {
                cropBitmap.value = PhotoStore.loadUpright(appContext, source)
            } catch (e: Exception) {
                Toast.makeText(appContext, "사진을 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading.value = false
            }
        }
    }

    fun removePhoto() {
        photo.value = GenrePhoto.None
        photoSource = null
    }

    fun confirmCrop(context: Context, cx: Float, cy: Float, r: Float) {
        val bitmap = cropBitmap.value ?: return
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val file = cropToSquareFile(appContext, bitmap, cx, cy, r)
                tempFiles += file
                photo.value = GenrePhoto.Picked(Uri.fromFile(file))
            } catch (e: Exception) {
                Toast.makeText(appContext, "사진을 만들지 못했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            }
            cropBitmap.value = null
        }
    }

    fun cancelCrop() {
        cropBitmap.value = null
    }

    /** 저장한다. 결과로 저장된 이름을 넘긴다(실패하면 null: 빈 이름·쉼표·이름 겹침·선호 안 고름). */
    fun save(context: Context, genreId: Int, oldName: String?, onDone: (String?) -> Unit) {
        if (isSaving.value) return
        val newName = name.value.trim()
        val fav = favorite.value
        if (!isValidCharaName(newName) || fav == null) { onDone(null); return }
        isSaving.value = true
        viewModelScope.launch {
            val dao = App.betrayDatabase.betrayDao()
            var copied: String? = null
            try {
                val newImage = when (val p = photo.value) {
                    GenrePhoto.None -> ""
                    is GenrePhoto.Saved -> null
                    is GenrePhoto.Picked -> copyBetrayImage(context, p.uri, prefix = "betray_chara_").also { copied = it }
                }
                val ok = dao.saveChara(genreId, oldName, newName, newImage)
                // 이름을 바꿨으면 saveChara 가 선호 이름도 바꿔 두었으므로 새 이름으로 정한다.
                if (ok) dao.setFavorite(genreId, newName, fav)
                if (ok) {
                    close()
                    onDone(newName)
                } else {
                    copied?.let { dao.deleteUnusedImages(listOf(it)) }
                    onDone(null)
                }
            } catch (e: Exception) {
                copied?.let { dao.deleteUnusedImages(listOf(it)) }
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving.value = false
            }
        }
    }

    fun delete(genreId: Int, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            App.betrayDatabase.betrayDao().deleteCharaWithImage(genreId, name)
            close()
            onDone()
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

/**
 * 캐릭터 추가·고치기 화면.
 * @param onSaved 저장된 이름(이름을 바꿨으면 새 이름)
 * @param onDeleted 직접 추가한 캐릭터를 지웠을 때
 */
@Composable
fun BetrayCharaEditScreen(
    genreId: Int,
    genreName: String,
    target: CharaEditTarget,
    viewModel: BetrayCharaEditViewModel,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    remember(genreId, target.name) { viewModel.open(genreId, target); true }
    val name by viewModel.name.collectAsState()
    val favorite by viewModel.favorite.collectAsState()
    val photo by viewModel.photo.collectAsState()
    val cropBitmap by viewModel.cropBitmap.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isNew = target.name.isEmpty()
    val close = { viewModel.close(); onClose() }
    val photoLaunch = rememberPhotoLaunch { viewModel.onPicked(context, it) }

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

    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${target.name}' 캐릭터를 지울까요?") },
            text = { Text("이름과 사진만 지워집니다. 굿즈는 그대로이고, 굿즈에 이 이름이 있으면 캐릭터별 탭에 계속 나옵니다.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.delete(genreId, target.name, onDeleted) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }

    val save = {
        viewModel.save(context, genreId, target.name.ifEmpty { null }) { saved ->
            if (saved != null) onSaved(saved)
            else Toast.makeText(
                context,
                when {
                    name.contains(',') -> "이름에 쉼표(,)는 쓸 수 없습니다"
                    favorite == null -> "선호인지 비선호인지 골라 주세요"
                    else -> "이미 있거나 빈 이름입니다"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val photoIcon: @Composable (Int) -> Unit = { size ->
        EditableIcon(
            photo = photo,
            size = size,
            description = "캐릭터 사진",
            onCamera = photoLaunch.camera,
            onGallery = photoLaunch.gallery,
            onUrl = photoLaunch.url,
            onRecrop = if (viewModel.canRecrop(photo)) ({ viewModel.recrop(context) }) else null,
            onRemove = if (photo != GenrePhoto.None) ({ viewModel.removePhoto() }) else null
        )
    }
    val nameField: @Composable () -> Unit = {
        UnderlineNameField(
            value = name,
            onValueChange = { viewModel.name.value = it },
            label = "이름",
            leadingIcon = { Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(24.dp)) },
            isError = name.contains(','),
            supporting = when {
                name.contains(',') -> "쉼표(,)는 쓸 수 없습니다"
                !isNew -> "이름을 바꾸면 이 캐릭터가 들어간 굿즈의 캐릭터 이름도 함께 바뀝니다"
                else -> ""
            }
        )
    }
    val favoriteChoice: @Composable (Modifier) -> Unit = { modifier ->
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = favorite == true,
                onClick = { viewModel.favorite.value = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2, RoundedCornerShape(0.dp)),
                icon = {
                    Icon(
                        if (favorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = AppStyles.colorFavorite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) { Text("선호") }
            SegmentedButton(
                selected = favorite == false,
                onClick = { viewModel.favorite.value = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2, RoundedCornerShape(0.dp))
            ) { Text("비선호") }
        }
    }
    val saveButtons: @Composable () -> Unit = {
        SaveDeleteButtons(
            saveEnabled = name.isNotBlank() && favorite != null && !isLoading,
            isSaving = isSaving,
            onSave = save,
            onDelete = if (target.registered) ({ confirmDelete = true }) else null
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = if (isNew) "캐릭터 추가" else "캐릭터 편집",
            subtitle = genreName.ifEmpty { null },
            onBack = close
        )
        if (com.example.wowagoodsproject.component.isSidePanel()) {
            // 가로: 왼쪽 사진 | 오른쪽 입력(이름 · 선호/비선호 · 저장). 사진은 처음부터 보인다.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    photoIcon(180)
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(8.dp))
                    nameField()
                    favoriteChoice(Modifier.padding(top = 28.dp))
                    Spacer(modifier = Modifier.height(32.dp))
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
            // 이름을 적으면 선호/비선호 고르기가, 고르면 그 위(캐릭터명 위)에 아이콘이 서서히 나타난다(한 번 나타나면 그대로).
            var choiceShown by rememberSaveable(target.name) { mutableStateOf(name.isNotBlank()) }
            if (name.isNotBlank()) choiceShown = true
            AnimatedVisibility(visible = choiceShown && favorite != null, enter = fadeIn(tween(400)), exit = fadeOut()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    photoIcon(120)
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            nameField()
            AnimatedVisibility(visible = choiceShown, enter = fadeIn(tween(400)), exit = fadeOut()) {
                favoriteChoice(Modifier.padding(top = 28.dp))
            }

            // 저장(·삭제)은 아래. 선호/비선호를 고르면 나타난다.
            AnimatedVisibility(visible = choiceShown && favorite != null, enter = fadeIn(tween(400)), exit = fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                    saveButtons()
                }
            }
        }
    }
}

/** 화면 상태에서 쓰는 편집 대상 */
fun BetrayChara.editTarget(registeredList: List<BetrayCharaEntity>) =
    CharaEditTarget(name, registeredList.firstOrNull { it.charaNm == name }?.charaImgPath.orEmpty(), registered, favorite = favorite)
