package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewAssetLoader
import com.example.R
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.GraphicsQuality
import com.example.ui.theme.LocalPlinthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Adaptive Three.js + WebGL Top-Down Orthographic 3D Turntable Deck with automatic
 * GPU tier management, WebGL context-loss recovery, and zero-lag 2D Canvas fallback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurntableDeck(
    artworkUri: String?,
    isPlaying: Boolean,
    progressFraction: Float,
    volume: Float,
    trackChangeNonce: Int,
    trackChangeDirection: Int,
    graphicsQuality: GraphicsQuality,
    batterySaverMode: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPlinthColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cdTurntableText = stringResource(R.string.cd_turntable)

    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubDelta by rememberUpdatedState(onScrubDeltaMs)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSceneReady by remember { mutableStateOf(false) }
    var useFallback2D by remember { mutableStateOf(false) }
    var artworkDataUri by remember { mutableStateOf<String?>(null) }
    var lastHandledSwapNonce by remember { mutableIntStateOf(trackChangeNonce) }

    // Quantize progress sent over JS bridge (0.5% steps) so 120ms UI position ticks never flood WebView
    val quantizedProgress = remember(progressFraction) {
        ((progressFraction.coerceIn(0f, 1f) * 200f).roundToInt() / 200f)
    }
    val quantizedVolume = remember(volume) {
        ((volume.coerceIn(0f, 1f) * 100f).roundToInt() / 100f)
    }

    LaunchedEffect(artworkUri) {
        artworkDataUri = encodeArtworkAsDataUri(context, artworkUri)
    }

    val sceneAlpha by animateFloatAsState(
        targetValue = if (isSceneReady || useFallback2D) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 80 else 220),
        label = "turntableWebGLFade",
    )

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun onTogglePlay() {
                mainHandler.post {
                    PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                    latestOnTogglePlay()
                }
            }

            @JavascriptInterface
            fun onScrubDelta(deltaMs: Long) {
                mainHandler.post {
                    latestOnScrubDelta(deltaMs)
                }
            }

            @JavascriptInterface
            fun onVolumeChange(newVolume: Float) {
                mainHandler.post {
                    latestOnVolumeChange(newVolume.coerceIn(0f, 1f))
                }
            }

            @JavascriptInterface
            fun onWebGLContextLost() {
                mainHandler.post {
                    useFallback2D = true
                }
            }

            @JavascriptInterface
            fun onWebGLContextRestored() {
                mainHandler.post {
                    useFallback2D = false
                }
            }

            @JavascriptInterface
            fun onWebGLFailed(reason: String) {
                mainHandler.post {
                    useFallback2D = true
                }
            }
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wv.evaluateJavascript("window.plinthTurntable?.setPaused(true);", null)
                    wv.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    wv.onResume()
                    wv.evaluateJavascript("window.plinthTurntable?.setPaused(false);", null)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.evaluateJavascript("window.plinthTurntable?.dispose();", null)
                wv.removeJavascriptInterface("AndroidBridge")
                wv.destroy()
            }
            webViewRef = null
        }
    }

    LaunchedEffect(
        isSceneReady,
        useFallback2D,
        isPlaying,
        quantizedProgress,
        quantizedVolume,
        palette.isDark,
        graphicsQuality,
        batterySaverMode,
        reducedMotion,
        artworkDataUri,
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isSceneReady || useFallback2D) return@LaunchedEffect

        val payload = JSONObject().apply {
            put("isPlaying", isPlaying)
            put("progress", quantizedProgress.toDouble())
            put("volume", quantizedVolume.toDouble())
            put("isDark", palette.isDark)
            put("graphicsQuality", graphicsQuality.name)
            put("batterySaver", batterySaverMode)
            put("reducedMotion", reducedMotion)
            put("artworkDataUri", artworkDataUri ?: JSONObject.NULL)
        }
        wv.evaluateJavascript("window.plinthTurntable?.setState($payload);", null)
    }

    LaunchedEffect(isSceneReady, useFallback2D, trackChangeNonce) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isSceneReady || useFallback2D) return@LaunchedEffect
        if (trackChangeNonce != lastHandledSwapNonce) {
            lastHandledSwapNonce = trackChangeNonce
            wv.evaluateJavascript("window.plinthTurntable?.triggerTrackSwap($trackChangeDirection);", null)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
    ) {
        if (!useFallback2D) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(Color.TRANSPARENT)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }

                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            false
                        }

                        addJavascriptInterface(jsBridge, "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                return assetLoader.shouldInterceptRequest(request.url)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                isSceneReady = true
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                useFallback2D = true
                                return true
                            }
                        }

                        webViewRef = this
                        loadUrl("https://appassets.androidplatform.net/assets/turntable/index.html")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sceneAlpha },
            )
        } else {
            // Lightweight Compose Canvas Fallback when WebGL context is lost/unavailable
            FallbackFlatTurntableCanvas(
                artworkUri = artworkUri,
                isPlaying = isPlaying,
                progressFraction = quantizedProgress,
                reducedMotion = reducedMotion || batterySaverMode,
                onScrubDeltaMs = onScrubDeltaMs,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        }
    }
}

/**
 * Pure Compose 2D Flat Top-Down Fallback Deck preserving Plinth's luxury monochrome aesthetic
 * if a low-end GPU loses its WebGL context or fails WebGL initialization.
 */
@Composable
private fun FallbackFlatTurntableCanvas(
    artworkUri: String?,
    isPlaying: Boolean,
    progressFraction: Float,
    reducedMotion: Boolean,
    onScrubDeltaMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var discAngleDeg by remember { mutableFloatStateOf(0f) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }

    // 33⅓ RPM = 200 degrees per second
    LaunchedEffect(isPlaying, reducedMotion, lifecycleOwner) {
        if (!isPlaying) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastNs = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastNs != 0L) {
                        val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        val degPerSec = if (reducedMotion) 36f else 200f
                        discAngleDeg = (discAngleDeg + degPerSec * dt) % 360f
                    }
                    lastNs = now
                }
            }
        }
    }

    val chassisShape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1.04f)
                .clip(chassisShape)
                .background(Brush.linearGradient(listOf(palette.plinthTop, palette.plinthBottom)))
                .border(1.5.dp, palette.plinthEdgeLight, chassisShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val cx = size.width * 0.46f
                            val cy = size.height * 0.52f
                            lastTouchAngle = atan2(start.y - cy, start.x - cx)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width * 0.46f
                            val cy = size.height * 0.52f
                            val a = atan2(change.position.y - cy, change.position.x - cx)
                            var delta = a - lastTouchAngle
                            if (delta > PI) delta -= (2 * PI).toFloat()
                            if (delta < -PI) delta += (2 * PI).toFloat()
                            lastTouchAngle = a
                            discAngleDeg = (discAngleDeg + Math.toDegrees(delta.toDouble()).toFloat()) % 360f
                            val ms = ((delta / (2 * PI)) * 5500.0).roundToInt().toLong()
                            if (ms != 0L) onScrubDeltaMs(ms)
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val platterCenter = Offset(w * 0.46f, h * 0.52f)
                val discR = size.minDimension * 0.37f

                // Platter metal rim
                drawCircle(
                    color = palette.plinthEdgeShadow.copy(alpha = 0.7f),
                    radius = discR * 1.055f,
                    center = platterCenter,
                )
                // Deep black vinyl LP
                drawCircle(
                    color = ComposeColor(0xFF07080B),
                    radius = discR,
                    center = platterCenter,
                )

                // Subtle rotating sheen & concentric grooves
                rotate(discAngleDeg, pivot = platterCenter) {
                    for (g in 1..6) {
                        val gr = discR * (0.42f + g * 0.088f)
                        drawCircle(
                            color = ComposeColor.White.copy(alpha = 0.045f),
                            radius = gr,
                            center = platterCenter,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawArc(
                        color = ComposeColor.White.copy(alpha = 0.12f),
                        startAngle = -38f,
                        sweepAngle = 44f,
                        useCenter = false,
                        topLeft = Offset(platterCenter.x - discR * 0.75f, platterCenter.y - discR * 0.75f),
                        size = androidx.compose.ui.geometry.Size(discR * 1.5f, discR * 1.5f),
                        style = Stroke(width = discR * 0.32f),
                    )
                }

                // Minimalist center spindle
                drawCircle(
                    color = palette.inkSecondary,
                    radius = discR * 0.33f,
                    center = platterCenter,
                )
                drawCircle(
                    color = ComposeColor(0xFFE6ECF5),
                    radius = discR * 0.045f,
                    center = platterCenter,
                )

                // Articulated horizontal tonearm
                val pivot = Offset(w * 0.85f, h * 0.22f)
                drawCircle(color = palette.plinthEdgeShadow, radius = 16.dp.toPx(), center = pivot)
                drawCircle(color = palette.plinthTop, radius = 11.dp.toPx(), center = pivot)

                val sweepDeg = if (isPlaying) 20f + progressFraction * 18f else 2f
                val armLen = hypot((platterCenter.x - pivot.x).toDouble(), (platterCenter.y - pivot.y).toDouble()).toFloat() * 0.98f
                val rad = Math.toRadians((90f + sweepDeg).toDouble())
                val tip = Offset(
                    pivot.x + (armLen * cos(rad)).toFloat(),
                    pivot.y + (armLen * sin(rad)).toFloat(),
                )
                val armPath = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    lineTo(tip.x, tip.y)
                }
                drawPath(armPath, color = ComposeColor(0xFF1A1E26), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color = ComposeColor(0xFFD2DAE6), radius = 4.dp.toPx(), center = tip)
            }

            if (!artworkUri.isNullOrBlank()) {
                CircularArtworkDisc(
                    artworkUri = artworkUri,
                    contentDescription = stringResource(R.string.cd_album_art),
                    showInnerRing = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = discAngleDeg }
                        .fillMaxSize(0.24f),
                )
            }
        }
    }
}

private suspend fun encodeArtworkAsDataUri(context: Context, artworkUri: String?): String? {
    if (artworkUri.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(artworkUri)
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null

            val targetSize = 256
            val scaled = if (rawBitmap.width > targetSize || rawBitmap.height > targetSize) {
                Bitmap.createScaledBitmap(rawBitmap, targetSize, targetSize, true).also {
                    if (it != rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 86, baos)
            scaled.recycle()
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }.getOrNull()
    }
}
