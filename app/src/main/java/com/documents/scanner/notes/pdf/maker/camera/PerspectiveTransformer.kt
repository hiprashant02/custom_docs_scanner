package com.documents.scanner.notes.pdf.maker.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

private const val TAG = "PerspectiveTransformer"

object PerspectiveTransformer {
    
    fun transformDocument(
        sourceBitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap? {
        if (corners.size != 4) {
            Log.e(TAG, "Need exactly 4 corners for perspective transform")
            return null
        }
        
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV not initialized")
            return null
        }
        
        try {
            val srcMat = Mat()
            Utils.bitmapToMat(sourceBitmap, srcMat)
            
            val srcPoints = Mat(4, 1, CvType.CV_32FC2)
            srcPoints.put(0, 0,
                corners[0].x.toDouble(), corners[0].y.toDouble(),
                corners[1].x.toDouble(), corners[1].y.toDouble(),
                corners[2].x.toDouble(), corners[2].y.toDouble(),
                corners[3].x.toDouble(), corners[3].y.toDouble()
            )
            
            val width1 = distance(corners[0], corners[1])
            val width2 = distance(corners[3], corners[2])
            val height1 = distance(corners[0], corners[3])
            val height2 = distance(corners[1], corners[2])
            
            val outputWidth = maxOf(width1, width2).toInt()
            val outputHeight = maxOf(height1, height2).toInt()
            
            Log.d(TAG, "Transform output size: ${outputWidth}x${outputHeight}")
            
            val dstPoints = Mat(4, 1, CvType.CV_32FC2)
            dstPoints.put(0, 0,
                0.0, 0.0,
                outputWidth.toDouble(), 0.0,
                outputWidth.toDouble(), outputHeight.toDouble(),
                0.0, outputHeight.toDouble()
            )
            
            val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            
            val dstMat = Mat()
            Imgproc.warpPerspective(
                srcMat,
                dstMat,
                perspectiveMatrix,
                Size(outputWidth.toDouble(), outputHeight.toDouble())
            )
            
            val resultBitmap = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(dstMat, resultBitmap)
            
            srcMat.release()
            srcPoints.release()
            dstPoints.release()
            perspectiveMatrix.release()
            dstMat.release()
            
            Log.d(TAG, "Perspective transform completed successfully")
            return resultBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Perspective transform failed", e)
            return null
        }
    }
    
    fun saveToCacheDir(
        context: Context,
        bitmap: Bitmap,
        filename: String = "captured_document_${System.currentTimeMillis()}"
    ): String? {
        try {
            val cacheDir = File(context.cacheDir, "scanned_documents")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val file = File(cacheDir, "$filename.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            
            Log.d(TAG, "Image saved to: ${file.absolutePath}")
            return file.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            return null
        }
    }
    
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
