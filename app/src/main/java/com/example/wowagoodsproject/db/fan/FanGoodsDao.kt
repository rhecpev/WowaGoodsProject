package com.example.wowagoodsproject.db.fan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface FanGoodsDao {

    @Query("SELECT * FROM tb_fan_goods ORDER BY fanGoodsReleaseDate DESC")
    suspend fun getAll(): List<FanGoodsEntity>

    /** 적힌 구입처만(구입처 추천). 겹치는 값도 그대로 */
    @Query("SELECT fanGoodsPurchaseStore FROM tb_fan_goods WHERE fanGoodsPurchaseStore != ''")
    suspend fun purchaseStores(): List<String>

    @Query("SELECT DISTINCT fanGoodsCategory FROM tb_fan_goods")
    suspend fun getAllCategories(): List<String>

    @Insert
    suspend fun insert(fanGoods: FanGoodsEntity)

    @Update
    suspend fun update(fanGoods: FanGoodsEntity)

    /** 2차창작 굿즈가 바뀔 때마다(다른 화면·데이터 입력 포함) 새 목록을 보낸다. */
    @Query("SELECT * FROM tb_fan_goods ORDER BY fanGoodsReleaseDate DESC")
    fun getAllFlow(): Flow<List<FanGoodsEntity>>

    @Query("SELECT * FROM tb_fan_goods WHERE fanGoodsId = :id")
    suspend fun getById(id: Int): FanGoodsEntity?

    @Delete
    suspend fun delete(fanGoods: FanGoodsEntity)

    @Query("DELETE FROM tb_fan_goods")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT fanGoodsCategory FROM tb_fan_goods WHERE fanGoodsCategory != ''")
    suspend fun getFanCategories(): List<String>

    /** 글자 일부도 맞는다. 부르는 쪽에서 hasChara 로 한 번 더 거른다. */
    @Query("SELECT * FROM tb_fan_goods WHERE fanGoodsChara LIKE '%' || :chara || '%' ORDER BY fanGoodsId DESC")
    suspend fun getByChara(chara: String): List<FanGoodsEntity>
    @Insert
    suspend fun insertAll(fanGoodsList: List<FanGoodsEntity>)

    // ── 등록한 시리즈 ──
    @Query("SELECT * FROM tb_fan_series ORDER BY seriesNm")
    fun seriesFlow(): Flow<List<FanSeriesEntity>>

    @Query("SELECT * FROM tb_fan_series ORDER BY seriesNm")
    suspend fun allSeries(): List<FanSeriesEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: FanSeriesEntity)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAllSeries(series: List<FanSeriesEntity>)

    @Query("DELETE FROM tb_fan_series WHERE seriesNm = :seriesNm")
    suspend fun deleteSeries(seriesNm: String)

    @Query("DELETE FROM tb_fan_series")
    suspend fun deleteAllSeries()

}