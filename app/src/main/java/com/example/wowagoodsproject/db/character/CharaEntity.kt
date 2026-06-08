package com.example.wowagoodsproject.db.character

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_character")
data class CharaEntity(
    @PrimaryKey(autoGenerate = true)
    val charaId: Int = 0,
    val charaNm: String = "",
    val charaUrl: String = "",
    val charaIsFavorite: Boolean = false
)