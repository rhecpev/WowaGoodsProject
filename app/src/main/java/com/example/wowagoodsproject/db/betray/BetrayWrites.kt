package com.example.wowagoodsproject.db.betray

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PhotoStore
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.purchaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * 배신 굿즈를 바꾸는 작업. 2차창작(FanGoodsWrites.kt)과 같이 id 로 DB 의 최신 값을 다시 읽어 바꿀 칸만 고친다.
 */

/** 최신 값을 읽어 [change] 만 반영해 저장한다. 굿즈가 없어졌으면 null. */
suspend fun BetrayDao.modify(id: Int, change: (BetrayGoodsEntity) -> BetrayGoodsEntity): BetrayGoodsEntity? =
    App.betrayDatabase.withTransaction {
        val fresh = goodsById(id) ?: return@withTransaction null
        val updated = change(fresh)
        if (updated != fresh) updateGoods(updated)
        updated
    }

/** 보유 ↔ 미보유. 보유가 아니면(미보유·구매예정) 보유로 바꾼다. */
suspend fun BetrayDao.toggleGotten(id: Int) {
    modify(id) { it.withStatus(if (it.status == GoodsStatus.GOTTEN) GoodsStatus.NOT_GOTTEN else GoodsStatus.GOTTEN) }
}

/** 구매예정 ↔ 미보유 */
suspend fun BetrayDao.togglePending(id: Int) {
    modify(id) { it.withStatus(if (it.status == GoodsStatus.PENDING) GoodsStatus.NOT_GOTTEN else GoodsStatus.PENDING) }
}

/** 구매예정으로 바꾸면서(이미 구매예정이면 정보만) 구매 정보를 저장한다. */
suspend fun BetrayDao.savePending(id: Int, info: PurchaseInfo) {
    modify(id) { it.withStatus(GoodsStatus.PENDING).withPurchaseInfo(info) }
}

suspend fun BetrayDao.setQuantity(id: Int, quantity: Int) {
    modify(id) { it.copy(goodsQuantity = quantity.coerceIn(1, 99)) }
}

/** 배송 시작일을 적는다(빈 문자열이면 취소). */
suspend fun BetrayDao.setShipping(id: Int, date: String) {
    modify(id) { if (it.status == GoodsStatus.PENDING) it.copy(goodsShippingDate = date) else it }
}

/** 여러 굿즈의 상태를 한 번에 바꾼다(다중 선택 일괄 변경). */
suspend fun BetrayDao.applyStatus(ids: List<Int>, status: GoodsStatus, edit: PurchaseInfoEdit? = null) {
    App.betrayDatabase.withTransaction {
        ids.forEach { id ->
            modify(id) { fresh ->
                val changed = fresh.withStatus(status)
                if (edit != null) changed.withPurchaseInfo(edit.applyTo(fresh.purchaseInfo)) else changed
            }
        }
    }
}

/** 굿즈를 지우고, 다른 배신 굿즈가 쓰지 않는 사진도 지운다. */
suspend fun BetrayDao.deleteWithImage(goods: BetrayGoodsEntity) {
    deleteGoods(goods)
    deleteUnusedImages(listOf(goods.goodsImgPath))
}

/** 장르를 지운다. 그 장르의 굿즈·선호 캐릭터·직접 추가한 캐릭터와 사진(장르 배경·아이콘 포함)도 함께 지운다. */
suspend fun BetrayDao.deleteGenreWithGoods(genreId: Int) {
    val images = goodsOf(genreId).map { it.goodsImgPath } +
        genre(genreId)?.let { listOf(it.genreImgPath, it.genreIconPath) }.orEmpty() +
        charasOf(genreId).map { it.charaImgPath } +
        seriesOf(genreId).flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }
    App.betrayDatabase.withTransaction {
        deleteGoodsOf(genreId)
        deleteFavoritesOf(genreId)
        deleteCharasOf(genreId)
        deleteSeriesOf(genreId)
        deleteGenre(genreId)
    }
    deleteUnusedImages(images)
}

/** 배신 굿즈 쪽에서 쓰는 모든 사진 경로(굿즈 사진 + 장르 배경·아이콘 + 캐릭터 사진). 빈 경로는 뺀다. */
suspend fun BetrayDao.usedImagePaths(): List<String> =
    (allGoods().map { it.goodsImgPath } +
        genres().flatMap { listOf(it.genreImgPath, it.genreIconPath) } +
        allCharas().map { it.charaImgPath } +
        allSeries().flatMap { listOf(it.seriesImgPath, it.seriesIconPath) }).filter { it.isNotEmpty() }

/** [paths] 중 이제 어느 배신 굿즈·장르도 쓰지 않는 사진을 지운다. 2차창작 굿즈가 쓰는 사진은 남긴다. */
suspend fun BetrayDao.deleteUnusedImages(paths: Collection<String>) {
    val used = com.example.wowagoodsproject.db.allUsedImagePaths()
    withContext(Dispatchers.IO) {
        paths.filter { it.isNotEmpty() && it !in used }.forEach { File(it).delete() }
    }
}

/** 선호 캐릭터를 켜고 끈다. 켜면 그 장르 선호 목록의 맨 뒤에 붙는다. */
suspend fun BetrayDao.toggleFavorite(genreId: Int, charaNm: String) {
    App.betrayDatabase.withTransaction {
        val current = favoritesOf(genreId)
        if (current.any { it.charaNm == charaNm }) {
            deleteFavorite(genreId, charaNm)
        } else {
            upsertFavorite(BetrayFavoriteEntity(genreId, charaNm, (current.maxOfOrNull { it.favoriteOrder } ?: 0) + 1))
        }
    }
}

/**
 * 고른 사진을 앱 폴더에 WebP 로 저장하고 경로를 돌려준다([PhotoStore.save]). 이름은 [prefix] 로 시작해 2차창작 사진과 섞이지 않는다.
 * 굿즈 사진은 "betray_", 장르 배경·아이콘은 "betray_genre_".
 */
suspend fun copyBetrayImage(context: Context, uri: Uri, prefix: String = "betray_"): String =
    PhotoStore.save(context, uri, prefix)

/**
 * 장르 이름·배경·아이콘을 바꾼다. 사진 값이 null 이면 그대로, 빈 문자열이면 뺀다.
 * 바뀌어서 이제 아무도 쓰지 않는 예전 사진은 지운다.
 */
suspend fun BetrayDao.updateGenreInfo(genreId: Int, name: String?, newImage: String?, newIcon: String?) {
    val oldImages = mutableListOf<String>()
    App.betrayDatabase.withTransaction {
        val fresh = genre(genreId) ?: return@withTransaction
        val updated = fresh.copy(
            genreNm = name?.trim()?.takeIf { it.isNotEmpty() } ?: fresh.genreNm,
            genreImgPath = newImage ?: fresh.genreImgPath,
            genreIconPath = newIcon ?: fresh.genreIconPath
        )
        if (updated != fresh) updateGenre(updated)
        if (updated.genreImgPath != fresh.genreImgPath) oldImages += fresh.genreImgPath
        if (updated.genreIconPath != fresh.genreIconPath) oldImages += fresh.genreIconPath
    }
    deleteUnusedImages(oldImages)
}

/** 캐릭터 이름이 쓸 수 있는지. 쉼표는 굿즈 캐릭터 칸의 구분자라 쓸 수 없다. */
fun isValidCharaName(name: String): Boolean = name.trim().isNotEmpty() && !name.contains(',')

/**
 * 직접 추가한 캐릭터를 저장한다(새로 만들기·고치기).
 * [oldName] 이 있고 이름이 바뀌면 그 장르 굿즈의 캐릭터 칸과 선호 캐릭터 이름도 함께 바꾼다.
 * [newImage] 가 null 이면 사진은 그대로, 빈 문자열이면 뺀다. 바뀌어서 아무도 안 쓰는 예전 사진은 지운다.
 * 이름이 비었거나 쉼표가 있거나, 다른 캐릭터(직접 추가했거나 굿즈에 나온)와 이름이 겹치면 false.
 */
suspend fun BetrayDao.saveChara(genreId: Int, oldName: String?, newName: String, newImage: String?): Boolean {
    val name = newName.trim()
    if (!isValidCharaName(name)) return false
    val old = oldName?.trim()?.takeIf { it.isNotEmpty() }
    val renaming = old != null && old != name
    val oldImages = mutableListOf<String>()
    val ok = App.betrayDatabase.withTransaction {
        val goods = goodsOf(genreId)
        if (renaming || old == null) {
            val taken = charasOf(genreId).any { it.charaNm == name } || goods.any { name in charaNames(it.goodsChara) }
            if (taken) return@withTransaction false
        }
        val existing = old?.let { chara(genreId, it) }
        val image = newImage ?: existing?.charaImgPath.orEmpty()
        if (existing != null && existing.charaImgPath != image) oldImages += existing.charaImgPath
        if (renaming) {
            deleteChara(genreId, old!!)
            // 굿즈 캐릭터 칸의 이름을 바꾼다("A,B" 에서 A 만 정확히).
            goods.forEach { g ->
                val names = charaNames(g.goodsChara)
                if (old in names) updateGoods(g.copy(goodsChara = names.map { if (it == old) name else it }.distinct().joinToString(",")))
            }
            // 선호 캐릭터면 순서를 그대로 둔 채 이름만 바꾼다.
            favoritesOf(genreId).firstOrNull { it.charaNm == old }?.let { fav ->
                deleteFavorite(genreId, old)
                upsertFavorite(fav.copy(charaNm = name))
            }
        }
        upsertChara(BetrayCharaEntity(genreId, name, image))
        true
    }
    if (ok) deleteUnusedImages(oldImages)
    return ok
}

/** 직접 추가한 캐릭터를 지운다(이름·사진). 굿즈는 그대로라 굿즈에 이름이 남아 있으면 자동 캐릭터로 계속 보인다. */
suspend fun BetrayDao.deleteCharaWithImage(genreId: Int, name: String) {
    val image = chara(genreId, name)?.charaImgPath.orEmpty()
    deleteChara(genreId, name)
    deleteUnusedImages(listOf(image))
}

/** 선호 캐릭터로 정하거나 푼다. 새로 정하면 선호 목록 맨 뒤에 붙고, 이미 같은 상태면 그대로 둔다. */
suspend fun BetrayDao.setFavorite(genreId: Int, charaNm: String, favorite: Boolean) {
    App.betrayDatabase.withTransaction {
        val current = favoritesOf(genreId)
        val isFav = current.any { it.charaNm == charaNm }
        if (favorite && !isFav) upsertFavorite(BetrayFavoriteEntity(genreId, charaNm, (current.maxOfOrNull { it.favoriteOrder } ?: 0) + 1))
        if (!favorite && isFav) deleteFavorite(genreId, charaNm)
    }
}

/** 선호 캐릭터 순서를 [names] 순서(1부터)로 저장한다. 목록에 없는 선호 캐릭터는 그대로 둔다. */
suspend fun BetrayDao.reorderFavorites(genreId: Int, names: List<String>) {
    App.betrayDatabase.withTransaction {
        val current = favoritesOf(genreId).map { it.charaNm }.toSet()
        names.filter { it in current }.forEachIndexed { i, name -> upsertFavorite(BetrayFavoriteEntity(genreId, name, i + 1)) }
    }
}

/**
 * 시리즈를 등록하거나 고친다(이름·배경·아이콘). [oldName] 이 있고 이름이 바뀌면 그 장르 굿즈의 시리즈 칸도 함께 바꾼다.
 * 사진 값이 null 이면 그대로, 빈 문자열이면 뺀다. 바뀌어서 아무도 안 쓰는 예전 사진은 지운다.
 * 이름이 비었거나 다른 등록 시리즈와 겹치면 false. 굿즈에만 적힌 같은 이름 시리즈는 그 시리즈를 등록하는 것으로 본다.
 */
suspend fun BetrayDao.saveSeries(genreId: Int, oldName: String?, newName: String, newImage: String?, newIcon: String?, newDate: String? = null): Boolean {
    val name = newName.trim()
    // '미분류'는 시리즈를 비운 굿즈를 묶는 이름이라 등록 시리즈로 쓰면 두 묶음이 섞인다.
    if (name.isEmpty() || name.equals(com.example.wowagoodsproject.screen.betray.NO_SERIES, ignoreCase = true)) return false
    val old = oldName?.trim()?.takeIf { it.isNotEmpty() }
    val oldImages = mutableListOf<String>()
    val ok = App.betrayDatabase.withTransaction {
        val registered = seriesOf(genreId)
        // 배신 굿즈는 시리즈 카드 묶기·이름 바꾸기가 대소문자를 가리므로 겹침도 같은 이름일 때만 본다.
        if (registered.any { it.seriesNm != old && it.seriesNm == name }) return@withTransaction false
        val existing = old?.let { o -> registered.firstOrNull { it.seriesNm == o } }
        val image = newImage ?: existing?.seriesImgPath.orEmpty()
        val icon = newIcon ?: existing?.seriesIconPath.orEmpty()
        if (existing != null) {
            if (existing.seriesImgPath != image) oldImages += existing.seriesImgPath
            if (existing.seriesIconPath != icon) oldImages += existing.seriesIconPath
            deleteSeries(genreId, existing.seriesNm)
        }
        if (old != null && old != name) {
            goodsOf(genreId).filter { it.goodsSeries.trim() == old }.forEach { updateGoods(it.copy(goodsSeries = name)) }
        }
        upsertSeries(BetraySeriesEntity(genreId, name, image, icon, newDate ?: existing?.seriesDate.orEmpty()))
        true
    }
    if (ok) deleteUnusedImages(oldImages)
    return ok
}

/** 등록한 시리즈를 지운다(이름·사진). 굿즈는 그대로라 굿즈에 이름이 있으면 시리즈로 계속 보인다. */
suspend fun BetrayDao.deleteSeriesWithImages(genreId: Int, name: String) {
    val s = series(genreId, name) ?: return
    deleteSeries(genreId, name)
    deleteUnusedImages(listOf(s.seriesImgPath, s.seriesIconPath))
}
