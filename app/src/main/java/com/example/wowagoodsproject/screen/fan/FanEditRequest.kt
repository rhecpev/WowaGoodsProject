package com.example.wowagoodsproject.screen.fan

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 비공식 굿즈 수정 요청. 비공식 탭·캐릭터별·검색·마이페이지의 굿즈 상세에서 '이 굿즈 수정'을 누르면 굿즈 번호를 넣고,
 * MainScreen 이 받아 수정 화면(fan_edit/{id})을 연 뒤 비운다.
 */
object FanEditRequest {
    val goodsId = MutableStateFlow<Int?>(null)
    fun open(id: Int) { goodsId.value = id }
}
