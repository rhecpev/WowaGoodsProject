package com.example.wowagoodsproject.db.official

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface GoodsDao {

    @Query("SELECT * FROM tb_goods")
    suspend fun getAll(): List<GoodsEntity>

    @Insert
    suspend fun insert(goods: GoodsEntity)

    @Update
    suspend fun update(goods: GoodsEntity)

    @Delete
    suspend fun delete(goods: GoodsEntity)

    @Query("SELECT * FROM tb_goods WHERE goodsChara LIKE '%' || :chara || '%'")
    fun getByCharaFlow(chara: String): Flow<List<GoodsEntity>>

    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series")
    suspend fun getBySeries(series: String): List<GoodsEntity>

    @Query("SELECT COUNT(*) FROM tb_goods WHERE goodsSeries = :series AND goodsChara LIKE '%' || :chara || '%'")
    suspend fun countBySeriesAndChara(series: String, chara: String): Int

    @Query("SELECT COUNT(*) FROM tb_goods WHERE goodsSeries = :series AND goodsChara LIKE '%' || :chara || '%' AND goodsIsGotten = 1")
    suspend fun countGottenBySeriesAndChara(series: String, chara: String): Int

    @Query("UPDATE tb_goods SET goodsIsGotten = 0")
    suspend fun resetAllGotten()

    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series AND goodsChara = :chara AND goodsCategory = :category AND goodsMemo = :memo LIMIT 1")
    suspend fun getByUniqueKey(series: String, chara: String, category: String, memo: String = ""): GoodsEntity?

    @Query("SELECT * FROM tb_goods")
    fun getAllFlow(): Flow<List<GoodsEntity>>

    @Query("SELECT DISTINCT goodsCategory FROM tb_goods WHERE goodsCategory != ''")
    suspend fun getAllCategories(): List<String>
}