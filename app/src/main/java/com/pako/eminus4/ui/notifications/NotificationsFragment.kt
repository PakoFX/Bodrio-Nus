package com.pako.eminus4.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.Activity
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.NotificationSummary
import com.pako.eminus4.ui.courses.AllActivitiesActivity
import com.pako.eminus4.ui.exams.ExamsActivity
import com.pako.eminus4.ui.forums.ForumsActivity
import com.pako.eminus4.ui.messages.MessagesActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class NotificationsFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var cardStatusBanner: MaterialCardView
    private lateinit var txtStatusEmoji: TextView
    private lateinit var txtStatusTitle: TextView
    private lateinit var txtStatusDesc: TextView

    private lateinit var txtBadgeActNuevas: TextView
    private lateinit var txtActividadesActivas: TextView

    private lateinit var txtBadgeExNuevos: TextView
    private lateinit var txtExamenesActivos: TextView

    private lateinit var txtBadgeFrNuevos: TextView
    private lateinit var txtForosActivos: TextView

    private lateinit var txtBadgeMsjNuevos: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas(view)
        cargarNotificaciones()
    }

    override fun onResume() {
        super.onResume()
        cargarNotificaciones()
    }

    private fun inicializarVistas(view: View) {
        val appBarLayout = view.findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val bannerVisible = (activity as? com.pako.eminus4.ui.main.MainActivity)?.isBannerVisible() == true
            v.setPadding(0, if (bannerVisible) 0 else systemBars.top, 0, 0)
            insets
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        cardStatusBanner = view.findViewById(R.id.cardStatusBanner)
        txtStatusEmoji = view.findViewById(R.id.txtStatusEmoji)
        txtStatusTitle = view.findViewById(R.id.txtStatusTitle)
        txtStatusDesc = view.findViewById(R.id.txtStatusDesc)

        txtBadgeActNuevas = view.findViewById(R.id.txtBadgeActNuevas)
        txtActividadesActivas = view.findViewById(R.id.txtActividadesActivas)

        txtBadgeExNuevos = view.findViewById(R.id.txtBadgeExNuevos)
        txtExamenesActivos = view.findViewById(R.id.txtExamenesActivos)

        txtBadgeFrNuevos = view.findViewById(R.id.txtBadgeFrNuevos)
        txtForosActivos = view.findViewById(R.id.txtForosActivos)

        txtBadgeMsjNuevos = view.findViewById(R.id.txtBadgeMsjNuevos)

        val cardActividades = view.findViewById<MaterialCardView>(R.id.cardActividades)
        val cardExamenes = view.findViewById<MaterialCardView>(R.id.cardExamenes)
        val cardForos = view.findViewById<MaterialCardView>(R.id.cardForos)
        val cardMensajeria = view.findViewById<MaterialCardView>(R.id.cardMensajeria)

        cardActividades.setOnClickListener {
            AllActivitiesActivity.start(requireContext())
        }

        cardExamenes.setOnClickListener {
            ExamsActivity.start(requireContext())
        }

        cardForos.setOnClickListener {
            ForumsActivity.start(requireContext())
        }

        cardMensajeria.setOnClickListener {
            MessagesActivity.start(requireContext())
        }

        swipeRefreshLayout.setColorSchemeColors(requireContext().getColor(android.R.color.holo_green_dark))
        swipeRefreshLayout.setOnRefreshListener {
            cargarNotificaciones()
        }
    }

    private fun cargarNotificaciones() {
        swipeRefreshLayout.isRefreshing = true

        // 1. Obtener cursos activos para calcular conteos reales
        val cachedCourses = sessionManager.getCachedCourses()
        if (cachedCourses.isNotEmpty()) {
            calcularYMostrarConCursos(cachedCourses)
        } else {
            apiClient.getAllCourses { result ->
                if (!isAdded) return@getAllCourses
                result.onSuccess { courses ->
                    sessionManager.saveCachedCourses(courses)
                    calcularYMostrarConCursos(courses)
                }.onFailure {
                    consultarEndpointNotificacionesFallback()
                }
            }
        }
    }

    private fun calcularYMostrarConCursos(courses: List<Course>) {
        val cursosActivos = courses.filter { it.isActivo() }
        val totalForos = cursosActivos.sumOf { it.foros }
        val totalExamenes = cursosActivos.sumOf { it.examenes }

        if (cursosActivos.isEmpty()) {
            consultarEndpointNotificacionesFallback()
            return
        }

        val pending = AtomicInteger(cursosActivos.size)
        var actividadesActivasCount = 0

        for (c in cursosActivos) {
            apiClient.getActivities(c.idCurso) { actResult ->
                if (!isAdded) return@getActivities

                actResult.onSuccess { activities ->
                    val activas = activities.count { esActividadActiva(it) }
                    synchronized(this) {
                        actividadesActivasCount += activas
                    }
                }

                if (pending.decrementAndGet() == 0) {
                    // Consultar mensajes sin leer
                    consultarMensajesYMostrarTodo(actividadesActivasCount, totalForos, totalExamenes)
                }
            }
        }
    }

    private fun consultarMensajesYMostrarTodo(actividades: Int, foros: Int, examenes: Int) {
        apiClient.getMessages(folderId = 1) { msgResult ->
            if (!isAdded) return@getMessages
            swipeRefreshLayout.isRefreshing = false

            val mensajesNoLeidos = msgResult.getOrNull()?.count { it.esNoLeido } ?: 0

            renderizarDatosReales(
                actividades = actividades,
                foros = foros,
                examenes = examenes,
                mensajesSinLeer = mensajesNoLeidos
            )
        }
    }

    private fun consultarEndpointNotificacionesFallback() {
        apiClient.getNotifications { result ->
            if (!isAdded) return@getNotifications
            swipeRefreshLayout.isRefreshing = false

            result.onSuccess { summary ->
                mostrarNotificaciones(summary)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al cargar notificaciones: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun esActividadActiva(act: Activity): Boolean {
        val titulo = act.titulo ?: act.nombreRaw ?: ""
        if (titulo.isBlank()) return false
        if (act.estaEntregada || act.estaCalificada) return false

        val fechaTermino = act.fechaTermino ?: return true
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(fechaTermino.substring(0, 19))
            date != null && date.after(Date())
        } catch (_: Exception) {
            true
        }
    }

    private fun renderizarDatosReales(
        actividades: Int,
        foros: Int,
        examenes: Int,
        mensajesSinLeer: Int
    ) {
        val totalAlertas = actividades + mensajesSinLeer + examenes

        // Banner de estado general
        if (totalAlertas > 0) {
            txtStatusEmoji.text = "⚡"
            txtStatusTitle.text = "$totalAlertas pendiente${if (totalAlertas > 1) "s" else ""} por atender"
            txtStatusDesc.text = if (actividades > 0) {
                "Tienes $actividades tarea${if (actividades > 1) "s" else ""} activa${if (actividades > 1) "s" else ""} por entregar."
            } else {
                "Revisa tus evaluaciones o mensajes sin leer."
            }
            cardStatusBanner.strokeColor = ContextCompat.getColor(requireContext(), R.color.stroke_alert)
        } else {
            txtStatusEmoji.text = "✨"
            txtStatusTitle.text = "Al corriente"
            txtStatusDesc.text = "No tienes avisos urgentes sin revisar en tus cursos."
            cardStatusBanner.strokeColor = ContextCompat.getColor(requireContext(), R.color.stroke_ok)
        }

        // Actividades
        txtActividadesActivas.text = "$actividades activa${if (actividades == 1) "" else "s"}"
        if (actividades > 0) {
            txtBadgeActNuevas.text = "$actividades pendiente${if (actividades == 1) "" else "s"}"
            txtBadgeActNuevas.visibility = View.VISIBLE
        } else {
            txtBadgeActNuevas.visibility = View.GONE
        }

        // Exámenes
        txtExamenesActivos.text = "$examenes activo${if (examenes == 1) "" else "s"}"
        if (examenes > 0) {
            txtBadgeExNuevos.text = "$examenes disponible${if (examenes == 1) "" else "s"}"
            txtBadgeExNuevos.visibility = View.VISIBLE
        } else {
            txtBadgeExNuevos.visibility = View.GONE
        }

        // Foros
        txtForosActivos.text = "$foros activo${if (foros == 1) "" else "s"}"
        if (foros > 0) {
            txtBadgeFrNuevos.text = "$foros activo${if (foros == 1) "" else "s"}"
            txtBadgeFrNuevos.visibility = View.VISIBLE
        } else {
            txtBadgeFrNuevos.visibility = View.GONE
        }

        // Mensajes
        if (mensajesSinLeer > 0) {
            txtBadgeMsjNuevos.text = "$mensajesSinLeer sin leer"
            txtBadgeMsjNuevos.visibility = View.VISIBLE
        } else {
            txtBadgeMsjNuevos.visibility = View.GONE
        }
    }

    private fun mostrarNotificaciones(s: NotificationSummary) {
        // Banner de estado general
        val totalAlertas = s.getTotalAlertas()
        if (totalAlertas > 0) {
            txtStatusEmoji.text = "⚡"
            txtStatusTitle.text = "$totalAlertas pendiente${if (totalAlertas > 1) "s" else ""} por atender"
            txtStatusDesc.text = "Revisa los entregables y evaluaciones activas a continuación."
            cardStatusBanner.strokeColor = ContextCompat.getColor(requireContext(), R.color.stroke_alert)
        } else {
            txtStatusEmoji.text = "✨"
            txtStatusTitle.text = "Al corriente"
            txtStatusDesc.text = "No tienes avisos urgentes sin revisar en tus cursos."
            cardStatusBanner.strokeColor = ContextCompat.getColor(requireContext(), R.color.stroke_ok)
        }

        // Actividades
        txtActividadesActivas.text = "${s.actActivas} activa${if (s.actActivas == 1) "" else "s"}"
        txtBadgeActNuevas.text = "${s.actNuevas} nueva${if (s.actNuevas == 1) "" else "s"}"
        txtBadgeActNuevas.visibility = if (s.actNuevas > 0) View.VISIBLE else View.GONE

        // Exámenes
        txtExamenesActivos.text = "${s.exActivos} activo${if (s.exActivos == 1) "" else "s"}"
        txtBadgeExNuevos.text = "${s.exNuevos} nuevo${if (s.exNuevos == 1) "" else "s"}"
        txtBadgeExNuevos.visibility = if (s.exNuevos > 0) View.VISIBLE else View.GONE

        // Foros
        txtForosActivos.text = "${s.frActivos} activo${if (s.frActivos == 1) "" else "s"}"
        txtBadgeFrNuevos.text = "${s.frNuevos} nuevo${if (s.frNuevos == 1) "" else "s"}"
        txtBadgeFrNuevos.visibility = if (s.frNuevos > 0) View.VISIBLE else View.GONE

        // Mensajes
        txtBadgeMsjNuevos.text = "${s.msjNuevos} sin leer"
        txtBadgeMsjNuevos.visibility = if (s.msjNuevos > 0) View.VISIBLE else View.GONE
    }
}

