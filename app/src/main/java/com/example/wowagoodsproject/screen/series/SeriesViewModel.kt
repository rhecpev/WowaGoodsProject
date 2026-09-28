package com.example.wowagoodsproject.screen.series

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
import com.example.wowagoodsproject.component.CATEGORY_COMPONENT
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.series.SeriesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SeriesViewModel : ViewModel() {

    private val _seriesList = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val seriesList: StateFlow<List<SeriesEntity>> = _seriesList

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _charaMap = MutableStateFlow<Map<String, CharaEntity>>(emptyMap())
    val charaMap: StateFlow<Map<String, CharaEntity>> = _charaMap

    private val _charaGoodsCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val charaGoodsCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _charaGoodsCountMap

    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    private val _selectedCharaFilter = MutableStateFlow<CharaEntity?>(null)
    val selectedCharaFilter: StateFlow<CharaEntity?> = _selectedCharaFilter

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries

    private val _seriesGoods = MutableStateFlow<List<GoodsEntity>>(emptyList())
    val seriesGoods: StateFlow<List<GoodsEntity>> = _seriesGoods

    private val _seriesCharaCountMap = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val seriesCharaCountMap: StateFlow<Map<String, Pair<Int, Int>>> = _seriesCharaCountMap

    private val _filteredSeriesList = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val filteredSeriesList: StateFlow<List<SeriesEntity>> = _filteredSeriesList

    private val _titleQuery = MutableStateFlow("")
    val titleQuery: StateFlow<String> = _titleQuery

    val countries = listOf("전체","버전", "중국", "대만", "한국", "일본", "기타")

    private val _tabScrollPositions = MutableStateFlow<Map<Int, Pair<Int, Int>>>(emptyMap())
    val tabScrollPositions: StateFlow<Map<Int, Pair<Int, Int>>> = _tabScrollPositions

    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex: StateFlow<Int> = _scrollIndex

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset

    fun saveScrollPosition(index: Int, offset: Int) {
        _scrollIndex.value = index
        _scrollOffset.value = offset
    }

    fun saveTabScrollPosition(tabIndex: Int, scrollIndex: Int, scrollOffset: Int) {
        val positions = _tabScrollPositions.value.toMutableMap()
        positions[tabIndex] = Pair(scrollIndex, scrollOffset)
        _tabScrollPositions.value = positions
    }

    fun getTabScrollPosition(tabIndex: Int): Pair<Int, Int> {
        return _tabScrollPositions.value[tabIndex] ?: Pair(0, 0)
    }


    init {
        loadCharaMap()
        viewModelScope.launch {
            App.seriesDatabase.seriesDao().getAllFlow()
                .combine(App.database.goodsDao().getAllFlow()) { series, goods ->
                    Pair(series, goods)
                }
                .collectLatest { (series, goods) ->
                    _seriesList.value = series
                    // 다른 탭·데이터 입력·백그라운드 업데이트로 바뀐 값도 열려 있는 시리즈에 바로 반영한다.
                    _selectedSeries.value?.let { selected ->
                        _seriesGoods.value = goods.filter { it.goodsSeries == selected.seriesNm }
                    }
                    updateFilteredList()
                    loadSeriesCharaCount()
                    loadCharaGoodsCount()
                }
        }
    }

    fun loadCharaMap() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest { charaList ->
                _allCharaList.value = charaList
                _charaMap.value = charaList.associateBy { it.charaNm }
            }
        }
    }

    private suspend fun loadCharaGoodsCount() {
        val allGoods = App.database.goodsDao().getAll()
            .filter { it.goodsCategory != CATEGORY_SET }
        val countMap = mutableMapOf<String, Pair<Int, Int>>()
        allGoods.forEach { goods ->
            goods.goodsChara.split(",").forEach { chara ->
                val name = chara.trim()
                if (name.isEmpty()) return@forEach
                val current = countMap[name] ?: Pair(0, 0)
                val gotten = if (goods.goodsStatus == GoodsStatus.GOTTEN.name) current.first + 1 else current.first
                countMap[name] = Pair(gotten, current.second + 1)
            }
        }
        _charaGoodsCountMap.value = countMap
    }

    private suspend fun loadSeriesCharaCount() {
        val allGoods = App.database.goodsDao().getAll()
            .filter { it.goodsCategory != CATEGORY_SET }

        val countMap = mutableMapOf<String, Pair<Int, Int>>()
        _seriesList.value.forEach { series ->
            series.seriesCharas.split(",").forEach { chara ->
                val name = chara.trim()
                if (name.isEmpty()) return@forEach
                val seriesGoods = allGoods.filter {
                    it.goodsSeries == series.seriesNm &&
                            it.goodsChara.contains(name)
                }
                countMap["${series.seriesNm}|$name"] = Pair(
                    seriesGoods.count { it.goodsStatus == GoodsStatus.GOTTEN.name },
                    seriesGoods.size
                )
            }
        }
        _seriesCharaCountMap.value = countMap
    }
    fun updateFilteredList() {
        val country = countries[_selectedTab.value]
        val byCountry = if (country == "전체") _seriesList.value
        else _seriesList.value.filter { it.seriesCountry == country }
        val charaFilter = _selectedCharaFilter.value
        val byChara = if (charaFilter == null) byCountry
        else byCountry.filter { series ->
            series.seriesCharas.split(",").map { it.trim() }.contains(charaFilter.charaNm)
        }
        val query = _titleQuery.value.trim()
        _filteredSeriesList.value = if (query.isEmpty()) byChara
        else byChara.filter { it.seriesNm.contains(query, ignoreCase = true) }
    }
    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
        updateFilteredList()
    }

    fun setCharaFilter(chara: CharaEntity?) {
        _selectedCharaFilter.value = chara
        updateFilteredList()
    }

    fun setTitleQuery(query: String) {
        _titleQuery.value = query
        updateFilteredList()
    }

    fun selectSeries(series: SeriesEntity) {
        _selectedSeries.value = series
        viewModelScope.launch {
            _seriesGoods.value = App.database.goodsDao().getBySeries(series.seriesNm)
        }
    }

    fun clearSelectedSeries() {
        _selectedSeries.value = null
        _seriesGoods.value = emptyList()
    }

    // 아래 쓰기 함수들은 DB 의 최신 값을 다시 읽어 저장한다(GoodsWrites.kt).
    // 목록·개수는 DB 변경을 지켜보는 init 의 collect 가 알아서 다시 읽는다.
    fun toggleGotten(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().toggleGotten(goods.goodsId) }
    }

    fun setPending(goods: GoodsEntity) {
        viewModelScope.launch { App.database.goodsDao().togglePending(goods.goodsId) }
    }
    /** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. [info] 가 있으면 구성품에 구매 정보도 적는다. */
    fun bulkSetStatus(setGoods: GoodsEntity, status: GoodsStatus, info: PurchaseInfo? = null) {
        viewModelScope.launch { App.database.goodsDao().bulkSetStatus(setGoods.goodsId, status, info) }
    }

    /** 구매예정으로 바꾸면서(또는 이미 구매예정이면 그대로) 구매 정보를 저장한다. */
    fun savePendingInfo(goods: GoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.database.goodsDao().savePending(goods.goodsId, info) }
    }

    /** 다중 선택한 굿즈를 한 번에 같은 상태로 바꾼다. [edit] 는 구매예정일 때 입력한 구매 정보. */
    fun applyStatus(targets: List<GoodsEntity>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
        viewModelScope.launch { App.database.goodsDao().applyStatus(targets.map { it.goodsId }, status, edit) }
    }

    fun setQuantity(goods: GoodsEntity, quantity: Int) {
        viewModelScope.launch { App.database.goodsDao().setQuantity(goods.goodsId, quantity) }
    }

    fun getCharasForSeries(series: SeriesEntity): List<CharaEntity> {
        if (series.seriesCharas.isEmpty()) return emptyList()
        return series.seriesCharas.split(",").mapNotNull { _charaMap.value[it.trim()] }
    }

    fun getCharaGoodsCount(charaNm: String): Pair<Int, Int> {
        return _charaGoodsCountMap.value[charaNm] ?: Pair(0, 0)
    }

    fun getSeriesCharaCount(seriesNm: String, charaNm: String): Pair<Int, Int> {
        return _seriesCharaCountMap.value["$seriesNm|$charaNm"] ?: Pair(0, 0)
    }
}