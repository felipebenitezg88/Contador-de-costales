
package com.example.contadordecostales

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private var totalCount = 0

@Volatile
private var isCounting = true

private val savedCounts = mutableListOf<String>()

    private val stateLock = Any()
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
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startCamera()
            else statusText.text = "Permiso de cámara denegado"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Respetar las barras del teléfono.
window.statusBarColor = Color.rgb(20, 29, 39)
window.navigationBarColor = Color.rgb(20, 29, 39)
window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

if (android.os.Build.VERSION.SDK_INT >= 29) {
    window.isNavigationBarContrastEnforced = false
    window.isStatusBarContrastEnforced = false
}

        linePosition = preferences.getInt("line_position", 50)
        sensitivity = preferences.getInt("sensitivity", 2)
        soundEnabled = preferences.getBoolean("sound_enabled", true)

        cameraExecutor = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(
            AudioManager.STREAM_NOTIFICATION, 80
        )

        createInterface()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun makeButton(
        label: String,
        color: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(color)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0, dp(52), 1f
            ).apply {
                setMargins(dp(4), dp(3), dp(4), dp(3))
            }
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.rgb(20, 29, 39))
        }

        val title = TextView(this).apply {
            text = "CONTADOR DE COSTALES"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(10))
        }

        val counterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(38, 55, 70))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val counterLabel = TextView(this).apply {
            text = "TOTAL DE COSTALES"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            text = "0"
            textSize = 42f
            setTextColor(Color.rgb(75, 230, 150))
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Preparando cámara..."
            textSize = 13f
            setTextColor(Color.rgb(75, 230, 150))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(3))
        }

        counterCard.addView(counterLabel)
        counterCard.addView(countText)
        counterCard.addView(statusText)

        val cameraLabel = TextView(this).apply {
            text = "CÁMARA EN VIVO"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(9), dp(4), dp(5))
        }

        val cameraContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(12, 19, 27))
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        cameraContainer.addView(
            previewView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        )

        val rowOne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        lateinit var pauseButton: Button
pauseButton = makeButton(
    "PAUSAR", Color.rgb(37, 99, 235)
) {
    isCounting = !isCounting
    pauseButton.text =
        if (isCounting) "PAUSAR" else "REANUDAR"
    statusText.text =
        if (isCounting) "Conteo activo" else "Conteo pausado"
}

        val resetButton = makeButton(
            "REINICIAR", Color.rgb(71, 85, 105)
        ) {
            synchronized(stateLock) {
                totalCount = 0
                previousSides.clear()
                countedIds.clear()
            }
            countText.text = "0"
            statusText.text = "Contador reiniciado"
        }

        rowOne.addView(pauseButton)
        rowOne.addView(resetButton)

        val rowTwo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val settingsButton = makeButton(
            "AJUSTES", Color.rgb(37, 99, 235)
        ) {
            showSettings()
        }

        val historyButton = makeButton(
            "HISTORIAL", Color.rgb(38, 55, 70)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Historial")
                .setMessage("El historial de producción se agregará en la siguiente actualización.")
                .setPositiveButton("Aceptar", null)
                .show()
        }

        val reportsButton = makeButton(
            "REPORTES", Color.rgb(38, 55, 70)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Reportes")
                .setMessage("Los reportes de producción se agregarán en la siguiente actualización.")
                .setPositiveButton("Aceptar", null)
                .show()
        }

        rowTwo.addView(settingsButton)
        rowTwo.addView(historyButton)
        rowTwo.addView(reportsButton)

        root.addView(title)
        root.addView(
            counterCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(cameraLabel)
        root.addView(
            cameraContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            ).apply {
                bottomMargin = dp(7)
            }
        )
        root.addView(rowOne)
        root.addView(rowTwo)

        setContentView(root)

androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
    val bars = insets.getInsets(
        androidx.core.view.WindowInsetsCompat.Type.systemBars()
    )

    view.setPadding(
        dp(12) + bars.left,
        dp(10) + bars.top,
        dp(12) + bars.right,
        dp(8) + bars.bottom
    )
    insets
}

androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(8))
        }

        val lineLabel = TextView(this).apply {
            text = "Posición de línea: $linePosition%"
        }
        content.addView(lineLabel)

        val lineBar = SeekBar(this).apply {
            max = 60
            progress = linePosition - 20
        }
        content.addView(lineBar)

        val sensitivityLabel = TextView(this).apply {
            text = "Sensibilidad: ${sensitivity / 10f}% del área"
        }
        content.addView(sensitivityLabel)

        val sensitivityBar = SeekBar(this).apply {
            max = 29
            progress = sensitivity - 1
        }
        content.addView(sensitivityBar)

        lineBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
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
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
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
                            mediaImage, rotation
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

                                        if (areaRatio < sensitivity / 1000f) {
                                            continue
                                        }

                                        val lineX =
                                            imageWidth * linePosition / 100f
                                        val isRightSide =
                                            box.centerX() >= lineX

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
                                        countText.text = newCount.toString()
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
