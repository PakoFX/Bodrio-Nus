package com.pako.eminus4.ui.courses

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient

/**
 * CourseDetailActivity — Muestra el detalle de un curso con su lista de actividades.
 *
 * Recibe los datos del curso por Intent (ID, nombre, color, thumbnail, stats)
 * y carga las actividades de la API de Eminus en segundo plano.
 */
class CourseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID          = "extra_course_id"
        const val EXTRA_COURSE_NAME        = "extra_course_name"
        const val EXTRA_COURSE_COLOR       = "extra_course_color"
        const val EXTRA_COURSE_THUMBNAIL   = "extra_course_thumbnail"
        const val EXTRA_COURSE_ACTIVIDADES = "extra_course_actividades"
        const val EXTRA_COURSE_FOROS       = "extra_course_foros"
        const val EXTRA_COURSE_INTEGRANTES = "extra_course_integrantes"
        const val EXTRA_COURSE_DEPENDENCIA = "extra_course_dependencia"
    }

    private lateinit var apiClient: EminusApiClient
    private lateinit var activityAdapter: ActivityAdapter

    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var headerBg: View
    private lateinit var imgThumbnail: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvActivities: RecyclerView
    private lateinit var progressActivities: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var txtEmpty: TextView
    private lateinit var statActividades: TextView
    private lateinit var statForos: TextView
    private lateinit var statIntegrantes: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_detail)

        val courseId          = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        val courseName        = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "Curso"
        val courseColor       = intent.getStringExtra(EXTRA_COURSE_COLOR) ?: "#00897B"
        val courseThumbnail   = intent.getStringExtra(EXTRA_COURSE_THUMBNAIL)
        val numActividades    = intent.getIntExtra(EXTRA_COURSE_ACTIVIDADES, 0)
        val numForos          = intent.getIntExtra(EXTRA_COURSE_FOROS, 0)
        val numIntegrantes    = intent.getIntExtra(EXTRA_COURSE_INTEGRANTES, 0)

        if (courseId == -1L) {
            finish()
            return
        }

        apiClient = EminusApiClient(SessionManager(this))

        inicializarVistas()
        configurarHeader(courseName, courseColor, courseThumbnail)
        configurarStats(numActividades, numForos, numIntegrantes)
        configurarRecyclerView()
        cargarActividades(courseId)
    }

    private fun inicializarVistas() {
        collapsingToolbar = findViewById(R.id.collapsingToolbar)
        toolbar           = findViewById(R.id.detailToolbar)
        headerBg          = findViewById(R.id.detailHeaderBg)
        imgThumbnail      = findViewById(R.id.imgDetailThumbnail)
        swipeRefresh      = findViewById(R.id.detailSwipeRefresh)
        rvActivities      = findViewById(R.id.rvActivities)
        progressActivities = findViewById(R.id.progressActivities)
        layoutEmpty       = findViewById(R.id.layoutDetailEmpty)
        txtEmpty          = findViewById(R.id.txtDetailEmpty)
        statActividades   = findViewById(R.id.statActividades)
        statForos         = findViewById(R.id.statForos)
        statIntegrantes   = findViewById(R.id.statIntegrantes)

        // Botón de navegación atrás
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Sistema de insets para el AppBar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        swipeRefresh.setColorSchemeColors(Color.parseColor("#00C853"))
    }

    private fun configurarHeader(nombre: String, colorHex: String, thumbnailUrl: String?) {
        collapsingToolbar.title = nombre
        collapsingToolbar.setContentScrimColor(
            try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#00897B") }
        )

        try {
            headerBg.setBackgroundColor(Color.parseColor(colorHex))
        } catch (_: Exception) {
            headerBg.setBackgroundColor(Color.parseColor("#00897B"))
        }

        if (!thumbnailUrl.isNullOrBlank()) {
            imgThumbnail.visibility = View.VISIBLE
            imgThumbnail.load(thumbnailUrl) { crossfade(true) }
        }
    }

    private fun configurarStats(actividades: Int, foros: Int, integrantes: Int) {
        statActividades.text = "📝 $actividades Act."
        statForos.text       = "💬 $foros Foro${if (foros == 1) "" else "s"}"
        statIntegrantes.text = "👥 $integrantes"
    }

    private fun configurarRecyclerView() {
        activityAdapter = ActivityAdapter { actividad ->
            val actividades = activityAdapter.currentList
            val index = actividades.indexOfFirst { it.idActividad == actividad.idActividad }
            val ids    = actividades.map { it.idActividad }.toLongArray()
            val titles = actividades.map { it.nombre }.toTypedArray()

            val intent = Intent(this, ActivityDetailActivity::class.java).apply {
                putExtra(ActivityDetailActivity.EXTRA_COURSE_ID, intent.getLongExtra(EXTRA_COURSE_ID, 0L))
                putExtra(ActivityDetailActivity.EXTRA_COURSE_NAME, intent.getStringExtra(EXTRA_COURSE_NAME) ?: "Curso")
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_ID, actividad.idActividad)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_TITLE, actividad.nombre)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_IDS, ids)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_TITLES, titles)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_INDEX, if (index >= 0) index else 0)
            }
            startActivity(intent)
        }
        rvActivities.layoutManager = LinearLayoutManager(this)
        rvActivities.adapter = activityAdapter

        swipeRefresh.setOnRefreshListener {
            val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
            if (courseId != -1L) cargarActividades(courseId)
        }
    }

    private fun cargarActividades(idCurso: Long) {
        if (!swipeRefresh.isRefreshing) {
            progressActivities.visibility = View.VISIBLE
        }
        layoutEmpty.visibility = View.GONE

        apiClient.getActivities(idCurso) { result ->
            progressActivities.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            result.onSuccess { actividades ->
                activityAdapter.submitList(actividades)
                if (actividades.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    txtEmpty.text = "Este curso no tiene actividades registradas"
                }
            }.onFailure { error ->
                layoutEmpty.visibility = View.VISIBLE
                txtEmpty.text = "No se pudieron cargar las actividades.\n${error.localizedMessage}"
                Toast.makeText(this, "Error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
