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

    @Query("DELETE FROM tb_news WHERE newsTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
