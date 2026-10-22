package com.example.wowagoodsproject.db.changed

import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.modify
import com.example.wowagoodsproject.db.official.withStatus

/** 바뀐 뒤 굿즈의 내용 키. [com.example.wowagoodsproject.contentKey] 와 같은 형식이다. */
private val ChangedGoodsEntity.newContentKey get() = "$newSeries|$newChara|$newCategory|$newPrice|$newMemo"

/**
 * [findCurrentGoods] 와 같은 규칙으로, 미리 읽어 둔 굿즈 목록에서 찾는다(목록 화면용).
 * [byContent] 의 키는 시리즈|캐릭터|카테고리|가격|메모 이다.
 */
fun ChangedGoodsEntity.matchCurrentGoods(
    byUid: Map<Int, GoodsEntity>,
    byContent: Map<String, GoodsEntity>
): GoodsEntity? {
    if (newUid > 0) byUid[newUid]?.let { return it }
    if (isDeleted) return null
    return byContent[newContentKey]
}

/** 기록이 가리키는 지금의 굿즈. 번호로 먼저 찾아서, 그 뒤에 가격·메모가 또 바뀌었어도 찾는다. */
suspend fun ChangedGoodsEntity.findCurrentGoods(): GoodsEntity? {
    val dao = App.database.goodsDao()
    if (newUid > 0) dao.getByUid(newUid)?.let { return it }
    if (isDeleted) return null
    return dao.getByUniqueKey(
        series = newSeries,
        chara = newChara,
        category = newCategory,
        memo = newMemo,
        price = newPrice
    )
}

/** 보관해 둔 원래 상태를 대체된 굿즈에 다시 적용하고, 사용자에게 보여 줄 결과 문구를 돌려준다. */
suspend fun restoreChangedGoods(changedId: Int): String {
    val record = App.changedGoodsDatabase.changedGoodsDao().getById(changedId)
    if (record == null || !record.canRestore) return "되돌릴 기록을 찾을 수 없습니다"
    val target = record.findCurrentGoods() ?: return "대상 굿즈를 찾을 수 없습니다"
    // 상태와 함께 사용자가 적어 둔 수량/구매 정보/배송 시작일도 되돌린다.
    App.database.goodsDao().modify(target.goodsId) {
        it.withStatus(record.oldStatus).copy(
            goodsQuantity = record.oldQuantity,
            goodsPurchaseStore = record.oldPurchaseStore,
            goodsPurchaseDate = record.oldPurchaseDate,
            goodsReceiveDate = record.oldReceiveDate,
            goodsShippingDate = if (record.oldStatus == GoodsStatus.PENDING.name) record.oldShippingDate else ""
        )
    }
    App.changedGoodsDatabase.changedGoodsDao().markRestored(record.changedId)
    return "상태를 되돌렸습니다"
}
