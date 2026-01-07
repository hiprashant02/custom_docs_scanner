package com.documents.scanner.notes.pdf.maker.camera

object DetectionConfig {
    
    enum class DetectionMode {
        BITMAP_BGR_3CHANNELS,
        PLANES_GRAYSCALE
    }
    
    var currentMode: DetectionMode = DetectionMode.BITMAP_BGR_3CHANNELS
    var enableLogging: Boolean = true
}
