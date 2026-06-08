package com.example.wowagoodsproject.db.character

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharaDao {

    @Query("SELECT * FROM tb_character")
    suspend fun getAll(): List<CharaEntity>

    @Insert
    suspend fun insert(chara : CharaEntity)

    @Delete
    suspend fun delete(chara : CharaEntity)

    @Update
    suspend fun update(chara : CharaEntity)

    @Query("SELECT * FROM tb_character")
    fun getAllFlow(): Flow<List<CharaEntity>>

}