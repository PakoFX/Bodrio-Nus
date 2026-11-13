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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.ActivityAttachment
import com.pako.eminus4.data.model.ActivityDetail
import com.pako.eminus4.data.model.DeliveryAttachment
import com.pako.eminus4.data.model.StudentDelivery
import com.pako.eminus4.ui.viewer.FileViewerActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID        = "extra_course_id"
        const val EXTRA_COURSE_NAME      = "extra_course_name"
        const val EXTRA_ACTIVITY_ID      = "extra_activity_id"
        const val EXTRA_ACTIVITY_TITLE   = "extra_activity_title"
        // Navegación entre actividades
        const val EXTRA_ACTIVITY_IDS     = "extra_activity_ids"
        const val EXTRA_ACTIVITY_TITLES  = "extra_activity_titles"
        const val EXTRA_ACTIVITY_INDEX   = "extra_activity_index"
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient
    private var courseId: Long = 0L
    private var activityId: Long = 0L
    private var courseName: String = ""
    private var activityTitleText: String = ""

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
    private lateinit var webDescription: WebView

    private lateinit var cardTeacherAttachments: MaterialCardView
    private lateinit var layoutAttachmentsList: LinearLayout

    private lateinit var layoutDeliveredFilesList: LinearLayout
    private lateinit var txtDeliveryComments: TextView
    private lateinit var layoutClosedNotice: LinearLayout
    private lateinit var txtClosedTitle: TextView
    private lateinit var txtClosedReason: TextView
    private lateinit var layoutSelectedFile: View
    private lateinit var txtSelectedFileName: TextView
    private lateinit var btnClearFile: ImageButton
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnSubmitDelivery: MaterialButton

    private lateinit var cardTeacherFeedback: MaterialCardView
    private lateinit var txtTeacherFeedback: TextView
    private lateinit var webTeacherFeedback: WebView

    private var selectedFileUri: Uri? = null
    private var currentDelivery: StudentDelivery? = null   // guardamos la entrega activa

    // Lista de actividades para navegación entre ellas
    private var activityIds: LongArray = LongArray(0)
    private var activityTitles: Array<String> = emptyArray()
    private var currentIndex: Int = 0

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
        activityTitleText = intent.getStringExtra(EXTRA_ACTIVITY_TITLE) ?: "Detalle de Actividad"

        // Lista para navegación (opcional — si no viene, navegación desactivada)
        activityIds    = intent.getLongArrayExtra(EXTRA_ACTIVITY_IDS) ?: LongArray(0)
        activityTitles = intent.getStringArrayExtra(EXTRA_ACTIVITY_TITLES) ?: emptyArray()
        currentIndex   = intent.getIntExtra(EXTRA_ACTIVITY_INDEX, 0)

        if (courseId == 0L || activityId == 0L) {
            finish()
            return
        }

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        cargarDetalleActividad()
    }

    private fun inicializarVistas() {
        toolbar              = findViewById(R.id.toolbar)
        val appBarLayout     = findViewById<View>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        configurarNavegacionActividades()
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
        webDescription       = findViewById(R.id.webDescription)

        cardTeacherAttachments = findViewById(R.id.cardTeacherAttachments)
        layoutAttachmentsList  = findViewById(R.id.layoutAttachmentsList)

        layoutDeliveredFilesList = findViewById(R.id.layoutDeliveredFilesList)
        txtDeliveryComments      = findViewById(R.id.txtDeliveryComments)
        layoutClosedNotice       = findViewById(R.id.layoutClosedNotice)
        txtClosedTitle           = findViewById(R.id.txtClosedTitle)
        txtClosedReason          = findViewById(R.id.txtClosedReason)
        layoutSelectedFile       = findViewById(R.id.layoutSelectedFile)
        txtSelectedFileName      = findViewById(R.id.txtSelectedFileName)
        btnClearFile             = findViewById(R.id.btnClearFile)
        btnSelectFile            = findViewById(R.id.btnSelectFile)
        btnSubmitDelivery        = findViewById(R.id.btnSubmitDelivery)

        cardTeacherFeedback      = findViewById(R.id.cardTeacherFeedback)
        txtTeacherFeedback       = findViewById(R.id.txtTeacherFeedback)
        webTeacherFeedback       = findViewById(R.id.webTeacherFeedback)

        // Configuración de WebViews auxiliares
        webDescription.settings.javaScriptEnabled = true
        webDescription.setBackgroundColor(Color.TRANSPARENT)

        webTeacherFeedback.settings.javaScriptEnabled = true
        webTeacherFeedback.setBackgroundColor(Color.TRANSPARENT)

        txtActivityTitle.text  = activityTitleText
        txtCourseSubtitle.text = courseName

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_uv_green))
        swipeRefresh.setOnRefreshListener {
            cargarDetalleActividad()
        }

        btnSelectFile.setOnClickListener {
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

    private fun configurarNavegacionActividades() {
        if (activityIds.size <= 1) {
            toolbar.title = "Actividad"
            return
        }
        toolbar.title = "${currentIndex + 1} / ${activityIds.size}"

        toolbar.inflateMenu(R.menu.menu_activity_nav)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.nav_prev_activity -> { navegarA(currentIndex - 1); true }
                R.id.nav_next_activity -> { navegarA(currentIndex + 1); true }
                else -> false
            }
        }
        actualizarEstadoBotonesNav()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = true  // no-op, menú manejado por toolbar directo

    override fun onOptionsItemSelected(item: MenuItem): Boolean = super.onOptionsItemSelected(item)

    private fun actualizarEstadoBotonesNav(menu: Menu? = null) {
        val m = menu ?: toolbar.menu
        m.findItem(R.id.nav_prev_activity)?.isEnabled = currentIndex > 0
        m.findItem(R.id.nav_next_activity)?.isEnabled = currentIndex < activityIds.size - 1
    }

    private fun navegarA(index: Int) {
        if (index < 0 || index >= activityIds.size) return
        currentIndex       = index
        activityId         = activityIds[index]
        activityTitleText  = activityTitles.getOrElse(index) { "Actividad" }

        toolbar.title          = "${currentIndex + 1} / ${activityIds.size}"
        txtActivityTitle.text  = activityTitleText
        actualizarEstadoBotonesNav()

        selectedFileUri = null
        layoutSelectedFile.visibility = View.GONE
        cargarDetalleActividad()
    }

    private fun cargarDetalleActividad() {
        swipeRefresh.isRefreshing = true

        // 1. Cargar detalles principales de la actividad (instrucciones y adjuntos del docente)
        apiClient.getActivityDetail(courseId, activityId) { result ->
            swipeRefresh.isRefreshing = false
            progressDesc.visibility = View.GONE

            result.onSuccess { detail ->
                mostrarDetalle(detail)
            }.onFailure { error ->
                Toast.makeText(this, "Error al cargar actividad: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Cargar estado de entrega y archivos enviados por el estudiante
        val userId = sessionManager.getUserId() ?: ""
        if (userId.isNotBlank()) {
            apiClient.getStudentDelivery(courseId, activityId, userId) { deliveryResult ->
                deliveryResult.onSuccess { delivery ->
                    mostrarEntregaEstudiante(delivery)
                }
            }
        }
    }

    private fun mostrarDetalle(detail: ActivityDetail) {
        activityTitleText = detail.titulo
        txtActivityTitle.text = detail.titulo
        txtCourseSubtitle.text = courseName

        // Ponderación / Calificación
        val valor = detail.valor ?: 10.0
        val cal = detail.calificacion
        if (cal != null) {
            badgeStatus.text = "Calificada (${"%.1f".format(cal)}/${"%.0f".format(valor)})"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_active)
            badgeStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_success_text))
            txtScoreValue.text = "Calificación: ${"%.1f".format(cal)}"
        } else if (detail.estaEntregada) {
            badgeStatus.text = "Entregada"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_badge)
            badgeStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_info_text))
            txtScoreValue.text = "Valor: ${"%.0f".format(valor)} pts"
        } else {
            badgeStatus.text = "Pendiente"
            badgeStatus.setBackgroundResource(R.drawable.bg_pill_closed)
            badgeStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_warning_text))
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
            if (descHtml.contains("<img", true) || descHtml.contains("<table", true)) {
                txtDescription.visibility = View.GONE
                webDescription.visibility = View.VISIBLE
                webDescription.loadDataWithBaseURL("https://eminus.uv.mx", formatHtmlForDisplay(descHtml), "text/html", "UTF-8", null)
            } else {
                webDescription.visibility = View.GONE
                txtDescription.visibility = View.VISIBLE
                txtDescription.text = HtmlCompat.fromHtml(descHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                txtDescription.movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            webDescription.visibility = View.GONE
            txtDescription.visibility = View.VISIBLE
            txtDescription.text = "Esta actividad no incluye instrucciones adicionales por escrito."
        }

        // Control de Estado de Cierre / Bloqueo de Entregas
        if (detail.estaCerrada) {
            layoutClosedNotice.visibility = View.VISIBLE
            btnSelectFile.visibility = View.GONE
            btnSubmitDelivery.visibility = View.GONE
            layoutSelectedFile.visibility = View.GONE

            if (detail.estaCalificada) {
                txtClosedTitle.text = "Actividad Calificada"
                txtClosedReason.text = "Esta actividad ya fue evaluada (${"%.1f".format(detail.calificacion)}/${"%.0f".format(valor)} pts). No se admiten modificaciones."
                txtClosedTitle.setTextColor(ContextCompat.getColor(this, R.color.badge_success_text))
            } else {
                txtClosedTitle.text = "Actividad Cerrada"
                txtClosedReason.text = "La fecha límite concluyó el ${formatearFechaIso(detail.fechaTermino)}. No se admiten nuevas entregas."
                txtClosedTitle.setTextColor(ContextCompat.getColor(this, R.color.badge_error_text))
            }
        } else {
            layoutClosedNotice.visibility = View.GONE
            btnSelectFile.visibility = View.VISIBLE
            btnSubmitDelivery.visibility = View.VISIBLE

            if (detail.estaEntregada) {
                btnSelectFile.text = "Modificar archivo de entrega…"
                btnSubmitDelivery.text = "Actualizar entrega en Eminus"
            } else {
                btnSelectFile.text = "Seleccionar archivo para entrega…"
                btnSubmitDelivery.text = "Enviar entrega a Eminus"
            }
        }

        // Archivos adjuntos del docente
        val adjuntos = detail.archivosDocente ?: emptyList()
        if (adjuntos.isNotEmpty()) {
            cardTeacherAttachments.visibility = View.VISIBLE
            layoutAttachmentsList.removeAllViews()

            for (adj in adjuntos) {
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_attachment, layoutAttachmentsList, false)
                val emoji = itemView.findViewById<TextView>(R.id.txtAttachmentEmoji)
                val nombre = itemView.findViewById<TextView>(R.id.txtAttachmentName)
                val peso = itemView.findViewById<TextView>(R.id.txtAttachmentSize)
                val btnView = itemView.findViewById<MaterialButton>(R.id.btnViewOnline)
                val btnDownload = itemView.findViewById<MaterialButton>(R.id.btnDownload)

                emoji.text = adj.getIconEmoji()
                nombre.text = adj.nombreOriginal
                peso.text = "${adj.peso ?: "Archivo adjunto"} · Material docente"

                btnView.setOnClickListener {
                    FileViewerActivity.start(
                        context = this,
                        url = adj.getDownloadUrl(),
                        fileName = adj.nombreOriginal,
                        fileSize = adj.peso,
                        activityTitle = detail.titulo
                    )
                }

                btnDownload.setOnClickListener {
                    descargarConDownloadManager(adj.getDownloadUrl(), adj.nombreOriginal)
                }

                layoutAttachmentsList.addView(itemView)
            }
        } else {
            cardTeacherAttachments.visibility = View.GONE
        }
    }

    private fun mostrarEntregaEstudiante(delivery: StudentDelivery) {
        val archivos = delivery.archivos ?: emptyList()

        if (archivos.isNotEmpty()) {
            layoutDeliveredFilesList.visibility = View.VISIBLE
            layoutDeliveredFilesList.removeAllViews()

            for (archivo in archivos) {
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_attachment, layoutDeliveredFilesList, false)
                val emoji = itemView.findViewById<TextView>(R.id.txtAttachmentEmoji)
                val nombre = itemView.findViewById<TextView>(R.id.txtAttachmentName)
                val peso = itemView.findViewById<TextView>(R.id.txtAttachmentSize)
                val btnView = itemView.findViewById<MaterialButton>(R.id.btnViewOnline)
                val btnDownload = itemView.findViewById<MaterialButton>(R.id.btnDownload)

                emoji.text = archivo.getIconEmoji()
                nombre.text = archivo.nombreOriginal
                peso.text = "${archivo.peso ?: "Archivo entregado"} · Tu entrega"

                btnView.setOnClickListener {
                    FileViewerActivity.start(
                        context = this,
                        url = archivo.getDownloadUrl(activityId),
                        fileName = archivo.nombreOriginal,
                        fileSize = archivo.peso,
                        activityTitle = "Tu Entrega"
                    )
                }

                btnDownload.setOnClickListener {
                    descargarConDownloadManager(archivo.getDownloadUrl(activityId), archivo.nombreOriginal)
                }

                layoutDeliveredFilesList.addView(itemView)
            }
        } else {
            layoutDeliveredFilesList.visibility = View.GONE
        }

        // Comentario de entrega del alumno
        val descEntrega = delivery.descEntrega
        if (!descEntrega.isNullOrBlank()) {
            txtDeliveryComments.visibility = View.VISIBLE
            txtDeliveryComments.text = HtmlCompat.fromHtml(descEntrega, HtmlCompat.FROM_HTML_MODE_COMPACT)
        } else {
            txtDeliveryComments.visibility = View.GONE
        }

        // Retroalimentación o comentarios de revisión del docente con imágenes
        val descRev = delivery.descRevision
        if (!descRev.isNullOrBlank()) {
            cardTeacherFeedback.visibility = View.VISIBLE
            if (descRev.contains("<img", true) || descRev.contains("<table", true)) {
                txtTeacherFeedback.visibility = View.GONE
                webTeacherFeedback.visibility = View.VISIBLE
                webTeacherFeedback.loadDataWithBaseURL("https://eminus.uv.mx", formatHtmlForDisplay(descRev), "text/html", "UTF-8", null)
            } else {
                webTeacherFeedback.visibility = View.GONE
                txtTeacherFeedback.visibility = View.VISIBLE
                txtTeacherFeedback.text = HtmlCompat.fromHtml(descRev, HtmlCompat.FROM_HTML_MODE_COMPACT)
                txtTeacherFeedback.movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            cardTeacherFeedback.visibility = View.GONE
        }
    }

    private fun formatHtmlForDisplay(html: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body {
                margin: 0;
                padding: 0;
                font-family: -apple-system, Roboto, Helvetica, Arial, sans-serif;
                font-size: 14px;
                color: #1E293B;
                line-height: 1.5;
                background-color: transparent;
              }
              img {
                max-width: 100%;
                height: auto;
                border-radius: 8px;
                margin: 6px 0;
                display: block;
              }
              p { margin: 0 0 8px 0; }
              a { color: #0284C7; }
            </style>
            </head>
            <body>$html</body>
            </html>
        """.trimIndent()
    }

    private fun descargarConDownloadManager(downloadUrl: String, fileName: String) {
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle(fileName)
                setDescription("Descargando archivo institucional de Eminus…")
                val token = sessionManager.getAccessToken()
                if (!token.isNullOrBlank()) {
                    addRequestHeader("Authorization", "Bearer $token")
                }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            downloadManager.enqueue(request)
            Toast.makeText(this, "Descargando: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var nombre = "archivo_seleccionado"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = android.provider.OpenableColumns.DISPLAY_NAME.let { col -> cursor.getColumnIndex(col) }
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
