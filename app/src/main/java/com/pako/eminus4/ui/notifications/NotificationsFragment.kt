package com.pako.eminus4.ui.notifications

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.AgendaLoader
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.CourseActivityItem
import com.pako.eminus4.data.model.EstadoAlumno
import com.pako.eminus4.data.model.ExamItem
import com.pako.eminus4.data.model.ForumItem
import com.pako.eminus4.data.model.MessageItem
import com.pako.eminus4.ui.courses.ActividadCardBinder
import com.pako.eminus4.ui.courses.AllActivitiesActivity
import com.pako.eminus4.ui.exams.ExamsActivity
import com.pako.eminus4.ui.forums.ForumsActivity
import com.pako.eminus4.ui.main.MainActivity
import com.pako.eminus4.ui.messages.MessageDetailActivity
import com.pako.eminus4.ui.messages.MessagesActivity
import com.pako.eminus4.util.Plazos
import java.util.concurrent.TimeUnit

/**
 * Pestaña "Avisos" como agenda:
 *  1. Resumen (cuántas tienes por hacer y qué vence pronto).
 *  2. Por hacer: las actividades mismas, ordenadas por fecha límite.
 *  3. Novedades: calificaciones recientes, mensajes sin leer, exámenes disponibles, foros con aportes.
 *  Las secciones vacías no se muestran.
 */
class NotificationsFragment : Fragment() {

    companion object {
        private const val MAX_POR_HACER = 5
        private const val MAX_FILAS = 3
        private val DIAS_CALIFICACION_RECIENTE = TimeUnit.DAYS.toMillis(14)
        private val HORAS_URGENTE = TimeUnit.HOURS.toMillis(72)
        private const val RECARGA_MIN_MS = 60_000L
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient
    private lateinit var loader: AgendaLoader

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var txtHoy: TextView
    private lateinit var cardResumen: MaterialCardView
    private lateinit var imgResumen: ImageView
    private lateinit var txtResumenTitulo: TextView
    private lateinit var txtResumenDetalle: TextView

    private lateinit var seccionPorHacer: View
    private lateinit var txtTituloPorHacer: TextView
    private lateinit var layoutPorHacer: LinearLayout
    private lateinit var btnVerMasPorHacer: MaterialButton

    private lateinit var seccionCalificaciones: View
    private lateinit var layoutCalificaciones: LinearLayout
    private lateinit var seccionMensajes: View
    private lateinit var txtTituloMensajes: TextView
    private lateinit var layoutMensajes: LinearLayout
    private lateinit var seccionExamenes: View
    private lateinit var layoutExamenes: LinearLayout
    private lateinit var seccionForos: View
    private lateinit var layoutForos: LinearLayout
    private lateinit var cardEsperando: MaterialCardView
    private lateinit var txtEsperando: TextView
    private lateinit var badgeAccesoActividades: TextView
    private lateinit var badgeAccesoMensajes: TextView
    private lateinit var badgeAccesoForos: TextView
    private lateinit var badgeAccesoExamenes: TextView

    /** Evita pintar resultados viejos si se lanzó otra carga mientras tanto. */
    private var generacion = 0
    private var cargando = false
    private var ultimaCarga = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        apiClient = EminusApiClient(sessionManager)
        loader = AgendaLoader(sessionManager, apiClient)
        inicializarVistas(view)
    }

    override fun onResume() {
        super.onResume()
        // Al volver de un detalle (p. ej. tras entregar) o en la primera carga
        if (!isHidden || ultimaCarga == 0L) cargar()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && SystemClock.elapsedRealtime() - ultimaCarga > RECARGA_MIN_MS) cargar()
    }

    private fun inicializarVistas(view: View) {
        val appBarLayout = view.findViewById<View>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bannerVisible = (activity as? MainActivity)?.isBannerVisible() == true
            v.setPadding(0, if (bannerVisible) 0 else systemBars.top, 0, 0)
            insets
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        txtHoy = view.findViewById(R.id.txtHoy)
        cardResumen = view.findViewById(R.id.cardResumen)
        imgResumen = view.findViewById(R.id.imgResumen)
        txtResumenTitulo = view.findViewById(R.id.txtResumenTitulo)
        txtResumenDetalle = view.findViewById(R.id.txtResumenDetalle)

        seccionPorHacer = view.findViewById(R.id.seccionPorHacer)
        txtTituloPorHacer = view.findViewById(R.id.txtTituloPorHacer)
        layoutPorHacer = view.findViewById(R.id.layoutPorHacer)
        btnVerMasPorHacer = view.findViewById(R.id.btnVerMasPorHacer)

        seccionCalificaciones = view.findViewById(R.id.seccionCalificaciones)
        layoutCalificaciones = view.findViewById(R.id.layoutCalificaciones)
        seccionMensajes = view.findViewById(R.id.seccionMensajes)
        txtTituloMensajes = view.findViewById(R.id.txtTituloMensajes)
        layoutMensajes = view.findViewById(R.id.layoutMensajes)
        seccionExamenes = view.findViewById(R.id.seccionExamenes)
        layoutExamenes = view.findViewById(R.id.layoutExamenes)
        seccionForos = view.findViewById(R.id.seccionForos)
        layoutForos = view.findViewById(R.id.layoutForos)
        cardEsperando = view.findViewById(R.id.cardEsperando)
        txtEsperando = view.findViewById(R.id.txtEsperando)
        badgeAccesoActividades = view.findViewById(R.id.badgeAccesoActividades)
        badgeAccesoMensajes = view.findViewById(R.id.badgeAccesoMensajes)
        badgeAccesoForos = view.findViewById(R.id.badgeAccesoForos)
        badgeAccesoExamenes = view.findViewById(R.id.badgeAccesoExamenes)

        txtHoy.text = Plazos.hoyLargo()

        btnVerMasPorHacer.setOnClickListener {
            AllActivitiesActivity.start(requireContext(), AllActivitiesActivity.Pestana.POR_HACER)
        }
        cardEsperando.setOnClickListener {
            AllActivitiesActivity.start(requireContext(), AllActivitiesActivity.Pestana.ESPERANDO)
        }
        view.findViewById<View>(R.id.accesoActividades).setOnClickListener {
            AllActivitiesActivity.start(requireContext())
        }
        view.findViewById<View>(R.id.accesoMensajes).setOnClickListener { MessagesActivity.start(requireContext()) }
        view.findViewById<View>(R.id.accesoForos).setOnClickListener { ForumsActivity.start(requireContext()) }
        view.findViewById<View>(R.id.accesoExamenes).setOnClickListener { ExamsActivity.start(requireContext()) }

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.brand_uv_green))
        swipeRefreshLayout.setOnRefreshListener { cargar(forzar = true) }
    }

    // ===================== CARGA =====================

    private fun cargar(forzar: Boolean = false) {
        if (cargando && !forzar) return
        cargando = true
        val gen = ++generacion
        swipeRefreshLayout.isRefreshing = true
        txtHoy.text = Plazos.hoyLargo()

        loader.cargar { r ->
            if (!vigente(gen)) return@cargar
            cargando = false
            ultimaCarga = SystemClock.elapsedRealtime()
            swipeRefreshLayout.isRefreshing = false

            pintarAgenda(r)
            cargarNovedades(gen, r.cursos)
        }
    }

    private fun vigente(gen: Int) = gen == generacion && isAdded && view != null

    private fun cargarNovedades(gen: Int, cursos: List<Course>) {
        apiClient.getMessages(folderId = 1) { res ->
            if (!vigente(gen)) return@getMessages
            pintarMensajes(res.getOrNull()?.filter { it.esNoLeido } ?: emptyList())
        }

        // Exámenes: solo cursos que los tienen
        val conExamenes = cursos.filter { it.examenes > 0 }
        val examenes = mutableListOf<ExamItem>()
        var pendExamenes = conExamenes.size
        if (pendExamenes == 0) pintarExamenes(emptyList())
        for (c in conExamenes) {
            apiClient.getExams(c.idCurso) { res ->
                if (!vigente(gen)) return@getExams
                res.getOrNull()?.forEach {
                    it.idCurso = c.idCurso
                    it.nombreCurso = c.getNombreLimpio()
                    examenes.add(it)
                }
                if (--pendExamenes == 0) pintarExamenes(examenes)
            }
        }

        // Foros: solo cursos que los tienen
        val conForos = cursos.filter { it.foros > 0 }
        val foros = mutableListOf<ForumItem>()
        var pendForos = conForos.size
        if (pendForos == 0) pintarForos(emptyList())
        for (c in conForos) {
            apiClient.getForums(c.idCurso) { res ->
                if (!vigente(gen)) return@getForums
                res.getOrNull()?.forEach {
                    it.idCurso = c.idCurso
                    it.nombreCurso = c.getNombreLimpio()
                    foros.add(it)
                }
                if (--pendForos == 0) pintarForos(foros)
            }
        }
    }

    // ===================== AGENDA =====================

    private fun pintarAgenda(r: AgendaLoader.Resultado) {
        val ctx = requireContext()
        val ahora = System.currentTimeMillis()
        val porHacer = r.actividades
            .filter { it.activity.estadoAlumno.esPendiente }
            .sortedBy { it.activity.terminoMillis ?: Long.MAX_VALUE }
        val urgentes = porHacer.count { a ->
            a.activity.terminoMillis?.let { it - ahora in 0..HORAS_URGENTE } == true
        }
        val enEquipo = porHacer.count { it.activity.esPorEquipos }
        val esperando = r.actividades.count { it.activity.estadoAlumno == EstadoAlumno.ESPERANDO }

        // ---- Resumen ----
        val sinDatos = r.cursos.isEmpty() && r.actividades.isEmpty()
        when {
            sinDatos && (r.sesionExpirada || r.desdeCache) -> {
                ponerIconoResumen(R.drawable.ic_cloud_off, R.color.text_secondary)
                txtResumenTitulo.text = "No se pudo cargar tu agenda"
                txtResumenDetalle.text = if (r.sesionExpirada)
                    "Tu sesión de Eminus caducó. Renuévala desde el aviso superior."
                else "Sin conexión con Eminus. Desliza hacia abajo para reintentar."
                cardResumen.strokeColor = ContextCompat.getColor(ctx, R.color.stroke_ok)
            }
            porHacer.isEmpty() -> {
                ponerIconoResumen(R.drawable.ic_task_alt, R.color.badge_success_text)
                txtResumenTitulo.text = "Estás al corriente"
                txtResumenDetalle.text = "No tienes entregas pendientes en tus cursos activos."
                cardResumen.strokeColor = ContextCompat.getColor(ctx, R.color.badge_success_text)
            }
            else -> {
                val n = porHacer.size
                if (urgentes > 0) ponerIconoResumen(R.drawable.ic_local_fire_department, R.color.badge_error_text)
                else ponerIconoResumen(R.drawable.ic_pending_actions, R.color.badge_warning_text)
                txtResumenTitulo.text = "Tienes $n por hacer"
                val partes = mutableListOf<String>()
                if (urgentes > 0) {
                    partes += "$urgentes vence${if (urgentes > 1) "n" else ""} en los próximos 3 días"
                } else {
                    val prox = porHacer.first()
                    partes += "Lo próximo: ${prox.activity.nombre} (${Plazos.plazo(prox.activity.terminoMillis).texto.replaceFirstChar { it.lowercase() }})"
                }
                if (enEquipo > 0) partes += "$enEquipo en equipo"
                txtResumenDetalle.text = partes.joinToString(" · ")
                cardResumen.strokeColor = ContextCompat.getColor(
                    ctx, if (urgentes > 0) R.color.badge_error_text else R.color.badge_warning_text
                )
            }
        }
        if (!sinDatos && (r.sesionExpirada || r.cursosConError > 0 || r.desdeCache)) {
            txtResumenDetalle.append(
                if (r.sesionExpirada) "\nSesión caducada: puede faltar información."
                else "\nSin conexión: puede faltar información."
            )
        }

        ponerContador(badgeAccesoActividades, porHacer.size)

        // ---- Por hacer ----
        layoutPorHacer.removeAllViews()
        seccionPorHacer.visibility = if (porHacer.isEmpty()) View.GONE else View.VISIBLE
        txtTituloPorHacer.text = "POR HACER · ${porHacer.size}"
        val inflater = LayoutInflater.from(ctx)
        porHacer.take(MAX_POR_HACER).forEach { item ->
            val card = inflater.inflate(R.layout.item_all_activity, layoutPorHacer, false)
            ActividadCardBinder.bind(card, item) { ActividadCardBinder.abrirDetalle(ctx, it) }
            layoutPorHacer.addView(card)
        }
        if (porHacer.size > MAX_POR_HACER) {
            btnVerMasPorHacer.visibility = View.VISIBLE
            btnVerMasPorHacer.text = "Ver las ${porHacer.size} pendientes"
        } else {
            btnVerMasPorHacer.visibility = View.GONE
        }

        // ---- Calificaciones recientes ----
        val recientes = r.actividades
            .filter { it.activity.calificacion != null }
            .mapNotNull { a -> Plazos.parseIso(a.activity.fechaRevision)?.let { a to it } }
            .filter { ahora - it.second <= DIAS_CALIFICACION_RECIENTE }
            .sortedByDescending { it.second }
            .take(MAX_FILAS)
        layoutCalificaciones.removeAllViews()
        seccionCalificaciones.visibility = if (recientes.isEmpty()) View.GONE else View.VISIBLE
        recientes.forEach { (item, revision) ->
            val act = item.activity
            val cal = act.calificacion ?: 0.0
            val max = act.calificacionMax
            agregarFila(
                layoutCalificaciones, R.drawable.ic_star, R.color.badge_success_text, act.nombre,
                "${Plazos.haceCuanto(revision).replaceFirstChar { it.uppercase() }} · ${item.courseName}",
                "${ActividadCardBinder.formatoNota(cal)}/${ActividadCardBinder.formatoNota(max)}",
                ContextCompat.getColor(ctx, if (cal >= max * 0.6) R.color.badge_success_text else R.color.badge_error_text)
            ) { ActividadCardBinder.abrirDetalle(ctx, item) }
        }

        // ---- Esperando calificación ----
        if (esperando > 0) {
            cardEsperando.visibility = View.VISIBLE
            txtEsperando.text = if (esperando == 1) "1 entrega esperando calificación"
                                else "$esperando entregas esperando calificación"
        } else {
            cardEsperando.visibility = View.GONE
        }
    }

    // ===================== NOVEDADES =====================

    private fun pintarMensajes(noLeidos: List<MessageItem>) {
        layoutMensajes.removeAllViews()
        seccionMensajes.visibility = if (noLeidos.isEmpty()) View.GONE else View.VISIBLE
        txtTituloMensajes.text = "MENSAJES SIN LEER · ${noLeidos.size}"
        ponerContador(badgeAccesoMensajes, noLeidos.size)

        val ctx = requireContext()
        noLeidos.take(MAX_FILAS).forEach { m ->
            val cuando = Plazos.haceCuanto(Plazos.parseIso(m.fechaEnviado))
            agregarFila(
                layoutMensajes, R.drawable.ic_mail, R.color.badge_info_text, m.titulo?.takeIf { it.isNotBlank() } ?: "(Sin asunto)",
                listOf(m.getNombreRemitente(), cuando).filter { it.isNotBlank() }.joinToString(" · "),
                null, null
            ) { MessageDetailActivity.start(ctx, m.idMensaje) }
        }
        if (noLeidos.size > MAX_FILAS) {
            agregarFila(layoutMensajes, R.drawable.ic_inbox, R.color.text_secondary, "Ver los ${noLeidos.size} mensajes sin leer", "", null, null) {
                MessagesActivity.start(ctx)
            }
        }
    }

    private fun pintarExamenes(examenes: List<ExamItem>) {
        val ahora = System.currentTimeMillis()
        val disponibles = examenes
            .filter { !it.estaCalificado && !it.estaVencido }
            .filter { (Plazos.parseIso(it.fechaInicio) ?: 0L) <= ahora }
            .sortedBy { Plazos.parseIso(it.fechaTermino) ?: Long.MAX_VALUE }
        ponerContador(badgeAccesoExamenes, disponibles.size)
        layoutExamenes.removeAllViews()
        seccionExamenes.visibility = if (disponibles.isEmpty()) View.GONE else View.VISIBLE

        val ctx = requireContext()
        disponibles.take(MAX_FILAS).forEach { ex ->
            val p = Plazos.plazo(Plazos.parseIso(ex.fechaTermino))
            agregarFila(
                layoutExamenes, R.drawable.ic_quiz, R.color.brand_uv_green, ex.titulo, "${ex.nombreCurso} · ${p.texto}",
                null, null
            ) { ExamsActivity.start(ctx) }
        }
    }

    private fun pintarForos(foros: List<ForumItem>) {
        val conNovedades = foros.filter { it.nuevosComentarios > 0 }
            .sortedByDescending { it.nuevosComentarios }
        ponerContador(badgeAccesoForos, conNovedades.size)
        layoutForos.removeAllViews()
        seccionForos.visibility = if (conNovedades.isEmpty()) View.GONE else View.VISIBLE

        val ctx = requireContext()
        conNovedades.take(MAX_FILAS).forEach { f ->
            agregarFila(
                layoutForos, R.drawable.ic_forum, R.color.badge_info_text, f.titulo, f.nombreCurso,
                "${f.nuevosComentarios} nuevo${if (f.nuevosComentarios == 1) "" else "s"}",
                ContextCompat.getColor(ctx, R.color.badge_info_text)
            ) { ForumsActivity.start(ctx) }
        }
    }

    private fun agregarFila(
        contenedor: LinearLayout,
        @DrawableRes icono: Int,
        @ColorRes colorIcono: Int,
        titulo: String,
        subtitulo: String,
        valor: String?,
        colorValor: Int?,
        onClick: () -> Unit
    ) {
        val fila = LayoutInflater.from(requireContext()).inflate(R.layout.item_aviso_fila, contenedor, false)
        fila.findViewById<ImageView>(R.id.imgFilaIcono).apply {
            setImageResource(icono)
            imageTintList = ContextCompat.getColorStateList(requireContext(), colorIcono)
        }
        fila.findViewById<TextView>(R.id.txtFilaTitulo).text = titulo
        fila.findViewById<TextView>(R.id.txtFilaSubtitulo).apply {
            text = subtitulo
            visibility = if (subtitulo.isBlank()) View.GONE else View.VISIBLE
        }
        fila.findViewById<TextView>(R.id.txtFilaValor).apply {
            text = valor ?: ""
            visibility = if (valor.isNullOrBlank()) View.GONE else View.VISIBLE
            colorValor?.let { setTextColor(it) }
        }
        fila.setOnClickListener { onClick() }
        contenedor.addView(fila)
    }

    private fun ponerIconoResumen(@DrawableRes icono: Int, @ColorRes color: Int) {
        imgResumen.setImageResource(icono)
        imgResumen.imageTintList = ContextCompat.getColorStateList(requireContext(), color)
    }

    /** Contador rojo sobre un acceso rápido; se oculta en 0. */
    private fun ponerContador(badge: TextView, n: Int) {
        badge.visibility = if (n > 0) View.VISIBLE else View.GONE
        badge.text = if (n > 99) "99+" else n.toString()
    }
}
