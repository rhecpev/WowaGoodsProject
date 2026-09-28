package com.example.wowagoodsproject.db.fan

import androidx.room.withTransaction
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.purchaseInfo

/*
 * 2차창작 굿즈를 바꾸는 작업은 모두 여기를 거친다.
 * 공식 굿즈(GoodsWrites.kt)와 같은 이유로, 화면이 들고 있던 값이 아니라
 * id 로 DB 의 최신 값을 다시 읽어 바꿀 칸만 고친 뒤 저장한다(트랜잭션 안에서).
 */

/** 최신 값을 읽어 [change] 만 반영해 저장한다. 굿즈가 없어졌으면 null. */
suspend fun FanGoodsDao.modify(id: Int, change: (FanGoodsEntity) -> FanGoodsEntity): FanGoodsEntity? =
    App.fanDatabase.withTransaction {
        val fresh = getById(id) ?: return@withTransaction null
        val updated = change(fresh)
        if (updated != fresh) update(updated)
        updated
    }

suspend fun FanGoodsDao.setStatus(id: Int, status: GoodsStatus, info: PurchaseInfo? = null) {
    modify(id) { fresh ->
        val changed = fresh.withStatus(status.name)
        if (info != null) changed.withPurchaseInfo(info) else changed
    }
}

/** 보유 ↔ 미보유. 보유가 아니면(미보유·구매예정) 보유로 바꾼다. 판단도 최신 상태로 한다. */
suspend fun FanGoodsDao.toggleGotten(id: Int) {
    modify(id) {
        it.withStatus(if (it.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN.name else GoodsStatus.GOTTEN.name)
    }
}

/** 구매예정 ↔ 미보유. 구매예정이 아니면 구매예정으로 바꾼다. */
suspend fun FanGoodsDao.togglePending(id: Int) {
    modify(id) {
        it.withStatus(if (it.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN.name else GoodsStatus.PENDING.name)
    }
}

/** 구매예정으로 바꾸면서(이미 구매예정이면 정보만) 구매 정보를 저장한다. */
suspend fun FanGoodsDao.savePending(id: Int, info: PurchaseInfo) = setStatus(id, GoodsStatus.PENDING, info)

suspend fun FanGoodsDao.setQuantity(id: Int, quantity: Int) {
    modify(id) { it.copy(fanGoodsQuantity = quantity) }
}

/** 구매 정보 일부만 고친다. [edit] 에서 null 인 칸은 그대로 둔다. */
suspend fun FanGoodsDao.applyPurchaseInfo(id: Int, edit: PurchaseInfoEdit) {
    modify(id) { it.withPurchaseInfo(edit.applyTo(it.purchaseInfo)) }
}

/** 배송 시작일을 적는다(빈 문자열이면 취소). [onlyNotStarted] 면 이미 배송 중인 굿즈는 그대로 둔다. */
suspend fun FanGoodsDao.setShipping(id: Int, date: String, onlyNotStarted: Boolean = false) {
    modify(id) { if (onlyNotStarted && it.fanGoodsShippingDate.isNotEmpty()) it else it.copy(fanGoodsShippingDate = date) }
}

/**
 * 여러 2차창작 굿즈의 상태를 한 번에 바꾼다(다중 선택 일괄 변경).
 * [edit] 는 구매예정으로 바꿀 때 입력한 구매 정보. 손대지 않은(null) 항목은 굿즈마다 기존 값을 둔다.
 */
suspend fun FanGoodsDao.applyStatus(ids: List<Int>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
    App.fanDatabase.withTransaction {
        ids.forEach { id ->
            modify(id) { fresh ->
                val changed = fresh.withStatus(status.name)
                if (edit != null) changed.withPurchaseInfo(edit.applyTo(fresh.purchaseInfo)) else changed
            }
        }
    }
}
