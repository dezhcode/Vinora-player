package com.example.ui.components

import androidx.compose.runtime.Immutable
import com.example.data.GraphicsQuality
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

@Immutable
data class NormPoint(
    val x: Float,
    val y: Float,
)

@Immutable
data class NormBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Immutable
data class LayerSpec(
    val file: String?,
    val filePattern: String?,
    val zIndex: Int,
    val bounds: NormBounds,
    val transformOrigin: NormPoint,
    val frameCount: Int = 1,
) {
    fun fileForFrame(frameIndex: Int): String {
        val pattern = filePattern ?: return file.orEmpty()
        val padded = frameIndex.toString().padStart(2, '0')
        return pattern.replace("{index}", padded)
    }
}

@Immutable
data class TurntableGeometry(
    val vinylCenter: NormPoint,
    val platterRimRadius: Float,
    val vinylOuterRadius: Float,
    val playableOuterRadius: Float,
    val playableInnerRadius: Float,
    val albumLabelRadius: Float,
    val spindleRadius: Float,
    val tonearmPivot: NormPoint,
    val tonearmEffectiveLength: Float,
    val tonearmRestAngleDeg: Float,
    val tonearmOuterGrooveAngleDeg: Float,
    val tonearmInnerGrooveAngleDeg: Float,
    val tonearmHeadShellOffsetDeg: Float,
    val tonearmKeyframeAnglesDeg: List<Float>,
    val leftKnobCenter: NormPoint,
    val leftKnobRadius: Float,
    val rightKnobCenter: NormPoint,
    val rightKnobRadius: Float,
)

@Immutable
data class TurntableLayers(
    val turntableBase: LayerSpec,
    val metalPlatter: LayerSpec,
    val leftKnobNormal: LayerSpec,
    val leftKnobPressed: LayerSpec,
    val rightKnobNormal: LayerSpec,
    val rightKnobPressed: LayerSpec,
    val vinylRecord: LayerSpec,
    val vinylReflection: LayerSpec,
    val centerSpindle: LayerSpec,
    val tonearmShadow: LayerSpec,
    val tonearmArm: LayerSpec,
)

@Immutable
data class TurntableLayoutManifest(
    val schemaVersion: Int,
    val referenceSizePx: Int,
    val aspectRatio: Float,
    val themes: List<String>,
    val resolutions: List<Int>,
    val geometry: TurntableGeometry,
    val layers: TurntableLayers,
) {
    companion object {
        fun fromJson(rawJson: String): TurntableLayoutManifest {
            val root = JSONObject(rawJson)
            val geomObj = root.getJSONObject("geometry")
            val layersObj = root.getJSONObject("layers")

            val themesArr = root.getJSONArray("themes")
            val themes = (0 until themesArr.length()).map { themesArr.getString(it) }

            val resArr = root.getJSONArray("resolutions")
            val resolutions = (0 until resArr.length()).map { resArr.getInt(it) }.sorted()

            val anglesArr = geomObj.getJSONArray("tonearmKeyframeAnglesDeg")
            val keyframeAngles = (0 until anglesArr.length()).map { anglesArr.getDouble(it).toFloat() }

            val geometry = TurntableGeometry(
                vinylCenter = parsePoint(geomObj.getJSONObject("vinylCenter")),
                platterRimRadius = geomObj.getDouble("platterRimRadius").toFloat(),
                vinylOuterRadius = geomObj.getDouble("vinylOuterRadius").toFloat(),
                playableOuterRadius = geomObj.getDouble("playableOuterRadius").toFloat(),
                playableInnerRadius = geomObj.getDouble("playableInnerRadius").toFloat(),
                albumLabelRadius = geomObj.getDouble("albumLabelRadius").toFloat(),
                spindleRadius = geomObj.getDouble("spindleRadius").toFloat(),
                tonearmPivot = parsePoint(geomObj.getJSONObject("tonearmPivot")),
                tonearmEffectiveLength = geomObj.getDouble("tonearmEffectiveLength").toFloat(),
                tonearmRestAngleDeg = geomObj.getDouble("tonearmRestAngleDeg").toFloat(),
                tonearmOuterGrooveAngleDeg = geomObj.getDouble("tonearmOuterGrooveAngleDeg").toFloat(),
                tonearmInnerGrooveAngleDeg = geomObj.getDouble("tonearmInnerGrooveAngleDeg").toFloat(),
                tonearmHeadShellOffsetDeg = geomObj.getDouble("tonearmHeadShellOffsetDeg").toFloat(),
                tonearmKeyframeAnglesDeg = keyframeAngles,
                leftKnobCenter = parsePoint(geomObj.getJSONObject("leftKnobCenter")),
                leftKnobRadius = geomObj.getDouble("leftKnobRadius").toFloat(),
                rightKnobCenter = parsePoint(geomObj.getJSONObject("rightKnobCenter")),
                rightKnobRadius = geomObj.getDouble("rightKnobRadius").toFloat(),
            )

            val layers = TurntableLayers(
                turntableBase = parseLayer(layersObj.getJSONObject("turntableBase")),
                metalPlatter = parseLayer(layersObj.getJSONObject("metalPlatter")),
                leftKnobNormal = parseLayer(layersObj.getJSONObject("leftKnobNormal")),
                leftKnobPressed = parseLayer(layersObj.getJSONObject("leftKnobPressed")),
                rightKnobNormal = parseLayer(layersObj.getJSONObject("rightKnobNormal")),
                rightKnobPressed = parseLayer(layersObj.getJSONObject("rightKnobPressed")),
                vinylRecord = parseLayer(layersObj.getJSONObject("vinylRecord")),
                vinylReflection = parseLayer(layersObj.getJSONObject("vinylReflection")),
                centerSpindle = parseLayer(layersObj.getJSONObject("centerSpindle")),
                tonearmShadow = parseLayer(layersObj.getJSONObject("tonearmShadow")),
                tonearmArm = parseLayer(layersObj.getJSONObject("tonearmArm")),
            )

            return TurntableLayoutManifest(
                schemaVersion = root.getInt("schemaVersion"),
                referenceSizePx = root.getInt("referenceSizePx"),
                aspectRatio = root.optDouble("aspectRatio", 1.0).toFloat(),
                themes = themes,
                resolutions = resolutions,
                geometry = geometry,
                layers = layers,
            )
        }

        private fun parsePoint(obj: JSONObject): NormPoint = NormPoint(
            x = obj.getDouble("x").toFloat(),
            y = obj.getDouble("y").toFloat(),
        )

        private fun parseBounds(obj: JSONObject): NormBounds = NormBounds(
            x = obj.getDouble("x").toFloat(),
            y = obj.getDouble("y").toFloat(),
            width = obj.getDouble("width").toFloat(),
            height = obj.getDouble("height").toFloat(),
        )

        private fun parseLayer(obj: JSONObject): LayerSpec = LayerSpec(
            file = if (obj.has("file") && !obj.isNull("file")) obj.getString("file") else null,
            filePattern = if (obj.has("filePattern") && !obj.isNull("filePattern")) obj.getString("filePattern") else null,
            zIndex = obj.getInt("zIndex"),
            bounds = parseBounds(obj.getJSONObject("bounds")),
            transformOrigin = parsePoint(obj.getJSONObject("transformOrigin")),
            frameCount = obj.optInt("frameCount", 1),
        )
    }
}

@Immutable
data class TonearmKeyframeSelection(
    val frameIndex: Int,
    val keyframeAngleDeg: Float,
    val residualRotationZDeg: Float,
)

object TurntableKinematics {
    /**
     * Exact Law-of-Cosines tonearm tracking yaw in Three.js degrees using the normalized
     * platter center, gimbal pivot, effective tonearm length, and outer/inner groove radii
     * extracted from `turntable-layout.json`.
     */
    fun computeGrooveAngleDeg(geometry: TurntableGeometry, progressFraction: Float): Float {
        val p = progressFraction.coerceIn(0f, 1f)
        val r = geometry.playableOuterRadius -
            (geometry.playableOuterRadius - geometry.playableInnerRadius) * p

        val dx = geometry.vinylCenter.x - geometry.tonearmPivot.x
        val dy = geometry.vinylCenter.y - geometry.tonearmPivot.y
        val dist = hypot(dx, dy)
        val effLen = geometry.tonearmEffectiveLength

        if (dist <= 0.0001f || effLen <= 0.0001f) {
            return geometry.tonearmOuterGrooveAngleDeg +
                (geometry.tonearmInnerGrooveAngleDeg - geometry.tonearmOuterGrooveAngleDeg) * p
        }

        val thetaPc = atan2(dx, dy)
        val cosAlpha = ((dist * dist + effLen * effLen - r * r) / (2f * dist * effLen))
            .coerceIn(-1f, 1f)
        val yawRad = thetaPc + acos(cosAlpha)
        return Math.toDegrees(yawRad.toDouble()).toFloat()
    }

    /**
     * Picks the nearest pre-rendered tonearm keyframe and computes the small GPU `rotationZ`
     * residual angle (sign inverted vs Three.js Y-yaw so clockwise screen motion matches).
     */
    fun selectTonearmKeyframe(
        targetAngleDeg: Float,
        keyframeAnglesDeg: List<Float>,
    ): TonearmKeyframeSelection {
        if (keyframeAnglesDeg.isEmpty()) {
            return TonearmKeyframeSelection(0, targetAngleDeg, 0f)
        }
        var bestIdx = 0
        var bestDist = abs(targetAngleDeg - keyframeAnglesDeg[0])
        for (i in 1 until keyframeAnglesDeg.size) {
            val d = abs(targetAngleDeg - keyframeAnglesDeg[i])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        val keyAngle = keyframeAnglesDeg[bestIdx]
        // Three.js more-negative yaw swings stylus left (clockwise around pivot on screen -> +rotationZ)
        val residualRotationZ = -(targetAngleDeg - keyAngle)
        return TonearmKeyframeSelection(
            frameIndex = bestIdx,
            keyframeAngleDeg = keyAngle,
            residualRotationZDeg = residualRotationZ,
        )
    }

    /**
     * Selects optimal pre-rendered asset resolution tier (512 / 1024 / 2048) matching device
     * viewport size, battery saver setting, and user GraphicsQuality preference.
     */
    fun selectResolutionTier(
        graphicsQuality: GraphicsQuality,
        batterySaverMode: Boolean,
        containerSizePx: Int,
        availableResolutions: List<Int> = listOf(512, 1024, 2048),
    ): Int {
        val sorted = availableResolutions.ifEmpty { listOf(1024) }.sorted()
        val desired = when {
            batterySaverMode || graphicsQuality == GraphicsQuality.LOW -> 512
            graphicsQuality == GraphicsQuality.MEDIUM -> 1024
            graphicsQuality == GraphicsQuality.HIGH -> if (containerSizePx >= 900) 2048 else 1024
            else -> when {
                containerSizePx <= 620 -> 512
                containerSizePx <= 1250 -> 1024
                else -> 2048
            }
        }
        return sorted.minByOrNull { abs(it - desired) } ?: 1024
    }
}
