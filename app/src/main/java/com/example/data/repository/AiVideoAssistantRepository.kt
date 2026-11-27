package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiColorAudioSuggestion
import com.example.data.model.AiCutSuggestion
import com.example.data.model.AiQuickCutPlan
import com.example.data.model.CaptionCue
import com.example.data.model.ColorPreset
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FileDataPart
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "AiVideoAssistant"
private const val AI_TIMEOUT_MS = 60_000L
private const val MEDIA_GEN_TIMEOUT_MS = 120_000L
private const val MODEL_NAME = "gemini-3.8-flash"
private const val IMAGE_MODEL_NAME = "gemini-nano-banana-2.1"
private const val VIDEO_MODEL_NAME = "gemini-omni-1.2-flash"
private const val MUSIC_CLIP_MODEL_NAME = "lyria-3-clip-preview"
private const val MUSIC_PRO_MODEL_NAME = "lyria-3-pro-preview"

enum class VideoGenAspectRatio(val raw: String, val label: String) {
    LANDSCAPE_16_9("16:9", "16:9 Landscape"),
    PORTRAIT_9_16("9:16", "9:16 Portrait")
}

enum class MusicModelOption(
    val modelId: String,
    val label: String,
    val subtitle: String
) {
    SHORT_CLIP(
        modelId = MUSIC_CLIP_MODEL_NAME,
        label = "Short Clip (≤30s)",
        subtitle = "Up to 30s loop • lyria-3-clip-preview"
    ),
    FULL_TRACK(
        modelId = MUSIC_PRO_MODEL_NAME,
        label = "Full Track",
        subtitle = "Full-length song • lyria-3-pro-preview"
    )
}

data class AiGeneratedClipResult(
    val title: String,
    val visualPromptUsed: String,
    val suggestedTransition: TransitionType,
    val suggestedCaption: String,
    val emphasisWord: String,
    val keyframeBitmap: Bitmap
)

data class AiGeneratedVideoResult(
    val title: String,
    val aspectRatio: VideoGenAspectRatio,
    val videoBytes: ByteArray?,
    val mimeType: String,
    val remoteFileUri: String?,
    val previewBitmap: Bitmap?,
    val notes: String,
    val modelUsed: String
)

data class AiGeneratedImageResult(
    val bitmap: Bitmap,
    val responseText: String,
    val isEdit: Boolean
)

data class AiGeneratedMusicResult(
    val title: String,
    val audioBytes: ByteArray,
    val mimeType: String,
    val lyricsOrNotes: String,
    val modelUsed: String
)

class AiVideoAssistantRepository {

    private val quickCutSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Concise editorial summary of the quick-cut plan (under 240 chars)"),
            "pacingStyle" to Schema.string(description = "Pacing label such as Viral Fast-Cut, Cinematic Flow, or Highlight Reel"),
            "estimatedTimeSavedMs" to Schema.integer(description = "Total milliseconds trimmed from dead air or slow tails"),
            "cuts" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "clipIndex" to Schema.integer(description = "0-based index of the source clip in the timeline"),
                        "trimStartMs" to Schema.integer(description = "Trim in-point in milliseconds within the clip"),
                        "trimEndMs" to Schema.integer(description = "Trim out-point in milliseconds within the clip"),
                        "speed" to Schema.double(description = "Playback speed multiplier between 0.5 and 2.5"),
                        "transition" to Schema.enumeration(
                            values = TransitionType.entries.map { it.raw },
                            description = "Recommended outgoing transition"
                        ),
                        "reason" to Schema.string(description = "Brief editorial rationale for this cut")
                    )
                ),
                description = "Ordered list of optimized clip cuts"
            )
        )
    )

    private val captionSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Short note describing the generated captions"),
            "captions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Caption start timestamp in milliseconds"),
                        "endMs" to Schema.integer(description = "Caption end timestamp in milliseconds"),
                        "text" to Schema.string(description = "Crisp, natural subtitle line (max 70 characters)"),
                        "emphasisWord" to Schema.string(description = "Single key word in the caption to highlight"),
                        "speaker" to Schema.string(description = "Speaker label such as Narrator or Host")
                    )
                ),
                description = "Sequential timed captions covering the timeline"
            )
        )
    )

    private val colorAudioSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Brief description of the color grade and audio balance"),
            "colorPreset" to Schema.enumeration(
                values = ColorPreset.entries.map { it.raw },
                description = "Closest cinema LUT preset"
            ),
            "exposure" to Schema.double(description = "Exposure shift between -1.0 and 1.0"),
            "contrast" to Schema.double(description = "Contrast multiplier between 0.7 and 1.6"),
            "saturation" to Schema.double(description = "Saturation multiplier between 0.0 and 1.6"),
            "temperature" to Schema.double(description = "Warm/cool temperature between -0.6 and 0.6"),
            "tint" to Schema.double(description = "Green/magenta tint between -0.4 and 0.4"),
            "vignette" to Schema.double(description = "Vignette amount between 0.0 and 0.7"),
            "voiceoverVolume" to Schema.double(description = "Voiceover track volume between 0.5 and 1.5"),
            "musicVolume" to Schema.double(description = "Music bed track volume between 0.15 and 0.9"),
            "originalVolume" to Schema.double(description = "Original camera ambience volume between 0.2 and 1.1")
        )
    )

    private val quickCutModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickCutSchema
            }
        )
    }

    private val captionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionSchema
            }
        )
    }

    private val aiClipConceptSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short punchy cinema clip title (2 to 4 words)"),
            "imagePrompt" to Schema.string(description = "Rich photographic cinema frame prompt for image generation"),
            "transition" to Schema.enumeration(
                values = TransitionType.entries.map { it.raw },
                description = "Recommended outgoing transition for this scene"
            ),
            "caption" to Schema.string(description = "Optional concise narrative subtitle line for this clip"),
            "emphasisWord" to Schema.string(description = "Single high-impact keyword from the caption")
        )
    )

    private val colorAudioModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = colorAudioSchema
            }
        )
    }

    private val aiClipConceptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = aiClipConceptSchema
            }
        )
    }

    private val aiClipImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val videoGenerationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = VIDEO_MODEL_NAME
        )
    }

    private val musicClipModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MUSIC_CLIP_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    private val musicProModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MUSIC_PRO_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    suspend fun generateQuickCuts(
        project: VideoProject,
        pacingStyle: String,
        editorBrief: String,
        keyframeBitmaps: List<Bitmap> = emptyList()
    ): Result<AiQuickCutPlan> {
        return try {
            val clips = project.typedClips()
            val clipDescriptions = clips.mapIndexed { idx, clip ->
                "Clip $idx: '${clip.label}', duration=${clip.endMs - clip.startMs}ms, trim=[${clip.trimStartMs}..${clip.trimEndMs}]ms, speed=${clip.speed}x, scene=${clip.sampleScene}"
            }.joinToString("\n")

            val prompt = buildString {
                appendLine("You are an expert mobile video editor creating a tight quick-cut edit plan.")
                appendLine("Project: '${project.title}' (${project.aspectRatio}, ${project.resolution} ${project.frameRate}fps)")
                appendLine("Target Pacing Style: $pacingStyle")
                if (editorBrief.isNotBlank()) {
                    appendLine("Editor Notes: $editorBrief")
                }
                appendLine("Current Timeline Clips:")
                appendLine(clipDescriptions.ifBlank { "Clip 0: 'Main Footage', trim=[0..12000]ms, speed=1.0x" })
                appendLine("Produce tighter trimStartMs/trimEndMs (minimum 800ms per cut, within each clip's available range), dynamic speed adjustments, and transitions that remove dead air and boost retention.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (keyframeBitmaps.isNotEmpty()) {
                    quickCutModel.generateContent(
                        content {
                            keyframeBitmaps.take(4).forEach { image(it) }
                            text(prompt)
                        }
                    )
                } else {
                    quickCutModel.generateContent(prompt)
                }
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty quick-cut response."))
            val json = JSONObject(rawJson)
            val cutsArr = json.optJSONArray("cuts")
            val cuts = buildList {
                if (cutsArr != null) {
                    for (i in 0 until cutsArr.length()) {
                        val item = cutsArr.getJSONObject(i)
                        val clipIdx = item.optInt("clipIndex", i).coerceIn(0, (clips.size - 1).coerceAtLeast(0))
                        val trimStart = item.optLong("trimStartMs", 0L).coerceAtLeast(0L)
                        val trimEnd = item.optLong("trimEndMs", trimStart + 2500L).coerceAtLeast(trimStart + 600L)
                        add(
                            AiCutSuggestion(
                                clipIndex = clipIdx,
                                trimStartMs = trimStart,
                                trimEndMs = trimEnd,
                                speed = item.optDouble("speed", 1.0).coerceIn(0.5, 2.5),
                                transition = TransitionType.fromRaw(item.optString("transition")),
                                reason = item.optString("reason", "Tightened pacing cut")
                            )
                        )
                    }
                }
            }

            Result.success(
                AiQuickCutPlan(
                    summary = json.optString("summary", "AI Quick-Cut pacing applied.").take(500),
                    pacingStyle = json.optString("pacingStyle", pacingStyle),
                    estimatedTimeSavedMs = json.optLong("estimatedTimeSavedMs", 0L).coerceAtLeast(0L),
                    cuts = cuts
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quick-cut generation timed out", e)
            Result.failure(Exception("AI Quick-Cut timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quick-cut generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Quick-Cut failed."))
        }
    }

    suspend fun generateAutomatedCaptions(
        project: VideoProject,
        language: String,
        scriptOrContext: String,
        keyframeBitmaps: List<Bitmap> = emptyList(),
        inlineAudioOrVideoBytes: ByteArray? = null,
        inlineMimeType: String? = null
    ): Result<Pair<String, List<CaptionCue>>> {
        return try {
            val totalDuration = project.totalDurationMs.coerceAtLeast(6000L)
            val clipsSummary = project.typedClips().joinToString("; ") { clip ->
                "${clip.label} (${clip.startMs}ms-${clip.endMs}ms)"
            }

            val prompt = buildString {
                appendLine("Generate accurate, punchy, time-synced video captions in $language.")
                appendLine("Project Title: '${project.title}', Total Duration: ${totalDuration}ms, Aspect Ratio: ${project.aspectRatio}.")
                appendLine("Timeline Scenes: $clipsSummary")
                if (scriptOrContext.isNotBlank()) {
                    appendLine("Voiceover / Scene Context: $scriptOrContext")
                }
                if (inlineAudioOrVideoBytes != null) {
                    appendLine("Transcribe spoken audio from the attached media accurately with exact timestamps.")
                } else {
                    appendLine("Analyze the visual frames and timeline structure to craft engaging synchronized captions spanning 0ms to ${totalDuration}ms without overlapping timestamps.")
                }
                appendLine("Keep each caption concise (3 to 10 words) with one high-impact emphasisWord.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                captionModel.generateContent(
                    content {
                        if (inlineAudioOrVideoBytes != null && !inlineMimeType.isNullOrBlank()) {
                            inlineData(inlineAudioOrVideoBytes, inlineMimeType)
                        } else {
                            keyframeBitmaps.take(4).forEach { image(it) }
                        }
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned empty captions."))
            val json = JSONObject(rawJson)
            val summary = json.optString("summary", "AI captions generated in $language.")
            val arr = json.optJSONArray("captions")
            val cues = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val start = obj.optLong("startMs", i * 2500L).coerceIn(0L, totalDuration - 300L)
                        val end = obj.optLong("endMs", start + 2200L).coerceIn(start + 300L, totalDuration)
                        val text = obj.optString("text", "").trim()
                        if (text.isNotBlank()) {
                            add(
                                CaptionCue(
                                    captionId = "cap_${UUID.randomUUID().toString().take(8)}",
                                    startMs = start,
                                    endMs = end,
                                    text = text.take(180),
                                    emphasisWord = obj.optString("emphasisWord", "").take(40),
                                    speaker = obj.optString("speaker", "Narrator").take(40)
                                )
                            )
                        }
                    }
                }
            }.sortedBy { it.startMs }

            if (cues.isEmpty()) {
                Result.failure(IllegalStateException("No captions could be parsed from AI response."))
            } else {
                Result.success(summary to cues)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Caption generation timed out", e)
            Result.failure(Exception("AI Captioning timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Caption generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Captioning failed."))
        }
    }

    suspend fun suggestColorAndAudioMix(
        project: VideoProject,
        moodPrompt: String,
        sampleBitmap: Bitmap? = null
    ): Result<AiColorAudioSuggestion> {
        return try {
            val prompt = buildString {
                appendLine("Recommend a cinematic real-time color grade and balanced multi-track audio mix for this mobile video edit.")
                appendLine("Project: '${project.title}' (${project.resolution} ${project.frameRate}fps)")
                appendLine("Desired Mood / Look: ${moodPrompt.ifBlank { "Cinematic, crisp contrast, clear voiceover over balanced music bed" }}")
                appendLine("Return realistic LUT preset, grading sliders, and voiceover/music/original audio track levels.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (sampleBitmap != null) {
                    colorAudioModel.generateContent(
                        content {
                            image(sampleBitmap)
                            text(prompt)
                        }
                    )
                } else {
                    colorAudioModel.generateContent(prompt)
                }
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty color/mix suggestion."))
            val json = JSONObject(rawJson)
            Result.success(
                AiColorAudioSuggestion(
                    summary = json.optString("summary", "Applied AI cinema look & audio ducking.").take(400),
                    colorPreset = ColorPreset.fromRaw(json.optString("colorPreset")),
                    exposure = json.optDouble("exposure", 0.0).coerceIn(-2.0, 2.0),
                    contrast = json.optDouble("contrast", 1.1).coerceIn(0.5, 2.0),
                    saturation = json.optDouble("saturation", 1.05).coerceIn(0.0, 2.0),
                    temperature = json.optDouble("temperature", 0.0).coerceIn(-1.0, 1.0),
                    tint = json.optDouble("tint", 0.0).coerceIn(-1.0, 1.0),
                    vignette = json.optDouble("vignette", 0.2).coerceIn(0.0, 1.0),
                    voiceoverVolume = json.optDouble("voiceoverVolume", 1.1).coerceIn(0.0, 2.0),
                    musicVolume = json.optDouble("musicVolume", 0.4).coerceIn(0.0, 2.0),
                    originalVolume = json.optDouble("originalVolume", 0.65).coerceIn(0.0, 2.0)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Color/Audio suggestion timed out", e)
            Result.failure(Exception("AI Color & Mix timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Color/Audio suggestion failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Color & Mix failed."))
        }
    }

    suspend fun generateAiVideoClip(
        userPrompt: String,
        visualStyle: String,
        aspectRatio: String,
        cameraMotion: String
    ): Result<AiGeneratedClipResult> {
        return try {
            val cleanPrompt = userPrompt.trim()
            if (cleanPrompt.isBlank()) {
                return Result.failure(IllegalArgumentException("Please enter a scene description for your AI clip."))
            }

            // Step 1: Direct scene metadata & refined cinema prompt via Gemini 3.8 Flash
            val directorPrompt = buildString {
                appendLine("You are a cinematographer creating an AI-generated B-roll video clip concept.")
                appendLine("User Scene Idea: $cleanPrompt")
                appendLine("Visual Style: $visualStyle")
                appendLine("Aspect Ratio: $aspectRatio, Camera Motion: $cameraMotion")
                appendLine("Produce a short clip title, a vivid ultra-detailed cinema still frame prompt (no text overlays, no watermarks), an outgoing transition, and a short subtitle caption.")
            }

            val conceptResponse = withTimeout(AI_TIMEOUT_MS) {
                aiClipConceptModel.generateContent(directorPrompt)
            }
            val conceptJson = JSONObject(
                conceptResponse.text ?: throw IllegalStateException("AI returned empty clip concept.")
            )
            val title = conceptJson.optString("title", "AI Cinema Clip").trim().take(48)
            val refinedImagePrompt = conceptJson.optString("imagePrompt", "$cleanPrompt, $visualStyle, cinematic 4k film still")
            val transition = TransitionType.fromRaw(conceptJson.optString("transition"))
            val caption = conceptJson.optString("caption", "").trim().take(120)
            val emphasisWord = conceptJson.optString("emphasisWord", "").trim().take(30)

            // Step 2: Generate authentic visual keyframe via Gemini Nano Banana 2.1
            val imageResponse = withTimeout(AI_TIMEOUT_MS) {
                aiClipImageModel.generateContent(
                    "$refinedImagePrompt. Style: $visualStyle. Composition suited for $aspectRatio cinema frame. Photorealistic lighting, no text or watermarks."
                )
            }
            val generatedBitmap = imageResponse.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: return Result.failure(IllegalStateException("AI image model did not return a keyframe bitmap."))

            Result.success(
                AiGeneratedClipResult(
                    title = title.ifBlank { "AI Cinema Clip" },
                    visualPromptUsed = refinedImagePrompt,
                    suggestedTransition = transition,
                    suggestedCaption = caption,
                    emphasisWord = emphasisWord,
                    keyframeBitmap = generatedBitmap
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI video clip generation timed out", e)
            Result.failure(Exception("AI clip generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI video clip generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI clip generation failed."))
        }
    }

    /**
     * Generates a video from text using gemini-omni-1.2-flash (16:9 landscape or 9:16 portrait).
     */
    suspend fun generateVideoFromText(
        prompt: String,
        aspectRatio: VideoGenAspectRatio,
        visualStyle: String
    ): Result<AiGeneratedVideoResult> {
        return try {
            val cleanPrompt = prompt.trim()
            if (cleanPrompt.isBlank()) {
                return Result.failure(IllegalArgumentException("Enter a prompt describing the video to generate."))
            }

            val fullPrompt = buildString {
                appendLine("Generate a high-definition cinematic video clip.")
                appendLine("Aspect Ratio: ${aspectRatio.raw} (${aspectRatio.label}).")
                appendLine("Visual Style: $visualStyle.")
                appendLine("Scene Description: $cleanPrompt")
                appendLine("Output smooth, coherent motion with photorealistic lighting and no watermarks.")
            }

            val response = withTimeout(MEDIA_GEN_TIMEOUT_MS) {
                videoGenerationModel.generateContent(fullPrompt)
            }
            parseVideoGenerationResponse(
                response = response,
                fallbackTitle = cleanPrompt.take(36),
                aspectRatio = aspectRatio
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Text-to-video timed out", e)
            Result.failure(Exception("Video generation timed out after 120 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text-to-video failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Video generation failed."))
        }
    }

    /**
     * Animates a user-uploaded photo into a video using gemini-omni-1.2-flash (16:9 or 9:16).
     */
    suspend fun animateImageToVideo(
        sourceBitmap: Bitmap,
        motionPrompt: String,
        aspectRatio: VideoGenAspectRatio
    ): Result<AiGeneratedVideoResult> {
        return try {
            val motionBrief = motionPrompt.trim().ifBlank {
                "Smooth cinematic camera motion bringing the still photograph to life naturally"
            }
            val fullPrompt = buildString {
                appendLine("Animate this reference image into a fluid, high-definition video.")
                appendLine("Aspect Ratio: ${aspectRatio.raw} (${aspectRatio.label}).")
                appendLine("Motion Direction: $motionBrief")
                appendLine("Preserve the subject identity, lighting, and visual style of the input photo.")
            }

            val response = withTimeout(MEDIA_GEN_TIMEOUT_MS) {
                videoGenerationModel.generateContent(
                    content {
                        image(sourceBitmap)
                        text(fullPrompt)
                    }
                )
            }
            parseVideoGenerationResponse(
                response = response,
                fallbackTitle = "Animated: ${motionBrief.take(28)}",
                aspectRatio = aspectRatio
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image-to-video animation timed out", e)
            Result.failure(Exception("Image animation timed out after 120 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image-to-video animation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Image animation failed."))
        }
    }

    private fun parseVideoGenerationResponse(
        response: com.google.firebase.ai.type.GenerateContentResponse,
        fallbackTitle: String,
        aspectRatio: VideoGenAspectRatio
    ): Result<AiGeneratedVideoResult> {
        val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
        val videoInline = parts.filterIsInstance<InlineDataPart>()
            .firstOrNull { it.mimeType.startsWith("video/", ignoreCase = true) && it.inlineData.isNotEmpty() }
        val videoFile = parts.filterIsInstance<FileDataPart>()
            .firstOrNull { it.mimeType.startsWith("video/", ignoreCase = true) || it.uri.isNotBlank() }
        val imagePart = parts.filterIsInstance<ImagePart>().firstOrNull()
        val textNotes = parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }.trim()

        if (videoInline == null && videoFile == null && imagePart == null) {
            val detail = textNotes.take(220).ifBlank {
                "No video stream was returned by $VIDEO_MODEL_NAME."
            }
            return Result.failure(IllegalStateException(detail))
        }

        return Result.success(
            AiGeneratedVideoResult(
                title = fallbackTitle.trim().ifBlank { "AI Video" }.take(48),
                aspectRatio = aspectRatio,
                videoBytes = videoInline?.inlineData,
                mimeType = videoInline?.mimeType ?: videoFile?.mimeType ?: "video/mp4",
                remoteFileUri = videoFile?.uri,
                previewBitmap = imagePart?.image,
                notes = textNotes.take(280),
                modelUsed = VIDEO_MODEL_NAME
            )
        )
    }

    /**
     * Creates a new image from text OR edits an existing image using gemini-nano-banana-2.1.
     */
    suspend fun createOrEditImage(
        prompt: String,
        sourceBitmap: Bitmap? = null,
        aspectRatioHint: String = "16:9"
    ): Result<AiGeneratedImageResult> {
        return try {
            val cleanPrompt = prompt.trim()
            if (cleanPrompt.isBlank()) {
                return Result.failure(IllegalArgumentException("Enter a prompt to create or edit your image."))
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (sourceBitmap != null) {
                    aiClipImageModel.generateContent(
                        content {
                            image(sourceBitmap)
                            text("$cleanPrompt. Maintain photorealistic detail and cohesive lighting. Target framing: $aspectRatioHint.")
                        }
                    )
                } else {
                    aiClipImageModel.generateContent(
                        "$cleanPrompt. High-craft studio composition suited for $aspectRatioHint framing, crisp detail, no watermarks."
                    )
                }
            }

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val generatedBitmap = parts.filterIsInstance<ImagePart>().firstOrNull()?.image
                ?: return Result.failure(
                    IllegalStateException(
                        parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }.trim().take(200)
                            .ifBlank { "No image was returned by $IMAGE_MODEL_NAME." }
                    )
                )
            val captionText = parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }.trim()

            Result.success(
                AiGeneratedImageResult(
                    bitmap = generatedBitmap,
                    responseText = captionText,
                    isEdit = sourceBitmap != null
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image create/edit timed out", e)
            Result.failure(Exception("Image generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image create/edit failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Image generation failed."))
        }
    }

    /**
     * Generates AI music using lyria-3-clip-preview (<=30s) or lyria-3-pro-preview (full track).
     */
    suspend fun generateMusicTrack(
        prompt: String,
        option: MusicModelOption,
        instrumentalOnly: Boolean
    ): Result<AiGeneratedMusicResult> {
        return try {
            val cleanPrompt = prompt.trim()
            if (cleanPrompt.isBlank()) {
                return Result.failure(IllegalArgumentException("Describe the music genre, mood, or instruments to generate."))
            }

            val model = when (option) {
                MusicModelOption.SHORT_CLIP -> musicClipModel
                MusicModelOption.FULL_TRACK -> musicProModel
            }
            val finalPrompt = buildString {
                append(cleanPrompt)
                if (instrumentalOnly) {
                    append(". Strictly instrumental soundtrack with no vocals.")
                }
                if (option == MusicModelOption.SHORT_CLIP) {
                    append(" Create a tight 30-second video soundtrack loop.")
                } else {
                    append(" Create a complete full-length structured music track.")
                }
            }

            val response = withTimeout(MEDIA_GEN_TIMEOUT_MS) {
                model.generateContent(finalPrompt)
            }
            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>()
                .firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) && it.inlineData.isNotEmpty() }
                ?: parts.filterIsInstance<InlineDataPart>().firstOrNull { it.inlineData.isNotEmpty() }

            val notes = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }.trim()
            if (audioPart == null) {
                val detail = notes.take(220).ifBlank {
                    "No audio data was returned by ${option.modelId}."
                }
                return Result.failure(IllegalStateException(detail))
            }

            val trackTitle = cleanPrompt
                .split(",", ".", "-")
                .firstOrNull()
                ?.trim()
                ?.take(42)
                ?.ifBlank { "AI Soundtrack" }
                ?: "AI Soundtrack"

            Result.success(
                AiGeneratedMusicResult(
                    title = trackTitle,
                    audioBytes = audioPart.inlineData,
                    mimeType = audioPart.mimeType.ifBlank { "audio/mpeg" },
                    lyricsOrNotes = notes.take(800),
                    modelUsed = option.modelId
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Music generation timed out", e)
            Result.failure(Exception("Music generation timed out after 120 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Music generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Music generation failed."))
        }
    }
}
