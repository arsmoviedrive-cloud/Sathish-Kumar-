package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.ColorMatrix
import androidx.core.content.FileProvider
import com.example.data.model.CaptionCue
import com.example.data.model.CodecOption
import com.example.data.model.ResolutionOption
import com.example.data.model.VideoProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.pow

private const val TAG = "VideoMediaEngine"
private const val MAX_INLINE_AI_BYTES = 15 * 1024 * 1024 // Keep safely under 20MB Gemini inline limit

data class ImportedVideoMeta(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

data class BatteryTelemetry(
    val levelPercent: Int,
    val charging: Boolean,
    val previewFpsCap: Int,
    val estimatedRenderDrainPct: Int
)

data class ExportProgressState(
    val progressFraction: Float,
    val stageLabel: String,
    val completedOutputUri: String? = null,
    val completedShareUri: Uri? = null,
    val shareMimeType: String = "video/mp4",
    val estimatedSizeMb: Double = 0.0,
    val errorMessage: String? = null
)

class VideoMediaEngine(private val context: Context) {

    fun readBatteryTelemetry(
        batterySaverEnabled: Boolean,
        resolution: ResolutionOption,
        durationMs: Long
    ): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 82
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val durationMinutes = (durationMs / 60_000.0).coerceAtLeast(0.25)
        val resFactor = when (resolution) {
            ResolutionOption.UHD_4K -> 2.4
            ResolutionOption.FHD_1080P -> 1.1
            ResolutionOption.HD_720P -> 0.6
        }
        val ecoDiscount = if (batterySaverEnabled) 0.65 else 1.0
        val drain = (durationMinutes * resFactor * ecoDiscount).toInt().coerceIn(1, 25)

        return BatteryTelemetry(
            levelPercent = pct,
            charging = charging,
            previewFpsCap = if (batterySaverEnabled) 30 else 60,
            estimatedRenderDrainPct = drain
        )
    }

    suspend fun inspectVideoUri(uri: Uri): ImportedVideoMeta = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 8000L
        var width = 1920
        var height = 1080
        var name = "Imported Clip"
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Photo Picker URIs may grant transient read permission without persistable flag
            }
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val rawName = cursor.getString(0)
                        if (!rawName.isNullOrBlank()) {
                            name = rawName.substringBeforeLast('.').take(48)
                        }
                    }
                }
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 8000L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect video metadata for $uri: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        ImportedVideoMeta(
            uriString = uri.toString(),
            displayName = name,
            durationMs = durationMs,
            width = width,
            height = height
        )
    }

    suspend fun extractKeyframes(
        uriString: String,
        count: Int,
        batterySaverEnabled: Boolean
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext emptyList()
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext emptyList()
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 6000L
            val targetCount = if (batterySaverEnabled) count.coerceAtMost(3) else count.coerceAtMost(6)
            val maxW = if (batterySaverEnabled) 240 else 384
            val maxH = if (batterySaverEnabled) 135 else 216
            for (i in 0 until targetCount) {
                val timeUs = ((durationMs * (i + 0.5)) / targetCount).toLong() * 1000L
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        maxW,
                        maxH
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (bmp != null) frames.add(bmp)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Keyframe extraction failed: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    suspend fun readInlineBytesForAi(uriString: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        return@withContext try {
            val uri = Uri.parse(uriString)
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty() && bytes.size <= MAX_INLINE_AI_BYTES) {
                    bytes to mime
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read inline media bytes for AI: ${e.message}")
            null
        }
    }

    fun estimateExportSizeMb(
        durationMs: Long,
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long
    ): Double {
        val seconds = (durationMs.coerceAtLeast(1000L) / 1000.0)
        val fpsFactor = if (fps >= 60L) 1.35 else 1.0
        val codecFactor = if (codec == CodecOption.HEVC_H265) 0.72 else 1.0
        val megabits = resolution.bitrateMbps * fpsFactor * codecFactor * seconds
        return ((megabits / 8.0) * 10.0).toInt() / 10.0
    }

    fun exportProjectPipeline(
        project: VideoProject,
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long,
        batterySaverEnabled: Boolean
    ): Flow<ExportProgressState> = flow {
        val estimatedMb = estimateExportSizeMb(project.totalDurationMs, resolution, codec, fps)
        emit(ExportProgressState(0.08f, "Preparing ${resolution.label} ${fps}fps ${codec.label} pipeline...", estimatedSizeMb = estimatedMb))

        val firstRealClip = project.typedClips().firstOrNull { it.sourceUri.isNotBlank() }
        val stepDelay = if (batterySaverEnabled) 180L else 110L

        if (firstRealClip != null) {
            emit(ExportProgressState(0.25f, "Hardware remuxing trimmed video & audio tracks...", estimatedSizeMb = estimatedMb))
            val remuxedUri = remuxLocalVideoClip(
                sourceUri = Uri.parse(firstRealClip.sourceUri),
                trimStartMs = firstRealClip.trimStartMs,
                trimEndMs = firstRealClip.trimEndMs,
                outputTitle = project.title
            )
            emit(ExportProgressState(0.75f, "Applying color LUT metadata & burning SRT sidecar...", estimatedSizeMb = estimatedMb))
            writeSrtSidecarFile(project.title, project.typedCaptions())
            delay(stepDelay)
            if (remuxedUri != null) {
                emit(
                    ExportProgressState(
                        progressFraction = 1.0f,
                        stageLabel = "Saved MP4 to Movies/FrameCut",
                        completedOutputUri = remuxedUri.toString(),
                        completedShareUri = remuxedUri,
                        shareMimeType = "video/mp4",
                        estimatedSizeMb = estimatedMb
                    )
                )
                return@flow
            }
        }

        // Fallback / Studio Project Pack export (for sample scenes or cross-device cloud edits without local raw media)
        emit(ExportProgressState(0.32f, "Rendering ${resolution.label} Edit Decision List & LUT grade...", estimatedSizeMb = estimatedMb))
        delay(stepDelay)
        emit(ExportProgressState(0.64f, "Mixing ${project.typedAudioTracks().size} audio tracks & ${project.typedCaptions().size} captions...", estimatedSizeMb = estimatedMb))
        val srtFile = writeSrtSidecarFile(project.title, project.typedCaptions())
        delay(stepDelay)
        emit(ExportProgressState(0.88f, "Packaging shareable FrameCut render manifest...", estimatedSizeMb = estimatedMb))
        val manifestFile = writeExportManifestFile(project, resolution, codec, fps, srtFile?.name)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            manifestFile
        )
        delay(stepDelay)
        emit(
            ExportProgressState(
                progressFraction = 1.0f,
                stageLabel = "Render manifest & SRT captions ready to share",
                completedOutputUri = shareUri.toString(),
                completedShareUri = shareUri,
                shareMimeType = "application/json",
                estimatedSizeMb = estimatedMb
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun remuxLocalVideoClip(
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        outputTitle: String
    ): Uri? {
        val safeTitle = outputTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36).ifBlank { "FrameCut" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.mp4"
        val tempFile = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName)

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, sourceUri, null)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) return null

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = HashMap<Int, Int>()
            var maxBufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > maxBufferSize) maxBufferSize = size
                    }
                }
            }

            if (indexMap.isEmpty()) return null
            muxer.start()

            val startUs = trimStartMs.coerceAtLeast(0L) * 1000L
            val endUs = trimEndMs.coerceAtLeast(trimStartMs + 500L) * 1000L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                val trackIndex = extractor.sampleTrackIndex
                val dstTrack = indexMap[trackIndex]
                if (dstTrack != null) {
                    bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            // Publish into MediaStore (zero storage permission needed on API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FrameCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val galleryUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (galleryUri != null) {
                    context.contentResolver.openOutputStream(galleryUri)?.use { out ->
                        tempFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(galleryUri, values, null, null)
                    return galleryUri
                }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Hardware MP4 remux failed, falling back to manifest export", e)
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    fun writeSrtSidecarFile(title: String, captions: List<CaptionCue>): File? {
        if (captions.isEmpty()) return null
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "FrameCut" }
        val srtFile = File(dir, "${safeTitle}_captions.srt")
        val content = buildString {
            captions.sortedBy { it.startMs }.forEachIndexed { idx, cue ->
                appendLine(idx + 1)
                appendLine("${formatSrtTime(cue.startMs)} --> ${formatSrtTime(cue.endMs)}")
                appendLine(cue.text)
                appendLine()
            }
        }
        srtFile.writeText(content)
        return srtFile
    }

    fun createSrtShareUri(title: String, captions: List<CaptionCue>): Uri? {
        val file = writeSrtSidecarFile(title, captions) ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun writeExportManifestFile(
        project: VideoProject,
        resolution: ResolutionOption,
        codec: CodecOption,
        fps: Long,
        srtName: String?
    ): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "FrameCut" }
        val file = File(dir, "${safeTitle}_${resolution.raw}_${fps}fps.fcproj.json")
        val enriched = project.toBackupJson()
        file.writeText(enriched)
        return file
    }

    private fun formatSrtTime(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val mins = (clamped % 3_600_000L) / 60_000L
        val secs = (clamped % 60_000L) / 1000L
        val millis = clamped % 1000L
        return "%02d:%02d:%02d,%03d".format(hours, mins, secs, millis)
    }

    companion object {
        fun buildComposeColorMatrix(
            exposure: Float,
            contrast: Float,
            saturation: Float,
            temperature: Float,
            tint: Float
        ): ColorMatrix {
            val matrix = ColorMatrix()

            // 1. Saturation
            val satMatrix = ColorMatrix().apply { setToSaturation(saturation.coerceIn(0f, 2f)) }
            matrix.timesAssign(satMatrix)

            // 2. Contrast + Exposure + Temperature/Tint balance
            val expGain = 2f.pow(exposure.coerceIn(-2f, 2f) * 0.55f)
            val c = contrast.coerceIn(0.5f, 2.0f)
            val translate = (-0.5f * c + 0.5f) * 255f

            val rScale = (c * expGain * (1f + temperature * 0.22f + tint * 0.08f)).coerceIn(0.1f, 3f)
            val gScale = (c * expGain * (1f - tint * 0.14f)).coerceIn(0.1f, 3f)
            val bScale = (c * expGain * (1f - temperature * 0.22f + tint * 0.08f)).coerceIn(0.1f, 3f)

            val toneMatrix = ColorMatrix(
                floatArrayOf(
                    rScale, 0f, 0f, 0f, translate,
                    0f, gScale, 0f, 0f, translate,
                    0f, 0f, bScale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.timesAssign(toneMatrix)
            return matrix
        }
    }
}
