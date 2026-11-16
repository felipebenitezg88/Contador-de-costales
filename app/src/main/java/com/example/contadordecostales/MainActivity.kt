
package com.example.contadordecostales

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private val preferences by lazy {
        getSharedPreferences("contador_config", MODE_PRIVATE)
    }

    private var linePosition = 50
    private var sensitivity = 2
    private var soundEnabled = true

    private val stateLock = Any()
    private var totalCount = 0

    @Volatile
    private var isCounting = true

    private val previousSides = mutableMapOf<Int, Boolean>()
    private val countedIds = mutableSetOf<Int>()

    private val detectorLazy = lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()

        ObjectDetection.getClient(options)
    }

    private val detector by detectorLazy

    private var toneGenerator: ToneGenerator? = null

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

        linePosition = preferences.getInt("line_position", 50)
        sensitivity = preferences.getInt("sensitivity", 2)
        soundEnabled = preferences.getBoolean("sound_enabled", true)

        cameraExecutor = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

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
            setPadding(16, 20, 16, 12)
            setBackgroundColor(Color.rgb(20, 25, 32))
        }

        val title = TextView(this).apply {
            text = "CONTADOR DE COSTALES"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            text = "Costales: 0"
            textSize = 34f
            setTextColor(Color.rgb(70, 220, 130))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
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

        val settingsButton = Button(this).apply {
            text = "Ajustes"
            setOnClickListener {
                showSettings()
            }
        }

        buttons.addView(pauseButton)
        buttons.addView(resetButton)
        buttons.addView(settingsButton)

        root.addView(title)
        root.addView(countText)
        root.addView(statusText)
        root.addView(previewView, previewParams)
        root.addView(buttons)

        setContentView(root)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 8)
        }

        val lineLabel = TextView(this)
        lineLabel.text = "Posición de línea: $linePosition%"
        content.addView(lineLabel)

        val lineBar = SeekBar(this).apply {
            max = 60
            progress = linePosition - 20
        }
        content.addView(lineBar)

        val sensitivityLabel = TextView(this)
        sensitivityLabel.text =
            "Sensibilidad: ${sensitivity / 10f}% del área"
        content.addView(sensitivityLabel)

        val sensitivityBar = SeekBar(this).apply {
            max = 29
            progress = sensitivity - 1
        }
        content.addView(sensitivityBar)

        lineBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    lineLabel.text =
                        "Posición de línea: ${progress + 20}%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        sensitivityBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    sensitivityLabel.text =
                        "Sensibilidad: ${(progress + 1) / 10f}% del área"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val soundSwitch = Switch(this).apply {
            text = "Sonido al contar"
            isChecked = soundEnabled
        }
        content.addView(soundSwitch)

        AlertDialog.Builder(this)
            .setTitle("Ajustes del contador")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                linePosition = lineBar.progress + 20
                sensitivity = sensitivityBar.progress + 1
                soundEnabled = soundSwitch.isChecked

                preferences.edit()
                    .putInt("line_position", linePosition)
                    .putInt("sensitivity", sensitivity)
                    .putBoolean("sound_enabled", soundEnabled)
                    .apply()

                synchronized(stateLock) {
                    previousSides.clear()
                    countedIds.clear()
                }

                statusText.text = "Configuración guardada"
            }
            .show()
    }

    private fun startCamera() {
        statusText.text = "Iniciando cámara..."

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

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
                        val rotation =
                            imageProxy.imageInfo.rotationDegrees

                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            rotation
                        )

                        val rotated = rotation == 90 || rotation == 270

                        val imageWidth =
                            if (rotated) imageProxy.height
                            else imageProxy.width

                        val imageHeight =
                            if (rotated) imageProxy.width
                            else imageProxy.height

                        detector.process(inputImage)
                            .addOnSuccessListener(cameraExecutor) { objects ->
                                if (!isCounting) return@addOnSuccessListener

                                var newCount = -1

                                synchronized(stateLock) {
                                    for (obj in objects) {
                                        val id = obj.trackingId ?: continue

                                        val box = obj.boundingBox
                                        val areaRatio =
                                            (box.width().toFloat() *
                                             box.height().toFloat()) /
                                            (imageWidth.toFloat() *
                                             imageHeight.toFloat())

                                        val minimumArea =
                                            sensitivity / 1000f

                                        if (areaRatio < minimumArea) {
                                            continue
                                        }

                                        val centerX = box.centerX()
                                        val lineX =
                                            imageWidth * linePosition / 100f

                                        val isRightSide = centerX >= lineX
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
                                        countText.text =
                                            "Costales: $newCount"

                                        if (soundEnabled) {
                                            toneGenerator?.startTone(
                                                ToneGenerator.TONE_PROP_BEEP,
                                                150
                                            )
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener(cameraExecutor) {
                                runOnUiThread {
                                    statusText.text =
                                        "Error al analizar imagen"
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

        if (detectorLazy.isInitialized()) {
            detector.close()
        }

        toneGenerator?.release()
        toneGenerator = null
    }
}
