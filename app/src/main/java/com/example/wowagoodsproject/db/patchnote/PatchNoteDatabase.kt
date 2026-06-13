package com.example.wowagoodsproject.db.patchnote

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PatchNoteEntity::class], version = 1)
abstract class PatchNoteDatabase : RoomDatabase() {
    abstract fun patchNoteDao(): PatchNoteDao
}