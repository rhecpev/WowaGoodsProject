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

    /** 여러 시리즈의 굿즈를 한 번에(시리즈마다 따로 읽지 않게). 인자는 999개까지라 부르는 쪽이 나눠 부른다 */
    @Query("SELECT * FROM tb_goods WHERE goodsSeries IN (:series) ORDER BY goodsReleaseDate DESC")
    suspend fun getBySeriesIn(series: List<String>): List<GoodsEntity>

    /** 적힌 구입처만(구입처 추천). 많이 쓴 순으로 세려고 겹치는 값도 그대로 */
    @Query("SELECT goodsPurchaseStore FROM tb_goods WHERE goodsPurchaseStore != ''")
    suspend fun purchaseStores(): List<String>

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