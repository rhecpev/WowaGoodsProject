package com.example.wowagoodsproject.screen.mypage

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.content.Intent
import com.example.wowagoodsproject.UpdateManager
import com.example.wowagoodsproject.component.GoodsStatus

/**
 * 백업 파일에 담기는 공식 굿즈 한 줄.
 *
 * 모든 칸이 nullable 인 이유: 예전 버전에서 만든 zip 에는 나중에 생긴 칸(상태·메모·구매 정보)이
 * 아예 없고, Gson 은 그 자리에 코틀린 기본값 대신 null 을 넣는다.
 * 그대로 엔티티에 옮기면 non-null 칸에 null 이 들어가 복원이 통째로 실패한다.
 */
data class OfficialGoodsBackup(
    val goodsSeries: String? = null,
    val goodsChara: String? = null,
    val goodsCategory: String? = null,
    val goodsStatus: String? = null,
    val goodsMemo: String? = null,
    val goodsPrice: String? = null,
    val goodsPurchaseDate: String? = null,
    val goodsPurchaseStore: String? = null
)

/** 백업 파일에 담기는 2차창작 굿즈. nullable 인 이유는 [OfficialGoodsBackup] 과 같다. */
data class FanGoodsBackup(
    val fanGoodsReleaseDate: String? = null,
    val fanGoodsSeries: String? = null,
    val fanGoodsPrice: String? = null,
    val fanGoodsChara: String? = null,
    val fanGoodsCategory: String? = null,
    val fanGoodsImgPath: String? = null,
    val fanGoodsIsGotten: Boolean? = null,
    val fanGoodsStatus: String? = null,
    val fanGoodsMemo: String? = null,
    val fanGoodsPurchaseDate: String? = null,
    val fanGoodsPurchaseStore: String? = null
) {
    /** @param imgPath 백업 안 이미지를 앱 폴더에 풀어 놓은 뒤의 경로 */
    fun toEntity(imgPath: String) = FanGoodsEntity(
        fanGoodsId = 0,
        fanGoodsReleaseDate = fanGoodsReleaseDate.orEmpty(),
        fanGoodsSeries = fanGoodsSeries.orEmpty(),
        fanGoodsPrice = fanGoodsPrice.orEmpty(),
        fanGoodsChara = fanGoodsChara.orEmpty(),
        fanGoodsCategory = fanGoodsCategory.orEmpty(),
        fanGoodsImgPath = imgPath,
        fanGoodsIsGotten = fanGoodsIsGotten ?: false,
        // 상태 칸이 생기기 전 백업이면 보유 여부로 상태를 되살린다.
        fanGoodsStatus = fanGoodsStatus
            ?: if (fanGoodsIsGotten == true) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name,
        fanGoodsMemo = fanGoodsMemo.orEmpty(),
        fanGoodsPurchaseDate = fanGoodsPurchaseDate.orEmpty(),
        fanGoodsPurchaseStore = fanGoodsPurchaseStore.orEmpty()
    )
}

class MyPageViewModel : ViewModel() {

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _officialGottenGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGottenGoods: StateFlow<List<GoodsEntity>> = _officialGottenGoods

    private val _allSeriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val allSeriesGoods: StateFlow<List<GoodsEntity>> = _allSeriesGoods

    private val _fanGottenGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGottenGoods: StateFlow<List<FanGoodsEntity>> = _fanGottenGoods

    private val _pendingOfficialGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val pendingOfficialGoods: StateFlow<List<GoodsEntity>> = _pendingOfficialGoods

    private val _pendingFanGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val pendingFanGoods: StateFlow<List<FanGoodsEntity>> = _pendingFanGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _currentSection = MutableStateFlow<String?>(null)
    val currentSection: StateFlow<String?> = _currentSection

    private val _selectedCharaFilter = MutableStateFlow<String?>(null)
    val selectedCharaFilter: StateFlow<String?> = _selectedCharaFilter

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter

    private val _showFilterDialog = MutableStateFlow(false)
    val showFilterDialog: StateFlow<Boolean> = _showFilterDialog

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress

    private fun reportImport(fraction: Float) {
        _importProgress.value = fraction.coerceIn(0f, 1f)
    }

    private val _latestVersion = MutableStateFlow<String?>(null)
    val latestVersion: StateFlow<String?> = _latestVersion

    private val _unreadNewsCount = MutableStateFlow(0)
    val unreadNewsCount: StateFlow<Int> = _unreadNewsCount

    init {
        loadCharaList()
        loadGottenGoods()
        checkLatestVersion()
        observeUnreadNews()
    }

    private fun observeUnreadNews() {
        viewModelScope.launch {
            App.newsDatabase.newsDao().getUnreadCountFlow().collectLatest {
                _unreadNewsCount.value = it
            }
        }
    }

    private fun checkLatestVersion() {
        viewModelScope.launch {
            val result = UpdateManager.checkAppUpdate()
            if (result != null) {
                _latestVersion.value = result.first
            } else {
                // 하루 안 지났으면 SharedPreferences에서 캐시된 값 읽기
                val prefs = App.appContext.getSharedPreferences("wowa_prefs", Context.MODE_PRIVATE)
                _latestVersion.value = prefs.getString("cached_latest_version", null)
            }
        }
    }

    fun loadCharaList() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _charaList.value = it
            }
        }
    }

    fun loadGottenGoods() {
        viewModelScope.launch {
            val allGoods = App.database.goodsDao().getAll()

            // 세트 굿즈는 카운트/목록에서 제외하고 낱개 굿즈만 집계한다.
            // 구매예정은 '구매예정 굿즈' 페이지에서 따로 보므로 여기서는 보유만 센다.
            val gottenGoods = allGoods.filter {
                it.goodsCategory != CATEGORY_SET && it.goodsStatus == GoodsStatus.GOTTEN.name
            }

            _officialGottenGoods.value = gottenGoods
            val seriesList = gottenGoods.map { it.goodsSeries }.distinct()
            _allSeriesGoods.value = seriesList.flatMap {
                App.database.goodsDao().getBySeries(it)
            }
            val allFanGoods = App.fanDatabase.fanGoodsDao().getAll()
            _fanGottenGoods.value = allFanGoods.filter { it.isGotten }

            // 구매예정 목록도 같은 조회 결과에서 뽑아 둔다(세트 굿즈는 제외).
            _pendingOfficialGoods.value = allGoods.filter {
                it.goodsCategory != CATEGORY_SET && it.status == GoodsStatus.PENDING
            }
            _pendingFanGoods.value = allFanGoods.filter { it.status == GoodsStatus.PENDING }
        }
    }

    /**
     * 고른 구매예정 굿즈에 구매일/구입처를 한 번에 적어 넣는다.
     * null 을 넘긴 항목은 건드리지 않고, 빈 문자열은 지우라는 뜻이다.
     */
    fun applyPurchaseInfo(
        officialIds: Set<Int>,
        fanIds: Set<Int>,
        purchaseDate: String?,
        purchaseStore: String?
    ) {
        if (purchaseDate == null && purchaseStore == null) return
        viewModelScope.launch {
            _pendingOfficialGoods.value
                .filter { it.goodsId in officialIds }
                .forEach { goods ->
                    App.database.goodsDao().update(
                        goods.copy(
                            goodsPurchaseDate = purchaseDate ?: goods.goodsPurchaseDate,
                            goodsPurchaseStore = purchaseStore ?: goods.goodsPurchaseStore
                        )
                    )
                }
            _pendingFanGoods.value
                .filter { it.fanGoodsId in fanIds }
                .forEach { goods ->
                    App.fanDatabase.fanGoodsDao().update(
                        goods.copy(
                            fanGoodsPurchaseDate = purchaseDate ?: goods.fanGoodsPurchaseDate,
                            fanGoodsPurchaseStore = purchaseStore ?: goods.fanGoodsPurchaseStore
                        )
                    )
                }
            loadGottenGoods()
        }
    }

    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            loadGottenGoods()
        }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(goodsStatus = newStatus.name)
            App.database.goodsDao().update(updated)

            if (updated.goodsCategory != CATEGORY_SET && updated.goodsMemo.isNotEmpty()) {
                val allGoods = App.database.goodsDao().getBySeries(updated.goodsSeries)
                val siblings = allGoods.filter {
                    it.goodsCategory != CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                val setGoods = allGoods.find {
                    it.goodsCategory == CATEGORY_SET && it.goodsMemo == updated.goodsMemo
                }
                setGoods?.let { set ->
                    val newIsGotten = siblings.all { it.goodsStatus == GoodsStatus.GOTTEN.name }
                    App.database.goodsDao().update(set.copy(goodsStatus = if (newIsGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name))
                }
            }

            loadGottenGoods()
        }
    }
    // After - 추가
    /** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. */
    fun bulkSetOfficialStatus(setGoods: GoodsEntity, status: GoodsStatus) {
        viewModelScope.launch {
            val allGoods = App.database.goodsDao().getBySeries(setGoods.goodsSeries)
            val components = allGoods.filter {
                it.goodsCategory != CATEGORY_SET && it.goodsMemo == setGoods.goodsMemo
            }
            val newStatus = status.name
            components.forEach { comp ->
                App.database.goodsDao().update(comp.copy(goodsStatus = newStatus))
            }
            App.database.goodsDao().update(setGoods.copy(goodsStatus = newStatus))
            loadGottenGoods()
        }
    }
    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGottenGoods()
        }
    }

    fun setFanPending(goods: FanGoodsEntity) {
        viewModelScope.launch {
            val newStatus = if (goods.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING
            val updated = goods.copy(fanGoodsStatus = newStatus.name)
            App.fanDatabase.fanGoodsDao().update(updated)
            loadGottenGoods()
        }
    }


    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setSection(section: String?) {
        _currentSection.value = section
    }

    fun setCharaFilter(chara: String?) {
        _selectedCharaFilter.value = chara
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setShowFilterDialog(show: Boolean) {
        _showFilterDialog.value = show
    }


    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val gson = Gson()
                    val fanGoods = App.fanDatabase.fanGoodsDao().getAll()
                    val fanGoodsJson = gson.toJson(fanGoods)
                    val officialGoods = App.database.goodsDao().getAll()
                    val officialBackup = officialGoods.map {
                        OfficialGoodsBackup(
                            goodsSeries = it.goodsSeries,
                            goodsChara = it.goodsChara,
                            goodsCategory = it.goodsCategory,
                            goodsStatus = it.goodsStatus,
                            goodsMemo = it.goodsMemo,
                            goodsPrice = it.goodsPrice,
                            goodsPurchaseDate = it.goodsPurchaseDate,
                            goodsPurchaseStore = it.goodsPurchaseStore
                        )
                    }
                    val officialJson = gson.toJson(officialBackup)
                    val charas = App.charaDatabase.charaDao().getAll()
                    val favoriteCharas = charas.filter { it.charaIsFavorite }.map { it.charaNm }
                    val charaJson = gson.toJson(favoriteCharas)

                    val zipFile = File(context.filesDir, "wowa_backup.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                        zip.putNextEntry(ZipEntry("fan_goods.json"))
                        zip.write(fanGoodsJson.toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("official_gotten.json"))
                        zip.write(officialJson.toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("favorite_charas.json"))
                        zip.write(charaJson.toByteArray())
                        zip.closeEntry()
                        fanGoods.forEach { goods ->
                            if (goods.fanGoodsImgPath.isNotEmpty()) {
                                val imgFile = File(goods.fanGoodsImgPath)
                                if (imgFile.exists()) {
                                    zip.putNextEntry(ZipEntry("images/${imgFile.name}"))
                                    FileInputStream(imgFile).use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(
                                android.provider.MediaStore.Downloads.DISPLAY_NAME,
                                "wowa_backup.zip"
                            )
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                        }
                        val resolver = context.contentResolver
                        val downloadUri = resolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        downloadUri?.let { uri ->
                            resolver.openOutputStream(uri)?.use { output ->
                                FileInputStream(zipFile).copyTo(output)
                            }
                        }
                    } else {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            zipFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "백업 파일 내보내기").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    zipFile.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "추출 완료!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0f
            try {
                withContext(Dispatchers.IO) {
                    val gson = Gson()
                    var fanGoodsJson: String? = null
                    var officialJson: String? = null
                    var favoriteCharaJson: String? = null
                    val imageMap = mutableMapOf<String, ByteArray>()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "fan_goods.json" -> fanGoodsJson =
                                        zip.bufferedReader().readText()

                                    entry.name == "official_gotten.json" -> officialJson =
                                        zip.bufferedReader().readText()

                                    entry.name == "favorite_charas.json" -> favoriteCharaJson =
                                        zip.bufferedReader().readText()

                                    entry.name.startsWith("images/") -> {
                                        val fileName = entry.name.removePrefix("images/")
                                        imageMap[fileName] = zip.readBytes()
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    reportImport(0.25f)

                    if (imageMap.isEmpty()) {
                        reportImport(0.45f)
                    } else {
                        imageMap.entries.forEachIndexed { i, (fileName, bytes) ->
                            val imgFile = File(context.filesDir, fileName)
                            imgFile.writeBytes(bytes)
                            reportImport(0.25f + 0.20f * (i + 1) / imageMap.size)
                        }
                    }
                    fanGoodsJson?.let { json ->
                        val type = object : TypeToken<List<FanGoodsBackup>>() {}.type
                        val fanGoods: List<FanGoodsBackup> = gson.fromJson(json, type)
                        App.fanDatabase.fanGoodsDao().deleteAll()
                        val mappedGoods = fanGoods.map { backup ->
                            val oldPath = backup.fanGoodsImgPath.orEmpty()
                            val newPath = if (oldPath.isNotEmpty()) {
                                File(context.filesDir, File(oldPath).name).absolutePath
                            } else ""
                            backup.toEntity(newPath)
                        }
                        App.fanDatabase.fanGoodsDao().insertAll(mappedGoods)
                    }
                    reportImport(0.6f)
                    officialJson?.let { json ->
                        val type = object : TypeToken<List<OfficialGoodsBackup>>() {}.type
                        val backups: List<OfficialGoodsBackup> = gson.fromJson(json, type)
                        App.database.goodsDao().resetAllGotten()
                        val allLocalGoods = App.database.goodsDao().getAll()
                        val localGoodsMap = allLocalGoods.associateBy {
                            "${it.goodsSeries}|${it.goodsChara}|${it.goodsCategory}|${it.goodsMemo}|${it.goodsPrice}"
                        }
                        backups.forEachIndexed { i, backup ->
                            // null 을 그대로 문자열에 끼우면 "null" 이 되어 짝을 못 찾는다.
                            val key = listOf(
                                backup.goodsSeries.orEmpty(),
                                backup.goodsChara.orEmpty(),
                                backup.goodsCategory.orEmpty(),
                                backup.goodsMemo.orEmpty(),
                                backup.goodsPrice.orEmpty()
                            ).joinToString("|")
                            localGoodsMap[key]?.let { goods ->
                                App.database.goodsDao().update(
                                    goods.copy(
                                        goodsStatus = backup.goodsStatus ?: GoodsStatus.NOT_GOTTEN.name,
                                        goodsPurchaseDate = backup.goodsPurchaseDate.orEmpty(),
                                        goodsPurchaseStore = backup.goodsPurchaseStore.orEmpty()
                                    )
                                )
                            }
                            if (backups.isNotEmpty()) {
                                reportImport(0.6f + 0.32f * (i + 1) / backups.size)
                            }
                        }
                    }
                    reportImport(0.92f)
                    favoriteCharaJson?.let { json ->
                        val type = object : TypeToken<List<String>>() {}.type
                        val favoriteCharas: List<String> = gson.fromJson(json, type)
                        val allCharas = App.charaDatabase.charaDao().getAll()
                        allCharas.forEachIndexed { i, chara ->
                            App.charaDatabase.charaDao().update(
                                chara.copy(charaIsFavorite = favoriteCharas.contains(chara.charaNm))
                            )
                            if (allCharas.isNotEmpty()) {
                                reportImport(0.92f + 0.08f * (i + 1) / allCharas.size)
                            }
                        }
                    }
                    reportImport(1f)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "입력 완료!", Toast.LENGTH_SHORT).show()
                }
                loadGottenGoods()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "입력 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

}