package com.example.wowagoodsproject.component

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 캐릭터 한 명의 굿즈 수. */
data class CharaShare(val name: String, val count: Int)

/**
 * 보유 굿즈의 캐릭터 비율 게이지에 쓸 값.
 * @property top 많은 순서 상위 캐릭터(색으로 그린다)
 * @property othersCount 나머지 캐릭터를 모두 합친 수('기타')
 * @property total 전체 수. 여러 캐릭터가 함께 있는 굿즈는 캐릭터마다 한 번씩 세므로 굿즈 수보다 클 수 있다.
 * @property othersNames '기타'에 묶인 캐릭터 이름(많은 순)
 */
data class CharaShares(
    val top: List<CharaShare>,
    val othersCount: Int,
    val total: Int,
    val othersNames: List<String> = emptyList()
) {
    /** '기타' 칸 이름: "감심 외 12명"(가장 많은 캐릭터 외 나머지 수). 한 명뿐이면 그 이름. */
    val othersLabel: String
        get() = when (othersNames.size) {
            0 -> "기타"
            1 -> othersNames.first()
            else -> "${othersNames.first()} 외 ${othersNames.size - 1}명"
        }
}

/**
 * 굿즈들의 캐릭터 비율. 캐릭터 칸("A,B")의 캐릭터마다 1씩 센다.
 * 많은 순으로 [topCount] 명만 따로 두고 나머지는 '기타'로 합친다. 같은 수면 선호 우선순위, 그다음 이름순.
 * [onlyFavorites] 면 선호 캐릭터만 따로 두고(많은 순) 선호가 아닌 캐릭터는 모두 '기타'로 합친다.
 * 이때 선호 캐릭터가 하나도 없으면 [topCount] 기준으로 돌아간다(게이지가 전부 '기타'가 되지 않게).
 */
fun charaShares(
    goods: List<GoodsItem>,
    favoritesInOrder: List<String>,
    topCount: Int = 6,
    onlyFavorites: Boolean = false
): CharaShares {
    val counts = mutableMapOf<String, Int>()
    goods.forEach { g ->
        g.chara.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .forEach { counts[it] = (counts[it] ?: 0) + 1 }
    }
    val favRank = favoritesInOrder.withIndex().associate { it.value to it.index }
    val sorted = counts.entries.sortedWith(
        compareByDescending<Map.Entry<String, Int>> { it.value }
            .thenBy { favRank[it.key] ?: Int.MAX_VALUE }
            .thenBy { it.key }
    )
    val total = counts.values.sum()
    if (onlyFavorites && favoritesInOrder.isNotEmpty()) {
        val favSet = favoritesInOrder.toSet()
        val (fav, rest) = sorted.partition { it.key in favSet }
        return CharaShares(fav.map { CharaShare(it.key, it.value) }, rest.sumOf { it.value }, total, rest.map { it.key })
    }
    val top = sorted.take(topCount).map { CharaShare(it.key, it.value) }
    val rest = sorted.drop(topCount)
    return CharaShares(top, rest.sumOf { it.value }, total, rest.map { it.key })
}

// ── 색 배정 ─────────────────────────────────────────────────────────

/** HSL. h 0~360, s·l 0~1 */
data class Hsl(val h: Float, val s: Float, val l: Float)

fun argbToHsl(argb: Int): Hsl {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Hsl(h, s.coerceIn(0f, 1f), l)
}

fun hslToArgb(c: Hsl): Int {
    val cc = (1f - abs(2f * c.l - 1f)) * c.s
    val x = cc * (1f - abs((c.h / 60f) % 2f - 1f))
    val m = c.l - cc / 2f
    val (r, g, b) = when {
        c.h < 60f -> Triple(cc, x, 0f)
        c.h < 120f -> Triple(x, cc, 0f)
        c.h < 180f -> Triple(0f, cc, x)
        c.h < 240f -> Triple(0f, x, cc)
        c.h < 300f -> Triple(x, 0f, cc)
        else -> Triple(cc, 0f, x)
    }
    fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** 회색에 가까운 색(채도 낮음)은 색조가 의미가 없어 밝기로만 비교한다. */
internal const val GREY_SATURATION = 0.18f

/** 두 색이 게이지에서 헷갈릴 만큼 가까운지 */
fun tooClose(a: Hsl, b: Hsl, minHueGap: Float = 30f): Boolean {
    val aGrey = a.s < GREY_SATURATION
    val bGrey = b.s < GREY_SATURATION
    if (aGrey && bGrey) return abs(a.l - b.l) < 0.18f
    if (aGrey != bGrey) return false
    val dh = abs(a.h - b.h).let { min(it, 360f - it) }
    return dh < minHueGap && abs(a.l - b.l) < 0.25f
}

/**
 * 배경 위에서 잘 보이게 밝기·채도를 맞춘다. 어두운 테마는 밝게, 밝은 테마는 어둡게 둔다.
 * 너무 흐린 색은 채도를 조금 올려 회색 덩어리로 보이지 않게 한다.
 */
fun fitToTheme(c: Hsl, darkTheme: Boolean): Hsl {
    val l = if (darkTheme) c.l.coerceIn(0.50f, 0.75f) else c.l.coerceIn(0.30f, 0.58f)
    val s = if (c.s < GREY_SATURATION) c.s else c.s.coerceAtLeast(0.45f)
    return Hsl(c.h, s, l)
}

/**
 * 캐릭터마다 서로 헷갈리지 않는 색을 정한다. [order] 앞의 캐릭터부터 정하므로 선호 캐릭터를 앞에 두면 먼저 좋은 색을 받는다.
 * 1. 그 캐릭터 사진의 후보 색([candidates], 좋은 순)을 차례로 보고, 이미 정한 색과 가깝지 않은 첫 색을 쓴다.
 * 2. 후보가 모두 가까우면 첫 후보의 색조를 조금씩 돌려 벌린다.
 * 3. 후보가 없으면(사진을 못 읽음) 이름으로 정한 색조에서 같은 방식으로 찾는다.
 * @return 이름 → ARGB
 */
fun assignDistinctColors(
    order: List<String>,
    candidates: Map<String, List<Int>>,
    darkTheme: Boolean
): Map<String, Int> {
    val used = mutableListOf<Hsl>()
    val result = linkedMapOf<String, Int>()
    for (name in order) {
        val cands = candidates[name].orEmpty().map { fitToTheme(argbToHsl(it), darkTheme) }
        val base = cands.firstOrNull()
            ?: fitToTheme(Hsl(((name.hashCode() and 0x7FFFFFFF) % 360).toFloat(), 0.6f, 0.5f), darkTheme)
        var pick = cands.firstOrNull { c -> used.none { tooClose(it, c) } }
        if (pick == null) {
            // 회색 계열이면 색조를 돌려도 소용없으니 채도를 주고 돌린다.
            val start = if (base.s < GREY_SATURATION) base.copy(s = 0.5f) else base
            pick = (1..11).asSequence()
                .map { step -> start.copy(h = (start.h + step * 32f) % 360f) }
                .firstOrNull { c -> used.none { tooClose(it, c) } }
                ?: start
        }
        used += pick
        result[name] = hslToArgb(pick)
    }
    return result
}

/**
 * 캐릭터별 공식 굿즈 수집 현황(보유 수, 전체 수). 캐릭터별 탭의 퍼센트와 같은 기준이다.
 * 세트 굿즈는 빼고, 캐릭터 칸("A,B")의 캐릭터마다 센다. 보유는 상태가 '보유'인 것만(구매예정은 아님).
 */
fun charaCollection(officialGoods: List<GoodsItem>): Map<String, Pair<Int, Int>> {
    val map = mutableMapOf<String, Pair<Int, Int>>()
    officialGoods.filter { it.category != CATEGORY_SET }.forEach { g ->
        g.chara.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            val (gotten, total) = map[name] ?: (0 to 0)
            map[name] = (if (g.status == GoodsStatus.GOTTEN) gotten + 1 else gotten) to total + 1
        }
    }
    return map
}

/**
 * 끌어서 바꾼 순서를 전체 선호 순서에 합친다.
 * [fullOrder] 는 지금 선호 순서 전체, [reorderedVisible] 은 화면에 보이던 일부(검색 중일 수 있음)를 새로 늘어놓은 것.
 * 보이던 캐릭터들이 차지하던 자리에 새 순서대로 다시 채우고, 안 보이던 캐릭터는 제자리에 둔다.
 */
fun mergeFavoriteOrder(fullOrder: List<String>, reorderedVisible: List<String>): List<String> {
    val visible = reorderedVisible.toSet()
    val queue = ArrayDeque(reorderedVisible.filter { it in fullOrder })
    return fullOrder.map { name -> if (name in visible && queue.isNotEmpty()) queue.removeFirst() else name }
}
