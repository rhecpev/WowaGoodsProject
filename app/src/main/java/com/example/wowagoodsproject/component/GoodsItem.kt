package com.example.wowagoodsproject.component

interface GoodsItem {
    val imgPath: String
    val series: String
    val chara: String
    val category: String
    val price: String
    val isGotten: Boolean
    val status: GoodsStatus
    val memo: String
}

const val CATEGORY_SET = "세트"
const val CATEGORY_COMPONENT = "구성품"

enum class GoodsStatus { GOTTEN, NOT_GOTTEN, PENDING }
enum class GottenStatus { GOTTEN, NOT_GOTTEN, PARTIAL, PENDING }