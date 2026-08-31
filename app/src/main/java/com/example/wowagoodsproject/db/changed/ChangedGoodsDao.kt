package com.example.wowagoodsproject.db.changed

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangedGoodsDao {

    @Query("SELECT * FROM tb_changed_goods ORDER BY changedTime DESC, changedId DESC")
    fun getAllFlow(): Flow<List<ChangedGoodsEntity>>

    @Query("SELECT * FROM tb_changed_goods WHERE isRestored = 0 ORDER BY changedTime DESC, changedId DESC")
    fun getPendingFlow(): Flow<List<ChangedGoodsEntity>>

    @Query("SELECT * FROM tb_changed_goods WHERE changedId = :id LIMIT 1")
    suspend fun getById(id: Int): ChangedGoodsEntity?

    @Insert
    suspend fun insert(changed: ChangedGoodsEntity): Long

    @Query("UPDATE tb_changed_goods SET isRestored = 1 WHERE changedId = :id")
    suspend fun markRestored(id: Int)

    @Delete
    suspend fun delete(changed: ChangedGoodsEntity)

    @Query("DELETE FROM tb_changed_goods")
    suspend fun deleteAll()
}
