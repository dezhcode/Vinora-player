package com.example.ui.components

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class PreRenderedTurntableAssets(
    val manifest: TurntableLayoutManifest,
    val themeName: String,
    val resolutionTier: Int,
    val turntableBase: ImageBitmap,
    val metalPlatter: ImageBitmap,
    val leftKnobNormal: ImageBitmap,
    val leftKnobPressed: ImageBitmap,
    val rightKnobNormal: ImageBitmap,
    val rightKnobPressed: ImageBitmap,
    val vinylRecord: ImageBitmap,
    val vinylReflection: ImageBitmap,
    val centerSpindle: ImageBitmap,
    val tonearmShadowFrames: List<ImageBitmap>,
    val tonearmArmFrames: List<ImageBitmap>,
)

/**
 * Thread-safe pre-decoding & in-memory cache for pre-rendered 3D turntable layer bitmaps.
 * All decoding runs off the UI thread before composition renders the deck.
 */
object TurntableAssetRepository {
    private const val ROOT_ASSET_DIR = "turntable"
    private const val MANIFEST_FILE = "$ROOT_ASSET_DIR/turntable-layout.json"

    @Volatile
    private var cachedManifest: TurntableLayoutManifest? = null
    private val bundleCache = ConcurrentHashMap<String, PreRenderedTurntableAssets>()

    fun clearCacheForTesting() {
        cachedManifest = null
        bundleCache.clear()
    }

    fun loadManifestSync(assets: AssetManager): TurntableLayoutManifest? {
        cachedManifest?.let { return it }
        return runCatching {
            val raw = assets.open(MANIFEST_FILE).bufferedReader().use { it.readText() }
            TurntableLayoutManifest.fromJson(raw).also { cachedManifest = it }
        }.getOrNull()
    }

    fun getCachedAssets(
        assets: AssetManager,
        isDark: Boolean,
        requestedResolution: Int,
    ): PreRenderedTurntableAssets? {
        val manifest = cachedManifest ?: loadManifestSync(assets) ?: return null
        val theme = if (isDark && manifest.themes.contains("dark")) "dark" else "light"
        val res = manifest.resolutions.minByOrNull { kotlin.math.abs(it - requestedResolution) } ?: 1024
        return bundleCache["${theme}_$res"]
    }

    suspend fun loadAssets(
        context: Context,
        isDark: Boolean,
        requestedResolution: Int,
    ): PreRenderedTurntableAssets? = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val manifest = loadManifestSync(assetManager) ?: return@withContext null
        val theme = if (isDark && manifest.themes.contains("dark")) "dark" else "light"
        val res = manifest.resolutions.minByOrNull { kotlin.math.abs(it - requestedResolution) } ?: 1024
        val cacheKey = "${theme}_$res"

        bundleCache[cacheKey]?.let { return@withContext it }

        val folder = "$ROOT_ASSET_DIR/$theme/$res"
        val layers = manifest.layers

        val decoded = runCatching {
            val base = decodeAssetBitmap(assetManager, "$folder/${layers.turntableBase.file}")
                ?: return@runCatching null
            val platter = decodeAssetBitmap(assetManager, "$folder/${layers.metalPlatter.file}")
                ?: return@runCatching null
            val leftNormal = decodeAssetBitmap(assetManager, "$folder/${layers.leftKnobNormal.file}")
                ?: return@runCatching null
            val leftPressed = decodeAssetBitmap(assetManager, "$folder/${layers.leftKnobPressed.file}")
                ?: return@runCatching null
            val rightNormal = decodeAssetBitmap(assetManager, "$folder/${layers.rightKnobNormal.file}")
                ?: return@runCatching null
            val rightPressed = decodeAssetBitmap(assetManager, "$folder/${layers.rightKnobPressed.file}")
                ?: return@runCatching null
            val vinyl = decodeAssetBitmap(assetManager, "$folder/${layers.vinylRecord.file}")
                ?: return@runCatching null
            val reflection = decodeAssetBitmap(assetManager, "$folder/${layers.vinylReflection.file}")
                ?: return@runCatching null
            val spindle = decodeAssetBitmap(assetManager, "$folder/${layers.centerSpindle.file}")
                ?: return@runCatching null

            val frameCount = layers.tonearmArm.frameCount.coerceAtLeast(1)
            val shadows = ArrayList<ImageBitmap>(frameCount)
            val arms = ArrayList<ImageBitmap>(frameCount)
            for (i in 0 until frameCount) {
                val shadowBmp = decodeAssetBitmap(assetManager, "$folder/${layers.tonearmShadow.fileForFrame(i)}")
                    ?: return@runCatching null
                val armBmp = decodeAssetBitmap(assetManager, "$folder/${layers.tonearmArm.fileForFrame(i)}")
                    ?: return@runCatching null
                shadows.add(shadowBmp)
                arms.add(armBmp)
            }

            PreRenderedTurntableAssets(
                manifest = manifest,
                themeName = theme,
                resolutionTier = res,
                turntableBase = base,
                metalPlatter = platter,
                leftKnobNormal = leftNormal,
                leftKnobPressed = leftPressed,
                rightKnobNormal = rightNormal,
                rightKnobPressed = rightPressed,
                vinylRecord = vinyl,
                vinylReflection = reflection,
                centerSpindle = spindle,
                tonearmShadowFrames = shadows,
                tonearmArmFrames = arms,
            )
        }.getOrNull()

        if (decoded != null) {
            bundleCache[cacheKey] = decoded
        }
        decoded
    }

    private fun decodeAssetBitmap(assets: AssetManager, path: String): ImageBitmap? {
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
        }.getOrNull()
    }
}
