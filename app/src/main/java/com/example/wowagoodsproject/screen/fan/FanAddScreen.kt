package com.example.wowagoodsproject.screen.fan

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import java.io.File

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showCharaDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categorySearch by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val filteredCategories = categoryList.filter {
        it.contains(categorySearch, ignoreCase = true)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { viewModel.onImageSelected(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> viewModel.onImageSelected(uri) }

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

    fun onCameraClick() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    if (showCategoryDialog) {
        Dialog(
            onDismissRequest = {
                showCategoryDialog = false
                categorySearch = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "카테고리 선택", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = categorySearch,
                        onValueChange = { categorySearch = it },
                        label = { Text("검색") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (categorySearch.isNotEmpty() && !categoryList.contains(categorySearch)) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onCategoryChange(categorySearch)
                                            showCategoryDialog = false
                                            categorySearch = ""
                                        }
                                ) {
                                    Text(
                                        text = "\"$categorySearch\" 새로 추가",
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        items(filteredCategories) { cat ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onCategoryChange(cat)
                                        showCategoryDialog = false
                                        categorySearch = ""
                                    }
                            ) {
                                Text(
                                    text = cat,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showCategoryDialog = false
                            categorySearch = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("닫기") }
                }
            }
        }
    }

    if (showCharaDialog) {
        AlertDialog(
            onDismissRequest = { showCharaDialog = false },
            title = { Text("캐릭터 선택") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(charaList) { chara ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { viewModel.toggleChara(chara.charaNm) }
                                .padding(4.dp)
                        ) {
                            Box {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = if (chara.charaUrl.isNotEmpty()) chara.charaUrl else null
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (selectedCharas.contains(chara.charaNm)) 2.dp else 0.dp,
                                            color = if (selectedCharas.contains(chara.charaNm)) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                if (selectedCharas.contains(chara.charaNm)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chara.charaNm,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCharaDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    @Composable
    fun ImageBox(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.border(1.dp, Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(text = "이미지를 선택해주세요")
            }
        }
    }

    @Composable
    fun ImageButtons() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onCameraClick() },
                modifier = Modifier.weight(1f)
            ) { Text("카메라") }
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) { Text("갤러리") }
        }
    }

    @Composable
    fun InputFields() {
        OutlinedTextField(
            value = series,
            onValueChange = { viewModel.onSeriesChange(it) },
            label = { Text("시리즈") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = price,
            onValueChange = { viewModel.onPriceChange(it) },
            label = { Text("가격") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { viewModel.onCategoryChange(it) },
            label = { Text("카테고리") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCategoryDialog = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showCharaDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedCharas.isEmpty()) "캐릭터 선택"
                else selectedCharas.joinToString(", ")
            )
        }
        if (selectedCharas.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedCharas.forEach { chara ->
                    AssistChip(
                        onClick = { viewModel.toggleChara(chara) },
                        label = { Text(chara, style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            val charaEntity = charaList.find { it.charaNm == chara }
                            if (charaEntity?.charaUrl?.isNotEmpty() == true) {
                                Image(
                                    painter = rememberAsyncImagePainter(charaEntity.charaUrl),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("보유 여부")
            Switch(
                checked = isGotten,
                onCheckedChange = { viewModel.onIsGottenChange(it) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = memo,
            onValueChange = { viewModel.onMemoChange(it) },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
    }

    @Composable
    fun ActionButtons() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) { Text("취소") }
            Button(
                onClick = {
                    viewModel.insert(context = context, onSuccess = onNavigateBack)
                },
                modifier = Modifier.weight(1f)
            ) { Text("등록") }
        }
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ImageBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ImageButtons()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    InputFields()
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButtons()
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = "2차창작 굿즈 등록", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            ImageBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImageButtons()
            Spacer(modifier = Modifier.height(8.dp))
            InputFields()
            Spacer(modifier = Modifier.height(8.dp))
            ActionButtons()
        }
    }
}