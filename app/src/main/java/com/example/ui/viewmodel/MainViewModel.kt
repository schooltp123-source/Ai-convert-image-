package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.db.ConversionEntity
import com.example.data.model.*
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface ImageToPromptState {
    object Idle : ImageToPromptState
    object Loading : ImageToPromptState
    data class Success(val description: String) : ImageToPromptState
    data class Error(val message: String) : ImageToPromptState
}

sealed interface PromptToImageState {
    object Idle : PromptToImageState
    object Loading : PromptToImageState
    data class Success(val base64Image: String) : PromptToImageState
    data class Error(val message: String) : PromptToImageState
}

class MainViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _imageToPromptState = MutableStateFlow<ImageToPromptState>(ImageToPromptState.Idle)
    val imageToPromptState: StateFlow<ImageToPromptState> = _imageToPromptState

    private val _promptToImageState = MutableStateFlow<PromptToImageState>(PromptToImageState.Idle)
    val promptToImageState: StateFlow<PromptToImageState> = _promptToImageState

    val historyList: StateFlow<List<ConversionEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun resetImageToPrompt() {
        _imageToPromptState.value = ImageToPromptState.Idle
    }

    fun resetPromptToImage() {
        _promptToImageState.value = PromptToImageState.Idle
    }

    // Convert Image to Prompt (Image Description using gemini-3.5-flash)
    fun convertImageToPrompt(bitmap: Bitmap, customPrompt: String? = null) {
        viewModelScope.launch {
            _imageToPromptState.value = ImageToPromptState.Loading
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _imageToPromptState.value = ImageToPromptState.Error("រកមិនឃើញ API Key របស់ Gemini ទេ! សូមកំណត់វានៅក្នុង Secrets panel ក្នុង AI Studio។")
                    return@launch
                }

                val base64ForApi = withContext(Dispatchers.Default) {
                    val scaled = scaleBitmapDown(bitmap, 1024)
                    scaled.toBase64()
                }

                val systemOrUserPrompt = customPrompt ?: "សូមវិភាគរូបភាពនេះឱ្យបានលម្អិត រួចបង្កើតជាសមាសភាគបម្លែងពីរ៖ (១) ការពណ៌នាជារូបរាងទូទៅជាភាសាខ្មែរ (២) អក្សរណែនាំជាភាសាខ្មែរ និងអង់គ្លេសច្បាស់លាស់សម្រាប់សរសេរជាសំណួរដើម្បីបង្កើតរូបភាពស្រដៀងគ្នានេះ។"

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = systemOrUserPrompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64ForApi))
                            )
                        )
                    )
                )

                val response = RetrofitClient.service.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )

                val description = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "មិនអាចទាញយកការពណ៌នាពីរូបភាពនេះបានទេ។"

                _imageToPromptState.value = ImageToPromptState.Success(description)

                // Save to history with a compressed thumbnail
                withContext(Dispatchers.Default) {
                    val dbThumbnail = scaleBitmapDown(bitmap, 360).toBase64()
                    val entity = ConversionEntity(
                        type = "IMAGE_TO_PROMPT",
                        inputText = "វិភាគរូបភាព",
                        inputImageBase64 = dbThumbnail,
                        outputText = description,
                        outputImageBase64 = null
                    )
                    repository.insertHistory(entity)
                }

            } catch (e: Exception) {
                _imageToPromptState.value = ImageToPromptState.Error(e.localizedMessage ?: "មានបញ្ហាពេលទាក់ទងទៅម៉ាស៊ីនបម្រើ Gemini")
            }
        }
    }

    // Convert Prompt to Image (Image Generation using gemini-2.5-flash-image)
    fun generateImageFromPrompt(prompt: String, aspectRatio: String = "1:1", imageSize: String = "1K") {
        viewModelScope.launch {
            _promptToImageState.value = PromptToImageState.Loading
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _promptToImageState.value = PromptToImageState.Error("រកមិនឃើញ API Key របស់ Gemini ទេ! សូមកំណត់វានៅក្នុង Secrets panel ក្នុង AI Studio។")
                    return@launch
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = aspectRatio, imageSize = imageSize),
                        responseModalities = listOf("TEXT", "IMAGE"),
                        temperature = 1.0f
                    )
                )

                val response = RetrofitClient.service.generateContent(
                    model = "gemini-2.5-flash-image",
                    apiKey = apiKey,
                    request = request
                )

                val imagePart = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inlineData != null }
                val base64Data = imagePart?.inlineData?.data

                if (base64Data != null) {
                    _promptToImageState.value = PromptToImageState.Success(base64Data)

                    // Compress the generated image to thumbnail size for lightweight storage
                    withContext(Dispatchers.Default) {
                        try {
                            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            val dbThumbnail = scaleBitmapDown(decodedBitmap, 360).toBase64()

                            val entity = ConversionEntity(
                                type = "PROMPT_TO_IMAGE",
                                inputText = prompt,
                                inputImageBase64 = null,
                                outputText = "រូបភាពទំហំ $aspectRatio ($imageSize)",
                                outputImageBase64 = dbThumbnail // save thumbnail representation
                            )
                            repository.insertHistory(entity)
                        } catch (e: Exception) {
                            // If base64 decoder fails we store the original but truncated or bypass
                            val entity = ConversionEntity(
                                type = "PROMPT_TO_IMAGE",
                                inputText = prompt,
                                inputImageBase64 = null,
                                outputText = "រូបភាពទំហំ $aspectRatio ($imageSize)",
                                outputImageBase64 = if (base64Data.length < 200000) base64Data else null
                            )
                            repository.insertHistory(entity)
                        }
                    }
                } else {
                    _promptToImageState.value = PromptToImageState.Error("មិនបានទទួលរូបភាពពីរថយន្តបញ្ញាញាណសិប្បនិម្មិតទេ។")
                }

            } catch (e: Exception) {
                _promptToImageState.value = PromptToImageState.Error(e.localizedMessage ?: "មានបញ្ហាពេលទាក់ទងទៅម៉ាស៊ីនបម្រើ Gemini")
            }
        }
    }

    fun deleteHistoryItem(item: ConversionEntity) {
        viewModelScope.launch {
            repository.deleteHistoryById(item.id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // Helper utilities for bitmap and base64 handling
    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val newWidth: Int
        val newHeight: Int
        if (srcWidth > srcHeight) {
            if (srcWidth <= maxDimension) return bitmap
            newWidth = maxDimension
            newHeight = (srcHeight * (maxDimension.toFloat() / srcWidth)).toInt()
        } else {
            if (srcHeight <= maxDimension) return bitmap
            newHeight = maxDimension
            newWidth = (srcWidth * (maxDimension.toFloat() / srcHeight)).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}

class MainViewModelFactory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
