package com.example.wowagoodsproject.screen.betray

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File

/*
 * 장르·캐릭터 편집 화면이 함께 쓰는 부품: 떠오르는 이름표가 있는 밑줄 이름 칸, 카메라/갤러리 버튼,
 * 연필 표시가 붙은 아이콘(누르면 사진 변경 / 위치·크기 변경), 연필 표시가 붙은 배경 사진.
 */

/**
 * 밑줄 하나로 된 이름 칸. 비어 있을 때는 칸 안에 [label] 이 보이고, 누르거나 입력하면 그 글자가 작은 이름표로 위로 올라간다.
 * 입력 중에는 밑줄이 진하고 굵어진다.
 */
@Composable
internal fun UnderlineNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supporting: String = "",
    singleLine: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onImeAction: () -> Unit = {},
    /** 칸 왼쪽 아이콘(굿즈 등록 칸들) */
    leadingIcon: (@Composable () -> Unit)? = null,
    /** 칸을 누르거나 떠날 때(구입처 추천을 열고 닫는 데 쓴다) */
    onFocusChange: (Boolean) -> Unit = {},
    /** true 면 키보드를 띄우지 않는다(가로 화면 가격 칸은 오른쪽 숫자 패드로 적는다) */
    readOnly: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val floated = focused || value.isNotEmpty()
    val t by animateFloatAsState(if (floated) 1f else 0f, tween(180), label = "label")
    val big = MaterialTheme.typography.titleLarge
    val small = MaterialTheme.typography.labelMedium
    val lineColor = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }
    // 아이콘이 있으면 제목은 아이콘 아래(왼쪽 칸)에 두고, 칸에는 값과 밑줄만 둔다.
    val labelUnderIcon = leadingIcon != null
    FieldWithIcon(leadingIcon, label = if (labelUnderIcon) label else null) { Column(modifier = Modifier.fillMaxWidth()) {
        // 이미 적힌 값으로 열 때(요약에서 고치기 등) 커서를 글 끝에 둔다. 밖에서 값이 바뀌어도 끝으로.
        var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }
        if (field.text != value) field = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
        Box(modifier = Modifier.fillMaxWidth().padding(top = if (labelUnderIcon) 0.dp else 20.dp)) {
            BasicTextField(
                value = field,
                onValueChange = { v -> field = v; if (v.text != value) onValueChange(v.text) },
                singleLine = singleLine,
                readOnly = readOnly,
                maxLines = if (singleLine) 1 else 5,
                textStyle = big.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = label }
            )
            // 이름표: 비었을 때는 입력 자리에 크게, 떠오르면 칸 위에 작게. 누름은 아래 입력 칸으로 그대로 간다.
            if (!labelUnderIcon) Text(
                text = label,
                style = androidx.compose.ui.text.lerp(big, small, t),
                color = lerp(colors.onSurfaceVariant.copy(alpha = 0.7f), if (isError) colors.error else if (focused) colors.primary else colors.onSurfaceVariant, t),
                maxLines = 1,
                modifier = Modifier.offset(y = (8 - 28 * t).dp)
            )
        }
        HorizontalDivider(thickness = if (focused || isError) 2.dp else 1.dp, color = lineColor)
        if (supporting.isNotEmpty()) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    } }
}

/** 입력 칸 왼쪽 아이콘(+ 아래 제목). 공용 부품 [com.example.wowagoodsproject.component.FieldWithIcon] 을 그대로 쓴다. */
@Composable
internal fun FieldWithIcon(
    leadingIcon: (@Composable () -> Unit)?,
    label: String? = null,
    centerAligned: Boolean = false,
    content: @Composable () -> Unit
) = com.example.wowagoodsproject.component.FieldWithIcon(leadingIcon, label, centerAligned, content)

/** 카메라·갤러리·URL 로 사진 한 장을 받아 [onPicked] 로 넘긴다. 카메라 권한·URL 입력 창도 여기서 다룬다. */
internal class PhotoLaunch(val camera: () -> Unit, val gallery: () -> Unit, val url: () -> Unit)

@Composable
internal fun rememberPhotoLaunch(onPicked: (Uri) -> Unit): PhotoLaunch {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) latest(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { latest(it) } }
    fun launchCamera() {
        val temp = File(context.cacheDir, "camera_temp_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", temp)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }
    // URL: 주소를 받아 사진을 내려받고, 카메라·갤러리로 고른 것처럼 넘긴다.
    var showUrl by remember { mutableStateOf(false) }
    if (showUrl) {
        com.example.wowagoodsproject.component.PhotoUrlDialog(
            onPicked = { showUrl = false; latest(it) },
            onDismiss = { showUrl = false }
        )
    }
    return PhotoLaunch(
        camera = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
            else permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        gallery = { galleryLauncher.launch("image/*") },
        url = { showUrl = true }
    )
}

/** 카메라 / 갤러리 큰 버튼 두 개 */
@Composable
internal fun PhotoSourceButtons(onCamera: () -> Unit, onGallery: () -> Unit, onUrl: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCamera, shape = RectangleShape, modifier = Modifier.weight(1f).height(56.dp)) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("카메라", style = MaterialTheme.typography.titleSmall)
        }
        OutlinedButton(onClick = onGallery, shape = RectangleShape, modifier = Modifier.weight(1f).height(56.dp)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("갤러리", style = MaterialTheme.typography.titleSmall)
        }
        OutlinedButton(onClick = onUrl, shape = RectangleShape, modifier = Modifier.weight(1f).height(56.dp)) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("URL", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 오른쪽 위 연필 표시 */
@Composable
private fun PencilBadge(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
    }
}

/**
 * 메뉴 두 단계: 처음에는 [사진 변경, 위치·크기 변경, 사진 빼기], 사진 변경을 고르면 [카메라, 갤러리].
 * 사진이 없으면 바로 [카메라, 갤러리].
 */
@Composable
private fun PhotoEditMenu(
    expanded: Boolean,
    hasPhoto: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onUrl: () -> Unit,
    onRecrop: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    var pickSource by remember(expanded) { mutableStateOf(!hasPhoto) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (pickSource) {
            DropdownMenuItem(
                text = { Text("카메라") },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = { onDismiss(); onCamera() }
            )
            DropdownMenuItem(
                text = { Text("갤러리") },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = { onDismiss(); onGallery() }
            )
            DropdownMenuItem(
                text = { Text("URL") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                onClick = { onDismiss(); onUrl() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("변경") },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                onClick = { pickSource = true }
            )
            if (onRecrop != null) {
                DropdownMenuItem(
                    text = { Text("위치·크기 변경") },
                    leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) },
                    onClick = { onDismiss(); onRecrop() }
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("빼기", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { onDismiss(); onRemove() }
                )
            }
        }
    }
}

/**
 * 원 모양 아이콘 + 오른쪽 위 연필 표시. 누르면 사진 변경(카메라/갤러리) · 위치·크기 변경 · 사진 빼기.
 * 사진이 없으면 누르자마자 카메라/갤러리를 고른다.
 */
@Composable
internal fun EditableIcon(
    photo: GenrePhoto,
    size: Int,
    description: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onUrl: () -> Unit,
    onRecrop: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menu = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            val model = photo.model
            if (model == null) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((size / 3).dp))
            } else {
                Image(painter = rememberAsyncImagePainter(model), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        PencilBadge(Modifier.align(Alignment.TopEnd))
        PhotoEditMenu(
            expanded = menu,
            hasPhoto = photo != GenrePhoto.None,
            onDismiss = { menu = false },
            onCamera = onCamera,
            onGallery = onGallery,
            onUrl = onUrl,
            onRecrop = onRecrop,
            onRemove = onRemove
        )
    }
}

/** 직사각형 배경 사진 + 오른쪽 위 연필 표시. 누르면 사진 변경(카메라/갤러리) · 사진 빼기. */
@Composable
internal fun EditableBanner(
    photo: GenrePhoto,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onUrl: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menu = true }
                .semantics { contentDescription = "배경 사진" },
            contentAlignment = Alignment.Center
        ) {
            val model = photo.model
            if (model == null) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            } else {
                Image(painter = rememberAsyncImagePainter(model), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        PencilBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            PhotoEditMenu(
                expanded = menu,
                hasPhoto = photo != GenrePhoto.None,
                onDismiss = { menu = false },
                onCamera = onCamera,
                onGallery = onGallery,
                onUrl = onUrl,
                onRecrop = null,
                onRemove = onRemove
            )
        }
    }
}

/** 기본 아이콘(사진 없는 장르·캐릭터) */
@Composable
internal fun EmptyIconMark(size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Outlined.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun SectionHint(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 편집 화면 아래쪽 단추. 지울 수 있으면 왼쪽 '삭제' · 오른쪽 '저장'으로 반씩, 아니면 '저장' 하나가 가로로 꽉 찬다.
 */
@Composable
internal fun SaveDeleteButtons(saveEnabled: Boolean, isSaving: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onDelete != null) {
            OutlinedButton(
                onClick = onDelete,
                shape = RectangleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("삭제", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
        Button(
            onClick = onSave,
            enabled = saveEnabled && !isSaving,
            shape = RectangleShape,
            modifier = Modifier.weight(1f).height(56.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("저장", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
