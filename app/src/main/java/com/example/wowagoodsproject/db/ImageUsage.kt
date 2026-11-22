package com.example.wowagoodsproject.db

import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.betray.usedImagePaths

/**
 * 앱 폴더의 사진을 쓰는 모든 곳: 2차창작 굿즈·2차창작 시리즈, 배신 굿즈(굿즈·장르·캐릭터·시리즈).
 * 사진을 지울 때는 여기에 없는 것만 지운다(한 사진을 여러 곳이 써도 남게).
 */
suspend fun allUsedImagePaths(): Set<String> {
    val fan = App.fanDatabase.fanGoodsDao()
    return (fan.getAll().map { it.fanGoodsImgPath } +
        fan.allSeries().flatMap { listOf(it.seriesImgPath, it.seriesIconPath) } +
        App.betrayDatabase.betrayDao().usedImagePaths()).filter { it.isNotEmpty() }.toSet()
}
