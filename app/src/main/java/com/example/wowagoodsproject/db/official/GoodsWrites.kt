package com.example.wowagoodsproject.db.official

import androidx.room.withTransaction
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.purchaseInfo

/*
 * 공식 굿즈를 바꾸는 작업은 모두 여기를 거친다.
 *
 * 화면이 들고 있는 굿즈 값은 다른 탭·데이터 입력·백그라운드 업데이트 뒤에 옛 값일 수 있다.
 * 그 값을 통째로 저장하면 그사이 바뀐 수량·구매 정보가 옛 값으로 되돌아가므로,
 * 항상 id 로 DB 의 최신 값을 다시 읽어 바꿀 칸만 고친 뒤 저장한다(트랜잭션 안에서).
 */

/** 최신 값을 읽어 [change] 만 반영해 저장한다. 굿즈가 없어졌으면 null. */
suspend fun GoodsDao.modify(id: Int, change: (GoodsEntity) -> GoodsEntity): GoodsEntity? =
    App.database.withTransaction {
        val fresh = getById(id) ?: return@withTransaction null
        val updated = change(fresh)
        if (updated != fresh) update(updated)
        updated
    }

/** 세트 굿즈의 보유 상태를 구성품에 맞춘다(구성품이 모두 보유면 보유, 아니면 미보유). */
private suspend fun GoodsDao.syncSetStatus(series: String, memo: String) {
    if (memo.isEmpty()) return
    val allGoods = getBySeries(series)
    val set = allGoods.find { it.goodsCategory == CATEGORY_SET && it.goodsMemo == memo } ?: return
    val allGotten = allGoods
        .filter { it.goodsCategory != CATEGORY_SET && it.goodsMemo == memo }
        .all { it.goodsStatus == GoodsStatus.GOTTEN.name }
    modify(set.goodsId) {
        it.withStatus(if (allGotten) GoodsStatus.GOTTEN.name else GoodsStatus.NOT_GOTTEN.name)
    }
}

/** 상태를 [status] 로 바꾸고, 구성품이면 세트 상태도 맞춘다. [info] 가 있으면 구매 정보도 적는다. */
suspend fun GoodsDao.setStatus(id: Int, status: GoodsStatus, info: PurchaseInfo? = null) {
    App.database.withTransaction {
        val updated = modify(id) { fresh ->
            val changed = fresh.withStatus(status.name)
            if (info != null) changed.withPurchaseInfo(info) else changed
        } ?: return@withTransaction
        if (updated.goodsCategory != CATEGORY_SET) syncSetStatus(updated.goodsSeries, updated.goodsMemo)
    }
}

/** 보유 ↔ 미보유. 보유가 아니면(미보유·구매예정) 보유로 바꾼다. 판단도 최신 상태로 한다. */
suspend fun GoodsDao.toggleGotten(id: Int) {
    val current = getById(id) ?: return
    setStatus(id, if (current.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN)
}

/** 구매예정 ↔ 미보유. 구매예정이 아니면 구매예정으로 바꾼다. */
suspend fun GoodsDao.togglePending(id: Int) {
    val current = getById(id) ?: return
    setStatus(id, if (current.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING)
}

/** 구매예정으로 바꾸면서(이미 구매예정이면 정보만) 구매 정보를 저장한다. */
suspend fun GoodsDao.savePending(id: Int, info: PurchaseInfo) = setStatus(id, GoodsStatus.PENDING, info)

suspend fun GoodsDao.setQuantity(id: Int, quantity: Int) {
    modify(id) { it.copy(goodsQuantity = quantity) }
}

/** 구매 정보 일부만 고친다. [edit] 에서 null 인 칸은 그대로 둔다. */
suspend fun GoodsDao.applyPurchaseInfo(id: Int, edit: PurchaseInfoEdit) {
    modify(id) { it.withPurchaseInfo(edit.applyTo(it.purchaseInfo)) }
}

/** 배송 시작일을 적는다(빈 문자열이면 취소). [onlyNotStarted] 면 이미 배송 중인 굿즈는 그대로 둔다. */
suspend fun GoodsDao.setShipping(id: Int, date: String, onlyNotStarted: Boolean = false) {
    modify(id) { if (onlyNotStarted && it.goodsShippingDate.isNotEmpty()) it else it.copy(goodsShippingDate = date) }
}

/** 세트 구성품 + 세트 자신을 한 번에 같은 상태로 바꾼다. [info] 가 있으면 구성품에 구매 정보도 적는다. */
suspend fun GoodsDao.bulkSetStatus(setId: Int, status: GoodsStatus, info: PurchaseInfo? = null) {
    App.database.withTransaction {
        val set = getById(setId) ?: return@withTransaction
        getBySeries(set.goodsSeries)
            .filter { it.goodsCategory != CATEGORY_SET && it.goodsMemo == set.goodsMemo }
            .forEach { comp ->
                modify(comp.goodsId) {
                    val changed = it.withStatus(status.name)
                    if (info != null) changed.withPurchaseInfo(info) else changed
                }
            }
        modify(set.goodsId) { it.withStatus(status.name) }
    }
}

/**
 * 여러 굿즈의 상태를 한 번에 바꾼다(다중 선택 일괄 변경).
 * 세트 굿즈를 고르면 구성품도 같은 상태로 바꾸고, 구성품만 바뀐 세트는 구성품에 맞춰 보유 상태를 다시 계산한다.
 * [edit] 는 구매예정으로 바꿀 때 입력한 구매 정보. 손대지 않은(null) 항목은 굿즈마다 기존 값을 둔다.
 */
suspend fun GoodsDao.applyStatus(ids: List<Int>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
    App.database.withTransaction {
        val change: (GoodsEntity) -> GoodsEntity = { fresh ->
            val changed = fresh.withStatus(status.name)
            if (edit != null) changed.withPurchaseInfo(edit.applyTo(fresh.purchaseInfo)) else changed
        }
        val targets = ids.mapNotNull { getById(it) }
        val done = mutableSetOf<Int>()

        val explicitSets = targets.filter { it.goodsCategory == CATEGORY_SET }
        explicitSets.forEach { set ->
            getBySeries(set.goodsSeries)
                .filter { it.goodsCategory != CATEGORY_SET && it.goodsMemo == set.goodsMemo }
                .forEach { comp -> if (done.add(comp.goodsId)) modify(comp.goodsId, change) }
            if (done.add(set.goodsId)) modify(set.goodsId, change)
        }

        val touchedSetKeys = mutableSetOf<Pair<String, String>>()
        targets.filter { it.goodsCategory != CATEGORY_SET }.forEach { goods ->
            if (done.add(goods.goodsId)) modify(goods.goodsId, change)
            if (goods.goodsMemo.isNotEmpty()) touchedSetKeys += goods.goodsSeries to goods.goodsMemo
        }

        // 직접 고른 세트는 위에서 이미 맞췄으니 건너뛴다.
        val explicitKeys = explicitSets.map { it.goodsSeries to it.goodsMemo }.toSet()
        (touchedSetKeys - explicitKeys).forEach { (series, memo) -> syncSetStatus(series, memo) }
    }
}
