package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.formatDurationShort
import com.example.data.repository.MusicModelOption
import com.example.data.repository.VideoGenAspectRatio
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CaptionViolet
import com.example.ui.theme.CinemaCoral
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.PlayheadCyan
import com.example.ui.viewmodel.AiImageStudioState
import com.example.ui.viewmodel.AiMusicGenState
import com.example.ui.viewmodel.AiVideoGenState
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.StudioViewModel

enum class AiMediaStudioMode(val label: String, val badge: String) {
    TEXT_TO_VIDEO("Text → Video", "Omni 1.2"),
    IMAGE_TO_VIDEO("Animate Photo", "Omni 1.2"),
    IMAGE_CREATE_EDIT("Image Studio", "Nano 2.1"),
    MUSIC_GEN("AI Music", "Lyria 3")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCreativeHubSheet(
    state: EditorUiState,
    viewModel: StudioViewModel,
    initialMode: AiMediaStudioMode = AiMediaStudioMode.TEXT_TO_VIDEO,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("ai_creative_hub_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CaptionViolet)
                    Column {
                        Text(
                            text = "AI Media Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Generate videos, animate photos, edit images & compose music",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close AI Media Studio")
                }
            }

            AiGenerativeStudioPanel(
                state = state,
                viewModel = viewModel,
                initialMode = initialMode,
                onAddedToTimeline = onDismiss
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun AiGenerativeStudioPanel(
    state: EditorUiState,
    viewModel: StudioViewModel,
    initialMode: AiMediaStudioMode = AiMediaStudioMode.TEXT_TO_VIDEO,
    onAddedToTimeline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeMode by remember(initialMode) { mutableStateOf(initialMode) }
    var animateSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CaptionViolet.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .testTag("ai_generative_studio_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 4-Mode Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiMediaStudioMode.entries.forEach { mode ->
                    val selected = activeMode == mode
                    val (icon, accent) = when (mode) {
                        AiMediaStudioMode.TEXT_TO_VIDEO -> Icons.Filled.Videocam to CinemaCoral
                        AiMediaStudioMode.IMAGE_TO_VIDEO -> Icons.Filled.Movie to PlayheadCyan
                        AiMediaStudioMode.IMAGE_CREATE_EDIT -> Icons.Filled.AutoFixHigh to CaptionViolet
                        AiMediaStudioMode.MUSIC_GEN -> Icons.Filled.MusicNote to AudioEmerald
                    }
                    AiStudioModeChip(
                        label = mode.label,
                        badge = mode.badge,
                        icon = icon,
                        selected = selected,
                        accent = accent,
                        testTag = "ai_studio_mode_${mode.name.lowercase()}",
                        onClick = { activeMode = mode }
                    )
                }
            }

            when (activeMode) {
                AiMediaStudioMode.TEXT_TO_VIDEO -> {
                    TextToVideoStudioCard(
                        videoState = state.aiVideoGenState,
                        onGenerate = { prompt, ratio, style ->
                            viewModel.generateVideoFromText(prompt, ratio, style)
                        },
                        onAddToTimeline = { success ->
                            viewModel.addGeneratedVideoToTimeline(success)
                            onAddedToTimeline?.invoke()
                        },
                        onReset = { viewModel.resetAiVideoGenState() }
                    )
                }

                AiMediaStudioMode.IMAGE_TO_VIDEO -> {
                    AnimateImageToVideoCard(
                        sourceBitmap = animateSourceBitmap,
                        onPhotoUriPicked = { uri ->
                            viewModel.decodePhotoBitmap(uri) { bmp -> animateSourceBitmap = bmp }
                        },
                        onClearPhoto = { animateSourceBitmap = null },
                        videoState = state.aiVideoGenState,
                        onAnimate = { bmp, prompt, ratio ->
                            viewModel.animateBitmapToVideo(bmp, prompt, ratio)
                        },
                        onAddToTimeline = { success ->
                            viewModel.addGeneratedVideoToTimeline(success)
                            onAddedToTimeline?.invoke()
                        },
                        onReset = { viewModel.resetAiVideoGenState() }
                    )
                }

                AiMediaStudioMode.IMAGE_CREATE_EDIT -> {
                    CreateEditImageStudioCard(
                        editSourceBitmap = editSourceBitmap,
                        onPickEditPhoto = { uri ->
                            viewModel.decodePhotoBitmap(uri) { bmp -> editSourceBitmap = bmp }
                        },
                        onSetEditBitmap = { bmp -> editSourceBitmap = bmp },
                        imageState = state.aiImageStudioState,
                        onSubmit = { prompt, sourceBmp, ratioHint ->
                            viewModel.createOrEditAiImage(prompt, sourceBmp, ratioHint)
                        },
                        onSendToAnimate = { bmp ->
                            animateSourceBitmap = bmp
                            viewModel.resetAiVideoGenState()
                            activeMode = AiMediaStudioMode.IMAGE_TO_VIDEO
                        },
                        onAddToTimeline = { bmp, title ->
                            viewModel.addAiImageToTimeline(bmp, title)
                            onAddedToTimeline?.invoke()
                        },
                        onSaveToGallery = { bmp, title ->
                            viewModel.saveAiImageToGallery(bmp, title)
                        }
                    )
                }

                AiMediaStudioMode.MUSIC_GEN -> {
                    GenerateMusicStudioCard(
                        musicState = state.aiMusicGenState,
                        onGenerate = { prompt, option, instrumental ->
                            viewModel.generateAiMusic(prompt, option, instrumental)
                        },
                        onAddToTimeline = { success ->
                            viewModel.addGeneratedMusicToTimeline(success)
                            onAddedToTimeline?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiStudioModeChip(
    label: String,
    badge: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = badge,
                    style = MonoBadgeStyle,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun TextToVideoStudioCard(
    videoState: AiVideoGenState,
    onGenerate: (String, VideoGenAspectRatio, String) -> Unit,
    onAddToTimeline: (AiVideoGenState.Success) -> Unit,
    onReset: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf(VideoGenAspectRatio.LANDSCAPE_16_9) }
    var visualStyle by remember { mutableStateOf("Cinematic 35mm") }

    val quickPrompts = remember {
        listOf(
            "Drone sweeping over misty coastal cliffs at golden hour",
            "Neon cyberpunk rain street with reflections and slow camera push",
            "Macro slow-motion waves crashing against volcanic black sand"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Generate Video from Text • gemini-omni-1.2-flash",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CinemaCoral
        )

        VideoGenResultBanner(
            state = videoState,
            onAddToTimeline = onAddToTimeline,
            onReset = onReset
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(320) },
            label = { Text("Describe the video scene & movement") },
            placeholder = { Text("e.g. Aerial sunrise over emerald alpine valley, 35mm film") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("text_to_video_prompt_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickPrompts.forEach { idea ->
                FilterChip(
                    selected = prompt == idea,
                    onClick = { prompt = idea },
                    label = { Text(idea.take(28) + "...", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Aspect Ratio Selector: strictly 16:9 Landscape or 9:16 Portrait
        Text("Video Aspect Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoGenAspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = aspectRatio == ratio,
                    onClick = { aspectRatio = ratio },
                    label = { Text(ratio.label) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("video_ratio_${ratio.raw.replace(":", "_")}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Cinematic 35mm", "Cyberpunk Neon", "Documentary", "Anime Cinema", "Aerial Drone").forEach { style ->
                FilterChip(
                    selected = visualStyle == style,
                    onClick = { visualStyle = style },
                    label = { Text(style, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Button(
            onClick = { onGenerate(prompt, aspectRatio, visualStyle) },
            enabled = videoState !is AiVideoGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("generate_text_video_button")
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Generate ${aspectRatio.raw} Video", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AnimateImageToVideoCard(
    sourceBitmap: Bitmap?,
    onPhotoUriPicked: (Uri) -> Unit,
    onClearPhoto: () -> Unit,
    videoState: AiVideoGenState,
    onAnimate: (Bitmap, String, VideoGenAspectRatio) -> Unit,
    onAddToTimeline: (AiVideoGenState.Success) -> Unit,
    onReset: () -> Unit
) {
    var motionPrompt by remember { mutableStateOf("Smooth cinematic dolly push with natural atmospheric movement") }
    var aspectRatio by remember { mutableStateOf(VideoGenAspectRatio.LANDSCAPE_16_9) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) onPhotoUriPicked(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Animate Photo into Video • gemini-omni-1.2-flash",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PlayheadCyan
        )

        VideoGenResultBanner(
            state = videoState,
            onAddToTimeline = onAddToTimeline,
            onReset = onReset
        )

        // Photo Upload Area (Zero-Permission Android Photo Picker)
        if (sourceBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PlayheadCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = "Uploaded photo to animate",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xCC07090E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Text(
                            text = "Replace",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayheadCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        color = Color(0xCC07090E),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onClearPhoto)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("upload_photo_for_video_button")
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Photo to Animate", fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedTextField(
            value = motionPrompt,
            onValueChange = { motionPrompt = it.take(260) },
            label = { Text("Motion & camera direction") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("animate_photo_prompt_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Slow drone pullback revealing the landscape",
                "Gentle breeze, drifting clouds & golden sun rays",
                "Dramatic 3D parallax orbit around the subject"
            ).forEach { preset ->
                FilterChip(
                    selected = motionPrompt == preset,
                    onClick = { motionPrompt = preset },
                    label = { Text(preset.take(26) + "...", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Text("Output Video Aspect Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoGenAspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = aspectRatio == ratio,
                    onClick = { aspectRatio = ratio },
                    label = { Text(ratio.label) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("animate_ratio_${ratio.raw.replace(":", "_")}")
                )
            }
        }

        Button(
            onClick = {
                if (sourceBitmap != null) {
                    onAnimate(sourceBitmap, motionPrompt, aspectRatio)
                }
            },
            enabled = sourceBitmap != null && videoState !is AiVideoGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("animate_photo_generate_button")
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Animate Photo (${aspectRatio.raw})", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VideoGenResultBanner(
    state: AiVideoGenState,
    onAddToTimeline: (AiVideoGenState.Success) -> Unit,
    onReset: () -> Unit
) {
    when (state) {
        is AiVideoGenState.Loading -> {
            AiLoadingStatusCard(message = state.stage)
        }

        is AiVideoGenState.Error -> {
            AiErrorStatusCard(message = state.message)
        }

        is AiVideoGenState.Success -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = AudioEmerald.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AudioEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.title} (${state.aspectRatio.raw})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AudioEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatDurationShort(state.durationMs),
                            style = MonoBadgeStyle,
                            color = PlayheadCyan
                        )
                    }

                    val uriStr = state.videoUri.orEmpty()
                    val frameAspect = if (state.aspectRatio == VideoGenAspectRatio.PORTRAIT_9_16) 9f / 16f else 16f / 9f
                    if (uriStr.isNotBlank() && !uriStr.startsWith("ai_file://")) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoURI(Uri.parse(uriStr))
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .aspectRatio(frameAspect)
                                    .fillMaxSize()
                            )
                        }
                    } else if (state.previewBitmap != null) {
                        Image(
                            bitmap = state.previewBitmap.asImageBitmap(),
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAddToTimeline(state) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_generated_video_to_timeline_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add to Timeline", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("New")
                        }
                    }
                }
            }
        }

        AiVideoGenState.Idle -> Unit
    }
}

@Composable
private fun CreateEditImageStudioCard(
    editSourceBitmap: Bitmap?,
    onPickEditPhoto: (Uri) -> Unit,
    onSetEditBitmap: (Bitmap?) -> Unit,
    imageState: AiImageStudioState,
    onSubmit: (prompt: String, sourceBitmap: Bitmap?, aspectRatioHint: String) -> Unit,
    onSendToAnimate: (Bitmap) -> Unit,
    onAddToTimeline: (Bitmap, String) -> Unit,
    onSaveToGallery: (Bitmap, String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var aspectHint by remember { mutableStateOf("16:9") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) onPickEditPhoto(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Create & Edit Images • gemini-nano-banana-2.1",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CaptionViolet
        )

        // Mode switch: Create from scratch vs Edit uploaded/existing image
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = editSourceBitmap == null,
                onClick = { onSetEditBitmap(null) },
                label = { Text("Create New Image") },
                modifier = Modifier.weight(1f).testTag("image_mode_create_chip")
            )
            FilterChip(
                selected = editSourceBitmap != null,
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                label = { Text(if (editSourceBitmap != null) "Editing Photo ✓" else "Upload Photo to Edit") },
                modifier = Modifier.weight(1f).testTag("image_mode_edit_chip")
            )
        }

        if (editSourceBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CaptionViolet.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            ) {
                Image(
                    bitmap = editSourceBitmap.asImageBitmap(),
                    contentDescription = "Source image for AI editing",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color(0xCC07090E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "SOURCE IMAGE",
                        style = MonoBadgeStyle,
                        color = CaptionViolet,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                IconButton(
                    onClick = { onSetEditBitmap(null) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear source photo", tint = Color.White)
                }
            }
        }

        when (imageState) {
            is AiImageStudioState.Loading -> AiLoadingStatusCard(message = imageState.stage)
            is AiImageStudioState.Error -> AiErrorStatusCard(message = imageState.message)
            is AiImageStudioState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CaptionViolet.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CaptionViolet.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (imageState.isEdit) "Edited Image Ready" else "Generated Image Ready",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CaptionViolet
                        )
                        Image(
                            bitmap = imageState.bitmap.asImageBitmap(),
                            contentDescription = "AI created or edited image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(176.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    onAddToTimeline(imageState.bitmap, imageState.promptUsed.take(36))
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("add_ai_image_to_timeline_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Clip", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onSendToAnimate(imageState.bitmap) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("animate_generated_image_button")
                            ) {
                                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Animate", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onSetEditBitmap(imageState.bitmap) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refine Edit", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onSaveToGallery(imageState.bitmap, imageState.promptUsed.take(24)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Photo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            AiImageStudioState.Idle -> Unit
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(300) },
            label = {
                Text(
                    if (editSourceBitmap != null) "Describe how to edit this image"
                    else "Describe the image to create"
                )
            },
            placeholder = {
                Text(
                    if (editSourceBitmap != null) "e.g. Change sky to dramatic golden sunset with soft rim light"
                    else "e.g. Ultra-sharp studio shot of vintage cinema camera on dark slate"
                )
            },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_studio_prompt_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("16:9", "9:16", "1:1").forEach { ratio ->
                FilterChip(
                    selected = aspectHint == ratio,
                    onClick = { aspectHint = ratio },
                    label = { Text(ratio) }
                )
            }
        }

        Button(
            onClick = { onSubmit(prompt, editSourceBitmap, aspectHint) },
            enabled = imageState !is AiImageStudioState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("image_studio_submit_button")
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (editSourceBitmap != null) "Apply AI Image Edit" else "Generate AI Image",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GenerateMusicStudioCard(
    musicState: AiMusicGenState,
    onGenerate: (prompt: String, option: MusicModelOption, instrumentalOnly: Boolean) -> Unit,
    onAddToTimeline: (AiMusicGenState.Success) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf(MusicModelOption.SHORT_CLIP) }
    var instrumentalOnly by remember { mutableStateOf(true) }

    val quickGenres = remember {
        listOf(
            "Atmospheric synthwave sunset drive, warm analog pads, 108 BPM",
            "Lo-fi chill hip-hop beats with soft Rhodes piano and vinyl crackle",
            "Epic cinematic orchestral strings building to a triumphant brass climax",
            "Upbeat energetic indie electronic soundtrack for travel montage"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Generate AI Music • ${selectedOption.modelId}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AudioEmerald
        )

        when (musicState) {
            is AiMusicGenState.Loading -> AiLoadingStatusCard(message = musicState.stage)
            is AiMusicGenState.Error -> AiErrorStatusCard(message = musicState.message)
            is AiMusicGenState.Success -> {
                GeneratedMusicPlayerCard(
                    result = musicState,
                    onAddToTimeline = { onAddToTimeline(musicState) }
                )
            }

            AiMusicGenState.Idle -> Unit
        }

        // Model Tier Selector: lyria-3-clip-preview (<=30s) vs lyria-3-pro-preview (full track)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicModelOption.entries.forEach { option ->
                val isSelected = selectedOption == option
                Surface(
                    color = if (isSelected) AudioEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AudioEmerald else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedOption = option }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("music_model_${option.name.lowercase()}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) AudioEmerald else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(300) },
            label = { Text("Describe genre, mood, tempo & instruments") },
            placeholder = { Text("e.g. Dreamy synthwave with pulsing bassline, 114 BPM") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("music_prompt_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickGenres.forEach { idea ->
                FilterChip(
                    selected = prompt == idea,
                    onClick = { prompt = idea },
                    label = { Text(idea.take(26) + "...", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Instrumental Only (No Vocals)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = instrumentalOnly,
                onCheckedChange = { instrumentalOnly = it },
                modifier = Modifier.testTag("music_instrumental_switch")
            )
        }

        Button(
            onClick = { onGenerate(prompt, selectedOption, instrumentalOnly) },
            enabled = musicState !is AiMusicGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("generate_music_button")
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Compose ${selectedOption.label}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GeneratedMusicPlayerCard(
    result: AiMusicGenState.Success,
    onAddToTimeline: () -> Unit
) {
    var isPlayingPreview by remember(result.localAudioPath) { mutableStateOf(false) }
    var mediaPlayer by remember(result.localAudioPath) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(result.localAudioPath) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AudioEmerald.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AudioEmerald.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AudioEmerald)
                        .clickable {
                            if (isPlayingPreview) {
                                runCatching { mediaPlayer?.pause() }
                                isPlayingPreview = false
                            } else {
                                val mp = mediaPlayer ?: MediaPlayer().apply {
                                    setDataSource(result.localAudioPath)
                                    prepare()
                                    setOnCompletionListener { isPlayingPreview = false }
                                }.also { mediaPlayer = it }
                                runCatching { mp.start() }
                                isPlayingPreview = true
                            }
                        }
                        .testTag("play_generated_music_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlayingPreview) "Pause generated music" else "Play generated music",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDurationShort(result.durationMs)} • ${result.modelUsed}",
                        style = MonoBadgeStyle,
                        color = AudioEmerald
                    )
                }
            }

            if (result.lyricsOrNotes.isNotBlank()) {
                Text(
                    text = result.lyricsOrNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onAddToTimeline,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_generated_music_to_timeline_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Music to Timeline Mixer", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiLoadingStatusCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AiErrorStatusCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
