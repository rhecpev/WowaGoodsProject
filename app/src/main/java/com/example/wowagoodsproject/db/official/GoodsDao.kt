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

    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series ORDER BY goodsReleaseDate DESC")
    suspend fun getBySeries(series: String): List<GoodsEntity>

    // 백업 불러오기 전에 상태를 초기화한다. 구매 정보도 백업 값으로 다시 채워지므로 같이 비운다.
    @Query("UPDATE tb_goods SET goodsStatus = 'NOT_GOTTEN', goodsPurchaseDate = '', goodsPurchaseStore = '', goodsReceiveDate = '', goodsQuantity = 1, goodsShippingDate = ''")
    suspend fun resetAllGotten()
    @Query("SELECT * FROM tb_goods WHERE goodsSeries = :series AND goodsChara = :chara AND goodsCategory = :category AND goodsMemo = :memo AND goodsPrice = :price LIMIT 1")
    suspend fun getByUniqueKey(series: String, chara: String, category: String, memo: String = "", price: String = ""): GoodsEntity?

    @Query("SELECT COUNT(*) FROM tb_goods")
    suspend fun count(): Int

    @Query("SELECT * FROM tb_goods WHERE goodsId = :id")
    suspend fun getById(id: Int): GoodsEntity?

    @Query("SELECT * FROM tb_goods WHERE goodsUid = :uid LIMIT 1")
    suspend fun getByUid(uid: Int): GoodsEntity?

    @Query("SELECT * FROM tb_goods ORDER BY goodsReleaseDate DESC")
    fun getAllFlow(): Flow<List<GoodsEntity>>

    @Query("SELECT DISTINCT goodsCategory FROM tb_goods WHERE goodsCategory != ''")
    suspend fun getAllCategories(): List<String>

    /** 글자 일부도 맞는다("양양" → "양양현령"). 부르는 쪽에서 hasChara 로 한 번 더 거른다. */
    @Query("SELECT * FROM tb_goods WHERE goodsChara LIKE '%' || :chara || '%' ORDER BY goodsReleaseDate DESC")
    suspend fun getByChara(chara: String): List<GoodsEntity>



}