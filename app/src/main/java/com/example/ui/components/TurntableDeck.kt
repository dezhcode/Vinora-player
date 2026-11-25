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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.example.R
import com.example.ui.theme.LocalPlinthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Hardware-accelerated Three.js + WebGL 3D Turntable Deck hosted offline via WebViewAssetLoader.
 * Renders a real multi-part 3D turntable (chamfered CNC plinth, 180g beveled vinyl LP with
 * InstancedMesh grooves & PBR clearcoat/normal maps, printed artwork label, chrome spindle,
 * articulated gimbal tonearm with MC cartridge & diamond stylus, 3D rotary volume dial, and
 * physical start/stop plunger button) at 60/120 FPS with strict lifecycle & GPU cleanup.
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
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPlinthColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val cdTurntableText = stringResource(R.string.cd_turntable)

    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubDelta by rememberUpdatedState(onScrubDeltaMs)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestHaptics by rememberUpdatedState(hapticsEnabled)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSceneReady by remember { mutableStateOf(false) }
    var artworkDataUri by remember { mutableStateOf<String?>(null) }
    var lastHandledSwapNonce by remember { mutableStateOf(trackChangeNonce) }

    // Decode & downscale album artwork off the UI thread for crisp Three.js texture upload
    LaunchedEffect(artworkUri) {
        artworkDataUri = encodeArtworkAsDataUri(context, artworkUri)
    }

    val sceneAlpha by animateFloatAsState(
        targetValue = if (isSceneReady) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "turntableWebGLFade",
    )

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun onTogglePlay() {
                mainHandler.post {
                    if (latestHaptics) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
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
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    // Pause WebGL render loop automatically when app/screen pauses
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
                wv.evaluateJavascript("window.plinthTurntable?.setPaused(true);", null)
                wv.removeJavascriptInterface("AndroidBridge")
                wv.destroy()
            }
            webViewRef = null
        }
    }

    // Push reactive state updates to the 3D Three.js scene
    LaunchedEffect(
        isSceneReady,
        isPlaying,
        progressFraction,
        volume,
        palette.isDark,
        reducedMotion,
        artworkDataUri,
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isSceneReady) return@LaunchedEffect

        val payload = JSONObject().apply {
            put("isPlaying", isPlaying)
            put("progress", progressFraction.toDouble().coerceIn(0.0, 1.0))
            put("volume", volume.toDouble().coerceIn(0.0, 1.0))
            put("isDark", palette.isDark)
            put("reducedMotion", reducedMotion)
            put("artworkDataUri", artworkDataUri ?: JSONObject.NULL)
        }
        wv.evaluateJavascript("window.plinthTurntable?.setState($payload);", null)
    }

    // Trigger physical vinyl swap animation when user skips tracks
    LaunchedEffect(isSceneReady, trackChangeNonce) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isSceneReady) return@LaunchedEffect
        if (trackChangeNonce != lastHandledSwapNonce) {
            lastHandledSwapNonce = trackChangeNonce
            wv.evaluateJavascript("window.plinthTurntable?.triggerTrackSwap($trackChangeDirection);", null)
        }
    }

    Box(
        modifier = modifier
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
    ) {
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

                    // Keep circular vinyl scratch gestures uninterrupted by parent containers
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
                    }

                    webViewRef = this
                    loadUrl("https://appassets.androidplatform.net/assets/turntable/index.html")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = sceneAlpha },
        )
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

            val targetSize = 384
            val scaled = if (rawBitmap.width > targetSize || rawBitmap.height > targetSize) {
                Bitmap.createScaledBitmap(rawBitmap, targetSize, targetSize, true).also {
                    if (it != rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, baos)
            scaled.recycle()
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }.getOrNull()
    }
}
