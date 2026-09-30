package com.example.wowagoodsproject.screen.mypage

import kotlinx.coroutines.flow.combine
import com.example.wowagoodsproject.db.fan.setShipping
import com.example.wowagoodsproject.db.fan.applyPurchaseInfo
import com.example.wowagoodsproject.db.fan.setQuantity
import com.example.wowagoodsproject.db.fan.applyStatus
import com.example.wowagoodsproject.db.fan.savePending
import com.example.wowagoodsproject.db.fan.togglePending
import com.example.wowagoodsproject.db.fan.toggleGotten
import com.example.wowagoodsproject.db.official.setShipping
import com.example.wowagoodsproject.db.official.applyPurchaseInfo
import com.example.wowagoodsproject.db.official.setQuantity
import com.example.wowagoodsproject.db.official.applyStatus
import com.example.wowagoodsproject.db.official.savePending
import com.example.wowagoodsproject.db.official.bulkSetStatus
import com.example.wowagoodsproject.db.official.togglePending
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.PurchaseInfo
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.PendingInfoFilter
import com.example.wowagoodsproject.component.toggle
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
    val goodsPurchaseStore: String? = null,
    val goodsReceiveDate: String? = null,
    val goodsQuantity: Int? = null,
    val goodsShippingDate: String? = null
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
    val fanGoodsPurchaseStore: String? = null,
    val fanGoodsReceiveDate: String? = null,
    val fanGoodsQuantity: Int? = null,
    val fanGoodsShippingDate: String? = null
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
        fanGoodsPurchaseStore = fanGoodsPurchaseStore.orEmpty(),
        fanGoodsReceiveDate = fanGoodsReceiveDate.orEmpty(),
        fanGoodsQuantity = fanGoodsQuantity ?: 1,
        fanGoodsShippingDate = fanGoodsShippingDate.orEmpty()
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

    /** 구매예정 목록에서만 쓰는 구매 정보 필터 */
    private val _pendingInfoFilters = MutableStateFlow<Set<PendingInfoFilter>>(emptySet())
    val pendingInfoFilters: StateFlow<Set<PendingInfoFilter>> = _pendingInfoFilters

    fun togglePendingInfoFilter(filter: PendingInfoFilter) {
        _pendingInfoFilters.value = _pendingInfoFilters.value.toggle(filter)
    }

    fun clearPendingInfoFilters() {
        _pendingInfoFilters.value = emptySet()
    }

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
        observeGoods()
        checkLatestVersion()
        observeUnreadNews()
    }

    /** 다른 탭·데이터 입력·백그라운드 업데이트로 굿즈가 바뀌면 보유/구매예정 목록을 바로 다시 만든다. */
    private fun observeGoods() {
        viewModelScope.launch {
            App.database.goodsDao().getAllFlow()
                .combine(App.fanDatabase.fanGoodsDao().getAllFlow()) { goods, fan -> goods to fan }
                .collectLatest { (goods, fan) -> applyGoods(goods, fan) }
        }
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
            applyGoods(App.database.goodsDao().getAll(), App.fanDatabase.fanGoodsDao().getAll())
        }
    }

    private fun applyGoods(allGoods: List<GoodsEntity>, allFanGoods: List<FanGoodsEntity>) {
        // 세트 굿즈는 카운트/목록에서 제외하고 낱개 굿즈만 집계한다.
        // 구매예정은 '구매예정 굿즈' 페이지에서 따로 보므로 여기서는 보유만 센다.
        val gottenGoods = allGoods.filter {
            it.goodsCategory != CATEGORY_SET && it.goodsStatus == GoodsStatus.GOTTEN.name
        }
        _officialGottenGoods.value = gottenGoods
        // 구매예정 굿즈도 상세/세트 팝업에서 최신 값을 찾을 수 있게 그 시리즈를 함께 담는다.
        val seriesSet = allGoods
            .filter { it.goodsStatus == GoodsStatus.GOTTEN.name || it.goodsStatus == GoodsStatus.PENDING.name }
            .map { it.goodsSeries }.toSet()
        _allSeriesGoods.value = allGoods.filter { it.goodsSeries in seriesSet }
        _fanGottenGoods.value = allFanGoods.filter { it.isGotten }

        // 구매예정 목록도 같은 조회 결과에서 뽑아 둔다(세트 굿즈는 제외).
        _pendingOfficialGoods.value = allGoods.filter {
            it.goodsCategory != CATEGORY_SET && it.status == GoodsStatus.PENDING
        }
        _pendingFanGoods.value = allFanGoods.filter { it.status == GoodsStatus.PENDING }
    }

    /**
     * 고른 구매예정 굿즈에 구입처/구매일/수령예정일을 한 번에 적어 넣는다.
     * null 인 항목은 건드리지 않고, 빈 문자열은 지우라는 뜻이다.
     */
    fun applyPurchaseInfo(
        officialIds: Set<Int>,
        fanIds: Set<Int>,
        edit: PurchaseInfoEdit
    ) {
        if (edit.isEmpty) return
        viewModelScope.launch {
            officialIds.forEach { App.database.goodsDao().applyPurchaseInfo(it, edit) }
            fanIds.forEach { App.fanDatabase.fanGoodsDao().applyPurchaseInfo(it, edit) }
        }
    }

    /**
     * 고른 구매예정 굿즈의 배송 시작일을 적는다. 빈 문자열이면 배송 시작을 취소한다.
     * [onlyNotStarted] 가 true 면 이미 배송이 시작된 굿즈는 날짜를 그대로 둔다.
     */
    fun setShipping(officialIds: Set<Int>, fanIds: Set<Int>, date: String, onlyNotStarted: Boolean = false) {
        viewModelScope.launch {
            officialIds.forEach { App.database.goodsDao().setShipping(it, date, onlyNotStarted) }
            fanIds.forEach { App.fanDatabase.fanGoodsDao().setShipping(it, date, onlyNotStarted) }
        }
    }

    // 아래 쓰기 함수들은 DB 의 최신 값을 다시 읽어 저장한다(GoodsWrites.kt / FanGoodsWrites.kt).
    // 목록은 DB 변경을 지켜보는 observeGoods 가 알아서 다시 만든다.
    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().toggleGotten(goods.goodsId) }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().togglePending(goods.goodsId) }
    }
    /** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. [info] 가 있으면 구성품에 구매 정보도 적는다. */
    fun bulkSetOfficialStatus(setGoods: GoodsEntity, status: GoodsStatus, info: PurchaseInfo? = null) {
        viewModelScope.launch { App.database.goodsDao().bulkSetStatus(setGoods.goodsId, status, info) }
    }
    /** 구매예정으로 바꾸면서(또는 이미 구매예정이면 그대로) 구매 정보를 저장한다. */
    fun saveOfficialPendingInfo(goods: GoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.database.goodsDao().savePending(goods.goodsId, info) }
    }

    fun saveFanPendingInfo(goods: FanGoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().savePending(goods.fanGoodsId, info) }
    }

    /** 다중 선택한 굿즈를 한 번에 같은 상태로 바꾼다. [edit] 는 구매예정일 때 입력한 구매 정보. */
    fun applyOfficialStatus(targets: List<GoodsEntity>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
        viewModelScope.launch { App.database.goodsDao().applyStatus(targets.map { it.goodsId }, status, edit) }
    }

    fun applyFanStatus(targets: List<FanGoodsEntity>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().applyStatus(targets.map { it.fanGoodsId }, status, edit) }
    }

    fun setOfficialQuantity(goods: GoodsEntity, quantity: Int) {
        viewModelScope.launch { App.database.goodsDao().setQuantity(goods.goodsId, quantity) }
    }

    fun setFanQuantity(goods: FanGoodsEntity, quantity: Int) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().setQuantity(goods.fanGoodsId, quantity) }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().toggleGotten(goods.fanGoodsId) }
    }

    fun setFanPending(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().togglePending(goods.fanGoodsId) }
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


    /**
     * 백업 zip 을 [zipFile] 에 만든다. 추출과 테스트가 함께 쓴다.
     * 새 칸을 추가하면 여기(추출)와 importData(입력), 백업 클래스 세 곳을 함께 고쳐야 한다.
     */
    internal suspend fun writeBackupZip(zipFile: File) = withContext(Dispatchers.IO) {
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
                goodsPurchaseStore = it.goodsPurchaseStore,
                goodsReceiveDate = it.goodsReceiveDate,
                goodsQuantity = it.goodsQuantity,
                goodsShippingDate = it.goodsShippingDate
            )
        }
        val officialJson = gson.toJson(officialBackup)
        val charas = App.charaDatabase.charaDao().getAll()
        val favoriteCharas = charas.filter { it.charaIsFavorite }.map { it.charaNm }
        val charaJson = gson.toJson(favoriteCharas)

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
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val zipFile = File(context.filesDir, "wowa_backup.zip")
                    writeBackupZip(zipFile)
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
                                        goodsPurchaseStore = backup.goodsPurchaseStore.orEmpty(),
                                        goodsReceiveDate = backup.goodsReceiveDate.orEmpty(),
                                        goodsQuantity = backup.goodsQuantity ?: 1,
                                        goodsShippingDate = backup.goodsShippingDate.orEmpty()
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