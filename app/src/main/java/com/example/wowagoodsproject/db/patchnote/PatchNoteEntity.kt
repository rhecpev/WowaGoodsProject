package com.example.wowagoodsproject.db.patchnote

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_patch_notes")
data class PatchNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val patchId: Int = 0,
    val patchTime: Long = System.currentTimeMillis(),
    val patchContent: String = ""
)