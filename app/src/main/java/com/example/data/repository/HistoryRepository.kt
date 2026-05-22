package com.example.data.repository

import com.example.data.db.ConversionDao
import com.example.data.db.ConversionEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val conversionDao: ConversionDao) {
    val allHistory: Flow<List<ConversionEntity>> = conversionDao.getAllHistory()

    suspend fun insertHistory(item: ConversionEntity) {
        conversionDao.insertHistory(item)
    }

    suspend fun deleteHistoryById(id: Int) {
        conversionDao.deleteHistoryById(id)
    }

    suspend fun clearAllHistory() {
        conversionDao.clearAllHistory()
    }
}
