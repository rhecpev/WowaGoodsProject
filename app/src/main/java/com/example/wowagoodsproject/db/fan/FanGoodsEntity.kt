package com.example.wowagoodsproject.db.fan

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo

@Entity(tableName = "tb_fan_goods")
data class FanGoodsEntity(
    @PrimaryKey(autoGenerate = true)
    val fanGoodsId: Int = 0,
    val fanGoodsReleaseDate: String = "",
    val fanGoodsSeries: String = "",
    val fanGoodsPrice: String = "",
    val fanGoodsChara: String = "",
    val fanGoodsCategory: String = "",
    val fanGoodsImgPath: String = "",
    val fanGoodsIsGotten: Boolean = false,
    val fanGoodsStatus: String = GoodsStatus.NOT_GOTTEN.name,
    val fanGoodsMemo: String = "",
    val fanGoodsPurchaseDate: String = "",
    val fanGoodsPurchaseStore: String = "",
    val fanGoodsReceiveDate: String = "",
    val fanGoodsQuantity: Int = 1,
    val fanGoodsShippingDate: String = ""
) : GoodsItem {
    override val imgPath get() = fanGoodsImgPath
    override val series get() = fanGoodsSeries
    override val chara get() = fanGoodsChara
    override val category get() = fanGoodsCategory
    override val price get() = fanGoodsPrice
    override val isGotten get() = fanGoodsStatus == GoodsStatus.GOTTEN.name
    override val status get() = GoodsStatus.valueOf(fanGoodsStatus)
    override val memo get() = fanGoodsMemo
    override val purchaseDate get() = fanGoodsPurchaseDate
    override val purchaseStore get() = fanGoodsPurchaseStore
    override val receiveDate get() = fanGoodsReceiveDate
    override val quantity get() = fanGoodsQuantity
    override val shippingDate get() = fanGoodsShippingDate

}

fun FanGoodsEntity.withPurchaseInfo(info: PurchaseInfo) = copy(
    fanGoodsPurchaseStore = info.purchaseStore,
    fanGoodsReceiveDate = info.receiveDate,
    fanGoodsPurchaseDate = info.purchaseDate
)

/**
 * 상태를 바꾼다. 미보유가 되면 수량도 1로 되돌리고, 구매예정에서 벗어나면 배송 시작일을 지운다.
 * 예전 칸인 fanGoodsIsGotten 도 함께 맞춰, 이 칸을 보는 옛 버전 백업과 어긋나지 않게 한다.
 */
fun FanGoodsEntity.withStatus(status: String) = copy(
    fanGoodsStatus = status,
    fanGoodsIsGotten = status == GoodsStatus.GOTTEN.name,
    fanGoodsQuantity = if (status == GoodsStatus.NOT_GOTTEN.name) 1 else fanGoodsQuantity,
    fanGoodsShippingDate = if (status == GoodsStatus.PENDING.name) fanGoodsShippingDate else ""
)
