package com.documents.scanner.notes.pdf.maker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.documents.scanner.notes.pdf.maker.camera.CameraScreen
import com.documents.scanner.notes.pdf.maker.review.ReviewScreen
import com.documents.scanner.notes.pdf.maker.ui.theme.DocumentScannerPdfMakerTheme

sealed class AppScreen {
    data object Camera : AppScreen()
    data class Review(val imagePath: String) : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocumentScannerPdfMakerTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Camera) }
                
                when (val screen = currentScreen) {
                    is AppScreen.Camera -> {
                        CameraScreen(
                            onDocumentCaptured = { imagePath ->
                                currentScreen = AppScreen.Review(imagePath)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is AppScreen.Review -> {
                        ReviewScreen(
                            imagePath = screen.imagePath,
                            onRetake = {
                                currentScreen = AppScreen.Camera
                            },
                            onConfirm = {
                                currentScreen = AppScreen.Camera
                            }
                        )
                    }
                }
            }
        }
    }
}