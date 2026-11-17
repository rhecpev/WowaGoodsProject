package com.example.wowagoodsproject.db.news

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {

    @Query("SELECT * FROM tb_news ORDER BY newsTime DESC, newsId DESC")
    fun getAllFlow(): Flow<List<NewsEntity>>

    @Query("SELECT COUNT(*) FROM tb_news WHERE newsIsRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Insert
    suspend fun insertAll(news: List<NewsEntity>)

    @Delete
    suspend fun delete(news: NewsEntity)

    @Query("DELETE FROM tb_news")
    suspend fun deleteAll()

    @Query("UPDATE tb_news SET newsIsRead = 1")
    suspend fun markAllRead()

    @Query("SELECT * FROM tb_news WHERE newsIsRead = 1")
    suspend fun getRead(): List<NewsEntity>

    /** 용량 정리: 읽은 소식만 지운다(안 읽은 소식은 남긴다). */
    @Query("DELETE FROM tb_news WHERE newsIsRead = 1")
    suspend fun deleteRead()

    @Query("DELETE FROM tb_news WHERE newsTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
