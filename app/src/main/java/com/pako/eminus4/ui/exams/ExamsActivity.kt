package com.pako.eminus4.ui.exams

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.ExamItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ExamsActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ExamsActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerExams: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    private val allExams = mutableListOf<ExamItem>()
    private lateinit var adapter: ExamsAdapter
    private var currentFilter: String = "todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exams)

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        cargarExamenes()
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
        recyclerExams = findViewById(R.id.recyclerExams)
        layoutEmpty = findViewById(R.id.layoutEmpty)

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_uv_green))
        swipeRefresh.setOnRefreshListener {
            cargarExamenes()
        }

        adapter = ExamsAdapter { exam ->
            val estado = exam.getEstadoTexto()
            val msg = buildString {
                append("📚 Curso: ${exam.nombreCurso}\n\n")
                append("Estado: $estado\n")
                val inicio = formatDate(exam.fechaInicio)
                val fin = formatDate(exam.fechaTermino)
                append("📅 Inicio: $inicio\n")
                append("📅 Cierre: $fin\n")
                if (exam.calificacion != null) {
                    append("\n🏆 Calificación: ${"%.1f".format(exam.calificacion)} / ${"%.0f".format(exam.valor ?: 10.0)} pts")
                } else {
                    append("\nValor: ${"%.0f".format(exam.valor ?: 10.0)} pts")
                }
                append("\n\nPara presentar este examen accede a la plataforma Eminus desde un navegador.")
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(exam.titulo)
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show()
        }
        recyclerExams.layoutManager = LinearLayoutManager(this)
        recyclerExams.adapter = adapter

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipDisponibles) -> "disponibles"
                checkedIds.contains(R.id.chipCalificados) -> "calificados"
                else -> "todos"
            }
            aplicarFiltro()
        }
    }

    private fun cargarExamenes() {
        swipeRefresh.isRefreshing = true

        apiClient.getAllCourses { result ->
            result.onSuccess { allCourses ->
                // Buscar en cursos activos y en cursos que tengan exámenes registrados
                val courses = allCourses.filter { it.isActivo() || it.examenes > 0 }
                if (courses.isEmpty()) {
                    swipeRefresh.isRefreshing = false
                    allExams.clear()
                    aplicarFiltro()
                    return@onSuccess
                }

                allExams.clear()
                val pendingRequests = AtomicInteger(courses.size)

                for (c in courses) {
                    apiClient.getExams(c.idCurso) { examResult ->
                        examResult.onSuccess { exams ->
                            for (ex in exams) {
                                ex.idCurso = c.idCurso
                                ex.nombreCurso = c.getNombreLimpio()
                            }
                            synchronized(allExams) {
                                allExams.addAll(exams)
                            }
                        }

                        if (pendingRequests.decrementAndGet() == 0) {
                            swipeRefresh.isRefreshing = false
                            allExams.sortBy { it.fechaTermino ?: "9999" }
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

    private fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "Sin fecha"
        return try {
            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es"))
            val date = inputFmt.parse(iso.substring(0, 19)) ?: return iso
            outputFmt.format(date)
        } catch (_: Exception) { iso }
    }

    private fun aplicarFiltro() {
        val filtered = when (currentFilter) {
            "disponibles" -> allExams.filter { !it.estaVencido && !it.estaCalificado }
            "calificados" -> allExams.filter { it.estaCalificado }
            else -> allExams
        }

        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}

class ExamsAdapter(
    private val onItemClick: (ExamItem) -> Unit
) : ListAdapter<ExamItem, ExamsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exam, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtCourseBadge: TextView = itemView.findViewById(R.id.txtCourseBadge)
        private val badgeStatus: TextView = itemView.findViewById(R.id.badgeStatus)
        private val txtExamTitle: TextView = itemView.findViewById(R.id.txtExamTitle)
        private val txtExamDates: TextView = itemView.findViewById(R.id.txtExamDates)
        private val txtPoints: TextView = itemView.findViewById(R.id.txtPoints)

        fun bind(item: ExamItem) {
            txtCourseBadge.text = item.nombreCurso
            txtExamTitle.text = item.titulo

            val inicio = formatearFecha(item.fechaInicio)
            val fin = formatearFecha(item.fechaTermino)
            txtExamDates.text = "📅 $inicio — $fin"

            val valor = item.valor ?: 10.0
            val cal = item.calificacion

            if (cal != null) {
                badgeStatus.text = "Calificado (${"%.1f".format(cal)})"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_active)
                badgeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.badge_success_text))
                txtPoints.text = "${"%.1f".format(cal)}/${"%.0f".format(valor)} pts"
            } else if (item.estaVencido) {
                badgeStatus.text = "Concluido"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_closed)
                badgeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.badge_neutral_text))
                txtPoints.text = "${"%.0f".format(valor)} pts"
            } else {
                badgeStatus.text = "Disponible"
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_badge)
                badgeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.badge_info_text))
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

    companion object DiffCallback : DiffUtil.ItemCallback<ExamItem>() {
        override fun areItemsTheSame(oldItem: ExamItem, newItem: ExamItem): Boolean {
            return oldItem.idExamen == newItem.idExamen
        }

        override fun areContentsTheSame(oldItem: ExamItem, newItem: ExamItem): Boolean {
            return oldItem == newItem
        }
    }
}
