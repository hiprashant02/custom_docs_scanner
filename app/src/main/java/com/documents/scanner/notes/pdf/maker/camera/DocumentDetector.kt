package com.documents.scanner.notes.pdf.maker.camera

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

import org.opencv.core.Size

private const val TAG = "DocumentDetector"

class DocumentDetector {
    
    companion object {
        private var isOpenCVInitialized = false
        
        fun initOpenCV(): Boolean {
            if (!isOpenCVInitialized) {
                isOpenCVInitialized = OpenCVLoader.initLocal()
                Log.d(TAG, if (isOpenCVInitialized) "OpenCV initialized" else "OpenCV FAILED")
            }
            return isOpenCVInitialized
        }
    }
    
    fun detectDocument(bitmap: Bitmap): DetectedDocument? {
        if (!isOpenCVInitialized && !initOpenCV()) return null
        
        try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)
            
            val bgrMat = Mat()
            Imgproc.cvtColor(srcMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
            
            val imageArea = srcMat.cols().toDouble() * srcMat.rows().toDouble()
            val imageCenter = Point(srcMat.cols() / 2.0, srcMat.rows() / 2.0)
            
            val squares = findSquares3Channels(bgrMat, imageArea)
            
            srcMat.release()
            bgrMat.release()
            
            val bestSquare = findBestSquare(squares, imageArea, imageCenter)
            
            if (bestSquare != null) {
                val ordered = orderCorners(bestSquare)
                return DetectedDocument(
                    corners = ordered.map { PointF(it.x.toFloat(), it.y.toFloat()) },
                    confidence = 0.9f
                )
            }
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            return null
        }
    }
    
    fun detectDocumentFromGrayscale(grayMat: Mat): DetectedDocument? {
        if (!isOpenCVInitialized && !initOpenCV()) return null
        
        try {
            val imageArea = grayMat.cols().toDouble() * grayMat.rows().toDouble()
            val imageCenter = Point(grayMat.cols() / 2.0, grayMat.rows() / 2.0)
            
            val squares = findSquaresGrayscale(grayMat, imageArea)
            
            val bestSquare = findBestSquare(squares, imageArea, imageCenter)
            
            if (bestSquare != null) {
                val ordered = orderCorners(bestSquare)
                return DetectedDocument(
                    corners = ordered.map { PointF(it.x.toFloat(), it.y.toFloat()) },
                    confidence = 0.85f
                )
            }
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Grayscale detection error", e)
            return null
        }
    }
    
    private fun findSquares3Channels(image: Mat, imageArea: Double): MutableList<List<Point>> {
        val squares = mutableListOf<List<Point>>()
        
        val minArea = imageArea * 0.05
        val maxArea = imageArea * 0.90
        
        val blurred = Mat()
        applyBlur(image, blurred)
        
        val gray0 = Mat(blurred.size(), CvType.CV_8UC1)
        val gray = Mat()
        
        for (c in 0..2) {
            Core.extractChannel(blurred, gray0, c)
            findSquaresInChannel(gray0, gray, minArea, maxArea, squares)
        }
        
        blurred.release()
        gray0.release()
        gray.release()
        
        Log.d(TAG, "[3-Channel] Found ${squares.size} candidate squares")
        return squares
    }
    
    private fun findSquaresGrayscale(grayMat: Mat, imageArea: Double): MutableList<List<Point>> {
        val squares = mutableListOf<List<Point>>()
        
        val minArea = imageArea * 0.05
        val maxArea = imageArea * 0.90
        
        val blurred = Mat()
        applyBlur(grayMat, blurred)
        
        val gray = Mat()
        
        findSquaresInChannel(blurred, gray, minArea, maxArea, squares)
        
        blurred.release()
        gray.release()
        
        Log.d(TAG, "[Grayscale] Found ${squares.size} candidate squares")
        return squares
    }
    
    private fun findSquaresInChannel(
        channel: Mat,
        gray: Mat,
        minArea: Double,
        maxArea: Double,
        squares: MutableList<List<Point>>
    ) {
        val thresholdLevel = 2
        for (l in 0 until thresholdLevel) {
            if (l == 0) {
                applyCannyEdgeDetection(channel, gray)
                applyMorphologicalOps(gray)
            } else {
                val thresh = ((l + 1) * 255 / thresholdLevel).toDouble()
                Imgproc.threshold(channel, gray, thresh, 255.0, Imgproc.THRESH_BINARY)
            }
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(gray, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val arcLen = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, arcLen * 0.02, true)
                
                val approxPoints = approx.toArray()
                
                if (approxPoints.size == 4) {
                    val area = abs(Imgproc.contourArea(approx))
                    val approxMat = MatOfPoint(*approxPoints)
                    
                    if (area >= minArea && area <= maxArea && Imgproc.isContourConvex(approxMat)) {
                        var maxCosine = 0.0
                        for (j in 2..4) {
                            val cosine = abs(angle(
                                approxPoints[j % 4],
                                approxPoints[j - 2],
                                approxPoints[j - 1]
                            ))
                            maxCosine = maxOf(maxCosine, cosine)
                        }
                        
                        if (maxCosine < 0.3) {
                            squares.add(approxPoints.toList())
                        }
                    }
                    approxMat.release()
                }
                
                contour2f.release()
                approx.release()
            }
            
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }
    
    private fun findBestSquare(squares: List<List<Point>>, imageArea: Double, imageCenter: Point): List<Point>? {
        if (squares.isEmpty()) return null
        
        var bestSquare: List<Point>? = null
        var bestScore = -1.0
        
        for (square in squares) {
            val area = abs(Imgproc.contourArea(MatOfPoint2f(*square.toTypedArray())))
            
            val centerX = square.map { it.x }.average()
            val centerY = square.map { it.y }.average()
            val distFromCenter = sqrt((centerX - imageCenter.x) * (centerX - imageCenter.x) + 
                                       (centerY - imageCenter.y) * (centerY - imageCenter.y))
            
            val maxDist = sqrt(imageCenter.x * imageCenter.x + imageCenter.y * imageCenter.y)
            val distScore = 1.0 - (distFromCenter / maxDist)
            val areaScore = area / imageArea
            
            val score = areaScore * 0.8 + distScore * 0.2
            
            if (score > bestScore) {
                bestScore = score
                bestSquare = square
            }
        }
        
        if (bestSquare != null) {
            Log.d(TAG, "Best document found with score ${"%.2f".format(bestScore)}")
        }
        
        return bestSquare
    }
    
    private fun applyBlur(src: Mat, dst: Mat) {
        when (DetectionConfig.blurMode) {
            DetectionConfig.BlurMode.MEDIAN_BLUR_9 -> {
                Imgproc.medianBlur(src, dst, 9)
            }
            DetectionConfig.BlurMode.GAUSSIAN_BLUR_5 -> {
                Imgproc.GaussianBlur(src, dst, Size(5.0, 5.0), 0.0)
            }
        }
    }
    
    private fun applyMorphologicalOps(mat: Mat) {
        when (DetectionConfig.morphMode) {
            DetectionConfig.MorphMode.DILATE_ONLY -> {
                Imgproc.dilate(mat, mat, Mat())
            }
            DetectionConfig.MorphMode.MORPH_CLOSE_WITH_ERODE -> {
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, kernel)
                Imgproc.erode(mat, mat, kernel)
                kernel.release()
            }
            DetectionConfig.MorphMode.MORPH_CLOSE_3x3 -> {
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, kernel)
                kernel.release()
            }
            DetectionConfig.MorphMode.MORPH_CLOSE_5x5 -> {
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, kernel)
                kernel.release()
            }
            DetectionConfig.MorphMode.MORPH_CLOSE_LEARNOPENCV -> {
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                for (i in 0 until 3) {
                    Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE, kernel)
                }
                kernel.release()
            }
        }
    }
    
    private fun applyCannyEdgeDetection(src: Mat, dst: Mat) {
        when (DetectionConfig.cannyMode) {
            DetectionConfig.CannyMode.FIXED_10_20 -> {
                Imgproc.Canny(src, dst, 10.0, 20.0, 3)
            }
            DetectionConfig.CannyMode.FIXED_75_200 -> {
                Imgproc.Canny(src, dst, 75.0, 200.0, 3)
            }
            DetectionConfig.CannyMode.AUTO_OTSU -> {
                val otsuThresh = calculateOtsuThreshold(src)
                val lowThresh = otsuThresh * 0.5
                val highThresh = otsuThresh
                Imgproc.Canny(src, dst, lowThresh, highThresh, 3)
            }
        }
    }
    
    private fun calculateOtsuThreshold(src: Mat): Double {
        val tempBinary = Mat()
        val otsuThreshValue = Imgproc.threshold(
            src, tempBinary, 0.0, 255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
        )
        tempBinary.release()
        return otsuThreshValue
    }
    
    private fun angle(pt1: Point, pt2: Point, pt0: Point): Double {
        val dx1 = pt1.x - pt0.x
        val dy1 = pt1.y - pt0.y
        val dx2 = pt2.x - pt0.x
        val dy2 = pt2.y - pt0.y
        return (dx1 * dx2 + dy1 * dy2) / sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10)
    }
    
    private fun orderCorners(points: List<Point>): List<Point> {
        val sortedBySum = points.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()
        
        val sortedByDiff = points.sortedBy { it.x - it.y }
        val bottomLeft = sortedByDiff.first()
        val topRight = sortedByDiff.last()
        
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
}
