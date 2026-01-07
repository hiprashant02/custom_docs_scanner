package com.documents.scanner.notes.pdf.maker.camera

object DetectionConfig {
    
    enum class DetectionMode {
        BITMAP_BGR_3CHANNELS,
        PLANES_GRAYSCALE
    }
    
    enum class BlurMode {
        MEDIAN_BLUR_9,
        GAUSSIAN_BLUR_5
    }
    
    //auto otsu is the best option
    //other options were creating problem
    enum class CannyMode {
        FIXED_10_20,
        FIXED_75_200,
        AUTO_OTSU
    }
    
    var currentMode: DetectionMode = DetectionMode.BITMAP_BGR_3CHANNELS
    var blurMode: BlurMode = BlurMode.GAUSSIAN_BLUR_5
    var cannyMode: CannyMode = CannyMode.AUTO_OTSU
    var enableLogging: Boolean = true
}
