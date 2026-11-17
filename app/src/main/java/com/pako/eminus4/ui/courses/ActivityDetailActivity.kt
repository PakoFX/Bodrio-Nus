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
    private lateinit var layoutDeliveryComment: View
    private lateinit var editDeliveryComment: EditText
    private lateinit var layoutUploadProgress: View
    private lateinit var txtUploadProgress: TextView
    private lateinit var btnEditDelivery: MaterialButton
    private lateinit var btnCancelEdit: MaterialButton
    private lateinit var txtEditHint: TextView

    private lateinit var cardTeacherFeedback: MaterialCardView
    private lateinit var txtTeacherFeedback: TextView
    private lateinit var webTeacherFeedback: WebView

    private var selectedFileUri: Uri? = null
    private var currentDelivery: StudentDelivery? = null   // guardamos la entrega activa
    private var currentDetail: ActivityDetail? = null
    private var enviandoEntrega = false
    private var comentarioPrecargado: String? = null
    /** Una entrega ya enviada solo se modifica tras pulsar "Editar entrega". */
    private var modoEdicion = false

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
            layoutDeliveryComment.visibility = View.VISIBLE
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
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
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
        layoutDeliveryComment    = findViewById(R.id.layoutDeliveryComment)
        editDeliveryComment      = findViewById(R.id.editDeliveryComment)
        layoutUploadProgress     = findViewById(R.id.layoutUploadProgress)
        txtUploadProgress        = findViewById(R.id.txtUploadProgress)
        btnEditDelivery          = findViewById(R.id.btnEditDelivery)
        btnCancelEdit            = findViewById(R.id.btnCancelEdit)
        txtEditHint              = findViewById(R.id.txtEditHint)

        btnEditDelivery.setOnClickListener {
            modoEdicion = true
            refrescarEntrega()
        }
        btnCancelEdit.setOnClickListener {
            salirDeEdicion()
        }

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
            limpiarArchivoSeleccionado()
        }

        btnSubmitDelivery.setOnClickListener {
            mostrarDialogoConfirmarEnvio()
        }

        // No permitir salir a media subida sin avisar (la entrega quedaría a medias)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (enviandoEntrega) {
                    Toast.makeText(this@ActivityDetailActivity, "Espera a que termine el envío…", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
        if (enviandoEntrega) return
        currentIndex       = index
        activityId         = activityIds[index]
        activityTitleText  = activityTitles.getOrElse(index) { "Actividad" }

        toolbar.title          = "${currentIndex + 1} / ${activityIds.size}"
        txtActivityTitle.text  = activityTitleText
        actualizarEstadoBotonesNav()

        limpiarArchivoSeleccionado()
        editDeliveryComment.setText("")
        comentarioPrecargado = null
        modoEdicion = false
        currentDelivery = null
        currentDetail = null
        cargarDetalleActividad()
    }

    private fun limpiarArchivoSeleccionado() {
        selectedFileUri = null
        layoutSelectedFile.visibility = View.GONE
        layoutDeliveryComment.visibility = View.GONE
        btnSubmitDelivery.isEnabled = false
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
        currentDetail = detail
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
        if (detail.estaCerrada || detail.esPorEquipos) {
            layoutClosedNotice.visibility = View.VISIBLE
            btnSelectFile.visibility = View.GONE
            btnSubmitDelivery.visibility = View.GONE
            layoutSelectedFile.visibility = View.GONE
            layoutDeliveryComment.visibility = View.GONE
            selectedFileUri = null

            if (!detail.estaCerrada && detail.esPorEquipos) {
                txtClosedTitle.text = "Actividad por Equipo"
                txtClosedReason.text = "Las entregas en equipo deben hacerse desde Eminus web para no sobrescribir el trabajo de tus compañeros."
                txtClosedTitle.setTextColor(ContextCompat.getColor(this, R.color.badge_info_text))
            } else if (detail.estaCalificada) {
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
        }
        actualizarControlesEntrega()

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

        // El detalle y la entrega se cargan en paralelo: si la entrega llegó primero,
        // se repinta para mostrar u ocultar "Quitar" según el estado real de la actividad.
        currentDelivery?.let { mostrarEntregaEstudiante(it) }
    }

    /** La actividad admite cambios (abierta e individual), sin importar el modo de edición. */
    private fun actividadAdmiteCambios(): Boolean {
        val d = currentDetail ?: return false
        return !d.estaCerrada && !d.esPorEquipos
    }

    /**
     * Se pueden agregar/quitar archivos si la actividad admite cambios y, cuando ya hay
     * una entrega enviada, solo después de pulsar "Editar entrega".
     */
    private fun puedeModificarEntrega(): Boolean {
        val d = currentDetail ?: return false
        return actividadAdmiteCambios() && !enviandoEntrega && (!d.estaEntregada || modoEdicion)
    }

    /** Muestra/oculta los controles de entrega según el estado y el modo de edición. */
    private fun actualizarControlesEntrega() {
        val d = currentDetail
        val admite = actividadAdmiteCambios()
        val entregada = d?.estaEntregada == true
        val editando = admite && (!entregada || modoEdicion)

        btnEditDelivery.visibility = if (admite && entregada && !modoEdicion) View.VISIBLE else View.GONE
        txtEditHint.visibility     = if (admite && entregada && modoEdicion) View.VISIBLE else View.GONE
        btnCancelEdit.visibility   = if (admite && entregada && modoEdicion) View.VISIBLE else View.GONE
        btnSelectFile.visibility   = if (editando) View.VISIBLE else View.GONE
        btnSubmitDelivery.visibility = if (editando) View.VISIBLE else View.GONE

        if (!editando) {
            selectedFileUri = null
            layoutSelectedFile.visibility = View.GONE
            layoutDeliveryComment.visibility = View.GONE
            btnSubmitDelivery.isEnabled = false
        }

        if (entregada) {
            btnSelectFile.text = "Agregar archivo a tu entrega…"
            btnSubmitDelivery.text = "Actualizar entrega en Eminus"
        } else {
            btnSelectFile.text = "Seleccionar archivo para entrega…"
            btnSubmitDelivery.text = "Enviar entrega a Eminus"
        }
    }

    /** Repinta controles y lista de archivos (para mostrar/ocultar "Quitar"). */
    private fun refrescarEntrega() {
        actualizarControlesEntrega()
        currentDelivery?.let { mostrarEntregaEstudiante(it) }
    }

    private fun salirDeEdicion() {
        if (enviandoEntrega) return
        modoEdicion = false
        // Restaurar el comentario original por si se modificó sin enviar
        editDeliveryComment.setText(comentarioPrecargado ?: "")
        refrescarEntrega()
    }

    private fun confirmarQuitarArchivo(archivo: DeliveryAttachment) {
        if (enviandoEntrega) return
        AlertDialog.Builder(this)
            .setTitle("Quitar archivo")
            .setMessage("¿Quitar \"${archivo.nombreOriginal}\" de tu entrega en Eminus? El docente dejará de verlo.")
            .setPositiveButton("Quitar") { _, _ -> quitarArchivo(archivo) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun quitarArchivo(archivo: DeliveryAttachment) {
        val idCurso = courseId
        val idActividad = activityId
        setEnviando(true, "Quitando \"${archivo.nombreOriginal}\"…")

        // Igual que al enviar: confirmar primero que la actividad siga abierta
        verificarActividadAbierta(idCurso, idActividad) {
            apiClient.deleteDeliveryFile(idCurso, archivo.idArchivo) { result ->
                result.onSuccess {
                    setEnviando(false)
                    Toast.makeText(this, "🗑️ Archivo quitado de tu entrega", Toast.LENGTH_SHORT).show()
                    if (activityId == idActividad) cargarDetalleActividad()
                }.onFailure { e ->
                    terminarConError("No se pudo quitar el archivo: ${mensajeError(e)}")
                }
            }
        }
    }

    private fun mostrarEntregaEstudiante(delivery: StudentDelivery) {
        currentDelivery = delivery
        val archivos = delivery.archivos ?: emptyList()

        // Precargar el comentario anterior para no borrarlo al actualizar la entrega
        if (editDeliveryComment.text.isNullOrBlank() && !delivery.descEntrega.isNullOrBlank()) {
            comentarioPrecargado =
                HtmlCompat.fromHtml(delivery.descEntrega, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            editDeliveryComment.setText(comentarioPrecargado)
        }

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

                val btnDelete = itemView.findViewById<MaterialButton>(R.id.btnDeleteAttachment)
                btnDelete.visibility = if (puedeModificarEntrega()) View.VISIBLE else View.GONE
                btnDelete.setOnClickListener { confirmarQuitarArchivo(archivo) }

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
            .setPositiveButton("Enviar") { _, _ -> enviarEntrega() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ===================== ENVÍO REAL DE ENTREGAS =====================

    private fun enviarEntrega() {
        val uri = selectedFileUri ?: return
        if (enviandoEntrega) return

        val nombreArchivo = obtenerNombreArchivo(uri)
        val mimeType = contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(nombreArchivo.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val comentario = editDeliveryComment.text?.toString()?.trim().orEmpty()
        val idCurso = courseId
        val idActividad = activityId

        setEnviando(true, "Preparando archivo…")

        // Copiar el contenido del Uri a un archivo temporal (OkHttp necesita un File)
        Thread {
            val tempFile = try {
                copiarUriACache(uri, nombreArchivo)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (tempFile == null) {
                    terminarConError("No se pudo leer el archivo seleccionado.")
                } else {
                    verificarActividadAbierta(idCurso, idActividad) {
                        obtenerIdEntrega(idCurso, idActividad) { idEntrega ->
                            subirYConfirmar(idCurso, idActividad, idEntrega, tempFile, nombreArchivo, mimeType, comentario)
                        }
                    }
                }
            }
        }.start()
    }

    /** Igual que Eminus web: antes de enviar, se vuelve a consultar el estado real de la actividad. */
    private fun verificarActividadAbierta(idCurso: Long, idActividad: Long, onOk: () -> Unit) {
        setEnviando(true, "Verificando que la actividad siga abierta…")
        apiClient.getActivityDetail(idCurso, idActividad) { result ->
            result.onSuccess { detail ->
                when {
                    detail.esPorEquipos -> terminarConError("Las actividades por equipo deben entregarse desde Eminus web.")
                    detail.estaCerrada -> {
                        terminarConError(if (detail.estaCalificada || detail.estado == 3)
                            "La actividad ya fue calificada; Eminus no admite cambios."
                        else
                            "La actividad ya cerró; Eminus no admite nuevas entregas.")
                        mostrarDetalle(detail)
                    }
                    else -> onOk()
                }
            }.onFailure { e -> terminarConError(mensajeError(e)) }
        }
    }

    /** Usa la entrega existente o crea el borrador (paso 1 del flujo de Eminus). */
    private fun obtenerIdEntrega(idCurso: Long, idActividad: Long, onId: (Long) -> Unit) {
        val existente = currentDelivery?.idActividadEntrega ?: 0L
        if (existente > 0) {
            onId(existente)
            return
        }
        setEnviando(true, "Creando entrega en Eminus…")
        apiClient.createDeliveryDraft(idCurso, idActividad) { result ->
            result.onSuccess { onId(it) }
                .onFailure { e -> terminarConError(mensajeError(e)) }
        }
    }

    private fun subirYConfirmar(
        idCurso: Long,
        idActividad: Long,
        idEntrega: Long,
        archivo: File,
        nombreArchivo: String,
        mimeType: String,
        comentario: String
    ) {
        setEnviando(true, "Subiendo \"$nombreArchivo\"…")
        apiClient.uploadDeliveryFile(idCurso, idActividad, idEntrega, archivo, nombreArchivo, mimeType) { upload ->
            upload.onSuccess {
                setEnviando(true, "Confirmando entrega…")
                val textoHtml = construirTextoEntrega(comentario, nombreArchivo)
                apiClient.submitDelivery(idCurso, idActividad, idEntrega, textoHtml) { submit ->
                    archivo.delete()
                    submit.onSuccess {
                        setEnviando(false)
                        limpiarArchivoSeleccionado()
                        modoEdicion = false   // entrega actualizada: volver a solo lectura
                        Toast.makeText(this, "✅ Entrega enviada a Eminus", Toast.LENGTH_LONG).show()
                        // Si el usuario navegó a otra actividad mientras tanto, no recargamos encima
                        if (activityId == idActividad) cargarDetalleActividad()
                    }.onFailure { e ->
                        terminarConError("El archivo se subió, pero no se pudo confirmar la entrega: ${mensajeError(e)}")
                    }
                }
            }.onFailure { e ->
                archivo.delete()
                terminarConError(mensajeError(e))
            }
        }
    }

    /** Eminus exige texto en la entrega; se manda como HTML (el editor web es Froala). */
    private fun construirTextoEntrega(comentario: String, nombreArchivo: String): String {
        val previo = currentDelivery?.descEntrega
        // Si el alumno no tocó el comentario precargado, se reenvía el HTML original intacto
        // (conserva enlaces, formato e imágenes que la versión en texto plano perdería).
        if (!previo.isNullOrBlank() && (comentario.isBlank() || comentario == comentarioPrecargado)) {
            return previo
        }
        if (comentario.isNotBlank()) {
            return comentario.lines().joinToString("") { "<p>${android.text.TextUtils.htmlEncode(it)}</p>" }
        }
        return "<p>Entrega: ${android.text.TextUtils.htmlEncode(nombreArchivo)}</p>"
    }

    private fun copiarUriACache(uri: Uri, nombre: String): File {
        val dir = File(cacheDir, "entregas").apply { mkdirs() }
        val seguro = nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destino = File(dir, "${System.currentTimeMillis()}_$seguro")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Uri sin contenido" }
            destino.outputStream().use { output -> input.copyTo(output) }
        }
        return destino
    }

    private fun setEnviando(enviando: Boolean, mensaje: String = "") {
        enviandoEntrega = enviando
        layoutUploadProgress.visibility = if (enviando) View.VISIBLE else View.GONE
        txtUploadProgress.text = mensaje
        btnSubmitDelivery.isEnabled = !enviando && selectedFileUri != null
        btnSelectFile.isEnabled = !enviando
        btnClearFile.isEnabled = !enviando
        editDeliveryComment.isEnabled = !enviando
        swipeRefresh.isEnabled = !enviando
    }

    private fun terminarConError(mensaje: String) {
        setEnviando(false)
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("No se pudo completar")
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun mensajeError(e: Throwable): String {
        val msg = e.localizedMessage ?: "Error desconocido"
        return when {
            msg.contains("401") -> "Tu sesión de Eminus caducó. Renueva la sesión desde la pantalla principal y vuelve a intentarlo."
            e is java.io.IOException -> "Sin conexión con Eminus. Revisa tu internet e inténtalo de nuevo."
            else -> msg
        }
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
