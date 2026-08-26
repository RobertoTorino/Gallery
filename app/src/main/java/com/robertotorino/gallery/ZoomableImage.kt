package com.robertotorino.gallery

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    isCropped: Boolean = false,
    isGrayscale: Boolean = false,
    onZoomStateChanged: (Boolean) -> Unit = {},
    onTap: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 3f
                        offset = Offset.Zero
                        onZoomStateChanged(scale > 1f)
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val pointersDown = event.changes.count { it.pressed }
                        val shouldHandleTransform = pointersDown > 1 || scale > 1f

                        if (shouldHandleTransform) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = (size.width.toFloat() * (newScale - 1f)) / 2f
                            val maxY = (size.height.toFloat() * (newScale - 1f)) / 2f

                            val newOffset = if (newScale <= 1f) {
                                Offset.Zero
                            } else {
                                val combined = offset + pan
                                Offset(
                                    x = combined.x.coerceIn(-maxX, maxX),
                                    y = combined.y.coerceIn(-maxY, maxY)
                                )
                            }

                            scale = newScale
                            offset = newOffset
                            onZoomStateChanged(newScale > 1f)

                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) {
                                    change.consume()
                                }
                            }
                        }

                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        val colorFilter = if (isGrayscale) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = if (isCropped) ContentScale.Crop else ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale * (if (flipHorizontal) -1f else 1f),
                    scaleY = scale * (if (flipVertical) -1f else 1f),
                    rotationZ = rotation,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
