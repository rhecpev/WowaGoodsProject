package com.example.wowagoodsproject

import com.example.wowagoodsproject.db.official.GoodsEntity

/*
 * 원본 굿즈 목록(goods.json)과 폰의 굿즈를 짝짓는다. DB 를 건드리지 않는 계산만 있어서 단위 테스트로 확인한다.
 *
 * 1. 굿즈 번호(goodsUid)가 같으면 같은 굿즈다. 가격·메모·이미지가 바뀌어도 사용자 기록은 그대로 둔다.
 * 2. 번호로 짝을 못 찾은 것끼리는 내용(시리즈·캐릭터·카테고리·가격·메모)이 같으면 같은 굿즈로 본다.
 *    번호 칸이 생기기 전에 받아 둔 굿즈(goodsUid = 0)는 첫 업데이트 때 이렇게 번호를 받는다.
 * 3. 번호가 없는 굿즈는 데이터가 오래됐을 수 있어, 시리즈·캐릭터·카테고리가 같고 메모나 가격 중 하나가 같은
 *    짝이 양쪽 모두 하나뿐이면 같은 굿즈로 본다.
 * 4. 그래도 짝이 없는 원본 굿즈는 추가하고, 폰의 굿즈는 지운다.
 */

/** 같은 굿즈인지 내용으로 판단할 때 쓰는 키 */
internal fun GoodsEntity.contentKey() = "$goodsSeries|$goodsChara|$goodsCategory|$goodsPrice|$goodsMemo"

/** 내용이 바뀌어 같은 굿즈의 대체 항목을 찾을 때 쓰는 느슨한 키 */
internal fun GoodsEntity.looseKey() = "$goodsSeries|$goodsChara|$goodsCategory"

/** 원본에서 받아 오는 칸에 [remote] 값을 넣는다. 상태·수량·구매 정보 같은 사용자 칸은 그대로 둔다. */
internal fun GoodsEntity.withSourceOf(remote: GoodsEntity) = copy(
    goodsUid = remote.goodsUid,
    goodsReleaseDate = remote.goodsReleaseDate,
    goodsSeries = remote.goodsSeries,
    goodsPrice = remote.goodsPrice,
    goodsChara = remote.goodsChara,
    goodsCategory = remote.goodsCategory,
    goodsUrl = remote.goodsUrl,
    goodsMemo = remote.goodsMemo
)

internal data class GoodsSyncPlan(
    /** 짝을 찾았고 원본 칸(번호 포함)이 달라진 굿즈. (폰의 굿즈, 원본 굿즈) */
    val updates: List<Pair<GoodsEntity, GoodsEntity>>,
    val inserts: List<GoodsEntity>,
    val deletes: List<GoodsEntity>
)

/** 원본 굿즈마다 0 이 아닌 번호가 하나씩 있는지. 없으면 번호로 짝지을 수 없어 업데이트를 멈춘다. */
internal fun requireGoodsUids(remote: List<GoodsEntity>) {
    val missing = remote.count { it.goodsUid <= 0 }
    if (missing > 0) throw IllegalStateException("goods.json 에 번호가 없는 굿즈가 ${missing}개 있어 동기화를 건너뜁니다")
    val duplicated = remote.groupBy { it.goodsUid }.filterValues { it.size > 1 }.keys
    if (duplicated.isNotEmpty()) throw IllegalStateException("goods.json 에 번호가 겹치는 굿즈가 있어 동기화를 건너뜁니다: ${duplicated.take(5)}")
}

internal fun planGoodsSync(local: List<GoodsEntity>, remote: List<GoodsEntity>): GoodsSyncPlan {
    val matched = mutableMapOf<GoodsEntity, GoodsEntity>() // 원본 → 폰
    val usedLocalIds = mutableSetOf<Int>()

    val localByUid = local.filter { it.goodsUid > 0 }.associateBy { it.goodsUid }
    for (r in remote) {
        val l = localByUid[r.goodsUid] ?: continue
        matched[r] = l
        usedLocalIds += l.goodsId
    }

    val leftoverByContent = local
        .filter { it.goodsId !in usedLocalIds }
        .groupBy { it.contentKey() }
        .mapValues { it.value.toMutableList() }
    for (r in remote) {
        if (r in matched) continue
        val l = leftoverByContent[r.contentKey()]?.removeFirstOrNull() ?: continue
        matched[r] = l
        usedLocalIds += l.goodsId
    }

    // 아직 번호를 받지 못한 굿즈(업데이트 뒤 첫 동기화)는, 폰의 데이터가 오래돼 그사이 가격이나 메모가 바뀌었을 수 있다.
    // 시리즈·캐릭터·카테고리가 같고 메모나 가격 중 하나가 같은 짝이 양쪽 모두 하나뿐이면 같은 굿즈로 본다.
    // 번호가 있는 굿즈는 번호로만 짝짓는다(번호가 사라졌으면 정말 사라진 것).
    val unnumbered = local.filter { it.goodsId !in usedLocalIds && it.goodsUid == 0 }
    if (unnumbered.isNotEmpty()) {
        val freeRemote = remote.filter { it !in matched }
        val remoteByLoose = freeRemote.groupBy { it.looseKey() }
        val localByLoose = unnumbered.groupBy { it.looseKey() }
        fun sameOne(l: GoodsEntity, r: GoodsEntity) = l.goodsMemo == r.goodsMemo || l.goodsPrice == r.goodsPrice
        for (l in unnumbered) {
            val r = remoteByLoose[l.looseKey()].orEmpty().filter { it !in matched && sameOne(l, it) }.singleOrNull() ?: continue
            // 거꾸로 봐도 이 굿즈 하나뿐이어야 한다
            val back = localByLoose[l.looseKey()].orEmpty().filter { it.goodsId !in usedLocalIds && sameOne(it, r) }
            if (back.singleOrNull() != l) continue
            matched[r] = l
            usedLocalIds += l.goodsId
        }
    }

    return GoodsSyncPlan(
        updates = remote.mapNotNull { r ->
            val l = matched[r] ?: return@mapNotNull null
            if (l.withSourceOf(r) != l) l to r else null
        },
        inserts = remote.filter { it !in matched },
        deletes = local.filter { it.goodsId !in usedLocalIds }
    )
}
