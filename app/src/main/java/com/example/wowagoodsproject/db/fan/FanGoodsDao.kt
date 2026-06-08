package com.example.wowagoodsproject.db.fan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface FanGoodsDao {

    @Query("SELECT * FROM tb_fan_goods")
    suspend fun getAll(): List<FanGoodsEntity>

    @Query("SELECT DISTINCT fanGoodsCategory FROM tb_fan_goods")
    suspend fun getAllCategories(): List<String>

    @Insert
    suspend fun insert(fanGoods: FanGoodsEntity)

    @Update
    suspend fun update(fanGoods: FanGoodsEntity)

    @Delete
    suspend fun delete(fanGoods: FanGoodsEntity)

    @Query("SELECT * FROM tb_fan_goods WHERE fanGoodsChara LIKE '%' || :chara || '%'")
    fun getByCharaFlow(chara: String): Flow<List<FanGoodsEntity>>

    @Query("DELETE FROM tb_fan_goods")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT fanGoodsCategory FROM tb_fan_goods WHERE fanGoodsCategory != ''")
    suspend fun getFanCategories(): List<String>
}