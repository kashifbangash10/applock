package com.applockerapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

object IntruderSelfieHelper {

    private const val TAG = "IntruderSelfieHelper"

    fun captureSelfie(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        targetPackage: String,
        attempts: Int,
        onComplete: (File?) -> Unit
    ) {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Camera permission not granted")
            onComplete(null)
            return
        }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Use front camera for selfie
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Unbind all first, then bind ImageCapture only
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                // Prepare output file
                val dir = File(context.filesDir, "intruder_selfies").apply { mkdirs() }
                val timestamp = System.currentTimeMillis()
                val cleanPkg = targetPackage.replace(".", "_").replace("/", "_")
                val file = File(dir, "selfie_${timestamp}_${cleanPkg}_${attempts}.jpg")

                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                // Small delay to let camera initialize before capture
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        imageCapture.takePicture(
                            outputOptions,
                            mainExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    Log.i(TAG, "Selfie saved: ${file.absolutePath} (${file.length()} bytes)")
                                    try { cameraProvider.unbindAll() } catch (e: Exception) {}
                                    onComplete(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(TAG, "Selfie capture error: ${exception.message}", exception)
                                    try { cameraProvider.unbindAll() } catch (e: Exception) {}
                                    onComplete(null)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during imageCapture.takePicture: ${e.message}", e)
                        try { cameraProvider.unbindAll() } catch (unbindEx: Exception) {}
                        onComplete(null)
                    }
                }, 500) // 500ms delay for camera to warm up

            } catch (e: Exception) {
                Log.e(TAG, "CameraX setup failed: ${e.message}", e)
                onComplete(null)
            }
        }, mainExecutor)
    }
}
