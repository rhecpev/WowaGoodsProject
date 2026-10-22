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

}