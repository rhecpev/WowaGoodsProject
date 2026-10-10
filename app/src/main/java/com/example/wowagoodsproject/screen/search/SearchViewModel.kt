package com.example.wowagoodsproject.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.applyStatus
import com.example.wowagoodsproject.db.fan.savePending
import com.example.wowagoodsproject.db.fan.setQuantity
import com.example.wowagoodsproject.db.fan.toggleGotten
import com.example.wowagoodsproject.db.fan.togglePending
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.applyStatus
import com.example.wowagoodsproject.db.official.bulkSetStatus
import com.example.wowagoodsproject.db.official.savePending
import com.example.wowagoodsproject.db.official.setQuantity
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.db.official.togglePending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 굿즈 검색 탭. 공식/2차창작 굿즈 전체를 지켜보고, 화면에서 캐릭터·카테고리로 거른다. */
class SearchViewModel : ViewModel() {

    /** 고른 캐릭터 이름. null 이면 캐릭터로 거르지 않는다. */
    private val _charaFilter = MutableStateFlow<String?>(null)
    val charaFilter: StateFlow<String?> = _charaFilter

    /** 고른 카테고리. null 이면 카테고리로 거르지 않는다. */
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    /** 보유 상태 필터(전체/보유/미보유/구매예정) */
    private val _statusFilter = MutableStateFlow(FilterType.ALL)
    val statusFilter: StateFlow<FilterType> = _statusFilter

    /** 필터 팝업에서 캐릭터 이미지/선호 여부를 보여주기 위한 전체 캐릭터 */
    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    /** 세트 굿즈까지 포함한 공식 굿즈 전체. 세트 정보를 찾는 데도 쓴다. */
    private val _officialGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGoods: StateFlow<List<GoodsEntity>> = _officialGoods

    private val _fanGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGoods: StateFlow<List<FanGoodsEntity>> = _fanGoods

    init {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest { _allCharaList.value = it }
        }
        viewModelScope.launch {
            App.database.goodsDao().getAllFlow().collectLatest { _officialGoods.value = it }
        }
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().getAllFlow().collectLatest { all ->
                _fanGoods.value = all.sortedByDescending { it.fanGoodsId }
            }
        }
    }

    fun setCharaFilter(chara: String?) {
        _charaFilter.value = chara
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun setStatusFilter(type: FilterType) {
        _statusFilter.value = type
    }

    fun clearFilters() {
        _charaFilter.value = null
        _categoryFilter.value = null
        _statusFilter.value = FilterType.ALL
    }

    /** 다중 선택한 굿즈를 한 번에 같은 상태로 바꾼다. [edit] 는 구매예정일 때 입력한 구매 정보. */
    fun applyStatus(
        official: List<GoodsEntity>,
        fan: List<FanGoodsEntity>,
        status: GoodsStatus,
        edit: PurchaseInfoEdit? = null
    ) {
        viewModelScope.launch {
            if (official.isNotEmpty()) {
                App.database.goodsDao().applyStatus(official.map { it.goodsId }, status, edit)
            }
            if (fan.isNotEmpty()) {
                App.fanDatabase.fanGoodsDao().applyStatus(fan.map { it.fanGoodsId }, status, edit)
            }
        }
    }

    // 아래 쓰기 함수들은 DB 의 최신 값을 다시 읽어 저장한다(GoodsWrites.kt / FanGoodsWrites.kt).
    // 목록은 DB 변경을 지켜보는 collect 가 알아서 다시 읽는다.
    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().toggleGotten(goods.goodsId) }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().togglePending(goods.goodsId) }
    }

    fun saveOfficialPendingInfo(goods: GoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.database.goodsDao().savePending(goods.goodsId, info) }
    }

    fun setOfficialQuantity(goods: GoodsEntity, quantity: Int) {
        viewModelScope.launch { App.database.goodsDao().setQuantity(goods.goodsId, quantity) }
    }

    /** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. [info] 가 있으면 구성품에 구매 정보도 적는다. */
    fun bulkSetOfficialStatus(setGoods: GoodsEntity, status: GoodsStatus, info: PurchaseInfo? = null) {
        viewModelScope.launch { App.database.goodsDao().bulkSetStatus(setGoods.goodsId, status, info) }
    }

    fun toggleFanGotten(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().toggleGotten(goods.fanGoodsId) }
    }

    fun setFanPending(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().togglePending(goods.fanGoodsId) }
    }

    fun saveFanPendingInfo(goods: FanGoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().savePending(goods.fanGoodsId, info) }
    }

    fun setFanQuantity(goods: FanGoodsEntity, quantity: Int) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().setQuantity(goods.fanGoodsId, quantity) }
    }
}
