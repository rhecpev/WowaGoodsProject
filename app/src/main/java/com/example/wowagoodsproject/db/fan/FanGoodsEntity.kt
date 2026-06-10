package com.example.wowagoodsproject.db.fan

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.wowagoodsproject.component.GoodsItem

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
    val fanGoodsMemo: String = ""
) : GoodsItem {
    override val imgPath get() = fanGoodsImgPath
    override val series get() = fanGoodsSeries
    override val chara get() = fanGoodsChara
    override val category get() = fanGoodsCategory
    override val price get() = fanGoodsPrice
    override val isGotten get() = fanGoodsIsGotten
}