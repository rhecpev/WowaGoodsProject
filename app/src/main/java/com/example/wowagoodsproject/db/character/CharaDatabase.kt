package com.example.wowagoodsproject.db.character

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CharaEntity::class], version = 2)
abstract class CharaDatabase : RoomDatabase(){
    abstract fun charaDao(): CharaDao
}