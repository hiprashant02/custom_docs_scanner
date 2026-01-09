package com.documents.scanner.notes.pdf.maker.camera

object DetectionConfig {
    
    // Phase 7: Added HSV_SATURATION for colored document detection
    // Phase 8: Added MORPH_GRADIENT for color-independent detection
    // Phase 9: COMBINED_AUTO - runs all methods, picks best result
    // Based on SO: https://stackoverflow.com/questions/8667818 (answer by mmgp)
    enum class DetectionMode {
        COMBINED_AUTO,            // Runs all methods, picks best (most robust)
        BITMAP_BGR_3CHANNELS,     // Best for white paper
        HSV_SATURATION,           // Best for colored backgrounds
        MORPH_GRADIENT,           // Color-independent (dilation-erosion)
        PLANES_GRAYSCALE
    }
    
    //Gaussian blur is best option -> tested
    enum class BlurMode {
        MEDIAN_BLUR_9,
        GAUSSIAN_BLUR_5
    }
    
    //auto otsu is the best option -> tested
    //this fixed the jittering, shaking, 50% times wrong detection problems
    //other options were creating problems
    //stackoverflow link: https://stackoverflow.com/questions/21324950/how-can-i-select-the-best-set-of-parameters-in-the-canny-edge-detection-algorith
    enum class CannyMode {
        FIXED_10_20,
        FIXED_75_200,
        AUTO_OTSU
    }
    
    //only morph with erode is not working, completely shitty -> tested
    //rest options performs similar -> tested
    enum class MorphMode {
        DILATE_ONLY,
        MORPH_CLOSE_WITH_ERODE,
        MORPH_CLOSE_3x3,
        MORPH_CLOSE_5x5,
        MORPH_CLOSE_LEARNOPENCV
    }
    
    //RETR_LIST is the best option -> tested
    //RETR_EXTERNAL is horrible, doesn't detect documents properly -> tested
    
    //epsilon 0.02 (2%) is the internet-recommended best value for approxPolyDP -> tested
    
    //Sort contours by area descending - fucked up detection, doesn't work -> tested
    
    //Phase 5: Contour Filtering
    //Aspect ratio filter removed - user wants receipts (long docs) to be scannable -> decided
    //minAreaRect removed - only needed for aspect ratio filter -> decided
    


    // Phase 6: Temporal Stabilization (Low-Pass Filter / EMA)
    // Based on SO: https://stackoverflow.com/questions/4611599/smoothing-data-from-a-sensor
    // Formula: output = output + α * (input - output)
    var stabilizationEnabled: Boolean = true
    var stabilizationAlpha: Float = 0.25f  // 0.1=very smooth, 0.5=responsive
    
    var currentMode: DetectionMode = DetectionMode.COMBINED_AUTO
    var blurMode: BlurMode = BlurMode.GAUSSIAN_BLUR_5
    var cannyMode: CannyMode = CannyMode.AUTO_OTSU
    var morphMode: MorphMode = MorphMode.DILATE_ONLY
    var enableLogging: Boolean = true
}
