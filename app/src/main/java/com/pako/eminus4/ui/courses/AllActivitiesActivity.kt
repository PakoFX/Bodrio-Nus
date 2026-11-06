package com.pako.eminus4.ui.courses

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.CourseActivityItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class AllActivitiesActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AllActivitiesActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerActivities: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    private val allActivities = mutableListOf<CourseActivityItem>()
    private lateinit var adapter: AllActivitiesAdapter
    private var currentFilter: String = "todas"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_activities)

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        cargarTodasLasActividades()
    }

    private fun inicializarVistas() {
        toolbar = findViewById(R.id.toolbar)
        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        toolbar.setNavigationOnClickListener { finish() }

        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerActivities = findViewById(R.id.recyclerActivities)
        layoutEmpty = findViewById(R.id.layoutEmpty)

        swipeRefresh.setColorSchemeColors(Color.parseColor("#00897B"))
        swipeRefresh.setOnRefreshListener {
            cargarTodasLasActividades()
        }

        adapter = AllActivitiesAdapter { item ->
            val intent = Intent(this, ActivityDetailActivity::class.java).apply {
                putExtra(ActivityDetailActivity.EXTRA_COURSE_ID, item.courseId)
                putExtra(ActivityDetailActivity.EXTRA_COURSE_NAME, item.courseName)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_ID, item.activity.idActividad)
                putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_TITLE, item.activity.titulo)
            }
            startActivity(intent)
        }

        recyclerActivities.layoutManager = LinearLayoutManager(this)
        recyclerActivities.adapter = adapter

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipPendientes) -> "pendientes"
                checkedIds.contains(R.id.chipEntregadas) -> "entregadas"
                checkedIds.contains(R.id.chipCalificadas) -> "calificadas"
                else -> "todas"
            }
            aplicarFiltro()
        }
    }

    private fun cargarTodasLasActividades() {
        swipeRefresh.isRefreshing = true

        apiClient.getAllCourses { result ->
            result.onSuccess { allCourses ->
                val courses = allCourses.filter { it.isActivo() }
                if (courses.isEmpty()) {
                    swipeRefresh.isRefreshing = false
                    allActivities.clear()
                    aplicarFiltro()
                    return@onSuccess
                }

                allActivities.clear()
                val pendingRequests = AtomicInteger(courses.size)

                for (c in courses) {
                    apiClient.getActivities(c.idCurso) { actResult ->
                        actResult.onSuccess { activities ->
                            val items = activities.map { act ->
                                CourseActivityItem(
                                    activity = act,
                                    courseId = c.idCurso,
                                    courseName = c.getNombreLimpio(),
                                    courseColor = c.getColorHexaLimpio()
                                )
                            }
                            synchronized(allActivities) {
                                allActivities.addAll(items)
                            }
                        }

                        if (pendingRequests.decrementAndGet() == 0) {
                            swipeRefresh.isRefreshing = false
                            // Ordenar por fecha de término ascendente
                            allActivities.sortBy { it.activity.fechaTermino ?: "9999" }
                            aplicarFiltro()
                        }
                    }
                }
            }.onFailure { error ->
                swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error al cargar cursos: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun aplicarFiltro() {
        val filtered = when (currentFilter) {
            "pendientes" -> allActivities.filter { !it.activity.estaEntregada && !it.activity.estaCalificada }
            "entregadas" -> allActivities.filter { it.activity.estaEntregada && !it.activity.estaCalificada }
            "calificadas" -> allActivities.filter { it.activity.estaCalificada }
            else -> allActivities
        }

        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}

class AllActivitiesAdapter(
    private val onItemClick: (CourseActivityItem) -> Unit
) : ListAdapter<CourseActivityItem, AllActivitiesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_all_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtCourseBadge: TextView = itemView.findViewById(R.id.txtCourseBadge)
        private val badgeStatus: TextView = itemView.findViewById(R.id.badgeStatus)
        private val txtActivityTitle: TextView = itemView.findViewById(R.id.txtActivityTitle)
        private val txtDueDate: TextView = itemView.findViewById(R.id.txtDueDate)
        private val txtPoints: TextView = itemView.findViewById(R.id.txtPoints)

        fun bind(item: CourseActivityItem) {
            val act = item.activity
            txtCourseBadge.text = item.courseName
            try {
                txtCourseBadge.setTextColor(Color.parseColor(item.courseColor))
            } catch (_: Exception) {
                txtCourseBadge.setTextColor(Color.parseColor("#00897B"))
            }

            txtActivityTitle.text = act.titulo
            txtDueDate.text = "⏳ Vence: ${formatearFecha(act.fechaTermino)}"

            val valor = act.valor ?: 10.0
            val cal = act.calificacion
            if (cal != null) {
                badgeStatus.text = "Calificada (${"%.1f".format(cal)})"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_active)
                badgeStatus.setTextColor(Color.parseColor("#16A34A"))
                txtPoints.text = "${"%.1f".format(cal)}/${"%.0f".format(valor)} pts"
            } else if (act.estaEntregada) {
                badgeStatus.text = "Entregada"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_badge)
                badgeStatus.setTextColor(Color.parseColor("#0284C7"))
                txtPoints.text = "${"%.0f".format(valor)} pts"
            } else {
                badgeStatus.text = "Pendiente"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_closed)
                badgeStatus.setTextColor(Color.parseColor("#F59E0B"))
                txtPoints.text = "${"%.0f".format(valor)} pts"
            }

            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun formatearFecha(iso: String?): String {
            if (iso.isNullOrBlank()) return "Sin fecha"
            return try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFmt = SimpleDateFormat("dd MMM, HH:mm", Locale("es"))
                val date = inputFmt.parse(iso.substring(0, 19)) ?: return iso
                outputFmt.format(date)
            } catch (_: Exception) {
                iso
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CourseActivityItem>() {
        override fun areItemsTheSame(oldItem: CourseActivityItem, newItem: CourseActivityItem): Boolean {
            return oldItem.activity.idActividad == newItem.activity.idActividad
        }

        override fun areContentsTheSame(oldItem: CourseActivityItem, newItem: CourseActivityItem): Boolean {
            return oldItem == newItem
        }
    }
}
