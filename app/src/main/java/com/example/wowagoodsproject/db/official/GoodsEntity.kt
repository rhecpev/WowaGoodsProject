package com.example.wowagoodsproject.db.official

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo

@Entity(tableName = "tb_goods")
data class GoodsEntity(
    @PrimaryKey(autoGenerate = true)
    val goodsId: Int = 0,
    val goodsReleaseDate: String = "",
    val goodsSeries: String = "",
    val goodsPrice: String = "",
    val goodsChara: String = "",
    val goodsCategory: String = "",
    val goodsIsGotten: Boolean = false,
    val goodsStatus: String = GoodsStatus.NOT_GOTTEN.name,
    val goodsUrl: String = "",
    val goodsMemo: String = "",
    val goodsPurchaseDate: String = "",
    val goodsPurchaseStore: String = "",
    val goodsReceiveDate: String = "",
    val goodsQuantity: Int = 1,
    val goodsShippingDate: String = ""
) : GoodsItem {
    override val imgPath get() = goodsUrl
    override val series get() = goodsSeries
    override val chara get() = goodsChara
    override val category get() = goodsCategory
    override val price get() = goodsPrice
    override val isGotten get() = goodsStatus == GoodsStatus.GOTTEN.name
    override val status get() = GoodsStatus.valueOf(goodsStatus)
    override val memo get() = goodsMemo
    override val purchaseDate get() = goodsPurchaseDate
    override val purchaseStore get() = goodsPurchaseStore
    override val receiveDate get() = goodsReceiveDate
    override val quantity get() = goodsQuantity
    override val shippingDate get() = goodsShippingDate
}

fun GoodsEntity.withPurchaseInfo(info: PurchaseInfo) = copy(
    goodsPurchaseStore = info.purchaseStore,
    goodsReceiveDate = info.receiveDate,
    goodsPurchaseDate = info.purchaseDate
)

/**
 * 상태를 바꾼다. 미보유가 되면 수량도 1로 되돌리고, 구매예정에서 벗어나면 배송 시작일을 지운다.
 * 예전 칸인 goodsIsGotten 도 함께 맞춰, 이 칸을 보는 옛 버전 백업과 어긋나지 않게 한다.
 */
fun GoodsEntity.withStatus(status: String) = copy(
    goodsStatus = status,
    goodsIsGotten = status == GoodsStatus.GOTTEN.name,
    goodsQuantity = if (status == GoodsStatus.NOT_GOTTEN.name) 1 else goodsQuantity,
    goodsShippingDate = if (status == GoodsStatus.PENDING.name) goodsShippingDate else ""
)
