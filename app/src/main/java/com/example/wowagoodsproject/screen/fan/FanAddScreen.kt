package com.example.wowagoodsproject.screen.fan

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.CategoryChoiceList
import com.example.wowagoodsproject.component.CharaChoiceGrid
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.StatusSegmentedButtons
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.navigation.TopBar
import java.io.File

/**
 * 2차창작 굿즈 등록.
 * 위에서부터 사진 → 기본 정보(시리즈/가격/카테고리) → 캐릭터 → 보유 상태 → 메모 순으로 채우고,
 * 아래에 붙은 등록 버튼으로 저장한다. 가로 모드에서는 사진을 왼쪽, 입력란을 오른쪽에 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanAddScreen(
    onNavigateBack: () -> Unit,
    viewModel: FanAddViewModel = viewModel()
) {
    val context = LocalContext.current
    val price by viewModel.price.collectAsState()
    val series by viewModel.series.collectAsState()
    val selectedCharas by viewModel.selectedCharas.collectAsState()
    val category by viewModel.category.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val charaList by viewModel.charaList.collectAsState()
    val categoryList by viewModel.categoryList.collectAsState()
    val isGotten by viewModel.isGotten.collectAsState()
    val memo by viewModel.memo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showCharaDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { viewModel.onImageSelected(it) }
        }
    }

    // 갤러리에서 고르지 않고 돌아오면 이미 고른 사진을 그대로 둔다.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    fun launchCamera() {
        val tempFile = File(context.cacheDir, "camera_temp_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    val onCameraClick: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val onGalleryClick: () -> Unit = { galleryLauncher.launch("image/*") }

    if (showCategoryDialog) {
        CategoryPickerDialog(
            categories = categoryList,
            selected = category,
            onSelect = { viewModel.onCategoryChange(it); showCategoryDialog = false },
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (showCharaDialog) {
        CharaPickerDialog(
            charas = charaList,
            selected = selectedCharas.toSet(),
            onToggle = { viewModel.toggleChara(it) },
            onDismiss = { showCharaDialog = false }
        )
    }

    val photo: @Composable (Modifier) -> Unit = { modifier ->
        PhotoPicker(
            imageUri = imageUri,
            onCamera = onCameraClick,
            onGallery = onGalleryClick,
            onRemove = { viewModel.onImageSelected(null) },
            modifier = modifier
        )
    }

    val form: @Composable () -> Unit = {
        FormSection(title = "기본 정보") {
            OutlinedTextField(
                value = series,
                onValueChange = { viewModel.onSeriesChange(it) },
                label = { Text("시리즈") },
                placeholder = { Text("예: 행사명, 작가명") },
                leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = price,
                onValueChange = { viewModel.onPriceChange(it) },
                label = { Text("가격") },
                placeholder = { Text("예: 5,000원") },
                leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            PickerField(
                value = category,
                label = "카테고리",
                placeholder = "카테고리 선택",
                icon = Icons.Default.Category,
                onClick = { showCategoryDialog = true }
            )
        }

        FormSection(title = "캐릭터", count = selectedCharas.size.takeIf { it > 0 }) {
            SelectedCharaChips(
                selected = selectedCharas,
                charaList = charaList,
                onRemove = { viewModel.toggleChara(it) },
                onAdd = { showCharaDialog = true }
            )
        }

        FormSection(title = "보유 상태") {
            StatusSegmentedButtons(
                current = if (isGotten) GoodsStatus.GOTTEN else GoodsStatus.NOT_GOTTEN,
                onSelect = { viewModel.onIsGottenChange(it == GoodsStatus.GOTTEN) },
                showPending = false
            )
        }

        FormSection(title = "메모") {
            OutlinedTextField(
                value = memo,
                onValueChange = { viewModel.onMemoChange(it) },
                placeholder = { Text("구매처, 구성 등 자유롭게 적어 두세요") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "2차창작 굿즈 등록", onBack = onNavigateBack)

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                photo(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) { form() }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                photo(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                form()
            }
        }

        // 스크롤과 상관없이 언제든 누를 수 있게 등록 버튼은 아래에 붙여 둔다.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp
        ) {
            Button(
                onClick = { viewModel.insert(context = context, onSuccess = onNavigateBack) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("등록하기", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** 사진 칸. 비어 있으면 카메라/갤러리 버튼을, 사진이 있으면 오른쪽 위에 다시 고르기/지우기 버튼을 둔다. */
@Composable
private fun PhotoPicker(
    imageUri: Uri?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (imageUri != null) {
            Box {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "선택한 사진",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoActionButton(Icons.Default.PhotoCamera, "카메라로 다시 찍기", onCamera)
                    PhotoActionButton(Icons.Default.PhotoLibrary, "갤러리에서 다시 고르기", onGallery)
                    PhotoActionButton(Icons.Default.Close, "사진 지우기", onRemove)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "굿즈 사진을 추가하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onCamera) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("카메라")
                    }
                    FilledTonalButton(onClick = onGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("갤러리")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoActionButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** 제목(+개수) 아래에 입력란을 모아 두는 구역. 굿즈 상세 팝업의 구역 제목과 같은 모양이다. */
@Composable
private fun FormSection(
    title: String,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(content = content)
}

/** 입력란처럼 생겼지만 누르면 선택 팝업을 여는 칸 */
@Composable
private fun PickerField(
    value: String,
    label: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    // readOnly 입력란은 클릭을 먹어 버리므로 눌림 이벤트를 직접 받아 팝업을 연다.
    // 팝업을 닫은 뒤 칸이 포커스된 채로 남지 않게 포커스도 풀어 준다.
    val interaction = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val latestOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Release) {
                focusManager.clearFocus()
                latestOnClick()
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        singleLine = true,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 고른 캐릭터(아바타 + 이름, 눌러서 빼기) + 끝에 '추가' 칩 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedCharaChips(
    selected: List<String>,
    charaList: List<CharaEntity>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        selected.forEach { name ->
            val url = charaList.find { it.charaNm == name }?.charaUrl.orEmpty()
            InputChip(
                selected = true,
                onClick = { onRemove(name) },
                label = { Text(name) },
                avatar = {
                    Image(
                        painter = rememberAsyncImagePainter(model = url.ifEmpty { null }),
                        contentDescription = null,
                        modifier = Modifier
                            .size(InputChipDefaults.AvatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "$name 빼기",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(if (selected.isEmpty()) "캐릭터 선택" else "추가") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
    }
}

/** 카테고리 고르기. 목록에 없는 이름을 치면 맨 위에 '새로 추가' 줄이 생긴다. */
@Composable
private fun CategoryPickerDialog(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim()
    val filtered = categories.filter { it.contains(query, ignoreCase = true) }
    val canAdd = query.isNotEmpty() && categories.none { it.equals(query, ignoreCase = true) }

    FilterDialogFrame(title = "카테고리", onDismiss = onDismiss, onClear = null) {
        SearchField(
            query = search,
            onQueryChange = { search = it },
            placeholder = "검색하거나 새 카테고리 입력",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (canAdd) {
            val blocked = query == CATEGORY_SET
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = !blocked) { onSelect(query) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (blocked) "'$query' 는 새로 추가할 수 없습니다" else "'$query' 새로 추가",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        CategoryChoiceList(
            categories = filtered,
            selected = selected.ifEmpty { null },
            onSelect = onSelect,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/** 캐릭터 여러 명 고르기. 선호 캐릭터가 먼저 나오고, 누를 때마다 넣고 뺀다. */
@Composable
private fun CharaPickerDialog(
    charas: List<CharaEntity>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = charas
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .filter { it.charaNm.contains(search.trim(), ignoreCase = true) }

    FilterDialogFrame(
        title = if (selected.isEmpty()) "캐릭터 선택" else "캐릭터 선택 · ${selected.size}명",
        onDismiss = onDismiss,
        onClear = null
    ) {
        SearchField(
            query = search,
            onQueryChange = { search = it },
            placeholder = "캐릭터 이름 검색",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(modifier = Modifier.height(12.dp))
        CharaChoiceGrid(
            charas = filtered,
            selectedName = null,
            selectedNames = selected,
            onSelect = { onToggle(it.charaNm) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("완료") }
    }
}
