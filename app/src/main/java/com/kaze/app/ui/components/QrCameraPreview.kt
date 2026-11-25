package com.kaze.app.ui.components

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Offline CameraX + ZXing QR viewfinder with persimmon corner brackets and sweeping scan beam.
 */
@Composable
fun QrCameraViewfinder(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val scanTransition = rememberInfiniteTransition(label = "qrScan")
    val beamY by scanTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beamY"
    )

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
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
                    val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor, ZxingQrAnalyzer { decoded ->
                                if (decoded.startsWith("kaze://pair")) {
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        onQrScanned(decoded)
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

        // Persimmon viewfinder brackets + sweeping scan line
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padX = size.width * 0.22f
            val padY = size.height * 0.14f
            val left = padX
            val right = size.width - padX
            val top = padY
            val bottom = size.height - padY
            val arm = 22.dp.toPx()
            val stroke = 2.6.dp.toPx()

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

            // Sweeping persimmon beam
            val y = size.height * beamY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        SuzuriColors.Persimmon.copy(alpha = 0f),
                        SuzuriColors.Persimmon,
                        SuzuriColors.Persimmon.copy(alpha = 0f)
                    ),
                    startX = left + 8.dp.toPx(),
                    endX = right - 8.dp.toPx()
                ),
                start = Offset(left + 8.dp.toPx(), y),
                end = Offset(right - 8.dp.toPx(), y),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

private class ZxingQrAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
    private var lastDecodedAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastDecodedAt < 1200L) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
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
                lastDecodedAt = now
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
