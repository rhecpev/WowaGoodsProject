package com.example.wowagoodsproject.db.official

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface GoodsDao {

    @Query("SELECT * FROM tb_goods ORDER BY goodsReleaseDate DESC")
    suspend fun getAll(): List<GoodsEntity>

    @Insert
    suspend fun insert(goods: GoodsEntity)

    @Update
    suspend fun update(goods: GoodsEntity)

    @Delete
    suspend fun delete(goods: GoodsEntity)

    @Query("SELECT * FROM tb_goods WHERE goodsChara LIKE '%' || :chara || '%' ORDER BY goodsReleaseDate DESC")
    fun getByCharaFlow(chara: String): Flow<List<GoodsEntity>>

    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series ORDER BY goodsReleaseDate DESC")
    suspend fun getBySeries(series: String): List<GoodsEntity>

    @Query("SELECT COUNT(*) FROM tb_goods WHERE goodsSeries = :series AND goodsChara LIKE '%' || :chara || '%' AND goodsCategory != '세트'")
    suspend fun countBySeriesAndChara(series: String, chara: String): Int

    @Query("SELECT COUNT(*) FROM tb_goods WHERE goodsSeries = :series AND goodsChara LIKE '%' || :chara || '%' AND goodsCategory != '세트' AND goodsStatus = 'GOTTEN'")
    suspend fun countGottenBySeriesAndChara(series: String, chara: String): Int
    @Query("UPDATE tb_goods SET goodsStatus = 'NOT_GOTTEN'")
    suspend fun resetAllGotten()
    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series AND goodsChara = :chara AND goodsCategory = :category AND goodsMemo = :memo AND goodsPrice = :price LIMIT 1")
    suspend fun getByUniqueKey(series: String, chara: String, category: String, memo: String = "", price: String = ""): GoodsEntity?

    @Query("SELECT * FROM tb_goods ORDER BY goodsReleaseDate DESC")
    fun getAllFlow(): Flow<List<GoodsEntity>>

    @Query("SELECT DISTINCT goodsCategory FROM tb_goods WHERE goodsCategory != ''")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT * FROM tb_goods WHERE goodsChara LIKE '%' || :chara || '%' ORDER BY goodsReleaseDate DESC")
    suspend fun getByChara(chara: String): List<GoodsEntity>



}