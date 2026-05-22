package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.ConversionEntity
import com.example.ui.viewmodel.ImageToPromptState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PromptToImageState
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    // Collect states
    val imageToPromptState by viewModel.imageToPromptState.collectAsStateWithLifecycle()
    val promptToImageState by viewModel.promptToImageState.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "បច្ចេកវិទ្យា Gemini AI គាំទ្រភាសាខ្មែរ", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_image_to_prompt), fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("nav_image_to_prompt")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Create, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_prompt_to_image), fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("nav_prompt_to_image")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history), fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("nav_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ImageToPromptView(
                    state = imageToPromptState,
                    onConvert = { bitmap, prompt -> viewModel.convertImageToPrompt(bitmap, prompt) },
                    onReset = { viewModel.resetImageToPrompt() }
                )
                1 -> PromptToImageView(
                    state = promptToImageState,
                    onGenerate = { prompt, ratio, size -> viewModel.generateImageFromPrompt(prompt, ratio, size) },
                    onReset = { viewModel.resetPromptToImage() }
                )
                2 -> HistoryView(
                    historyList = historyList,
                    onDelete = { viewModel.deleteHistoryItem(it) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
            }
        }
    }
}

// ==================== IMAGE TO PROMPT VIEW ====================

@Composable
fun ImageToPromptView(
    state: ImageToPromptState,
    onConvert: (Bitmap, String?) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var customPrompt by remember { mutableStateOf("") }
    
    val contentResolver = context.contentResolver
    val loadedBitmap = remember(imageUri) {
        imageUri?.let { uri ->
            try {
                if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            onReset()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .dashedBorder(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            strokeWidth = 2.dp,
                            dashLength = 10.dp,
                            gapLength = 8.dp,
                            cornerRadius = 24.dp
                        )
                        .clickable {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (loadedBitmap != null) {
                        Image(
                            bitmap = loadedBitmap.asImageBitmap(),
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                        IconButton(
                            onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    RoundedCornerShape(28.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ដាក់រូបភាពរបស់អ្នក",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ជ្រើសរើសរូបភាពដើម្បីបម្លែងទៅជាការណែនាំ (Prompt)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                label = { Text("ការណែនាំបន្ថែម (ស្រេចចិត្ត)") },
                placeholder = { Text("ឧ. វិភាគពណ៌ លក្ខណៈសិល្បៈលម្អិត...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_instructions_field"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        item {
            Button(
                onClick = {
                    if (loadedBitmap != null) {
                        val promptArg = if (customPrompt.trim().isNotEmpty()) customPrompt.trim() else null
                        onConvert(loadedBitmap, promptArg)
                    } else {
                        Toast.makeText(context, context.getString(R.string.error_no_image), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_analyze_image"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(
                        text = stringResource(R.string.btn_analyze),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        item {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "AnalyzeResultAnimation"
            ) { targetState ->
                when (targetState) {
                    is ImageToPromptState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ជ្រើសរើសរូបភាព រួចចុច 'វិភាគរូបភាព'",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp
                            )
                        }
                    }
                    is ImageToPromptState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = stringResource(R.string.label_analyzing),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is ImageToPromptState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "លទ្ធផលបង្កើតសំណួរគំរូ",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 15.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(targetState.description))
                                                Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Copy",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.btn_copy),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                shareText(context, targetState.description)
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share text",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    text = targetState.description,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("analyzed_prompt_text_result")
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "លក្ខណៈរចនាប័ទ្មពេញនិយម (ចុចដើម្បីបន្ថែម)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                val tagSuggestions = listOf("Cinematic", "4K_Resolution", "Oil_Painting", "Hyper_Realistic", "3D_Render", "Anime_Style", "Cyberpunk", "Vintage")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    tagSuggestions.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    if (!customPrompt.contains("#$tag")) {
                                                        customPrompt = if (customPrompt.trim().isEmpty()) {
                                                            "#$tag"
                                                        } else {
                                                            "$customPrompt #$tag"
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ImageToPromptState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = targetState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== PROMPT TO IMAGE VIEW ====================

@Composable
fun PromptToImageView(
    state: PromptToImageState,
    onGenerate: (String, String, String) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    
    // Config states
    var selectedRatio by remember { mutableStateOf("1:1") }
    var selectedSize by remember { mutableStateOf("1K") }

    val ratios = listOf("1:1", "16:9", "4:3", "3:2")
    val sizes = listOf("512px", "1K")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.label_enter_prompt)) },
                placeholder = { Text(stringResource(R.string.placeholder_prompt)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("prompt_input_field"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                maxLines = 4
            )
        }

        // Configuration cards
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ratio selection
                    Text(
                        text = stringResource(R.string.label_aspect_ratio),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ratios.forEach { ratio ->
                            FilterChip(
                                selected = selectedRatio == ratio,
                                onClick = { selectedRatio = ratio },
                                label = { Text(ratio) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Size selection
                    Text(
                        text = stringResource(R.string.label_image_resolution),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizes.forEach { size ->
                            FilterChip(
                                selected = selectedSize == size,
                                onClick = { selectedSize = size },
                                label = { Text(size) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (prompt.trim().isNotEmpty()) {
                        onGenerate(prompt, selectedRatio, selectedSize)
                    } else {
                        Toast.makeText(context, context.getString(R.string.error_no_prompt), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_generate_image"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(
                        text = stringResource(R.string.btn_generate),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        item {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "GenerationResultAnimation"
            ) { targetState ->
                when (targetState) {
                    is PromptToImageState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .dashedBorder(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 2.dp,
                                    dashLength = 10.dp,
                                    gapLength = 8.dp,
                                    cornerRadius = 24.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "លទ្ធផលដែលទទួលបាន",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "បញ្ចូលសំណួរបង្កើតរូបភាព រួចចុច 'បង្កើតរូបភាព'",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is PromptToImageState.Loading -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.label_generating),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is PromptToImageState.Success -> {
                        val decodedBytes = remember(targetState.base64Image) {
                            try {
                                Base64.decode(targetState.base64Image, Base64.DEFAULT)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val bitmap = remember(decodedBytes) {
                            decodedBytes?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }

                        if (bitmap != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Generated result",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(
                                                when (selectedRatio) {
                                                    "16:9" -> 1.77f
                                                    "4:3" -> 1.33f
                                                    "3:2" -> 1.5f
                                                    else -> 1.0f
                                                }
                                            )
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val success = saveImageToGallery(context, bitmap, prompt.take(20).replace(" ", "_"))
                                                if (success) {
                                                    Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "មិនអាចរក្សាទុករូបភាពបានទេ", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Download")
                                                Text("រក្សាទុក", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                shareImage(context, bitmap, promptText = prompt)
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Share")
                                                Text("ចែករំលែក", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "កំហុសក្នុងការគូររូបភាពឡើងវិញ",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is PromptToImageState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = targetState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== HISTORY VIEW ====================

@Composable
fun HistoryView(
    historyList: List<ConversionEntity>,
    onDelete: (ConversionEntity) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var selectedItemForDetail by remember { mutableStateOf<ConversionEntity?>(null) }

    if (historyList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "មិនទាន់មានប្រវត្តិនៃការបម្លែងនៅឡើយទេ",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "រាល់ការបម្លែងនឹងត្រូវបានរក្សាទុកនៅទីនេះដោយស្វ័យប្រវត្តិ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ប្រវត្តិនៃការប្រើប្រាស់ (${historyList.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(
                    onClick = { onClearAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("លុបទាំងអស់")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { selectedItemForDetail = item },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedItemForDetail?.let { item ->
        DetailDialog(
            item = item,
            onDismiss = { selectedItemForDetail = null }
        )
    }
}

@Composable
fun HistoryItemCard(
    item: ConversionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isImageToPrompt = item.type == "IMAGE_TO_PROMPT"
    val dateString = remember(item.timestamp) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val imageBase64 = if (isImageToPrompt) item.inputImageBase64 else item.outputImageBase64
    val previewBitmap = remember(imageBase64) {
        if (imageBase64.isNullOrEmpty()) null
        else {
            try {
                val decoded = Base64.decode(imageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail preview slot
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isImageToPrompt) Icons.Default.Add else Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Text info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (isImageToPrompt) "រូបភាព -> សំណួរ" else "សំណួរ -> រូបភាព",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isImageToPrompt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = if (isImageToPrompt) item.outputText ?: "" else item.inputText ?: "",
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Trash button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete from history",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun DetailDialog(
    item: ConversionEntity,
    onDismiss: () -> Unit
) {
    val isImageToPrompt = item.type == "IMAGE_TO_PROMPT"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val imageBase64 = if (isImageToPrompt) item.inputImageBase64 else item.outputImageBase64
    val bitmap = remember(imageBase64) {
        if (imageBase64.isNullOrEmpty()) null
        else {
            try {
                val decoded = Base64.decode(imageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isImageToPrompt) "ព័ត៌មានលម្អិត (បម្លែងរូបភាព)" else "ព័ត៌មានលម្អិត (បង្កើតរូបភាព)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bitmap != null) {
                        item {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Visual Asset",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    item {
                        Text(
                            text = if (isImageToPrompt) "អក្សរវិភាគ/សំណួរណែនាំ៖" else "សំណួរដែលបានបញ្ចូល៖",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isImageToPrompt) item.outputText ?: "" else item.inputText ?: "",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val textToCopy = if (isImageToPrompt) item.outputText else item.inputText
                            if (!textToCopy.isNullOrEmpty()) {
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ចម្លងអត្ថបទ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (bitmap != null && !isImageToPrompt) {
                        Button(
                            onClick = {
                                val success = saveImageToGallery(context, bitmap, "Stored_Result")
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "មិនអាចរក្សាទុករូបភាពបានទេ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("រក្សាទុករូប", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==================== GLOBAL HELPERS ====================

fun shareText(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "ចែករំលែកអត្ថបទ")
    context.startActivity(shareIntent)
}

fun shareImage(context: Context, bitmap: Bitmap, promptText: String) {
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream("$cachePath/image.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val imagePath = java.io.File(context.cacheDir, "images/image.png")
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imagePath
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, context.contentResolver.getType(contentUri))
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "រូបភាពនេះត្រូវបានបង្កើតដោយប្រើកម្មវិធីបម្លែង AI ជាមួយសំណួរ៖ \"$promptText\"")
        }
        context.startActivity(Intent.createChooser(shareIntent, "ចែករំលែករូបភាព"))
    } catch (e: Exception) {
        Toast.makeText(context, "មានកំហុសពេលចែករំលែករូបភាព៖ ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun saveImageToGallery(context: Context, bitmap: Bitmap, name: String): Boolean {
    val relativeLocation = Environment.DIRECTORY_PICTURES + "/ImagePromptAI"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

    return try {
        resolver.openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                return false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

// Custom extension to draw a beautiful dashed border matching the style guidelines
fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    dashLength: androidx.compose.ui.unit.Dp = 8.dp,
    gapLength: androidx.compose.ui.unit.Dp = 6.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp
) = this.drawBehind {
    val density = this
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val dashLengthPx = with(density) { dashLength.toPx() }
    val gapLengthPx = with(density) { gapLength.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx), 0f)
    val width = size.width
    val height = size.height

    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = strokeWidthPx / 2,
                top = strokeWidthPx / 2,
                right = width - strokeWidthPx / 2,
                bottom = height - strokeWidthPx / 2,
                radiusX = cornerRadiusPx,
                radiusY = cornerRadiusPx
            )
        )
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = pathEffect
        )
    )
}

