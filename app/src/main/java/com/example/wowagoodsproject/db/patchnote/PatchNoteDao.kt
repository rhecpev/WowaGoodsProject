package com.example.wowagoodsproject.db.patchnote

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchNoteDao {

    @Query("SELECT * FROM tb_patch_notes ORDER BY patchTime DESC")
    fun getAllFlow(): Flow<List<PatchNoteEntity>>

    @Query("SELECT * FROM tb_patch_notes ORDER BY patchTime DESC")
    suspend fun getAll(): List<PatchNoteEntity>

    @Insert
    suspend fun insert(patchNote: PatchNoteEntity)

    @Delete
    suspend fun delete(patchNote: PatchNoteEntity)

    @Query("DELETE FROM tb_patch_notes")
    suspend fun deleteAll()
}