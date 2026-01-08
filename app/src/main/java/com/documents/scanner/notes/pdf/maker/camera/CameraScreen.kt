package com.documents.scanner.notes.pdf.maker.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraScreen"

sealed class CameraState {
    data object Loading : CameraState()
    data object PermissionRequired : CameraState()
    data object PermissionDenied : CameraState()
    data object Ready : CameraState()
    data class Error(val message: String) : CameraState()
}

@Composable
fun CameraScreen(
    onDocumentCaptured: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cameraState by remember { mutableStateOf<CameraState>(CameraState.Loading) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraState = if (isGranted) {
            CameraState.Ready
        } else {
            CameraState.PermissionDenied
        }
    }
    
    LaunchedEffect(Unit) {
        cameraState = when {
            hasCameraPermission(context) -> CameraState.Ready
            else -> CameraState.PermissionRequired
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = cameraState) {
            is CameraState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            
            is CameraState.PermissionRequired -> {
                PermissionRationaleContent(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            is CameraState.PermissionDenied -> {
                PermissionDeniedContent(
                    onOpenSettings = {
                        openAppSettings(context)
                    },
                    onRetry = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            is CameraState.Ready -> {
                CameraPreviewWithOverlay(
                    onDocumentCaptured = onDocumentCaptured,
                    onError = { error ->
                        cameraState = CameraState.Error(error)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is CameraState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = {
                        cameraState = CameraState.Ready
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    onDocumentCaptured: ((String) -> Unit)?,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var detectionResult by remember { mutableStateOf<DocumentDetectionResult?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val objectDetectionAnalyzer = remember {
        ObjectDetectionAnalyzer { result ->
            mainHandler.post {
                detectionResult = result
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            objectDetectionAnalyzer.close()
        }
    }
    
    LaunchedEffect(previewView) {
        previewView?.let { view ->
            try {
                val cameraProvider = getCameraProvider(context)
                
                cameraProvider.unbindAll()
                
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also {
                        it.surfaceProvider = view.surfaceProvider
                    }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            objectDetectionAnalyzer
                        )
                    }
                
                val imageCaptureUseCase = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = imageCaptureUseCase
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCaptureUseCase
                )
                
                Log.d(TAG, "Camera bound with Preview, ImageAnalysis, and ImageCapture")
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                onError("Camera initialization failed: ${e.localizedMessage}")
            }
        }
    }
    
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        detectionResult?.let { result ->
            DocumentOverlay(
                detectedDocument = result.document,
                imageWidth = result.sourceWidth,
                imageHeight = result.sourceHeight,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        CaptureButton(
            isEnabled = detectionResult?.document != null && !isCapturing,
            isCapturing = isCapturing,
            onClick = {
                val capture = imageCapture ?: return@CaptureButton
                val currentDoc = detectionResult?.document ?: return@CaptureButton
                
                isCapturing = true
                
                captureAndProcessDocument(
                    context = context,
                    imageCapture = capture,
                    document = currentDoc,
                    executor = captureExecutor,
                    onSuccess = { filePath ->
                        mainHandler.post {
                            isCapturing = false
                            Toast.makeText(context, "Document captured", Toast.LENGTH_SHORT).show()
                            onDocumentCaptured?.invoke(filePath)
                        }
                    },
                    onError = { error ->
                        mainHandler.post {
                            isCapturing = false
                            Toast.makeText(context, "Capture failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
        
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(
                    color = if (detectionResult?.document != null) 
                        Color(0xFF4CAF50).copy(alpha = 0.8f) 
                    else 
                        Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (detectionResult?.document != null) 
                    "Document detected" 
                else 
                    "Point at a document",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        var showDebugPanel by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            Button(
                onClick = { showDebugPanel = !showDebugPanel },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("DEBUG", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            
            if (showDebugPanel) {
                Spacer(modifier = Modifier.height(4.dp))
                
                var detectionState by remember { mutableStateOf(DetectionConfig.currentMode) }
                var cannyState by remember { mutableStateOf(DetectionConfig.cannyMode) }
                var morphState by remember { mutableStateOf(DetectionConfig.morphMode) }
                var blurState by remember { mutableStateOf(DetectionConfig.blurMode) }
                
                Button(
                    onClick = {
                        val modes = DetectionConfig.DetectionMode.entries
                        val nextIndex = (detectionState.ordinal + 1) % modes.size
                        detectionState = modes[nextIndex]
                        DetectionConfig.currentMode = detectionState
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "D: ${detectionState.name.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = {
                        val modes = DetectionConfig.CannyMode.entries
                        val nextIndex = (cannyState.ordinal + 1) % modes.size
                        cannyState = modes[nextIndex]
                        DetectionConfig.cannyMode = cannyState
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "C: ${cannyState.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = {
                        val modes = DetectionConfig.MorphMode.entries
                        val nextIndex = (morphState.ordinal + 1) % modes.size
                        morphState = modes[nextIndex]
                        DetectionConfig.morphMode = morphState
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "M: ${morphState.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = {
                        val modes = DetectionConfig.BlurMode.entries
                        val nextIndex = (blurState.ordinal + 1) % modes.size
                        blurState = modes[nextIndex]
                        DetectionConfig.blurMode = blurState
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "B: ${blurState.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    isEnabled: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                color = if (isEnabled) Color.White else Color.Gray.copy(alpha = 0.5f)
            )
            .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = isEnabled && !isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color.Black,
                strokeWidth = 3.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isEnabled) Color(0xFF4CAF50) else Color.Gray
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun captureAndProcessDocument(
    context: Context,
    imageCapture: ImageCapture,
    document: DetectedDocument,
    executor: java.util.concurrent.Executor,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    
                    if (bitmap == null) {
                        onError("Failed to process image")
                        return
                    }
                    
                    Log.d(TAG, "Captured image: ${bitmap.width}x${bitmap.height}")
                    
                    val scaledCorners = document.corners.map { corner ->
                        android.graphics.PointF(
                            corner.x * bitmap.width / imageProxy.width,
                            corner.y * bitmap.height / imageProxy.height
                        )
                    }
                    
                    val transformedBitmap = PerspectiveTransformer.transformDocument(
                        sourceBitmap = bitmap,
                        corners = scaledCorners
                    )
                    
                    bitmap.recycle()
                    
                    if (transformedBitmap == null) {
                        onError("Failed to transform document")
                        return
                    }
                    
                    val filePath = PerspectiveTransformer.saveToCacheDir(
                        context = context,
                        bitmap = transformedBitmap
                    )
                    
                    transformedBitmap.recycle()
                    
                    if (filePath == null) {
                        onError("Failed to save image")
                        return
                    }
                    
                    onSuccess(filePath)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Image processing failed", e)
                    onError(e.localizedMessage ?: "Unknown error")
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Image capture failed", exception)
                onError(exception.localizedMessage ?: "Capture failed")
            }
        }
    )
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    } else {
        bitmap
    }
}

@Composable
private fun PermissionRationaleContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📷",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "This app needs camera access to scan documents. Your camera feed stays on your device and is not uploaded anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Grant Camera Access")
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🚫",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Camera Permission Denied",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Without camera access, you won't be able to scan documents. Please grant permission in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Try Again")
            }
            
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Camera Error",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Retry")
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PermissionChecker.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}
