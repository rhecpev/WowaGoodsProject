package com.example.wowagoodsproject.screen.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ProcessFilter
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.contentKey
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.changed.matchCurrentGoods
import com.example.wowagoodsproject.db.changed.restoreChangedGoods
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.bulkSetStatus
import com.example.wowagoodsproject.db.official.savePending
import com.example.wowagoodsproject.db.official.setQuantity
import com.example.wowagoodsproject.db.official.setShipping
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.db.official.togglePending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 변경 기록 한 줄과 그 기록이 가리키는 지금의 굿즈(원본에서 삭제됐으면 null) */
data class ChangedGoodsItem(val record: ChangedGoodsEntity, val current: GoodsEntity?)

/** 데이터 업데이트로 바뀐 보유·구매예정 굿즈의 기록 */
class ChangedGoodsViewModel : ViewModel() {

    private val changedDao get() = App.changedGoodsDatabase.changedGoodsDao()
    private val goodsDao get() = App.database.goodsDao()

    val allGoods: StateFlow<List<GoodsEntity>> = goodsDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 굿즈의 지금 값(상태·수량 등)으로 그린다. 다이얼로그에서 고치면 목록도 바로 다시 그려진다. */
    val items: StateFlow<List<ChangedGoodsItem>> = combine(changedDao.getAllFlow(), allGoods) { records, goods ->
        val byUid = goods.filter { it.goodsUid > 0 }.associateBy { it.goodsUid }
        val byContent = goods.associateBy { it.contentKey() }
        records.map { ChangedGoodsItem(it, it.matchCurrentGoods(byUid, byContent)) }
    }
        // 굿즈 하나만 바뀌어도 7천여 개를 다시 짝지으므로 메인 스레드 밖에서 한다.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 다이얼로그로 연 굿즈의 id. 굿즈 값은 [allGoods] 에서 최신으로 찾는다. */
    private val _openedGoodsId = MutableStateFlow<Int?>(null)
    val openedGoodsId: StateFlow<Int?> = _openedGoodsId

    fun openGoods(goods: GoodsEntity) {
        _openedGoodsId.value = goods.goodsId
    }

    fun dismissGoods() {
        _openedGoodsId.value = null
    }

    /** 다이얼로그에서 굿즈를 고친 뒤, 그 굿즈를 가리키는 기록에 확인 표시를 한다. */
    private fun editGoods(goodsIds: Set<Int>, write: suspend () -> Unit) {
        viewModelScope.launch {
            write()
            val ids = items.value.filter { it.current?.goodsId in goodsIds }.map { it.record.changedId }
            if (ids.isNotEmpty()) changedDao.markChecked(ids)
        }
    }

    fun toggleGotten(goods: GoodsEntity) =
        editGoods(setOf(goods.goodsId)) { goodsDao.toggleGotten(goods.goodsId) }

    fun togglePending(goods: GoodsEntity) =
        editGoods(setOf(goods.goodsId)) { goodsDao.togglePending(goods.goodsId) }

    fun savePending(goods: GoodsEntity, info: PurchaseInfo) =
        editGoods(setOf(goods.goodsId)) { goodsDao.savePending(goods.goodsId, info) }

    fun setQuantity(goods: GoodsEntity, quantity: Int) =
        editGoods(setOf(goods.goodsId)) { goodsDao.setQuantity(goods.goodsId, quantity) }

    /** 배송 시작 ↔ 취소 */
    fun toggleShipping(goods: GoodsEntity) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val date = if (goods.goodsShippingDate.isEmpty()) today else ""
        editGoods(setOf(goods.goodsId)) { goodsDao.setShipping(goods.goodsId, date) }
    }

    /** 세트와 구성품을 한 번에 바꾼다. 바뀌는 구성품을 가리키는 기록도 모두 확인 표시한다. */
    fun bulkSetStatus(setGoods: GoodsEntity, components: List<GoodsEntity>, status: GoodsStatus, info: PurchaseInfo? = null) =
        editGoods(components.map { it.goodsId }.toSet() + setGoods.goodsId) {
            goodsDao.bulkSetStatus(setGoods.goodsId, status, info)
        }

    fun restore(record: ChangedGoodsEntity) {
        viewModelScope.launch {
            _message.value = restoreChangedGoods(record.changedId)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun delete(record: ChangedGoodsEntity) {
        viewModelScope.launch { changedDao.delete(record) }
    }

    fun deleteAll() {
        viewModelScope.launch { changedDao.deleteAll() }
    }

    /** 처리(확인 표시)된 기록만 지운다. */
    fun deleteChecked() {
        viewModelScope.launch { changedDao.deleteChecked() }
    }

    /** 처리 여부 필터. 처음엔 아직 처리하지 않은 기록만 보여 준다. */
    private val _processFilter = MutableStateFlow(ProcessFilter.TODO)
    val processFilter: StateFlow<ProcessFilter> = _processFilter

    fun setProcessFilter(filter: ProcessFilter) {
        _processFilter.value = filter
    }
}
