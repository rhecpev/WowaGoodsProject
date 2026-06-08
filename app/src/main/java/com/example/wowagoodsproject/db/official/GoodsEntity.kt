package com.example.wowagoodsproject.db.official

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.wowagoodsproject.component.GoodsItem

@Entity(tableName = "tb_goods")
data class GoodsEntity(
    @PrimaryKey(autoGenerate = true)
    val goodsId: Int = 0,
    val goodsReleaseDate: String = "",
    val goodsSeries: String = "",
    val goodsPrice: Int = 0,
    val goodsChara: String = "",
    val goodsCategory: String = "",
    val goodsIsGotten: Boolean = false,
    val goodsUrl: String = "",
    val goodsMemo: String = ""
) : GoodsItem {
    override val imgPath get() = goodsUrl
    override val series get() = goodsSeries
    override val chara get() = goodsChara
    override val category get() = goodsCategory
    override val price get() = goodsPrice
    override val isGotten get() = goodsIsGotten
}