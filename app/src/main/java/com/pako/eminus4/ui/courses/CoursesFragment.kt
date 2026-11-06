package com.pako.eminus4.ui.courses

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.Course

class CoursesFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient
    private lateinit var courseAdapter: CourseAdapter

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvCourses: RecyclerView
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutEmptyState: View
    private lateinit var txtEmptyMessage: TextView
    private lateinit var txtGreeting: TextView
    private lateinit var txtStudentCampus: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var chipFilterActivos: Chip
    private lateinit var chipFilterTodos: Chip

    private var allCourses: List<Course> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_courses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas(view)
        configurarRecyclerView()
        configurarFiltros()
        cargarDatosDeUsuario()
        cargarCursosDesdeCache()
        cargarCursosDesdeApi()
    }

    private fun cargarCursosDesdeCache() {
        val cached = sessionManager.getCachedCourses()
        if (cached.isNotEmpty()) {
            allCourses = cached
            aplicarFiltro(soloActivos = chipFilterActivos.isChecked)
        }
    }

    private fun inicializarVistas(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        rvCourses = view.findViewById(R.id.rvCourses)
        progressLoading = view.findViewById(R.id.progressLoading)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        txtEmptyMessage = view.findViewById(R.id.txtEmptyMessage)
        txtGreeting = view.findViewById(R.id.txtGreeting)
        txtStudentCampus = view.findViewById(R.id.txtStudentCampus)
        btnRefresh = view.findViewById(R.id.btnRefreshCourses)
        chipFilterActivos = view.findViewById(R.id.chipFilterActivos)
        chipFilterTodos = view.findViewById(R.id.chipFilterTodos)

        val appBarLayout = view.findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val bannerVisible = (activity as? com.pako.eminus4.ui.main.MainActivity)?.isBannerVisible() == true
            v.setPadding(0, if (bannerVisible) 0 else systemBars.top, 0, 0)
            insets
        }

        swipeRefreshLayout.setColorSchemeColors(requireContext().getColor(android.R.color.holo_green_dark))
        swipeRefreshLayout.setOnRefreshListener {
            cargarCursosDesdeApi()
        }

        btnRefresh.setOnClickListener {
            swipeRefreshLayout.isRefreshing = true
            cargarCursosDesdeApi()
        }
    }

    private fun configurarRecyclerView() {
        courseAdapter = CourseAdapter { curso ->
            val intent = Intent(requireContext(), CourseDetailActivity::class.java).apply {
                putExtra(CourseDetailActivity.EXTRA_COURSE_ID, curso.idCurso)
                putExtra(CourseDetailActivity.EXTRA_COURSE_NAME, curso.nombre)
                putExtra(CourseDetailActivity.EXTRA_COURSE_COLOR, curso.getColorHexaLimpio())
                putExtra(CourseDetailActivity.EXTRA_COURSE_THUMBNAIL, curso.getFullThumbnailUrl())
                putExtra(CourseDetailActivity.EXTRA_COURSE_ACTIVIDADES, curso.actividades)
                putExtra(CourseDetailActivity.EXTRA_COURSE_FOROS, curso.foros)
                putExtra(CourseDetailActivity.EXTRA_COURSE_INTEGRANTES, curso.integrantes)
                putExtra(CourseDetailActivity.EXTRA_COURSE_DEPENDENCIA, curso.dependencia)
            }
            startActivity(intent)
        }
        rvCourses.layoutManager = LinearLayoutManager(requireContext())
        rvCourses.adapter = courseAdapter
    }

    private fun configurarFiltros() {
        chipFilterActivos.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) aplicarFiltro(soloActivos = true)
        }
        chipFilterTodos.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) aplicarFiltro(soloActivos = false)
        }
    }

    private fun cargarDatosDeUsuario() {
        val perfil = sessionManager.getUserProfile()
        txtGreeting.text = "¡Hola, ${perfil.getPrimerNombre()}!"
        txtStudentCampus.text = if (perfil.userCampus.isNotBlank()) perfil.userCampus else "Universidad Veracruzana"
    }

    fun recargarCursos() {
        if (isAdded) {
            cargarCursosDesdeApi()
        }
    }

    override fun onResume() {
        super.onResume()
        if (allCourses.isEmpty() && !sessionManager.isSessionExpired()) {
            cargarCursosDesdeApi()
        }
    }

    private fun cargarCursosDesdeApi() {
        if (!swipeRefreshLayout.isRefreshing && allCourses.isEmpty()) {
            progressLoading.visibility = View.VISIBLE
        }

        apiClient.getAllCourses { result ->
            if (!isAdded) return@getAllCourses

            progressLoading.visibility = View.GONE
            swipeRefreshLayout.isRefreshing = false

            result.onSuccess { cursos ->
                allCourses = cursos
                sessionManager.saveCachedCourses(cursos)
                sessionManager.setSessionExpired(false)
                (activity as? com.pako.eminus4.ui.main.MainActivity)?.ocultarBannerSesion()
                aplicarFiltro(soloActivos = chipFilterActivos.isChecked)
            }.onFailure { error ->
                val esExpirada = error.message?.contains("401") == true || error.message?.contains("expirada") == true
                if (esExpirada) {
                    sessionManager.setSessionExpired(true)
                }

                if (allCourses.isNotEmpty()) {
                    val mensaje = if (esExpirada) {
                        "⚠️ Sesión caducada. Mostrando cursos guardados."
                    } else {
                        "Modo sin conexión. Mostrando cursos guardados."
                    }
                    (activity as? com.pako.eminus4.ui.main.MainActivity)?.mostrarBannerSesionExpirada(mensaje)
                    aplicarFiltro(soloActivos = chipFilterActivos.isChecked)
                } else {
                    val mensaje = if (esExpirada) {
                        "⚠️ Sesión en línea caducada."
                    } else {
                        "Sin conexión a internet."
                    }
                    (activity as? com.pako.eminus4.ui.main.MainActivity)?.mostrarBannerSesionExpirada(mensaje)

                    layoutEmptyState.visibility = View.VISIBLE
                    txtEmptyMessage.text = if (esExpirada) {
                        "Tu sesión en línea ha caducado y aún no tienes materias descargadas.\n\nToca \"Renovar\" arriba para iniciar sesión y sincronizar tus cursos."
                    } else {
                        "No se pudo conectar a Eminus.\nDesliza hacia abajo para reintentar."
                    }
                    courseAdapter.submitList(emptyList())
                }
            }
        }
    }

    private fun aplicarFiltro(soloActivos: Boolean) {
        val cursosFiltrados = if (soloActivos) {
            allCourses.filter { it.isActivo() }
        } else {
            allCourses
        }

        courseAdapter.submitList(cursosFiltrados)

        if (cursosFiltrados.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            txtEmptyMessage.text = if (soloActivos) {
                "No tienes materias activas en este período"
            } else {
                "No se encontraron materias registradas"
            }
        } else {
            layoutEmptyState.visibility = View.GONE
        }
    }
}

