package com.example.contadordecostales

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
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

    // Seguimiento de cada costal detectado.
    private class Track(
        var isRight: Boolean,
        var framesSeen: Int = 0,
        var lastSeen: Long = 0L,
        var counted: Boolean = false
    )

    companion object {
        // Un costal debe verse al menos estos cuadros antes de contarlo
        // (evita contar detecciones falsas de un solo cuadro).
        private const val MIN_FRAMES_BEFORE_COUNT = 2

        // Si un costal no se ve durante este tiempo, se olvida su seguimiento.
        private const val TRACK_TIMEOUT_MS = 2000L

        // Detecciones más grandes que esto (del área total) se ignoran
        // (por ejemplo, la banda completa).
        private const val MAX_AREA_RATIO = 0.6f
    }

    private lateinit var previewView: PreviewView
    private lateinit var countOverlay: CountOverlay
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var metricsText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    private val preferences by lazy {
        getSharedPreferences("contador_config", MODE_PRIVATE)
    }

    private var linePosition = 50
    private var sensitivity = 2
    private var soundEnabled = true
    private var productionGoal = 1000
    private var zoneTop = 20
    private var zoneBottom = 80
    private var leftToRight = true

    private var totalCount = 0
    private var operationType: String = ""

    @Volatile
    private var isCounting = false

    private val savedCounts = mutableListOf<String>()
    private val historyPreferences by lazy {
        getSharedPreferences("historial_conteos", MODE_PRIVATE)
    }

    private val stateLock = Any()
    private val tracks = mutableMapOf<Int, Track>()

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
        productionGoal = preferences.getInt("production_goal", 1000).coerceAtLeast(1)
        zoneTop = preferences.getInt("zone_top", 20)
        zoneBottom = preferences.getInt("zone_bottom", 80)
        leftToRight = preferences.getBoolean("left_to_right", true)

        cameraExecutor = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(
            AudioManager.STREAM_NOTIFICATION, 80
        )

        // Cargar historial guardado
        savedCounts.clear()
        val historyJson = historyPreferences.getString("registros", "[]")
        val historyArray = JSONArray(historyJson)

        for (i in 0 until historyArray.length()) {
            savedCounts.add(historyArray.getString(i))
        }

        createInterface()
        updateOverlayConfig()
        showOperationDialog()

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

    private fun updateOverlayConfig() {
        countOverlay.setConfig(
            linePosition / 100f,
            zoneTop / 100f,
            zoneBottom / 100f,
            leftToRight
        )
    }

    private fun onProgress(action: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?, progress: Int, fromUser: Boolean
            ) {
                action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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

        metricsText = TextView(this).apply {
            text = "Meta: $productionGoal | Avance: 0%"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(2))
        }

        counterCard.addView(counterLabel)
        counterCard.addView(countText)
        counterCard.addView(metricsText)
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

        // FIT_CENTER muestra la imagen completa, para que la línea y la zona
        // dibujadas coincidan exactamente con lo que analiza la app.
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        countOverlay = CountOverlay(this)

        val cameraFrame = FrameLayout(this).apply {
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                countOverlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        cameraContainer.addView(
            cameraFrame,
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
            refreshLiveIndicators()
        }

        val resetButton = makeButton(
            "REINICIAR", Color.rgb(71, 85, 105)
        ) {
            synchronized(stateLock) {
                totalCount = 0
                tracks.clear()
            }
            countText.text = "0"
            metricsText.text = "Meta: $productionGoal | Avance: 0%"
            statusText.text = "Contador reiniciado"
        }

        rowOne.addView(pauseButton)
        rowOne.addView(resetButton)

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val saveButton = makeButton(
            "GUARDAR CONTEO", Color.rgb(22, 163, 74)
        ) {
            showSaveCountDialog()
        }

        saveRow.addView(saveButton)

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
            showHistory()
        }

        val reportsButton = makeButton(
            "REPORTES", Color.rgb(38, 55, 70)
        ) {
            showReports()
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
        root.addView(saveRow)
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

    private fun showOperationDialog() {
        val choices = arrayOf("CARGA", "DESCARGA")
        AlertDialog.Builder(this)
            .setTitle("Selecciona el tipo de operación")
            .setItems(choices) { dialog, which ->
                operationType = choices[which]
                isCounting = true
                statusText.text = "Operación: $operationType. Conteo activo."
                synchronized(stateLock) { tracks.clear() }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSaveCountDialog() {
        val originalCount = synchronized(stateLock) { totalCount }
        if (operationType.isBlank()) {
            showOperationDialog()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        val fields = linkedMapOf<String, android.widget.EditText>()
        fun addField(key: String, label: String, numeric: Boolean = false) {
            content.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.DKGRAY)
            })
            val input = android.widget.EditText(this).apply {
                hint = "Escribe aquí"; textSize = 15f
                if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            fields[key] = input
            content.addView(input)
        }
        content.addView(TextView(this).apply {
            text = "DATOS GENERALES"; textSize = 16f
            setTextColor(Color.BLACK); setPadding(0, dp(4), 0, dp(8))
        })
        val generalFields = listOf(
            "cajaNumero" to "Caja número",
            "tipoCaja" to "Tipo de caja (Full / Sencillo)",
            "fecha" to "Fecha",
            "transportista" to "Transportista",
            "cliente" to "Cliente",
            "operador" to "Operador",
            "sellos" to "Sellos",
            "cartaPorte" to "Carta porte",
            "remision" to "Remisión",
            "placasTracto" to "Placas tracto",
            "placasCaja" to "Placas caja",
            "numeroLiberacion" to "No. de liberación",
            "numeroEconomico" to "No. económico",
            "marca" to "Marca",
            "zafra" to "Zafra",
            "calidad" to "Calidad",
            "destino" to "Destino",
            "contrato" to "Contrato",
            "bultos" to "Bultos",
            "presentacion" to "Presentación",
            "tonelaje" to "Tonelaje",
            "fechaCertificado" to "F. de certificado"
        )
        generalFields.forEach { addField(it.first, it.second) }

        content.addView(TextView(this).apply {
            text = "REGISTRO DE FOLIOS (10 RENGLONES)"; textSize = 16f
            setTextColor(Color.BLACK); setPadding(0, dp(14), 0, dp(6))
        })
        val folioFields = mutableListOf<List<android.widget.EditText>>()
        repeat(10) { index ->
            content.addView(TextView(this).apply {
                text = "Renglón ${index + 1}"; textSize = 12f; setTextColor(Color.DKGRAY)
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val rowInputs = mutableListOf<android.widget.EditText>()
            repeat(3) { col ->
                val input = android.widget.EditText(this).apply {
                    hint = "Folio ${col + 1}"; textSize = 13f; singleLine = true
                    layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                        setMargins(dp(2), 0, dp(2), dp(3))
                    }
                }
                rowInputs.add(input); row.addView(input)
            }
            folioFields.add(rowInputs); content.addView(row)
        }

        content.addView(TextView(this).apply {
            text = "DATOS ADICIONALES"; textSize = 16f
            setTextColor(Color.BLACK); setPadding(0, dp(14), 0, dp(6))
        })
        listOf(
            "horaEntrada" to "Hora de entrada",
            "horaSalida" to "Hora de salida",
            "comprobanteTraslado" to "Comprobante de traslado",
            "numeroOrden" to "Número de orden",
            "bodega" to "Bodega",
            "banco" to "Banco"
        ).forEach { addField(it.first, it.second) }

        content.addView(TextView(this).apply {
            text = "DATOS QUE YA EXISTÍAN EN EL CONTADOR"; textSize = 16f
            setTextColor(Color.BLACK); setPadding(0, dp(14), 0, dp(6))
        })
        addField("cuadrilla", "Cuadrilla")
        addField("estiba", "Estiba")
        addField("rotosOrigen", "Sacos rotos de origen", true)
        addField("rotosManiobra", "Sacos rotos por maniobra", true)
        addField("comentarios", "Comentarios")
        addField("supervisor", "Supervisor")

        val scroll = android.widget.ScrollView(this).apply { addView(content) }
        val formDialog = AlertDialog.Builder(this)
            .setTitle("Datos del registro")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Vista previa", null)
            .create()
        formDialog.setOnShowListener {
            formDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val origen = fields["rotosOrigen"]?.text.toString().toIntOrNull() ?: 0
                val maniobra = fields["rotosManiobra"]?.text.toString().toIntOrNull() ?: 0
                if (origen + maniobra > originalCount) {
                    android.widget.Toast.makeText(this,
                        "Los sacos rotos no pueden superar el conteo",
                        android.widget.Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val values = fields.mapValues { it.value.text.toString().trim() }
                val folios = JSONArray()
                folioFields.forEach { row ->
                    val rowJson = JSONArray()
                    row.forEach { rowJson.put(it.text.toString().trim()) }
                    folios.put(rowJson)
                }
                val totalNeto = originalCount - origen - maniobra
                val previewLines = mutableListOf<String>()
                previewLines.add("CONSEDE — CONTROL OPERATIVO")
                previewLines.add("Operación: $operationType")
                previewLines.add("Conteo original: $originalCount")
                previewLines.add("Total neto: $totalNeto")
                previewLines.add("Meta: $productionGoal")
                generalFields.forEach { (key, label) ->
                    previewLines.add("$label: ${values[key].orEmpty().ifBlank { "—" }}")
                }
                previewLines.add("FOLIOS")
                folioFields.forEachIndexed { index, row ->
                    previewLines.add("${index + 1}. " + row.joinToString(" | ") {
                        it.text.toString().trim().ifBlank { "—" }
                    })
                }
                listOf(
                    "horaEntrada" to "Hora de entrada",
                    "horaSalida" to "Hora de salida",
                    "comprobanteTraslado" to "Comprobante de traslado",
                    "numeroOrden" to "Número de orden",
                    "bodega" to "Bodega",
                    "banco" to "Banco",
                    "cuadrilla" to "Cuadrilla",
                    "estiba" to "Estiba",
                    "rotosOrigen" to "Sacos rotos de origen",
                    "rotosManiobra" to "Sacos rotos por maniobra",
                    "comentarios" to "Comentarios",
                    "supervisor" to "Supervisor"
                ).forEach { (key, label) ->
                    previewLines.add("$label: ${values[key].orEmpty().ifBlank { "—" }}")
                }
                val previewView = TextView(this).apply {
                    text = previewLines.joinToString("\n"); textSize = 14f
                    setTextColor(Color.BLACK); setPadding(dp(18), dp(12), dp(18), dp(12))
                }
                val previewScroll = android.widget.ScrollView(this).apply {
                    addView(previewView); isFillViewport = true
                }
                formDialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Vista previa antes de guardar")
                    .setView(previewScroll)
                    .setNegativeButton("Volver a editar") { _, _ -> showSaveCountDialog() }
                    .setPositiveButton("Confirmar y guardar") { _, _ ->
                        val fechaRegistro = SimpleDateFormat(
                            "dd/MM/yyyy HH:mm:ss", Locale.getDefault()
                        ).format(Date())
                        val registro = JSONObject().apply {
                            put("fecha", fechaRegistro)
                            put("operacion", operationType)
                            put("conteoOriginal", originalCount)
                            put("totalNeto", totalNeto)
                            put("meta", productionGoal)
                            values.forEach { (key, value) -> put(key, value) }
                            put("rotosOrigen", origen)
                            put("rotosManiobra", maniobra)
                            put("folios", folios)
                        }
                        savedCounts.add(registro.toString())
                        val historyArray = JSONArray()
                        savedCounts.forEach { historyArray.put(it) }
                        historyPreferences.edit().putString("registros", historyArray.toString()).apply()
                        android.widget.Toast.makeText(this,
                            "Registro guardado correctamente", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
        formDialog.show()
    }

    private fun showHistory() {
        val records = savedCounts.mapNotNull { raw ->
            try { JSONObject(raw) } catch (_: Exception) { null }
        }.reversed()

        if (records.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Historial de producción")
                .setMessage("Todavía no hay conteos guardados. Usa GUARDAR CONTEO para registrar una producción.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        val items = records.map { record ->
            val fecha = record.optString("fecha", "Sin fecha")
            val total = record.optInt("totalNeto", 0)
            val cuadrilla = record.optString("cuadrilla", "Sin cuadrilla")
            val estiba = record.optString("estiba", "Sin estiba")
            "$fecha\nTotal neto: $total | Cuadrilla: $cuadrilla | Estiba: $estiba"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Historial de producción (" + records.size + ")")
            .setItems(items, null)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showReports() {
        val records = savedCounts.mapNotNull { raw ->
            try { JSONObject(raw) } catch (_: Exception) { null }
        }

        if (records.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Reportes de producción")
                .setMessage("Aún no hay registros para generar un reporte.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        val totalOriginal = records.sumOf { it.optInt("conteoOriginal", 0) }
        val rotosOrigen = records.sumOf { it.optInt("rotosOrigen", 0) }
        val rotosManiobra = records.sumOf { it.optInt("rotosManiobra", 0) }
        val totalNeto = records.sumOf { it.optInt("totalNeto", 0) }
        val texto = "Registros guardados: " + records.size + "\n" +
            "Costales detectados: $totalOriginal\n" +
            "Rotos de origen: $rotosOrigen\n" +
            "Rotos por maniobra: $rotosManiobra\n" +
            "TOTAL NETO DE PRODUCCIÓN: $totalNeto"

        AlertDialog.Builder(this)
            .setTitle("Reporte acumulado")
            .setMessage(texto)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(8))
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(content)
        }

        // Línea de conteo
        val lineLabel = TextView(this).apply {
            text = "Posición de la línea: $linePosition%"
        }
        content.addView(lineLabel)

        val lineBar = SeekBar(this).apply {
            max = 60
            progress = linePosition - 20
            setOnSeekBarChangeListener(onProgress {
                lineLabel.text = "Posición de la línea: ${it + 20}%"
            })
        }
        content.addView(lineBar)

        // Zona de la banda (solo se cuenta lo que está dentro)
        val topLabel = TextView(this).apply {
            text = "Zona de la banda, borde superior: $zoneTop%"
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(topLabel)

        val topBar = SeekBar(this).apply {
            max = 70
            progress = zoneTop.coerceIn(0, 70)
            setOnSeekBarChangeListener(onProgress {
                topLabel.text = "Zona de la banda, borde superior: $it%"
            })
        }
        content.addView(topBar)

        val bottomLabel = TextView(this).apply {
            text = "Zona de la banda, borde inferior: $zoneBottom%"
        }
        content.addView(bottomLabel)

        val bottomBar = SeekBar(this).apply {
            max = 70
            progress = (zoneBottom - 30).coerceIn(0, 70)
            setOnSeekBarChangeListener(onProgress {
                bottomLabel.text = "Zona de la banda, borde inferior: ${it + 30}%"
            })
        }
        content.addView(bottomBar)

        // Dirección de la banda
        val directionSwitch = Switch(this).apply {
            text = "La banda avanza de izquierda a derecha"
            isChecked = leftToRight
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(directionSwitch)

        // Tamaño mínimo
        val sensitivityLabel = TextView(this).apply {
            text = "Tamaño mínimo del costal: ${sensitivity / 10f}% del área"
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(sensitivityLabel)

        val sensitivityBar = SeekBar(this).apply {
            max = 29
            progress = sensitivity - 1
            setOnSeekBarChangeListener(onProgress {
                sensitivityLabel.text =
                    "Tamaño mínimo del costal: ${(it + 1) / 10f}% del área"
            })
        }
        content.addView(sensitivityBar)

        val goalLabel = TextView(this).apply {
            text = "Meta (costales)"
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(goalLabel)

        val goalInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(productionGoal.toString())
            hint = "Ej. 1000"
        }
        content.addView(goalInput)

        val soundSwitch = Switch(this).apply {
            text = "Sonido al contar"
            isChecked = soundEnabled
        }
        content.addView(soundSwitch)

        AlertDialog.Builder(this)
            .setTitle("Ajustes del contador")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                linePosition = lineBar.progress + 20
                sensitivity = sensitivityBar.progress + 1
                soundEnabled = soundSwitch.isChecked
                leftToRight = directionSwitch.isChecked
                zoneBottom = bottomBar.progress + 30
                zoneTop = topBar.progress.coerceAtMost(zoneBottom - 10)
                productionGoal = (goalInput.text.toString().toIntOrNull() ?: productionGoal).coerceAtLeast(1)

                preferences.edit()
                    .putInt("line_position", linePosition)
                    .putInt("sensitivity", sensitivity)
                    .putBoolean("sound_enabled", soundEnabled)
                    .putInt("production_goal", productionGoal)
                    .putInt("zone_top", zoneTop)
                    .putInt("zone_bottom", zoneBottom)
                    .putBoolean("left_to_right", leftToRight)
                    .apply()

                refreshLiveIndicators()
                updateOverlayConfig()

                synchronized(stateLock) {
                    tracks.clear()
                }

                statusText.text = "Configuración guardada"
            }
            .show()
    }

    private fun refreshLiveIndicators() {
        val count = synchronized(stateLock) { totalCount }
        updateProductionIndicators(count)
    }

    private fun updateProductionIndicators(count: Int) {
        val percent = ((count.toDouble() / productionGoal.toDouble()) * 100.0).toInt()
        metricsText.text = "Meta: $productionGoal | Avance: $percent%"
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
                                var newCount = -1
                                var currentCount = 0
                                val boxes = ArrayList<RectF>()

                                synchronized(stateLock) {
                                    val now = SystemClock.elapsedRealtime()

                                    // Olvidar costales que ya no se ven.
                                    tracks.entries.removeAll {
                                        now - it.value.lastSeen > TRACK_TIMEOUT_MS
                                    }

                                    val lineX = imageWidth * linePosition / 100f
                                    val minArea = sensitivity / 1000f

                                    for (obj in objects) {
                                        val id = obj.trackingId ?: continue
                                        val box = obj.boundingBox

                                        // 1) Filtro por tamaño.
                                        val areaRatio =
                                            (box.width().toFloat() *
                                             box.height().toFloat()) /
                                            (imageWidth.toFloat() *
                                             imageHeight.toFloat())
                                        if (areaRatio < minArea ||
                                            areaRatio > MAX_AREA_RATIO
                                        ) {
                                            continue
                                        }

                                        // 2) Solo lo que está dentro de la zona de la banda.
                                        val centerYPercent =
                                            box.centerY() * 100f / imageHeight
                                        if (centerYPercent < zoneTop ||
                                            centerYPercent > zoneBottom
                                        ) {
                                            continue
                                        }

                                        boxes.add(RectF(box))

                                        val isRightSide = box.centerX() >= lineX
                                        val track = tracks.getOrPut(id) {
                                            Track(isRightSide)
                                        }
                                        track.lastSeen = now
                                        track.framesSeen++

                                        // 3) Cuenta solo al cruzar la línea en el
                                        //    sentido en que avanza la banda.
                                        val crossedForward =
                                            if (leftToRight) {
                                                !track.isRight && isRightSide
                                            } else {
                                                track.isRight && !isRightSide
                                            }

                                        if (isCounting &&
                                            crossedForward &&
                                            !track.counted &&
                                            track.framesSeen >= MIN_FRAMES_BEFORE_COUNT
                                        ) {
                                            totalCount++
                                            track.counted = true
                                            newCount = totalCount
                                        }

                                        // Se actualiza siempre (incluso en pausa) para
                                        // no contar de golpe al reanudar.
                                        track.isRight = isRightSide
                                    }

                                    currentCount = totalCount
                                }

                                runOnUiThread {
                                    countOverlay.showDetections(imageWidth, imageHeight, boxes)
                                    if (newCount >= 0) {
                                        countText.text = newCount.toString()
                                        if (soundEnabled) {
                                            toneGenerator?.startTone(
                                                ToneGenerator.TONE_PROP_BEEP,
                                                150
                                            )
                                        }
                                    }
                                    updateProductionIndicators(currentCount)
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
