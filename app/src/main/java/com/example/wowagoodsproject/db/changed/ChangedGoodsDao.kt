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

    /** 아직 처리(확인 표시)하지 않은 기록 수 */
    @Query("SELECT COUNT(*) FROM tb_changed_goods WHERE isChecked = 0")
    fun uncheckedCountFlow(): Flow<Int>

    @Query("SELECT * FROM tb_changed_goods WHERE changedId = :id LIMIT 1")
    suspend fun getById(id: Int): ChangedGoodsEntity?

    @Insert
    suspend fun insert(changed: ChangedGoodsEntity): Long

    /** 상태를 되돌린 것도 고친 것이므로 처리(확인 표시)로 넘긴다. */
    @Query("UPDATE tb_changed_goods SET isRestored = 1, isChecked = 1 WHERE changedId = :id")
    suspend fun markRestored(id: Int)

    @Query("UPDATE tb_changed_goods SET isChecked = 1 WHERE changedId IN (:ids)")
    suspend fun markChecked(ids: List<Int>)

    @Delete
    suspend fun delete(changed: ChangedGoodsEntity)

    @Query("DELETE FROM tb_changed_goods")
    suspend fun deleteAll()

    @Query("DELETE FROM tb_changed_goods WHERE isChecked = 1")
    suspend fun deleteChecked()
}
