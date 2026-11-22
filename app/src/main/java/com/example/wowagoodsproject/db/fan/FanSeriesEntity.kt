package com.example.wowagoodsproject.db.fan

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 등록한 2차창작 시리즈: 이름·배경·아이콘. 2차창작 굿즈와는 시리즈 이름(앞뒤 공백 무시)으로 이어진다.
 * 등록하지 않은 시리즈(굿즈에만 적힌 이름)도 2차창작 탭에 나온다. DB 8부터.
 */
@Entity(tableName = "tb_fan_series")
data class FanSeriesEntity(
    @PrimaryKey
    val seriesNm: String,
    val seriesImgPath: String = "",
    val seriesIconPath: String = "",
    /** 시리즈 날짜 "yyyy-MM-dd"(만들 때 입력, 기본은 만든 날). 비어 있으면 굿즈 중 가장 최근 등록일을 보인다. DB 9부터 */
    val seriesDate: String = ""
)
