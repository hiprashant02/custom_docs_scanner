package com.documents.scanner.notes.pdf.maker.camera

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity

private val OverlayFillColor = Color(0x4000FF00)
private val OverlayStrokeColor = Color(0xFF00FF00)
private val CornerColor = Color.White
private const val StrokeWidth = 6f
private const val CornerRadius = 14f

@Composable
fun DocumentOverlay(
    detectedDocument: DetectedDocument?,
    imageWidth: Int,
    imageHeight: Int,
    imageRotation: Int = 0,
    isFrontCamera: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = with(LocalDensity.current) { maxWidth.toPx() }
        val canvasHeight = with(LocalDensity.current) { maxHeight.toPx() }
        
        val transformation = remember(imageWidth, imageHeight, canvasWidth, canvasHeight) {
            calculateFillCenterTransform(
                sourceWidth = imageWidth.toFloat(),
                sourceHeight = imageHeight.toFloat(),
                targetWidth = canvasWidth,
                targetHeight = canvasHeight
            )
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (detectedDocument == null || imageWidth <= 0 || imageHeight <= 0) {
                return@Canvas
            }
            
            val screenCorners = detectedDocument.corners.map { corner ->
                val transformed = transformation.transform(corner.x, corner.y)
                Offset(transformed.x, transformed.y)
            }
            
            drawQuadrilateral(screenCorners)
            drawCornerCircles(screenCorners)
        }
    }
}

private data class FillCenterTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun transform(x: Float, y: Float): PointF {
        return PointF(
            x * scale + offsetX,
            y * scale + offsetY
        )
    }
}

private fun calculateFillCenterTransform(
    sourceWidth: Float,
    sourceHeight: Float,
    targetWidth: Float,
    targetHeight: Float
): FillCenterTransform {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return FillCenterTransform(1f, 0f, 0f)
    }
    
    val sourceAspect = sourceWidth / sourceHeight
    val targetAspect = targetWidth / targetHeight
    
    val scale: Float
    val offsetX: Float
    val offsetY: Float
    
    if (sourceAspect > targetAspect) {
        scale = targetHeight / sourceHeight
        offsetX = (targetWidth - sourceWidth * scale) / 2f
        offsetY = 0f
    } else {
        scale = targetWidth / sourceWidth
        offsetX = 0f
        offsetY = (targetHeight - sourceHeight * scale) / 2f
    }
    
    return FillCenterTransform(scale, offsetX, offsetY)
}

private fun DrawScope.drawQuadrilateral(corners: List<Offset>) {
    if (corners.size != 4) return
    
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        lineTo(corners[1].x, corners[1].y)
        lineTo(corners[2].x, corners[2].y)
        lineTo(corners[3].x, corners[3].y)
        close()
    }
    
    drawPath(path, OverlayFillColor, style = Fill)
    
    drawPath(
        path,
        OverlayStrokeColor,
        style = Stroke(width = StrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawCornerCircles(corners: List<Offset>) {
    corners.forEach { corner ->
        drawCircle(CornerColor, radius = CornerRadius, center = corner)
        drawCircle(
            OverlayStrokeColor,
            radius = CornerRadius,
            center = corner,
            style = Stroke(width = 3f)
        )
    }
}
