package com.example.wowagoodsproject.db.character

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_character")
data class CharaEntity(
    @PrimaryKey(autoGenerate = true)
    val charaId: Int = 0,
    val charaNm: String = "",
    val charaUrl: String = "",
    val charaIsFavorite: Boolean = false,
    /** 선호 캐릭터끼리의 우선순위. 작을수록 앞. 선호가 아니면 0. */
    val charaFavoriteOrder: Int = 0
)