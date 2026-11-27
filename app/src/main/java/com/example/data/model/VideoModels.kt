package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AspectRatioOption(val raw: String, val label: String, val ratio: Float) {
    WIDE_16_9("16:9", "16:9 Wide", 16f / 9f),
    VERTICAL_9_16("9:16", "9:16 Reels", 9f / 16f),
    SQUARE_1_1("1:1", "1:1 Square", 1f),
    PORTRAIT_4_5("4:5", "4:5 Feed", 4f / 5f);

    companion object {
        fun fromRaw(raw: String?): AspectRatioOption =
            entries.firstOrNull { it.raw == raw } ?: WIDE_16_9
    }
}

enum class ResolutionOption(val raw: String, val label: String, val bitrateMbps: Double) {
    UHD_4K("4K", "4K UHD", 45.0),
    FHD_1080P("1080P", "1080p HD", 16.0),
    HD_720P("720P", "720p Web", 8.0);

    companion object {
        fun fromRaw(raw: String?): ResolutionOption =
            entries.firstOrNull { it.raw == raw } ?: UHD_4K
    }
}

enum class CodecOption(val raw: String, val label: String) {
    HEVC_H265("HEVC_H265", "H.265 HEVC (4K HDR)"),
    AVC_H264("AVC_H264", "H.264 AVC (Universal)");

    companion object {
        fun fromRaw(raw: String?): CodecOption =
            entries.firstOrNull { it.raw == raw } ?: HEVC_H265
    }
}

enum class ExportFormatOption(
    val raw: String,
    val label: String,
    val subtitle: String,
    val extension: String,
    val mimeType: String,
    val sizeMultiplier: Double
) {
    MP4("MP4", "MP4", "Universal • Social & Web", "mp4", "video/mp4", 1.0),
    MOV("MOV", "MOV", "QuickTime • Studio Master", "mov", "video/quicktime", 1.18);

    companion object {
        fun fromRaw(raw: String?): ExportFormatOption =
            entries.firstOrNull { it.raw == raw } ?: MP4
    }
}

enum class ColorPreset(
    val raw: String,
    val label: String,
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float,
    val vignette: Float
) {
    NATURAL("NATURAL", "Natural", 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f),
    KODAK_2383("KODAK_2383", "Kodak 2383", 0.08f, 1.18f, 1.12f, 0.14f, -0.04f, 0.28f),
    TEAL_ORANGE("TEAL_ORANGE", "Teal & Orange", 0.05f, 1.24f, 1.18f, 0.22f, -0.10f, 0.32f),
    FUJI_ETERNA("FUJI_ETERNA", "Fuji Eterna", 0.04f, 0.94f, 0.88f, -0.06f, 0.08f, 0.18f),
    NOIR_400("NOIR_400", "Noir 400", -0.05f, 1.42f, 0.0f, 0.0f, 0.0f, 0.48f),
    CYBER_NEON("CYBER_NEON", "Cyber Neon", 0.12f, 1.30f, 1.38f, -0.24f, 0.28f, 0.36f),
    GOLDEN_HOUR("GOLDEN_HOUR", "Golden Hour", 0.16f, 1.12f, 1.22f, 0.36f, 0.06f, 0.22f),
    FLAT_LOG("FLAT_LOG", "Cinema Log", 0.02f, 0.78f, 0.72f, 0.0f, 0.0f, 0.05f);

    companion object {
        fun fromRaw(raw: String?): ColorPreset =
            entries.firstOrNull { it.raw == raw } ?: NATURAL
    }
}

enum class CaptionStyle(val raw: String, val label: String) {
    KINETIC_BOLD("KINETIC_BOLD", "Kinetic Bold"),
    MINIMAL_SUB("MINIMAL_SUB", "Minimal Sub"),
    BROADCAST_BAR("BROADCAST_BAR", "Broadcast Bar"),
    NEON_POP("NEON_POP", "Neon Pop");

    companion object {
        fun fromRaw(raw: String?): CaptionStyle =
            entries.firstOrNull { it.raw == raw } ?: KINETIC_BOLD
    }
}

enum class TransitionType(
    val raw: String,
    val label: String,
    val shortCode: String,
    val description: String
) {
    CUT("CUT", "Hard Cut", "CT", "Instant frame cut"),
    CROSS_FADE("CROSS_FADE", "Cross-Fade", "CF", "Smooth opacity blend"),
    CROSS_DISSOLVE("CROSS_DISSOLVE", "Dissolve", "DS", "Soft filmic lap dissolve"),
    WHIP_PAN("WHIP_PAN", "Whip Pan", "WP", "High-energy directional sweep"),
    DIP_BLACK("DIP_BLACK", "Dip Black", "DB", "Fade out through pure black"),
    DIP_WHITE("DIP_WHITE", "Flash White", "DW", "Luminous white flash bloom"),
    ZOOM_IN("ZOOM_IN", "Zoom Punch", "ZM", "Dynamic kinetic push-in"),
    SLIDE_LEFT("SLIDE_LEFT", "Slide Wipe", "SL", "Horizontal directional push"),
    GLITCH("GLITCH", "RGB Glitch", "GL", "Chromatic cyber pulse cut"),
    BLUR_DISSOLVE("BLUR_DISSOLVE", "Blur Morph", "BL", "Soft optical defocus blend"),
    IRIS_WIPE("IRIS_WIPE", "Iris Wipe", "IR", "Circular cinema aperture wipe");

    companion object {
        fun fromRaw(raw: String?): TransitionType =
            entries.firstOrNull { it.raw == raw } ?: CUT
    }
}

enum class TransitionEasing(val raw: String, val label: String) {
    LINEAR("LINEAR", "Linear"),
    EASE_IN_OUT("EASE_IN_OUT", "Smooth Ease"),
    SPRING("SPRING", "Punchy Spring");

    companion object {
        fun fromRaw(raw: String?): TransitionEasing =
            entries.firstOrNull { it.raw == raw } ?: EASE_IN_OUT
    }
}

enum class AudioRole(val raw: String, val label: String) {
    ORIGINAL("ORIGINAL", "Original Clip"),
    VOICEOVER("VOICEOVER", "Voiceover"),
    MUSIC("MUSIC", "Music Bed"),
    SFX("SFX", "Sound FX");

    companion object {
        fun fromRaw(raw: String?): AudioRole =
            entries.firstOrNull { it.raw == raw } ?: MUSIC
    }
}

enum class ExportDestination(val raw: String, val label: String) {
    DEVICE_GALLERY("DEVICE_GALLERY", "Device Gallery"),
    YOUTUBE_SHORTS("YOUTUBE_SHORTS", "YouTube Shorts"),
    INSTAGRAM_REELS("INSTAGRAM_REELS", "Instagram Reels"),
    TIKTOK("TIKTOK", "TikTok"),
    CLOUD_MANIFEST("CLOUD_MANIFEST", "Cloud Studio Pack");

    companion object {
        fun fromRaw(raw: String?): ExportDestination =
            entries.firstOrNull { it.raw == raw } ?: DEVICE_GALLERY
    }
}

data class TimelineClip(
    val clipId: String = "clip_${UUID.randomUUID().toString().take(8)}",
    val label: String = "Clip",
    val sourceUri: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 6000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 6000L,
    val speed: Double = 1.0,
    val volume: Double = 1.0,
    val muted: Boolean = false,
    val transition: String = TransitionType.CUT.raw,
    val transitionDurationMs: Long = 500L,
    val transitionEasing: String = TransitionEasing.EASE_IN_OUT.raw,
    val sampleScene: String = "COASTAL_HIGHWAY"
) {
    val effectiveDurationMs: Long
        get() = (((trimEndMs - trimStartMs).coerceAtLeast(300L)) / speed.coerceIn(0.25, 4.0)).toLong()

    fun toMap(): Map<String, Any> = mapOf(
        "clipId" to clipId,
        "label" to label.take(60),
        "sourceUri" to sourceUri.take(500),
        "startMs" to startMs.coerceAtLeast(0L),
        "endMs" to endMs.coerceAtLeast(startMs + 300L),
        "trimStartMs" to trimStartMs.coerceAtLeast(0L),
        "trimEndMs" to trimEndMs.coerceAtLeast(trimStartMs + 300L),
        "speed" to speed.coerceIn(0.25, 4.0),
        "volume" to volume.coerceIn(0.0, 2.0),
        "muted" to muted,
        "transition" to TransitionType.fromRaw(transition).raw,
        "transitionDurationMs" to transitionDurationMs.coerceIn(150L, 1500L),
        "transitionEasing" to TransitionEasing.fromRaw(transitionEasing).raw,
        "sampleScene" to sampleScene.take(40)
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("clipId", clipId)
        put("label", label)
        put("sourceUri", sourceUri)
        put("startMs", startMs)
        put("endMs", endMs)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("speed", speed)
        put("volume", volume)
        put("muted", muted)
        put("transition", transition)
        put("transitionDurationMs", transitionDurationMs)
        put("transitionEasing", transitionEasing)
        put("sampleScene", sampleScene)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): TimelineClip = TimelineClip(
            clipId = (map["clipId"] as? String).orEmpty().ifBlank { "clip_${UUID.randomUUID().toString().take(8)}" },
            label = (map["label"] as? String).orEmpty().ifBlank { "Clip" },
            sourceUri = (map["sourceUri"] as? String).orEmpty(),
            startMs = (map["startMs"] as? Number)?.toLong() ?: 0L,
            endMs = (map["endMs"] as? Number)?.toLong() ?: 6000L,
            trimStartMs = (map["trimStartMs"] as? Number)?.toLong() ?: 0L,
            trimEndMs = (map["trimEndMs"] as? Number)?.toLong() ?: 6000L,
            speed = (map["speed"] as? Number)?.toDouble()?.coerceIn(0.25, 4.0) ?: 1.0,
            volume = (map["volume"] as? Number)?.toDouble()?.coerceIn(0.0, 2.0) ?: 1.0,
            muted = (map["muted"] as? Boolean) ?: false,
            transition = TransitionType.fromRaw(map["transition"] as? String).raw,
            transitionDurationMs = (map["transitionDurationMs"] as? Number)?.toLong()?.coerceIn(150L, 1500L) ?: 500L,
            transitionEasing = TransitionEasing.fromRaw(map["transitionEasing"] as? String).raw,
            sampleScene = (map["sampleScene"] as? String).orEmpty().ifBlank { "COASTAL_HIGHWAY" }
        )

        fun fromJson(obj: JSONObject): TimelineClip = TimelineClip(
            clipId = obj.optString("clipId", "clip_${UUID.randomUUID().toString().take(8)}"),
            label = obj.optString("label", "Clip"),
            sourceUri = obj.optString("sourceUri", ""),
            startMs = obj.optLong("startMs", 0L),
            endMs = obj.optLong("endMs", 6000L),
            trimStartMs = obj.optLong("trimStartMs", 0L),
            trimEndMs = obj.optLong("trimEndMs", 6000L),
            speed = obj.optDouble("speed", 1.0).coerceIn(0.25, 4.0),
            volume = obj.optDouble("volume", 1.0).coerceIn(0.0, 2.0),
            muted = obj.optBoolean("muted", false),
            transition = TransitionType.fromRaw(obj.optString("transition")).raw,
            transitionDurationMs = obj.optLong("transitionDurationMs", 500L).coerceIn(150L, 1500L),
            transitionEasing = TransitionEasing.fromRaw(obj.optString("transitionEasing")).raw,
            sampleScene = obj.optString("sampleScene", "COASTAL_HIGHWAY")
        )
    }
}

data class AudioTrack(
    val trackId: String = "trk_${UUID.randomUUID().toString().take(8)}",
    val name: String = "Audio Track",
    val role: String = AudioRole.MUSIC.raw,
    val sourceUri: String = "",
    val volume: Double = 0.85,
    val pan: Double = 0.0,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 300L,
    val offsetMs: Long = 0L,
    val durationMs: Long = 18000L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "trackId" to trackId,
        "name" to name.take(60),
        "role" to AudioRole.fromRaw(role).raw,
        "sourceUri" to sourceUri.take(500),
        "volume" to volume.coerceIn(0.0, 2.0),
        "pan" to pan.coerceIn(-1.0, 1.0),
        "muted" to muted,
        "solo" to solo,
        "fadeInMs" to fadeInMs.coerceIn(0L, 10000L),
        "fadeOutMs" to fadeOutMs.coerceIn(0L, 10000L),
        "offsetMs" to offsetMs.coerceAtLeast(0L),
        "durationMs" to durationMs.coerceAtLeast(500L)
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("trackId", trackId)
        put("name", name)
        put("role", role)
        put("sourceUri", sourceUri)
        put("volume", volume)
        put("pan", pan)
        put("muted", muted)
        put("solo", solo)
        put("fadeInMs", fadeInMs)
        put("fadeOutMs", fadeOutMs)
        put("offsetMs", offsetMs)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): AudioTrack = AudioTrack(
            trackId = (map["trackId"] as? String).orEmpty().ifBlank { "trk_${UUID.randomUUID().toString().take(8)}" },
            name = (map["name"] as? String).orEmpty().ifBlank { "Audio Track" },
            role = AudioRole.fromRaw(map["role"] as? String).raw,
            sourceUri = (map["sourceUri"] as? String).orEmpty(),
            volume = (map["volume"] as? Number)?.toDouble()?.coerceIn(0.0, 2.0) ?: 0.85,
            pan = (map["pan"] as? Number)?.toDouble()?.coerceIn(-1.0, 1.0) ?: 0.0,
            muted = (map["muted"] as? Boolean) ?: false,
            solo = (map["solo"] as? Boolean) ?: false,
            fadeInMs = (map["fadeInMs"] as? Number)?.toLong() ?: 0L,
            fadeOutMs = (map["fadeOutMs"] as? Number)?.toLong() ?: 300L,
            offsetMs = (map["offsetMs"] as? Number)?.toLong() ?: 0L,
            durationMs = (map["durationMs"] as? Number)?.toLong() ?: 18000L
        )

        fun fromJson(obj: JSONObject): AudioTrack = AudioTrack(
            trackId = obj.optString("trackId", "trk_${UUID.randomUUID().toString().take(8)}"),
            name = obj.optString("name", "Audio Track"),
            role = AudioRole.fromRaw(obj.optString("role")).raw,
            sourceUri = obj.optString("sourceUri", ""),
            volume = obj.optDouble("volume", 0.85).coerceIn(0.0, 2.0),
            pan = obj.optDouble("pan", 0.0).coerceIn(-1.0, 1.0),
            muted = obj.optBoolean("muted", false),
            solo = obj.optBoolean("solo", false),
            fadeInMs = obj.optLong("fadeInMs", 0L),
            fadeOutMs = obj.optLong("fadeOutMs", 300L),
            offsetMs = obj.optLong("offsetMs", 0L),
            durationMs = obj.optLong("durationMs", 18000L)
        )
    }
}

data class CaptionCue(
    val captionId: String = "cap_${UUID.randomUUID().toString().take(8)}",
    val startMs: Long = 0L,
    val endMs: Long = 2500L,
    val text: String = "",
    val emphasisWord: String = "",
    val speaker: String = "Speaker 1"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "captionId" to captionId,
        "startMs" to startMs.coerceAtLeast(0L),
        "endMs" to endMs.coerceAtLeast(startMs + 200L),
        "text" to text.take(180),
        "emphasisWord" to emphasisWord.take(40),
        "speaker" to speaker.take(40)
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("captionId", captionId)
        put("startMs", startMs)
        put("endMs", endMs)
        put("text", text)
        put("emphasisWord", emphasisWord)
        put("speaker", speaker)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): CaptionCue = CaptionCue(
            captionId = (map["captionId"] as? String).orEmpty().ifBlank { "cap_${UUID.randomUUID().toString().take(8)}" },
            startMs = (map["startMs"] as? Number)?.toLong() ?: 0L,
            endMs = (map["endMs"] as? Number)?.toLong() ?: 2500L,
            text = (map["text"] as? String).orEmpty(),
            emphasisWord = (map["emphasisWord"] as? String).orEmpty(),
            speaker = (map["speaker"] as? String).orEmpty().ifBlank { "Speaker 1" }
        )

        fun fromJson(obj: JSONObject): CaptionCue = CaptionCue(
            captionId = obj.optString("captionId", "cap_${UUID.randomUUID().toString().take(8)}"),
            startMs = obj.optLong("startMs", 0L),
            endMs = obj.optLong("endMs", 2500L),
            text = obj.optString("text", ""),
            emphasisWord = obj.optString("emphasisWord", ""),
            speaker = obj.optString("speaker", "Speaker 1")
        )
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Studio Editor",
    val themeMode: String = "OLED_DARK",
    val batterySaverEnabled: Boolean = false,
    val defaultResolution: String = "4K",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled Edit",
    val aspectRatio: String = AspectRatioOption.WIDE_16_9.raw,
    val resolution: String = ResolutionOption.UHD_4K.raw,
    val frameRate: Long = 60L,
    val totalDurationMs: Long = 18000L,
    val colorPreset: String = ColorPreset.NATURAL.raw,
    val exposure: Double = 0.0,
    val contrast: Double = 1.0,
    val saturation: Double = 1.0,
    val temperature: Double = 0.0,
    val tint: Double = 0.0,
    val vignette: Double = 0.0,
    val captionStyle: String = CaptionStyle.KINETIC_BOLD.raw,
    val clips: List<Map<String, Any?>> = emptyList(),
    val audioTracks: List<Map<String, Any?>> = emptyList(),
    val captions: List<Map<String, Any?>> = emptyList(),
    val aiSummary: String = "",
    val cloudBackupStatus: String = "SYNCED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typedClips(): List<TimelineClip> = clips.map { TimelineClip.fromMap(it) }
    fun typedAudioTracks(): List<AudioTrack> = audioTracks.map { AudioTrack.fromMap(it) }
    fun typedCaptions(): List<CaptionCue> = captions.map { CaptionCue.fromMap(it) }.sortedBy { it.startMs }

    fun withTypedContent(
        newClips: List<TimelineClip> = typedClips(),
        newAudioTracks: List<AudioTrack> = typedAudioTracks(),
        newCaptions: List<CaptionCue> = typedCaptions()
    ): VideoProject {
        val recomputedClips = mutableListOf<TimelineClip>()
        var cursorMs = 0L
        newClips.take(50).forEach { clip ->
            val dur = clip.effectiveDurationMs
            recomputedClips.add(clip.copy(startMs = cursorMs, endMs = cursorMs + dur))
            cursorMs += dur
        }
        return copy(
            totalDurationMs = cursorMs.coerceIn(0L, 36000000L),
            clips = recomputedClips.map { it.toMap() },
            audioTracks = newAudioTracks.take(12).map { it.toMap() },
            captions = newCaptions.take(200).sortedBy { it.startMs }.map { it.toMap() }
        )
    }

    fun toFirestoreCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.trim().ifBlank { "Untitled Edit" }.take(100),
        "aspectRatio" to AspectRatioOption.fromRaw(aspectRatio).raw,
        "resolution" to ResolutionOption.fromRaw(resolution).raw,
        "frameRate" to if (frameRate in listOf(24L, 30L, 60L)) frameRate else 60L,
        "totalDurationMs" to totalDurationMs.coerceIn(0L, 36000000L),
        "colorPreset" to ColorPreset.fromRaw(colorPreset).raw,
        "exposure" to exposure.coerceIn(-2.0, 2.0),
        "contrast" to contrast.coerceIn(0.5, 2.0),
        "saturation" to saturation.coerceIn(0.0, 2.0),
        "temperature" to temperature.coerceIn(-1.0, 1.0),
        "tint" to tint.coerceIn(-1.0, 1.0),
        "vignette" to vignette.coerceIn(0.0, 1.0),
        "captionStyle" to CaptionStyle.fromRaw(captionStyle).raw,
        "clips" to typedClips().take(50).map { it.toMap() },
        "audioTracks" to typedAudioTracks().take(12).map { it.toMap() },
        "captions" to typedCaptions().take(200).map { it.toMap() },
        "aiSummary" to aiSummary.take(600),
        "cloudBackupStatus" to if (cloudBackupStatus in listOf("SYNCED", "LOCAL_ONLY", "EXPORTED")) cloudBackupStatus else "SYNCED",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toFirestoreUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().ifBlank { "Untitled Edit" }.take(100),
        "aspectRatio" to AspectRatioOption.fromRaw(aspectRatio).raw,
        "resolution" to ResolutionOption.fromRaw(resolution).raw,
        "frameRate" to if (frameRate in listOf(24L, 30L, 60L)) frameRate else 60L,
        "totalDurationMs" to totalDurationMs.coerceIn(0L, 36000000L),
        "colorPreset" to ColorPreset.fromRaw(colorPreset).raw,
        "exposure" to exposure.coerceIn(-2.0, 2.0),
        "contrast" to contrast.coerceIn(0.5, 2.0),
        "saturation" to saturation.coerceIn(0.0, 2.0),
        "temperature" to temperature.coerceIn(-1.0, 1.0),
        "tint" to tint.coerceIn(-1.0, 1.0),
        "vignette" to vignette.coerceIn(0.0, 1.0),
        "captionStyle" to CaptionStyle.fromRaw(captionStyle).raw,
        "clips" to typedClips().take(50).map { it.toMap() },
        "audioTracks" to typedAudioTracks().take(12).map { it.toMap() },
        "captions" to typedCaptions().take(200).map { it.toMap() },
        "aiSummary" to aiSummary.take(600),
        "cloudBackupStatus" to if (cloudBackupStatus in listOf("SYNCED", "LOCAL_ONLY", "EXPORTED")) cloudBackupStatus else "SYNCED",
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toBackupJson(): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("projectId", id)
        put("title", title)
        put("aspectRatio", aspectRatio)
        put("resolution", resolution)
        put("frameRate", frameRate)
        put("totalDurationMs", totalDurationMs)
        put("colorPreset", colorPreset)
        put("exposure", exposure)
        put("contrast", contrast)
        put("saturation", saturation)
        put("temperature", temperature)
        put("tint", tint)
        put("vignette", vignette)
        put("captionStyle", captionStyle)
        put("aiSummary", aiSummary)
        put("clips", JSONArray(typedClips().map { it.toJson() }))
        put("audioTracks", JSONArray(typedAudioTracks().map { it.toJson() }))
        put("captions", JSONArray(typedCaptions().map { it.toJson() }))
    }.toString(2)

    companion object {
        fun fromBackupJson(jsonString: String, userId: String): VideoProject {
            val obj = JSONObject(jsonString)
            val clipsArr = obj.optJSONArray("clips") ?: JSONArray()
            val tracksArr = obj.optJSONArray("audioTracks") ?: JSONArray()
            val capsArr = obj.optJSONArray("captions") ?: JSONArray()

            val clips = (0 until clipsArr.length()).map { TimelineClip.fromJson(clipsArr.getJSONObject(it)) }
            val tracks = (0 until tracksArr.length()).map { AudioTrack.fromJson(tracksArr.getJSONObject(it)) }
            val caps = (0 until capsArr.length()).map { CaptionCue.fromJson(capsArr.getJSONObject(it)) }

            return VideoProject(
                id = obj.optString("projectId", ""),
                userId = userId,
                title = obj.optString("title", "Restored Backup").take(100),
                aspectRatio = AspectRatioOption.fromRaw(obj.optString("aspectRatio")).raw,
                resolution = ResolutionOption.fromRaw(obj.optString("resolution")).raw,
                frameRate = obj.optLong("frameRate", 60L),
                totalDurationMs = obj.optLong("totalDurationMs", 0L),
                colorPreset = ColorPreset.fromRaw(obj.optString("colorPreset")).raw,
                exposure = obj.optDouble("exposure", 0.0),
                contrast = obj.optDouble("contrast", 1.0),
                saturation = obj.optDouble("saturation", 1.0),
                temperature = obj.optDouble("temperature", 0.0),
                tint = obj.optDouble("tint", 0.0),
                vignette = obj.optDouble("vignette", 0.0),
                captionStyle = CaptionStyle.fromRaw(obj.optString("captionStyle")).raw,
                aiSummary = obj.optString("aiSummary", ""),
                cloudBackupStatus = "SYNCED"
            ).withTypedContent(clips, tracks, caps)
        }

        fun createDemoCinemaProject(userId: String, title: String = "Coastal Dusk 4K Reel"): VideoProject {
            val clips = listOf(
                TimelineClip(
                    clipId = "clip_open",
                    label = "Pacific Cliff Aerial",
                    startMs = 0L,
                    endMs = 6000L,
                    trimStartMs = 0L,
                    trimEndMs = 6000L,
                    speed = 1.0,
                    transition = TransitionType.CROSS_DISSOLVE.raw,
                    sampleScene = "COASTAL_HIGHWAY"
                ),
                TimelineClip(
                    clipId = "clip_neon",
                    label = "Midnight Rain Neon",
                    startMs = 6000L,
                    endMs = 12000L,
                    trimStartMs = 0L,
                    trimEndMs = 6000L,
                    speed = 1.0,
                    transition = TransitionType.WHIP_PAN.raw,
                    sampleScene = "NEON_CITY"
                ),
                TimelineClip(
                    clipId = "clip_outro",
                    label = "Horizon Pullback",
                    startMs = 12000L,
                    endMs = 18000L,
                    trimStartMs = 0L,
                    trimEndMs = 6000L,
                    speed = 1.0,
                    transition = TransitionType.DIP_BLACK.raw,
                    sampleScene = "COASTAL_HIGHWAY"
                )
            )
            val audioTracks = listOf(
                AudioTrack(
                    trackId = "trk_orig",
                    name = "Camera Ambience",
                    role = AudioRole.ORIGINAL.raw,
                    volume = 0.72,
                    durationMs = 18000L
                ),
                AudioTrack(
                    trackId = "trk_vo",
                    name = "Director Voiceover",
                    role = AudioRole.VOICEOVER.raw,
                    volume = 1.05,
                    durationMs = 16000L
                ),
                AudioTrack(
                    trackId = "trk_music",
                    name = "Synthwave Score",
                    role = AudioRole.MUSIC.raw,
                    volume = 0.45,
                    fadeInMs = 800L,
                    fadeOutMs = 1500L,
                    durationMs = 18000L
                )
            )
            val captions = listOf(
                CaptionCue(
                    captionId = "cap_01",
                    startMs = 400L,
                    endMs = 3400L,
                    text = "Golden hour light sweeps across the Pacific cliffs.",
                    emphasisWord = "Golden",
                    speaker = "Narrator"
                ),
                CaptionCue(
                    captionId = "cap_02",
                    startMs = 6300L,
                    endMs = 10200L,
                    text = "When night falls, neon reflections bring the avenue alive.",
                    emphasisWord = "neon",
                    speaker = "Narrator"
                ),
                CaptionCue(
                    captionId = "cap_03",
                    startMs = 12500L,
                    endMs = 16800L,
                    text = "Crafted on mobile in crisp 4K with real-time color grading.",
                    emphasisWord = "4K",
                    speaker = "Narrator"
                )
            )
            val preset = ColorPreset.KODAK_2383
            return VideoProject(
                userId = userId,
                title = title,
                aspectRatio = AspectRatioOption.WIDE_16_9.raw,
                resolution = ResolutionOption.UHD_4K.raw,
                frameRate = 60L,
                colorPreset = preset.raw,
                exposure = preset.exposure.toDouble(),
                contrast = preset.contrast.toDouble(),
                saturation = preset.saturation.toDouble(),
                temperature = preset.temperature.toDouble(),
                tint = preset.tint.toDouble(),
                vignette = preset.vignette.toDouble(),
                captionStyle = CaptionStyle.KINETIC_BOLD.raw,
                aiSummary = "Ready for AI Quick Cuts & Automated Captioning.",
                cloudBackupStatus = "SYNCED"
            ).withTypedContent(clips, audioTracks, captions)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ExportRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val resolution: String = ResolutionOption.UHD_4K.raw,
    val codec: String = CodecOption.HEVC_H265.raw,
    val frameRate: Long = 60L,
    val durationMs: Long = 0L,
    val estimatedSizeMb: Double = 0.0,
    val destination: String = ExportDestination.DEVICE_GALLERY.raw,
    val localOutputUri: String = "",
    val status: String = "COMPLETED",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toFirestoreCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "projectId" to projectId,
        "projectTitle" to projectTitle.trim().ifBlank { "Untitled Edit" }.take(100),
        "resolution" to ResolutionOption.fromRaw(resolution).raw,
        "codec" to CodecOption.fromRaw(codec).raw,
        "frameRate" to if (frameRate in listOf(24L, 30L, 60L)) frameRate else 60L,
        "durationMs" to durationMs.coerceIn(0L, 36000000L),
        "estimatedSizeMb" to estimatedSizeMb.coerceIn(0.0, 50000.0),
        "destination" to ExportDestination.fromRaw(destination).raw,
        "localOutputUri" to localOutputUri.take(500),
        "status" to if (status in listOf("COMPLETED", "FAILED")) status else "COMPLETED",
        "createdAt" to FieldValue.serverTimestamp()
    )
}

data class AiCutSuggestion(
    val clipIndex: Int,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Double,
    val transition: TransitionType,
    val reason: String
)

data class AiQuickCutPlan(
    val summary: String,
    val pacingStyle: String,
    val estimatedTimeSavedMs: Long,
    val cuts: List<AiCutSuggestion>
)

data class AiColorAudioSuggestion(
    val summary: String,
    val colorPreset: ColorPreset,
    val exposure: Double,
    val contrast: Double,
    val saturation: Double,
    val temperature: Double,
    val tint: Double,
    val vignette: Double,
    val voiceoverVolume: Double,
    val musicVolume: Double,
    val originalVolume: Double
)

fun formatTimecode(ms: Long, fps: Long = 30L): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val safeFps = fps.coerceIn(24L, 60L)
    val frames = ((clamped % 1000L) * safeFps) / 1000L
    return "%02d:%02d:%02d".format(minutes, seconds, frames)
}

fun formatDurationShort(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
