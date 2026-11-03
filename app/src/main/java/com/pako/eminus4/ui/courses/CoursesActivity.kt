package com.pako.eminus4.ui.courses

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.pako.eminus4.AppUpdater
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.ui.auth.LoginWebViewActivity

class CoursesActivity : AppCompatActivity() {

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
    private lateinit var btnLogout: ImageButton
    private lateinit var chipFilterActivos: Chip
    private lateinit var chipFilterTodos: Chip

    private var allCourses: List<Course> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            navegarALogin()
            return
        }

        setContentView(R.layout.activity_courses)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        configurarRecyclerView()
        configurarFiltros()
        cargarDatosDeUsuario()
        cargarCursosDesdeApi()

        // Verificar actualizaciones de GitHub en segundo plano
        AppUpdater.verificarActualizaciones(this, silent = true)
    }

    private fun inicializarVistas() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvCourses = findViewById(R.id.rvCourses)
        progressLoading = findViewById(R.id.progressLoading)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage)
        txtGreeting = findViewById(R.id.txtGreeting)
        txtStudentCampus = findViewById(R.id.txtStudentCampus)
        btnRefresh = findViewById(R.id.btnRefreshCourses)
        btnLogout = findViewById(R.id.btnLogout)
        chipFilterActivos = findViewById(R.id.chipFilterActivos)
        chipFilterTodos = findViewById(R.id.chipFilterTodos)

        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rvCourses) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + 48)
            insets
        }

        swipeRefreshLayout.setColorSchemeColors(getColor(android.R.color.holo_green_dark))
        swipeRefreshLayout.setOnRefreshListener {
            cargarCursosDesdeApi()
        }

        btnRefresh.setOnClickListener {
            swipeRefreshLayout.isRefreshing = true
            cargarCursosDesdeApi()
        }

        btnLogout.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }
    }

    private fun configurarRecyclerView() {
        courseAdapter = CourseAdapter { curso ->
            val intent = Intent(this, CourseDetailActivity::class.java).apply {
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
        rvCourses.layoutManager = LinearLayoutManager(this)
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
        // Formato UV típico: APELLIDO_P APELLIDO_M NOMBRE (ej. VIVEROS SALMONES FRANCISCO XAVIER)
        val palabras = perfil.userName.split(" ").filter { it.isNotBlank() }
        val nombreFormateado = when {
            palabras.size >= 3 -> palabras[2]
            palabras.isNotEmpty() -> palabras.first()
            else -> "Estudiante"
        }.lowercase().replaceFirstChar { it.uppercase() }

        txtGreeting.text = "¡Hola, $nombreFormateado!"
        txtStudentCampus.text = if (perfil.userCampus.isNotBlank()) perfil.userCampus else "Universidad Veracruzana"
    }

    private fun cargarCursosDesdeApi() {
        if (!swipeRefreshLayout.isRefreshing) {
            progressLoading.visibility = View.VISIBLE
        }
        layoutEmptyState.visibility = View.GONE

        apiClient.getAllCourses { result ->
            progressLoading.visibility = View.GONE
            swipeRefreshLayout.isRefreshing = false

            result.onSuccess { cursos ->
                allCourses = cursos
                aplicarFiltro(soloActivos = chipFilterActivos.isChecked)
            }.onFailure { error ->
                if (error.message?.contains("401") == true) {
                    Toast.makeText(this, "Sesión de Eminus cerrada. Inicia sesión.", Toast.LENGTH_SHORT).show()
                    val oldToken = sessionManager.getAccessToken()
                    sessionManager.clearSession()
                    navegarALogin(sessionExpired = true, expiredToken = oldToken)
                } else {
                    layoutEmptyState.visibility = View.VISIBLE
                    txtEmptyMessage.text = "Error al conectar con la UV:\n${error.localizedMessage}"
                    Toast.makeText(this, "Error de red: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun aplicarFiltro(soloActivos: Boolean) {
        val filtrados = if (soloActivos) {
            allCourses.filter { it.isActivo() }
        } else {
            allCourses
        }

        courseAdapter.submitList(filtrados)

        if (filtrados.isEmpty() && allCourses.isNotEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            txtEmptyMessage.text = "No tienes materias en esta categoría"
        } else if (allCourses.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            txtEmptyMessage.text = "No se encontraron materias registradas"
        } else {
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun mostrarDialogoCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Deseas salir de tu cuenta de Eminus?")
            .setPositiveButton("Salir") { _, _ ->
                val oldToken = sessionManager.getAccessToken()
                sessionManager.clearSession()
                navegarALogin(sessionExpired = true, expiredToken = oldToken)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun navegarALogin(sessionExpired: Boolean = false, expiredToken: String? = null) {
        val intent = Intent(this, LoginWebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SESSION_EXPIRED", sessionExpired)
            putExtra("EXPIRED_TOKEN", expiredToken)
        }
        startActivity(intent)
        finish()
    }
}
