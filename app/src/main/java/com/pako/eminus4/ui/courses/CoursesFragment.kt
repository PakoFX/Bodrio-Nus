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
        cargarCursosDesdeApi()
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
            v.setPadding(0, systemBars.top, 0, 0)
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

    private fun cargarCursosDesdeApi() {
        if (!swipeRefreshLayout.isRefreshing) {
            progressLoading.visibility = View.VISIBLE
        }

        apiClient.getAllCourses { result ->
            if (!isAdded) return@getAllCourses

            progressLoading.visibility = View.GONE
            swipeRefreshLayout.isRefreshing = false

            result.onSuccess { cursos ->
                allCourses = cursos
                aplicarFiltro(soloActivos = chipFilterActivos.isChecked)
            }.onFailure { error ->
                if (error.message?.contains("401") == true || error.message?.contains("expirada") == true) {
                    Toast.makeText(requireContext(), "Tu sesión ha expirado", Toast.LENGTH_LONG).show()
                    (activity as? com.pako.eminus4.ui.main.MainActivity)?.cerrarSesionDirecta()
                } else {
                    Toast.makeText(requireContext(), "Error al cargar cursos: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    if (allCourses.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        txtEmptyMessage.text = "No se pudo conectar a Eminus\nDesliza para reintentar"
                    }
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

