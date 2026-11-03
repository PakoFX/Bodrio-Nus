package com.pako.eminus4.ui.courses

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.ActivityAttachment
import com.pako.eminus4.data.model.ActivityDetail
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID      = "extra_course_id"
        const val EXTRA_COURSE_NAME    = "extra_course_name"
        const val EXTRA_ACTIVITY_ID    = "extra_activity_id"
        const val EXTRA_ACTIVITY_TITLE = "extra_activity_title"
    }

    private lateinit var apiClient: EminusApiClient
    private var courseId: Long = 0L
    private var activityId: Long = 0L
    private var courseName: String = ""

    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var txtActivityTitle: TextView
    private lateinit var txtCourseSubtitle: TextView
    private lateinit var badgeStatus: TextView
    private lateinit var txtScoreValue: TextView
    private lateinit var txtFechaInicio: TextView
    private lateinit var txtFechaTermino: TextView
    private lateinit var txtFechaEntrega: TextView
    private lateinit var progressDesc: ProgressBar
    private lateinit var txtDescription: TextView

    private lateinit var cardTeacherAttachments: MaterialCardView
    private lateinit var layoutAttachmentsList: LinearLayout

    private lateinit var layoutSelectedFile: View
    private lateinit var txtSelectedFileName: TextView
    private lateinit var btnClearFile: ImageButton
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnSubmitDelivery: MaterialButton

    private var selectedFileUri: Uri? = null

    // Selector moderno de documentos y archivos
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = obtenerNombreArchivo(uri)
            txtSelectedFileName.text = fileName
            layoutSelectedFile.visibility = View.VISIBLE
            btnSubmitDelivery.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        courseId   = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, 0L)
        courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "Curso"
        val initialTitle = intent.getStringExtra(EXTRA_ACTIVITY_TITLE) ?: "Detalle de Actividad"

        if (courseId == 0L || activityId == 0L) {
            finish()
            return
        }

        apiClient = EminusApiClient(SessionManager(this))

        inicializarVistas(initialTitle)
        cargarDetalleActividad()
    }

    private fun inicializarVistas(initialTitle: String) {
        toolbar              = findViewById(R.id.toolbar)
        val appBarLayout     = findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        swipeRefresh         = findViewById(R.id.swipeRefresh)
        txtActivityTitle     = findViewById(R.id.txtActivityTitle)
        txtCourseSubtitle    = findViewById(R.id.txtCourseSubtitle)
        badgeStatus          = findViewById(R.id.badgeStatus)
        txtScoreValue        = findViewById(R.id.txtScoreValue)
        txtFechaInicio       = findViewById(R.id.txtFechaInicio)
        txtFechaTermino      = findViewById(R.id.txtFechaTermino)
        txtFechaEntrega      = findViewById(R.id.txtFechaEntrega)
        progressDesc         = findViewById(R.id.progressDesc)
        txtDescription       = findViewById(R.id.txtDescription)

        cardTeacherAttachments = findViewById(R.id.cardTeacherAttachments)
        layoutAttachmentsList  = findViewById(R.id.layoutAttachmentsList)

        layoutSelectedFile   = findViewById(R.id.layoutSelectedFile)
        txtSelectedFileName  = findViewById(R.id.txtSelectedFileName)
        btnClearFile         = findViewById(R.id.btnClearFile)
        btnSelectFile        = findViewById(R.id.btnSelectFile)
        btnSubmitDelivery    = findViewById(R.id.btnSubmitDelivery)

        toolbar.title = "Detalle de Actividad"
        toolbar.setNavigationOnClickListener { finish() }

        txtActivityTitle.text = initialTitle
        txtCourseSubtitle.text = courseName

        swipeRefresh.setColorSchemeColors(Color.parseColor("#00897B"))
        swipeRefresh.setOnRefreshListener {
            cargarDetalleActividad()
        }

        btnSelectFile.setOnClickListener {
            // Permite seleccionar cualquier tipo de archivo admitido por Eminus
            filePickerLauncher.launch("*/*")
        }

        btnClearFile.setOnClickListener {
            selectedFileUri = null
            layoutSelectedFile.visibility = View.GONE
            btnSubmitDelivery.isEnabled = false
        }

        btnSubmitDelivery.setOnClickListener {
            mostrarDialogoConfirmarEnvio()
        }
    }

    private fun cargarDetalleActividad() {
        swipeRefresh.isRefreshing = true

        apiClient.getActivityDetail(courseId, activityId) { result ->
            swipeRefresh.isRefreshing = false
            progressDesc.visibility = View.GONE

            result.onSuccess { detail ->
                mostrarDetalle(detail)
            }.onFailure { error ->
                Toast.makeText(this, "Error al cargar actividad: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarDetalle(detail: ActivityDetail) {
        txtActivityTitle.text = detail.titulo
        txtCourseSubtitle.text = courseName

        // Ponderación / Calificación
        val valor = detail.valor ?: 10.0
        val cal = detail.calificacion
        if (cal != null) {
            badgeStatus.text = "Calificada (${"%.1f".format(cal)}/${"%.0f".format(valor)})"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_active)
            badgeStatus.setTextColor(Color.parseColor("#16A34A"))
            txtScoreValue.text = "Calificación: ${"%.1f".format(cal)}"
        } else if (detail.estaEntregada) {
            badgeStatus.text = "Entregada"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_badge)
            badgeStatus.setTextColor(Color.parseColor("#0284C7"))
            txtScoreValue.text = "Valor: ${"%.0f".format(valor)} pts"
        } else {
            badgeStatus.text = "Pendiente"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_closed)
            badgeStatus.setTextColor(Color.parseColor("#F59E0B"))
            txtScoreValue.text = "Valor: ${"%.0f".format(valor)} pts"
        }

        // Fechas
        txtFechaInicio.text  = "📅 Inicio: ${formatearFechaIso(detail.fechaInicio)}"
        txtFechaTermino.text = "⏳ Límite: ${formatearFechaIso(detail.fechaTermino)}"

        if (!detail.fechaEntrega.isNullOrBlank()) {
            txtFechaEntrega.visibility = View.VISIBLE
            txtFechaEntrega.text = "✅ Entregada el: ${formatearFechaIso(detail.fechaEntrega)}"
        } else {
            txtFechaEntrega.visibility = View.GONE
        }

        // Descripción / Instrucciones HTML
        val descHtml = detail.descripcion
        if (!descHtml.isNullOrBlank()) {
            txtDescription.visibility = View.VISIBLE
            txtDescription.text = HtmlCompat.fromHtml(descHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
            txtDescription.movementMethod = LinkMovementMethod.getInstance()
        } else {
            txtDescription.visibility = View.VISIBLE
            txtDescription.text = "Esta actividad no incluye instrucciones adicionales por escrito."
        }

        // Archivos adjuntos del docente
        val adjuntos = detail.archivosDocente ?: emptyList()
        if (adjuntos.isNotEmpty()) {
            cardTeacherAttachments.visibility = View.VISIBLE
            layoutAttachmentsList.removeAllViews()
            for (adj in adjuntos) {
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_activity_card, layoutAttachmentsList, false)
                val emoji = itemView.findViewById<TextView>(R.id.txtActivityEmoji)
                val nombre = itemView.findViewById<TextView>(R.id.txtActivityName)
                val peso = itemView.findViewById<TextView>(R.id.txtActivityDate)
                val status = itemView.findViewById<TextView>(R.id.txtActivityStatus)
                val score = itemView.findViewById<TextView>(R.id.txtActivityScore)

                emoji.text = adj.getIconEmoji()
                nombre.text = adj.nombreOriginal
                peso.text = adj.peso ?: "Archivo adjunto"
                status.text = "Descargar"
                status.setTextColor(Color.parseColor("#00897B"))
                score.visibility = View.GONE

                itemView.setOnClickListener {
                    descargarArchivoDocente(adj)
                }
                layoutAttachmentsList.addView(itemView)
            }
        } else {
            cardTeacherAttachments.visibility = View.GONE
        }
    }

    private fun descargarArchivoDocente(adj: ActivityAttachment) {
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(adj.getDownloadUrl())).apply {
                setTitle(adj.nombreOriginal)
                setDescription("Descargando recurso institucional de Eminus…")
                val token = SessionManager(this@ActivityDetailActivity).getAccessToken()
                if (!token.isNullOrBlank()) {
                    addRequestHeader("Authorization", "Bearer $token")
                }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, adj.nombreOriginal)
            }
            downloadManager.enqueue(request)
            Toast.makeText(this, "Iniciando descarga: ${adj.nombreOriginal}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var nombre = "archivo_seleccionado"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    nombre = it.getString(index)
                }
            }
        }
        return nombre
    }

    private fun mostrarDialogoConfirmarEnvio() {
        val fileName = txtSelectedFileName.text.toString()
        AlertDialog.Builder(this)
            .setTitle("Confirmar Entrega")
            .setMessage("¿Deseas enviar \"$fileName\" como entrega para esta actividad en Eminus?")
            .setPositiveButton("Enviar") { _, _ ->
                Toast.makeText(this, "Preparando entrega de \"$fileName\" a Eminus…", Toast.LENGTH_LONG).show()
                // Aquí se dispara el flujo de subida multipart con EminusApiClient
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatearFechaIso(iso: String?): String {
        if (iso.isNullOrBlank()) return "Sin fecha"
        return try {
            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFmt = SimpleDateFormat("dd 'de' MMMM yyyy, HH:mm", Locale("es"))
            val date = inputFmt.parse(iso.substring(0, 19)) ?: return iso
            outputFmt.format(date)
        } catch (_: Exception) {
            iso
        }
    }
}
