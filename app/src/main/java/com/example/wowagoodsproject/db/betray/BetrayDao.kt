package com.example.wowagoodsproject.db.betray

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BetrayDao {
    // ── 장르 ──
    @Query("SELECT * FROM tb_betray_genre ORDER BY genreOrder, genreId")
    fun genresFlow(): Flow<List<BetrayGenreEntity>>

    @Query("SELECT * FROM tb_betray_genre ORDER BY genreOrder, genreId")
    suspend fun genres(): List<BetrayGenreEntity>

    @Query("SELECT * FROM tb_betray_genre WHERE genreId = :id")
    suspend fun genre(id: Int): BetrayGenreEntity?

    @Insert
    suspend fun insertGenre(genre: BetrayGenreEntity): Long

    @Update
    suspend fun updateGenre(genre: BetrayGenreEntity)

    @Query("DELETE FROM tb_betray_genre WHERE genreId = :id")
    suspend fun deleteGenre(id: Int)

    // ── 굿즈 ──
    @Query("SELECT * FROM tb_betray_goods WHERE genreId = :genreId ORDER BY goodsRegDate DESC, goodsId DESC")
    fun goodsFlow(genreId: Int): Flow<List<BetrayGoodsEntity>>

    @Query("SELECT * FROM tb_betray_goods ORDER BY genreId, goodsRegDate DESC, goodsId DESC")
    suspend fun allGoods(): List<BetrayGoodsEntity>

    /** 적힌 구입처만(구입처 추천). 겹치는 값도 그대로 */
    @Query("SELECT goodsPurchaseStore FROM tb_betray_goods WHERE goodsPurchaseStore != ''")
    suspend fun purchaseStores(): List<String>

    @Query("SELECT * FROM tb_betray_goods WHERE genreId = :genreId")
    suspend fun goodsOf(genreId: Int): List<BetrayGoodsEntity>

    @Query("SELECT * FROM tb_betray_goods WHERE goodsId = :id")
    suspend fun goodsById(id: Int): BetrayGoodsEntity?

    @Insert
    suspend fun insertGoods(goods: BetrayGoodsEntity): Long

    @Insert
    suspend fun insertAllGoods(goods: List<BetrayGoodsEntity>)

    @Update
    suspend fun updateGoods(goods: BetrayGoodsEntity)

    @Delete
    suspend fun deleteGoods(goods: BetrayGoodsEntity)

    @Query("DELETE FROM tb_betray_goods WHERE genreId = :genreId")
    suspend fun deleteGoodsOf(genreId: Int)

    // ── 선호 캐릭터 ──
    @Query("SELECT * FROM tb_betray_favorite WHERE genreId = :genreId ORDER BY favoriteOrder, charaNm")
    fun favoritesFlow(genreId: Int): Flow<List<BetrayFavoriteEntity>>

    @Query("SELECT * FROM tb_betray_favorite ORDER BY genreId, favoriteOrder, charaNm")
    suspend fun allFavorites(): List<BetrayFavoriteEntity>

    @Query("SELECT * FROM tb_betray_favorite WHERE genreId = :genreId ORDER BY favoriteOrder, charaNm")
    suspend fun favoritesOf(genreId: Int): List<BetrayFavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: BetrayFavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFavorites(favorites: List<BetrayFavoriteEntity>)

    @Query("DELETE FROM tb_betray_favorite WHERE genreId = :genreId AND charaNm = :charaNm")
    suspend fun deleteFavorite(genreId: Int, charaNm: String)

    @Query("DELETE FROM tb_betray_favorite WHERE genreId = :genreId")
    suspend fun deleteFavoritesOf(genreId: Int)

    // ── 직접 추가한 캐릭터 ──
    @Query("SELECT * FROM tb_betray_chara WHERE genreId = :genreId ORDER BY charaNm")
    fun charasFlow(genreId: Int): Flow<List<BetrayCharaEntity>>

    @Query("SELECT * FROM tb_betray_chara WHERE genreId = :genreId ORDER BY charaNm")
    suspend fun charasOf(genreId: Int): List<BetrayCharaEntity>

    @Query("SELECT * FROM tb_betray_chara ORDER BY genreId, charaNm")
    suspend fun allCharas(): List<BetrayCharaEntity>

    @Query("SELECT * FROM tb_betray_chara WHERE genreId = :genreId AND charaNm = :charaNm")
    suspend fun chara(genreId: Int, charaNm: String): BetrayCharaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChara(chara: BetrayCharaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCharas(charas: List<BetrayCharaEntity>)

    @Query("DELETE FROM tb_betray_chara WHERE genreId = :genreId AND charaNm = :charaNm")
    suspend fun deleteChara(genreId: Int, charaNm: String)

    @Query("DELETE FROM tb_betray_chara WHERE genreId = :genreId")
    suspend fun deleteCharasOf(genreId: Int)

    // ── 등록한 시리즈 ──
    @Query("SELECT * FROM tb_betray_series WHERE genreId = :genreId ORDER BY seriesNm")
    fun seriesFlow(genreId: Int): Flow<List<BetraySeriesEntity>>

    @Query("SELECT * FROM tb_betray_series WHERE genreId = :genreId ORDER BY seriesNm")
    suspend fun seriesOf(genreId: Int): List<BetraySeriesEntity>

    @Query("SELECT * FROM tb_betray_series ORDER BY genreId, seriesNm")
    suspend fun allSeries(): List<BetraySeriesEntity>

    @Query("SELECT * FROM tb_betray_series WHERE genreId = :genreId AND seriesNm = :seriesNm")
    suspend fun series(genreId: Int, seriesNm: String): BetraySeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: BetraySeriesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSeries(series: List<BetraySeriesEntity>)

    @Query("DELETE FROM tb_betray_series WHERE genreId = :genreId AND seriesNm = :seriesNm")
    suspend fun deleteSeries(genreId: Int, seriesNm: String)

    @Query("DELETE FROM tb_betray_series WHERE genreId = :genreId")
    suspend fun deleteSeriesOf(genreId: Int)

    @Query("DELETE FROM tb_betray_series")
    suspend fun deleteAllSeries()

    // ── 백업 불러오기 ──
    @Query("DELETE FROM tb_betray_goods")
    suspend fun deleteAllGoods()

    @Query("DELETE FROM tb_betray_favorite")
    suspend fun deleteAllFavorites()

    @Query("DELETE FROM tb_betray_genre")
    suspend fun deleteAllGenres()

    @Query("DELETE FROM tb_betray_chara")
    suspend fun deleteAllCharas()

    @Insert
    suspend fun insertAllGenres(genres: List<BetrayGenreEntity>)
}
