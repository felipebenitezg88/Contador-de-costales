
package com.example.contadordecostales

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
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
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var countText: TextView
    private lateinit var statusText: TextView

    private var count = 0
    private var counting = true

    private val lastPositions = mutableMapOf<Int, Float>()
    private val countedIds = mutableSetOf<Int>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else statusText.text = "Permiso de cámara necesario"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createScreen()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "CONTADOR DE COSTALES"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            text = "Costales: 0"
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        statusText = TextView(this).apply {
            text = "Preparando cámara..."
            textSize = 15f
            gravity = Gravity.CENTER
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pauseButton = Button(this).apply {
            text = "Pausar"
            setOnClickListener {
                counting = !counting
                text = if (counting) "Pausar" else "Continuar"
                statusText.text =
                    if (counting) "Conteo activo" else "Conteo pausado"
            }
        }

        val resetButton = Button(this).apply {
            text = "Reiniciar"
            setOnClickListener {
                count = 0
                countedIds.clear()
                lastPositions.clear()
                countText.text = "Costales: 0"
            }
        }

        buttons.addView(pauseButton)
        buttons.addView(resetButton)

        root.addView(title)
        root.addView(countText)
        root.addView(statusText)
        root.addView(
            previewView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        root.addView(buttons)

        setContentView(root)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build()

            val detector = ObjectDetection.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image

                if (mediaImage == null) {
                    imageProxy.close()
                } else {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    detector.process(image)
                        .addOnSuccessListener { objects ->
                            if (counting) {
                                for (obj in objects) {
                                    val id = obj.trackingId ?: continue
                                    val bounds = obj.boundingBox
                                    val centerX =
                                        bounds.centerX().toFloat() /
                                        image.width.toFloat()

                                    val previous = lastPositions[id]

                                    if (previous != null &&
                                        ((previous < 0.5f && centerX >= 0.5f) ||
                                         (previous > 0.5f && centerX <= 0.5f)) &&
                                        countedIds.add(id)
                                    ) {
                                        count++
                                        runOnUiThread {
                                            countText.text = "Costales: $count"
                                        }
                                    }

                                    lastPositions[id] = centerX
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                statusText.text = "Cámara activa: pasa los costales por la línea"
            } catch (e: Exception) {
                statusText.text = "Error al iniciar la cámara"
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
