package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversion_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ConversionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ConversionEntity)

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM conversion_history")
    suspend fun clearAllHistory()
}
