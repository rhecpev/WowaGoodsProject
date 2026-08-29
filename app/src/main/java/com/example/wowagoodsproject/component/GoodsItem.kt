package com.example.wowagoodsproject.component

import java.net.URI

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

/** 한글 등 비 ASCII 문자가 섞인 이미지 URL을 Coil이 읽을 수 있게 인코딩한다. */
fun encodeGoodsImagePath(imgPath: String): String {
    if (!imgPath.startsWith("http")) return imgPath
    return try {
        URI(null, imgPath.removePrefix("https://"), null).toASCIIString()
            .let { "https://" + it.removePrefix("https:/") }
    } catch (e: Exception) {
        imgPath
    }
}
