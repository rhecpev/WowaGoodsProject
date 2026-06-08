package com.example.wowagoodsproject.component

interface GoodsItem {
    val imgPath: String
    val series: String
    val chara: String
    val category: String
    val price: Int
    val isGotten: Boolean
}