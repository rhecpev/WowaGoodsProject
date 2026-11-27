package com.example.wowagoodsproject.component

import androidx.compose.ui.unit.dp
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

    /** 구매예정 굿즈에 적어 두는 구매일. "yyyy-MM-dd", 안 정했으면 빈 문자열. */
    val purchaseDate: String

    /** 구매예정 굿즈를 살 곳. 안 정했으면 빈 문자열. */
    val purchaseStore: String

    /** 구매예정 굿즈를 받을 예정일. "yyyy-MM-dd", 안 정했으면 빈 문자열. */
    val receiveDate: String

    /** 보유/구매예정 수량. 기본 1개. */
    val quantity: Int

    /** 구매예정 굿즈의 배송 시작일. "yyyy-MM-dd", 배송 전이면 빈 문자열. */
    val shippingDate: String
}

/** 구매예정 굿즈에 적어 두는 구매 정보 묶음. 빈 문자열은 '미정'이다. */
data class PurchaseInfo(
    val purchaseStore: String = "",
    val purchaseDate: String = "",
    val receiveDate: String = ""
)

val GoodsItem.purchaseInfo
    get() = PurchaseInfo(purchaseStore = purchaseStore, purchaseDate = purchaseDate, receiveDate = receiveDate)

const val CATEGORY_SET = "세트"
const val CATEGORY_COMPONENT = "구성품"

enum class GoodsStatus { GOTTEN, NOT_GOTTEN, PENDING }

/** DB·백업에 적힌 상태 문자열을 읽는다. 모르는 값이면 앱이 꺼지지 않게 미보유로 본다. */
fun goodsStatusOf(name: String?): GoodsStatus =
    GoodsStatus.entries.firstOrNull { it.name == name } ?: GoodsStatus.NOT_GOTTEN
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


/**
 * 굿즈 카드 사진(그리드 칸 · 리스트 줄 공통). 두 보기가 같은 크기([LocalGoodsCardImagePx])로 사진을 읽어
 * 메모리 캐시를 같이 쓴다. 크기를 칸에 맞춰 따로 읽으면 보기를 바꾸는 순간 보이는 사진을 모두 다시 해독해 버벅인다.
 * 크기는 목록이 그리드 한 줄 개수로 정해 준다([ProvideGoodsCardImageSize]). 크기 단계가 바뀌어 새로 읽는 동안에는
 * 전에 읽어 둔 사진(메모리)을 그대로 보여 준다(빈 칸으로 깜빡이지 않게).
 */
@androidx.compose.runtime.Composable
fun rememberGoodsCardPainter(encodedPath: String): androidx.compose.ui.graphics.painter.Painter {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sizePx = LocalGoodsCardImagePx.current
    // 이 사진을 마지막으로 읽은 메모리 열쇠(다시 그릴 필요는 없어 상태가 아닌 그릇에 둔다)
    val lastKey = androidx.compose.runtime.remember(encodedPath) { arrayOfNulls<coil.memory.MemoryCache.Key>(1) }
    val request = androidx.compose.runtime.remember(encodedPath, sizePx) {
        if (encodedPath.isEmpty()) null
        else coil.request.ImageRequest.Builder(context)
            .data(encodedPath)
            .size(sizePx)
            .placeholderMemoryCacheKey(lastKey[0])
            .build()
    }
    return coil.compose.rememberAsyncImagePainter(
        model = request,
        onSuccess = { lastKey[0] = it.result.memoryCacheKey }
    )
}

/** 굿즈 카드 사진을 읽는 최소 크기(긴 변 px). 휴대폰 그리드 두 칸 너비 정도 */
const val GOODS_CARD_IMAGE_PX = 480

/** 카드 사진 크기 단계(px). 한 줄 개수를 조금 바꿔도 같은 단계면 다시 읽지 않는다. */
private val GOODS_CARD_IMAGE_STEPS = intArrayOf(GOODS_CARD_IMAGE_PX, 720, 1080, 1440)

/** 지금 목록의 카드 사진 크기(px). 목록 밖(확인 화면 등)은 [GOODS_CARD_IMAGE_PX] */
val LocalGoodsCardImagePx = androidx.compose.runtime.staticCompositionLocalOf { GOODS_CARD_IMAGE_PX }

/** 화면 너비 ÷ 그리드 한 줄 개수를 단계로 올린 크기. 리스트 보기도 같은 값을 써서 캐시를 같이 쓴다. */
internal fun goodsCardImagePx(screenWidthPx: Int, gridColumns: Int): Int {
    val need = screenWidthPx / gridColumns.coerceAtLeast(1)
    return GOODS_CARD_IMAGE_STEPS.firstOrNull { it >= need } ?: GOODS_CARD_IMAGE_STEPS.last()
}

/** 굿즈 목록(그리드·리스트)을 감싸 카드 사진 크기를 그리드 한 줄 개수에 맞춘다 */
@androidx.compose.runtime.Composable
fun ProvideGoodsCardImageSize(gridColumns: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGoodsCardImagePx provides goodsCardImagePx(px, gridColumns),
        content = content
    )
}
