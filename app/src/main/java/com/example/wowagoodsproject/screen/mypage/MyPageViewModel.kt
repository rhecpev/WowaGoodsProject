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
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import android.content.Intent
import com.example.wowagoodsproject.UpdateManager
import com.example.wowagoodsproject.component.GoodsStatus

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

    /** 구매예정 목록에서만 쓰는 구매 정보 필터. null 이면 전체. */
    private val _pendingInfoFilter = MutableStateFlow<PendingInfoFilter?>(null)
    val pendingInfoFilter: StateFlow<PendingInfoFilter?> = _pendingInfoFilter

    fun setPendingInfoFilter(filter: PendingInfoFilter?) {
        _pendingInfoFilter.value = filter
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

    /** 마이페이지 변경된 굿즈 중 아직 처리하지 않은 기록 수. 들어가면 처음 보이는 미처리 목록의 개수와 같다. */
    private val _changedGoodsCount = MutableStateFlow(0)
    val changedGoodsCount: StateFlow<Int> = _changedGoodsCount

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
        viewModelScope.launch {
            App.changedGoodsDatabase.changedGoodsDao().uncheckedCountFlow().collectLatest {
                _changedGoodsCount.value = it
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


    /** 백업 zip 을 [zipFile] 에 만든다. 추출과 테스트가 함께 쓴다. */
    internal suspend fun writeBackupZip(zipFile: File) = BackupZip.write(zipFile)

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
                        ) ?: throw IllegalStateException("다운로드 폴더에 파일을 만들지 못했습니다")
                        try {
                            val output = resolver.openOutputStream(downloadUri)
                                ?: throw IllegalStateException("다운로드 폴더에 쓰지 못했습니다")
                            output.use { FileInputStream(zipFile).use { input -> input.copyTo(it) } }
                        } catch (e: Exception) {
                            // 반쯤 쓴 파일이 다운로드 폴더에 남으면 나중에 백업으로 착각해 불러올 수 있다.
                            resolver.delete(downloadUri, null, null)
                            throw e
                        }
                        // 다운로드 폴더로 옮겼으니 앱 안의 임시 파일은 지운다.
                        zipFile.delete()
                    } else {
                        // 공유받는 앱이 나중에 이 파일을 읽으므로 여기서 지우지 않는다.
                        // 다음 추출 때 같은 이름으로 덮어쓴다.
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
                val report = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("파일을 열 수 없습니다")
                    input.use { BackupZip.restore(it, context.filesDir) { p -> reportImport(p) } }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, report.message(), Toast.LENGTH_LONG).show()
                }
                loadGottenGoods()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(App.appContext, "입력 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

}