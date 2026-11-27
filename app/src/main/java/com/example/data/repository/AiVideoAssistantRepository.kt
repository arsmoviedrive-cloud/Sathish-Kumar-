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
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "AiVideoAssistant"
private const val AI_TIMEOUT_MS = 60_000L
private const val MODEL_NAME = "gemini-3.8-flash"

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
                            values = listOf("CUT", "CROSS_DISSOLVE", "WHIP_PAN", "DIP_BLACK"),
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

    private val colorAudioModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = colorAudioSchema
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
}
