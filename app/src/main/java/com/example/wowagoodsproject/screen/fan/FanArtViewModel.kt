package com.example.wowagoodsproject.screen.fan

import com.example.wowagoodsproject.db.fan.setQuantity
import com.example.wowagoodsproject.db.fan.applyStatus
import com.example.wowagoodsproject.db.fan.savePending
import com.example.wowagoodsproject.db.fan.togglePending
import com.example.wowagoodsproject.db.fan.toggleGotten
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.PurchaseInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FanArtViewModel : ViewModel() {

    private val _goodsList = MutableStateFlow<List<FanGoodsEntity>>(emptyList())
    val goodsList: StateFlow<List<FanGoodsEntity>> = _goodsList
    private val _allCharaList = MutableStateFlow<List<CharaEntity>>(emptyList())
    val allCharaList: StateFlow<List<CharaEntity>> = _allCharaList

    init {
        loadCharaMap()
        // 다른 탭·데이터 입력으로 바뀐 값도 목록에 바로 반영한다.
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().getAllFlow().collectLatest { _goodsList.value = it }
        }
    }

    fun loadCharaMap() {
        viewModelScope.launch {
            App.charaDatabase.charaDao().getAllFlow().collectLatest { charaList ->
                _allCharaList.value = charaList
            }
        }
    }

    fun loadGoods() {
        viewModelScope.launch {
            _goodsList.value = App.fanDatabase.fanGoodsDao().getAll()
        }
    }

    // 아래 쓰기 함수들은 DB 의 최신 값을 다시 읽어 저장한다(FanGoodsWrites.kt).
    // 목록은 init 의 collect 가 알아서 다시 읽는다.
    fun toggleGotten(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().toggleGotten(goods.fanGoodsId) }
    }

    fun setPending(goods: FanGoodsEntity) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().togglePending(goods.fanGoodsId) }
    }

    /** 구매예정으로 바꾸면서(또는 이미 구매예정이면 그대로) 구매 정보를 저장한다. */
    fun savePendingInfo(goods: FanGoodsEntity, info: PurchaseInfo) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().savePending(goods.fanGoodsId, info) }
    }

    /** 다중 선택한 굿즈를 한 번에 같은 상태로 바꾼다. [edit] 는 구매예정일 때 입력한 구매 정보. */
    fun applyStatus(targets: List<FanGoodsEntity>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().applyStatus(targets.map { it.fanGoodsId }, status, edit) }
    }

    fun setQuantity(goods: FanGoodsEntity, quantity: Int) {
        viewModelScope.launch { App.fanDatabase.fanGoodsDao().setQuantity(goods.fanGoodsId, quantity) }
    }

    fun delete(goods: FanGoodsEntity) {
        viewModelScope.launch {
            App.fanDatabase.fanGoodsDao().delete(goods)
            loadGoods()
        }
    }


}