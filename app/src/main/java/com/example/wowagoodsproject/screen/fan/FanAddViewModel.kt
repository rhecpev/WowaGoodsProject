package com.example.wowagoodsproject.screen.fan

import com.example.wowagoodsproject.component.GoodsStatus
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.PhotoStore
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.withStatus
import com.example.wowagoodsproject.db.fan.deleteUnusedImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FanAddViewModel : ViewModel() {

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price

    private val _series = MutableStateFlow("")
    val series: StateFlow<String> = _series

    private val _selectedCharas = MutableStateFlow<List<String>>(emptyList())
    val selectedCharas: StateFlow<List<String>> = _selectedCharas

    private val _category = MutableStateFlow("")
    val category: StateFlow<String> = _category

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _categoryList = MutableStateFlow<List<String>>(emptyList())
    val categoryList: StateFlow<List<String>> = _categoryList

    private val _isGotten = MutableStateFlow(false)
    val isGotten: StateFlow<Boolean> = _isGotten

    private val _memo = MutableStateFlow("")
    val memo: StateFlow<String> = _memo

    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity

    /** 시리즈 고르기 목록: 등록한 시리즈(아이콘) + 2차창작 굿즈에 적힌 시리즈 */
    private val _seriesOptions = MutableStateFlow<List<SeriesOption>>(emptyList())
    val seriesOptions: StateFlow<List<SeriesOption>> = _seriesOptions

    init {
        loadCharaList()
        loadCategoryList()
        viewModelScope.launch {
            val dao = App.fanDatabase.fanGoodsDao()
            kotlinx.coroutines.flow.combine(dao.seriesFlow(), dao.getAllFlow()) { reg, goods ->
                seriesOptionsOf(reg.map { it.seriesNm to it.seriesImgPath }, goods.map { it.fanGoodsSeries })
            }.collectLatest { _seriesOptions.value = it }
        }
    }

    private fun loadCharaList() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest {
                _charaList.value = it
            }
        }
    }

    private fun loadCategoryList() {
        viewModelScope.launch {
            val fanCategories = App.fanDatabase.fanGoodsDao().getFanCategories()
            val officialCategories = App.database.goodsDao().getAllCategories()
            _categoryList.value = (fanCategories + officialCategories)
                .distinct()
                .filter { it != CATEGORY_SET }  // 추가
                .sorted()
        }
    }

    fun onPriceChange(value: String) { _price.value = value }
    fun onSeriesChange(value: String) { _series.value = value }
    fun onCategoryChange(value: String) { _category.value = value }
    fun onImageSelected(uri: Uri?) { _imageUri.value = uri }
    fun onIsGottenChange(value: Boolean) { _isGotten.value = value; _status.value = if (value) GoodsStatus.GOTTEN else GoodsStatus.NOT_GOTTEN }

    /** 보유 여부(미보유·구매예정·보유) */
    private val _status = MutableStateFlow(GoodsStatus.NOT_GOTTEN)
    val status: StateFlow<GoodsStatus> = _status
    fun onStatusChange(value: GoodsStatus) { _status.value = value; _isGotten.value = value == GoodsStatus.GOTTEN }

    /** 구매예정일 때의 구매 정보 */
    val purchaseStore = MutableStateFlow("")
    val purchaseDate = MutableStateFlow("")
    val receiveDate = MutableStateFlow("")
    fun onMemoChange(value: String) { _memo.value = value }
    fun onQuantityChange(value: Int) { _quantity.value = value.coerceIn(1, 99) }

    fun toggleChara(chara: String) {
        val current = _selectedCharas.value.toMutableList()
        if (current.contains(chara)) {
            current.remove(chara)
        } else {
            current.add(chara)
        }
        _selectedCharas.value = current
    }

    /** 고른 사진을 앱 폴더에 WebP 로 저장한다([PhotoStore.save]). 사진을 못 열면 없는 경로를 저장하지 않고 실패로 알린다. */
    private suspend fun copyImageToInternalStorage(context: Context, uri: Uri): String =
        PhotoStore.save(context, uri, "fan_")

    /** 고치는 굿즈. null 이면 새로 등록 */
    private val _editing = MutableStateFlow<FanGoodsEntity?>(null)
    val editing: StateFlow<FanGoodsEntity?> = _editing
    private val _editMissing = MutableStateFlow(false)
    /** 고치려던 굿즈를 찾지 못했다(그사이 지워짐) */
    val editMissing: StateFlow<Boolean> = _editMissing
    /** 불러온 때의 사진. 이 사진 그대로면 다시 복사하지 않는다 */
    private var originalImageUri: Uri? = null

    /** 고칠 굿즈를 불러와 입력 칸을 채운다. 같은 굿즈를 이미 불러왔으면 사용자가 고친 값을 덮지 않는다. */
    fun loadForEdit(id: Int) {
        if (_editing.value?.fanGoodsId == id) return
        _editMissing.value = false
        viewModelScope.launch {
            val g = App.fanDatabase.fanGoodsDao().getById(id)
            if (g == null) {
                // 그사이 지워진 굿즈: 화면이 로딩 표시에 멈추지 않고 알린 뒤 닫는다.
                _editMissing.value = true
                return@launch
            }
            _price.value = g.fanGoodsPrice
            _series.value = g.fanGoodsSeries
            _selectedCharas.value = com.example.wowagoodsproject.db.betray.charaNames(g.fanGoodsChara)
            _category.value = g.fanGoodsCategory
            _memo.value = g.fanGoodsMemo
            _status.value = g.status
            _isGotten.value = g.status == GoodsStatus.GOTTEN
            _quantity.value = g.fanGoodsQuantity
            purchaseStore.value = g.fanGoodsPurchaseStore
            purchaseDate.value = g.fanGoodsPurchaseDate
            receiveDate.value = g.fanGoodsReceiveDate
            originalImageUri = g.fanGoodsImgPath.takeIf { it.isNotEmpty() }?.let { Uri.fromFile(File(it)) }
            _imageUri.value = originalImageUri
            _editing.value = g
        }
    }

    /** 고치는 중에 처음 불러온 값에서 바뀐 것이 있는지(나갈 때 물을지) */
    fun hasEditChanges(): Boolean {
        val g = _editing.value ?: return false
        return _price.value != g.fanGoodsPrice || _series.value != g.fanGoodsSeries ||
            _selectedCharas.value.joinToString(",") != com.example.wowagoodsproject.db.betray.charaNames(g.fanGoodsChara).joinToString(",") ||
            _category.value != g.fanGoodsCategory || _memo.value != g.fanGoodsMemo || _status.value != g.status ||
            _quantity.value != g.fanGoodsQuantity || _imageUri.value != originalImageUri ||
            purchaseStore.value != g.fanGoodsPurchaseStore || purchaseDate.value != g.fanGoodsPurchaseDate || receiveDate.value != g.fanGoodsReceiveDate
    }

    private val _isSaving = MutableStateFlow(false)
    /** 저장 중에는 등록 버튼을 막아 두 번 눌러도 한 번만 저장되게 한다. */
    val isSaving: StateFlow<Boolean> = _isSaving

    fun insert(context: Context, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            // 저장소가 가득 찼거나 사진 권한이 사라지면 복사가 실패한다. 앱이 꺼지지 않게 알리고 화면에 남는다.
            val saved = try {
                saveGoods(context)
                true
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                false
            } finally {
                _isSaving.value = false
            }
            if (saved) onSuccess()
        }
    }

    /**
     * 저장할 시리즈 이름. '미분류'는 시리즈를 비운 굿즈를 묶는 이름이라, 그렇게 적었으면 비워 둔다
     * (적은 굿즈와 비운 굿즈가 한 묶음에 섞여도 같은 뜻이 되고, 그 묶음에서 '+'로 추가한 굿즈와도 맞다).
     */
    private fun savedSeries(): String =
        _series.value.takeUnless { it.trim().equals(com.example.wowagoodsproject.screen.fan.NO_FAN_SERIES, ignoreCase = true) } ?: ""

    private suspend fun saveGoods(context: Context) {
        _editing.value?.let { old -> updateGoods(context, old); return }
        val imgPath = _imageUri.value?.let {
            copyImageToInternalStorage(context, it)
        } ?: ""

        // 저장하지 못하면 방금 복사한 사진은 지운다(쓰이지 않는 사진을 남기지 않는다).
        try { App.fanDatabase.fanGoodsDao().insert(
            FanGoodsEntity(
                fanGoodsReleaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                fanGoodsSeries = savedSeries(),
                fanGoodsPrice = _price.value,
                fanGoodsChara = _selectedCharas.value.joinToString(","),
                fanGoodsCategory = _category.value,
                fanGoodsImgPath = imgPath,
                fanGoodsIsGotten = _status.value == GoodsStatus.GOTTEN,
                // 앱은 보유 여부를 상태 칸으로 판단하므로 상태도 같이 적어야 '보유' 로 보인다.
                fanGoodsStatus = _status.value.name,
                fanGoodsMemo = _memo.value,
                // 미보유 굿즈는 수량이 1 로 고정이다(상태를 바꿀 때와 같은 규칙).
                fanGoodsQuantity = if (_status.value == GoodsStatus.NOT_GOTTEN) 1 else _quantity.value,
                // 구매예정이 아니면 구매 정보는 비워 둔다.
                fanGoodsPurchaseStore = if (_status.value == GoodsStatus.PENDING) purchaseStore.value.trim() else "",
                fanGoodsPurchaseDate = if (_status.value == GoodsStatus.PENDING) purchaseDate.value else "",
                fanGoodsReceiveDate = if (_status.value == GoodsStatus.PENDING) receiveDate.value else ""
            )
        ) } catch (e: Exception) {
            if (imgPath.isNotEmpty()) java.io.File(imgPath).delete()
            throw e
        }
    }

    /**
     * 고친 내용을 그 굿즈 줄에 저장한다. 등록일·배송 시작일은 그대로 두고,
     * 구매예정이 아니면 구매 정보도 지우지 않는다(보유로 바꿔도 구입처가 남는 다른 화면과 같다).
     * 사진을 바꾸거나 뺐으면 예전 사진은 아무도 쓰지 않을 때 지운다.
     */
    private suspend fun updateGoods(context: Context, old: FanGoodsEntity) {
        val dao = App.fanDatabase.fanGoodsDao()
        // 고치는 사이 지워졌으면 사진을 복사하기 전에 멈춘다(저장된 척하지 않고, 쓰이지 않는 사진도 남기지 않는다).
        val fresh = dao.getById(old.fanGoodsId) ?: throw IllegalStateException("그사이 지워진 굿즈입니다")
        val uri = _imageUri.value
        val imgPath = when {
            uri == null -> ""
            uri == originalImageUri -> fresh.fanGoodsImgPath
            else -> copyImageToInternalStorage(context, uri)
        }
        val pending = _status.value == GoodsStatus.PENDING
        val updated = fresh.copy(
            fanGoodsSeries = savedSeries(),
            fanGoodsPrice = _price.value,
            fanGoodsChara = _selectedCharas.value.joinToString(","),
            fanGoodsCategory = _category.value,
            fanGoodsImgPath = imgPath,
            fanGoodsMemo = _memo.value,
            fanGoodsQuantity = if (_status.value == GoodsStatus.NOT_GOTTEN) 1 else _quantity.value,
            fanGoodsPurchaseStore = if (pending) purchaseStore.value.trim() else fresh.fanGoodsPurchaseStore,
            fanGoodsPurchaseDate = if (pending) purchaseDate.value else fresh.fanGoodsPurchaseDate,
            fanGoodsReceiveDate = if (pending) receiveDate.value else fresh.fanGoodsReceiveDate
        ).withStatus(_status.value.name)
        try {
            dao.update(updated)
        } catch (e: Exception) {
            // 저장하지 못하면 방금 복사한 새 사진은 지운다.
            if (imgPath.isNotEmpty() && imgPath != fresh.fanGoodsImgPath) java.io.File(imgPath).delete()
            throw e
        }
        if (fresh.fanGoodsImgPath.isNotEmpty() && fresh.fanGoodsImgPath != imgPath) {
            dao.deleteUnusedImages(listOf(fresh.fanGoodsImgPath))
        }
    }
}
