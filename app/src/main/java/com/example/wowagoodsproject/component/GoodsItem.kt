package com.example.wowagoodsproject.component

interface GoodsItem {
    val imgPath: String
    val series: String
    val chara: String
    val category: String
    val price: String
    val isGotten: Boolean
    val memo: String  // 추가
}

const val CATEGORY_SET = "세트"
const val CATEGORY_COMPONENT = "구성품"

enum class GottenStatus { GOTTEN, NOT_GOTTEN, PARTIAL }
