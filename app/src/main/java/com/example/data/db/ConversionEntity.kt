package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "IMAGE_TO_PROMPT" or "PROMPT_TO_IMAGE"
    val inputText: String?,
    val inputImageBase64: String?,
    val outputText: String?,
    val outputImageBase64: String?,
    val timestamp: Long = System.currentTimeMillis()
)
