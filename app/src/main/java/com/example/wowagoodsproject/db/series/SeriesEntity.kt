package com.example.wowagoodsproject.db.series

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_series")
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val seriesId: Int = 0,
    val seriesNm: String = "",
    val seriesUrl: String = "",
    val seriesCountry: String = "",
    val seriesCharas: String = "",
    val seriesDate: String = ""
)