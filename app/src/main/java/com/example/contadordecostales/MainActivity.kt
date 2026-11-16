
package com.example.contadordecostales

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService

    private val stateLock = Any()
    private var totalCount = 0
    @Volatile private var isCounting = true

    private val previousSides = mutableMapOf<Int, Boolean>()
    private val countedIds = mutableSetOf<Int>()

    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()

        ObjectDetection.getClient(options)
    }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                statusText.text = "Permiso de cámara denegado"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        createInterface()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
            setBackgroundColor(Color.rgb(20, 25, 32))
        }

        val title = TextView(this).apply {
            text = "CONTADOR DE COSTALES"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            text = "Costales: 0"
            textSize = 34f
            setTextColor(Color.rgb(70, 220, 130))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }

        statusText = TextView(this).apply {
            text = "Preparando cámara..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        val previewParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pauseButton = Button(this).apply {
            text = "Pausar"
            setOnClickListener {
                isCounting = !isCounting
                text = if (isCounting) "Pausar" else "Reanudar"
                statusText.text =
                    if (isCounting) "Conteo activo" else "Conteo pausado"
            }
        }

        val resetButton = Button(this).apply {
            text = "Reiniciar"
            setOnClickListener {
                synchronized(stateLock) {
                    totalCount = 0
                    previousSides.clear()
                    countedIds.clear()
                }
                countText.text = "Costales: 0"
                statusText.text = "Contador reiniciado"
            }
        }

        buttons.addView(pauseButton)
        buttons.addView(resetButton)

        root.addView(title)
        root.addView(countText)
        root.addView(statusText)
        root.addView(previewView, previewParams)
        root.addView(buttons)

        setContentView(root)
    }

    private fun startCamera() {
        statusText.text = "Iniciando cámara..."

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image

                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        val imageWidth =
                            if (imageProxy.imageInfo.rotationDegrees == 90 ||
                                imageProxy.imageInfo.rotationDegrees == 270
                            ) {
                                imageProxy.height
                            } else {
                                imageProxy.width
                            }

                        detector.process(inputImage)
                            .addOnSuccessListener(cameraExecutor) { objects ->
                                if (!isCounting) return@addOnSuccessListener

                                var newCount = -1

                                synchronized(stateLock) {
                                    for (obj in objects) {
                                        val id = obj.trackingId ?: continue
                                        val centerX = obj.boundingBox.centerX()
                                        val isRightSide =
                                            centerX >= imageWidth / 2f

                                        val previous = previousSides[id]

                                        if (previous != null &&
                                            previous != isRightSide &&
                                            !countedIds.contains(id)
                                        ) {
                                            totalCount++
                                            countedIds.add(id)
                                            newCount = totalCount
                                        }

                                        previousSides[id] = isRightSide
                                    }
                                }

                                if (newCount >= 0) {
                                    runOnUiThread {
                                        countText.text = "Costales: $newCount"
                                    }
                                }
                            }
                            .addOnFailureListener(cameraExecutor) {
                                runOnUiThread {
                                    statusText.text = "Error al analizar imagen"
                                }
                            }
                            .addOnCompleteListener(cameraExecutor) {
                                imageProxy.close()
                            }

                    } catch (e: Exception) {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )

                statusText.text = "Cámara activa. Conteo listo."

            } catch (e: Exception) {
                statusText.text = "No se pudo iniciar la cámara"
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        detector.close()
    }
}
