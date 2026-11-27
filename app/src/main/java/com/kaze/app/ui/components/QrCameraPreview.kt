package com.kaze.app.ui.components

import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kaze.app.ui.theme.SuzuriColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline CameraX + ZXing QR viewfinder with persimmon corner brackets and sweeping scan beam.
 * Properly unbinds CameraX on dispose, throttles QR decoding to ~5.5 Hz, and reuses the YUV buffer.
 */
@Composable
fun QrCameraViewfinder(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderHolder = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val isDisposed = remember { AtomicBoolean(false) }

    val scanTransition = rememberInfiniteTransition(label = "qrScan")
    val beamYState = scanTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beamY"
    )

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            runCatching { cameraProviderHolder.value?.unbindAll() }
            cameraProviderHolder.value = null
            executor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SuzuriColors.Surface)
            .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    if (isDisposed.get()) return@addListener
                    val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    if (isDisposed.get()) return@addListener
                    cameraProviderHolder.value = provider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor, ZxingQrAnalyzer { decoded ->
                                if (!isDisposed.get() && decoded.startsWith("kaze://pair")) {
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        if (!isDisposed.get()) {
                                            onQrScanned(decoded)
                                        }
                                    }
                                }
                            })
                        }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Persimmon viewfinder brackets + sweeping scan line (cached Brush, zero recompositions)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val padX = size.width * 0.22f
                    val padY = size.height * 0.14f
                    val left = padX
                    val right = size.width - padX
                    val top = padY
                    val bottom = size.height - padY
                    val arm = 22.dp.toPx()
                    val stroke = 2.6.dp.toPx()
                    val beamInset = 8.dp.toPx()
                    val beamWidth = 2.dp.toPx()
                    val beamBrush = Brush.horizontalGradient(
                        colors = listOf(
                            SuzuriColors.Persimmon.copy(alpha = 0f),
                            SuzuriColors.Persimmon,
                            SuzuriColors.Persimmon.copy(alpha = 0f)
                        ),
                        startX = left + beamInset,
                        endX = right - beamInset
                    )

                    onDrawBehind {
                        // Top-left
                        drawLine(SuzuriColors.Persimmon, Offset(left, top), Offset(left + arm, top), stroke, StrokeCap.Round)
                        drawLine(SuzuriColors.Persimmon, Offset(left, top), Offset(left, top + arm), stroke, StrokeCap.Round)
                        // Top-right
                        drawLine(SuzuriColors.Persimmon, Offset(right, top), Offset(right - arm, top), stroke, StrokeCap.Round)
                        drawLine(SuzuriColors.Persimmon, Offset(right, top), Offset(right, top + arm), stroke, StrokeCap.Round)
                        // Bottom-left
                        drawLine(SuzuriColors.Persimmon, Offset(left, bottom), Offset(left + arm, bottom), stroke, StrokeCap.Round)
                        drawLine(SuzuriColors.Persimmon, Offset(left, bottom), Offset(left, bottom - arm), stroke, StrokeCap.Round)
                        // Bottom-right
                        drawLine(SuzuriColors.Persimmon, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
                        drawLine(SuzuriColors.Persimmon, Offset(right, bottom), Offset(right, bottom - arm), stroke, StrokeCap.Round)

                        // Sweeping persimmon beam (deferred State read inside draw phase)
                        val y = size.height * beamYState.value
                        drawLine(
                            brush = beamBrush,
                            start = Offset(left + beamInset, y),
                            end = Offset(right - beamInset, y),
                            strokeWidth = beamWidth
                        )
                    }
                }
        )
    }
}

private class ZxingQrAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
        )
    }
    private var reusableBuffer = ByteArray(0)
    private var lastAnalyzedAtMs = 0L
    private var lastDecodedAtMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            // Throttle analysis to ~5.5 Hz and debounce successful scans to save CPU/battery
            if (now - lastAnalyzedAtMs < 180L || now - lastDecodedAtMs < 1200L) return
            lastAnalyzedAtMs = now

            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val remaining = buffer.remaining()
            if (reusableBuffer.size < remaining) {
                reusableBuffer = ByteArray(remaining)
            }
            buffer.get(reusableBuffer, 0, remaining)

            val source = PlanarYUVLuminanceSource(
                reusableBuffer,
                plane.rowStride,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            if (result?.text != null) {
                lastDecodedAtMs = now
                onDecoded(result.text)
            }
        } catch (_: Throwable) {
            // Frame had no QR code
        } finally {
            reader.reset()
            image.close()
        }
    }
}
