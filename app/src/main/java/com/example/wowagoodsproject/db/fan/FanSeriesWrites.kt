package com.example.wowagoodsproject.db.fan

import androidx.room.withTransaction
import com.example.wowagoodsproject.App

/**
 * 2차창작 시리즈를 등록하거나 고친다(이름·배경·아이콘). [oldName] 이 있고 이름이 바뀌면 그 시리즈 굿즈의 시리즈 칸도 함께 바꾼다.
 * 사진 값이 null 이면 그대로, 빈 문자열이면 뺀다. 바뀌어서 아무도 안 쓰는 예전 사진은 지운다.
 * 이름이 비었거나 다른 등록 시리즈와 겹치면 false.
 */
suspend fun FanGoodsDao.saveSeries(oldName: String?, newName: String, newImage: String?, newIcon: String?, newDate: String? = null): Boolean {
    val name = newName.trim()
    // '미분류'는 시리즈를 비운 굿즈를 묶는 이름이라 등록 시리즈로 쓰면 두 묶음이 섞인다.
    if (name.isEmpty() || name.equals(com.example.wowagoodsproject.screen.fan.NO_FAN_SERIES, ignoreCase = true)) return false
    val old = oldName?.trim()?.takeIf { it.isNotEmpty() }
    val oldImages = mutableListOf<String>()
    val ok = App.fanDatabase.withTransaction {
        val registered = allSeries()
        if (registered.any { it.seriesNm != old && it.seriesNm.equals(name, ignoreCase = true) }) return@withTransaction false
        val existing = old?.let { o -> registered.firstOrNull { it.seriesNm == o } }
        val image = newImage ?: existing?.seriesImgPath.orEmpty()
        val icon = newIcon ?: existing?.seriesIconPath.orEmpty()
        if (existing != null) {
            if (existing.seriesImgPath != image) oldImages += existing.seriesImgPath
            if (existing.seriesIconPath != icon) oldImages += existing.seriesIconPath
            deleteSeries(existing.seriesNm)
        }
        if (old != null && old != name) {
            // 시리즈 카드가 대소문자를 가리지 않고 묶으므로, 이름을 바꿀 때도 그 카드의 굿즈를 모두 옮긴다.
            getAll().filter { it.fanGoodsSeries.trim().equals(old, ignoreCase = true) }.forEach { update(it.copy(fanGoodsSeries = name)) }
        }
        upsertSeries(FanSeriesEntity(name, image, icon, newDate ?: existing?.seriesDate.orEmpty()))
        true
    }
    if (ok) deleteUnusedImages(oldImages)
    return ok
}

/** 등록한 시리즈를 지운다(이름·사진). 굿즈는 그대로라 굿즈에 이름이 있으면 시리즈로 계속 보인다. */
suspend fun FanGoodsDao.deleteSeriesWithImages(name: String) {
    val s = allSeries().firstOrNull { it.seriesNm == name } ?: return
    deleteSeries(name)
    deleteUnusedImages(listOf(s.seriesImgPath, s.seriesIconPath))
}
