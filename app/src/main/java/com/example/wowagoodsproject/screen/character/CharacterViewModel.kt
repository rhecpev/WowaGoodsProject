package com.example.wowagoodsproject.screen.character

import com.example.wowagoodsproject.db.fan.setQuantity
import com.example.wowagoodsproject.db.fan.applyStatus
import com.example.wowagoodsproject.db.fan.savePending
import com.example.wowagoodsproject.db.fan.togglePending
import com.example.wowagoodsproject.db.fan.toggleGotten
import com.example.wowagoodsproject.db.official.setQuantity
import com.example.wowagoodsproject.db.official.applyStatus
import com.example.wowagoodsproject.db.official.savePending
import com.example.wowagoodsproject.db.official.bulkSetStatus
import com.example.wowagoodsproject.db.official.togglePending
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.PurchaseInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CharacterViewModel : ViewModel() {

    private val _charaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val charaList: StateFlow<List<CharaEntity>> = _charaList

    private val _selectedChara = MutableStateFlow<CharaEntity?>(null)
    val selectedChara: StateFlow<CharaEntity?> = _selectedChara

    private val _officialGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val officialGoods: StateFlow<List<GoodsEntity>> = _officialGoods

    private val _allSeriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val allSeriesGoods: StateFlow<List<GoodsEntity>> = _allSeriesGoods

    private val _fanGoods = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val fanGoods: StateFlow<List<FanGoodsEntity>> = _fanGoods

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _showFavoriteOnly = MutableStateFlow(false)

    val showFavoriteOnly: StateFlow<Boolean> = _showFavoriteOnly

    private val _charaGoodsCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val charaGoodsCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _charaGoodsCountMap
    fun toggleFavoriteOnly() {
        _showFavoriteOnly.value = !_showFavoriteOnly.value
    }

    init {
        loadCharaList()
        loadCharaGoodsCount()
        observeFanGoods()
    }

    /** 다른 탭·데이터 입력으로 2차창작 굿즈가 바뀌어도 열려 있는 캐릭터 화면에 바로 반영한다. */
    private fun observeFanGoods() {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().getAllFlow().collectLatest { allFan ->
                _selectedChara.value?.let { chara ->
                    _fanGoods.value = allFan
                        .filter { it.fanGoodsChara.contains(chara.charaNm, ignoreCase = true) }
                        .sortedByDescending { it.fanGoodsId }
                }
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

    fun toggleFavorite(chara: CharaEntity) {
        viewModelScope.launch {
            App.charaDatabase.charaDao().update(
                chara.copy(charaIsFavorite = !chara.charaIsFavorite)
            )
        }
    }

    fun selectChara(chara: CharaEntity) {
        _selectedChara.value = chara
        viewModelScope.launch {
            val charaGoods = App.database.goodsDao().getByChara(chara.charaNm)
            _officialGoods.value = charaGoods
            val seriesList = charaGoods.map { it.goodsSeries }.distinct()
            _allSeriesGoods.value = seriesList.flatMap {
                App.database.goodsDao().getBySeries(it)
            }
            _fanGoods.value = App.fanDatabase.fanGoodsDao().getByChara(chara.charaNm)
        }
    }
    fun clearSelectedChara() {
        _selectedChara.value = null
        _officialGoods.value = emptyList()
        _allSeriesGoods.value = emptyList()
        _fanGoods.value = emptyList()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    // 아래 쓰기 함수들은 DB 의 최신 값을 다시 읽어 저장한다(GoodsWrites.kt / FanGoodsWrites.kt).
    // 목록은 DB 변경을 지켜보는 collect 가 알아서 다시 읽는다.
    fun toggleOfficialGotten(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().toggleGotten(goods.goodsId) }
    }

    fun setOfficialPending(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().togglePending(goods.goodsId) }
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

    /** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. [info] 가 있으면 구성품에 구매 정보도 적는다. */
    fun bulkSetOfficialStatus(setGoods: GoodsEntity, status: GoodsStatus, info: PurchaseInfo? = null) {
        viewModelScope.launch { App.database.goodsDao().bulkSetStatus(setGoods.goodsId, status, info) }
    }
    // After - loadCharaList() 아래에 함수 추가
    private fun loadCharaGoodsCount() {
        viewModelScope.launch {
            App.database.goodsDao().getAllFlow().collectLatest { allGoods ->
                val countMap = mutableMapOf<String, Pair<Int, Int>>()
                allGoods.filter { it.goodsCategory != CATEGORY_SET }.forEach { goods ->
                    goods.goodsChara.split(",").forEach { chara ->
                        val name = chara.trim()
                        if (name.isNotEmpty()) {
                            val current = countMap[name] ?: Pair(0, 0)
                            val gotten = if (goods.goodsStatus == GoodsStatus.GOTTEN.name) current.first + 1 else current.first
                            countMap[name] = Pair(gotten, current.second + 1)
                        }
                    }
                }
                _charaGoodsCountMap.value = countMap

                // 다른 탭·데이터 입력·백그라운드 업데이트로 바뀐 값도 열려 있는 캐릭터 화면에 바로 반영한다.
                _selectedChara.value?.let { chara ->
                    val charaGoods = allGoods.filter { it.goodsChara.contains(chara.charaNm, ignoreCase = true) }
                    _officialGoods.value = charaGoods
                    val seriesSet = charaGoods.map { it.goodsSeries }.toSet()
                    _allSeriesGoods.value = allGoods.filter { it.goodsSeries in seriesSet }
                }
            }
        }
    }
    fun deleteFanGoods(goods: FanGoodsEntity) {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().delete(goods)
        }
    }

}