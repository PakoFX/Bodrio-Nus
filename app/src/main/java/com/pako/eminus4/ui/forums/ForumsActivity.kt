package com.pako.eminus4.ui.forums

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
import androidx.core.text.HtmlCompat
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
import com.pako.eminus4.data.model.ForumItem
import java.util.concurrent.atomic.AtomicInteger

class ForumsActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ForumsActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerForums: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    private val allForums = mutableListOf<ForumItem>()
    private lateinit var adapter: ForumsAdapter
    private var currentFilter: String = "todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forums)

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        cargarForos()
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
        recyclerForums = findViewById(R.id.recyclerForums)
        layoutEmpty = findViewById(R.id.layoutEmpty)

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_uv_green))
        swipeRefresh.setOnRefreshListener {
            cargarForos()
        }

        adapter = ForumsAdapter { forum ->
            val cleanDesc = HtmlCompat.fromHtml(forum.descripcion ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            val msg = buildString {
                append("📚 Curso: ${forum.nombreCurso}\n\n")
                if (cleanDesc.isNotBlank()) append("$cleanDesc\n\n")
                append("💬 Comentarios: ${forum.totalComentarios}")
                if (forum.nuevosComentarios > 0) append(" (${forum.nuevosComentarios} nuevos)")
                append("\n")
                val valor = forum.valor ?: 10.0
                val cal = forum.calificacion
                if (cal != null) {
                    append("🏆 Calificación: ${"%.1f".format(cal)} / ${"%.0f".format(valor)} pts")
                } else {
                    append("Valor: ${"%.0f".format(valor)} pts")
                }
                append("\n\nPara participar en el foro accede a la plataforma Eminus desde un navegador.")
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(forum.titulo)
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show()
        }
        recyclerForums.layoutManager = LinearLayoutManager(this)
        recyclerForums.adapter = adapter

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipNuevos) -> "nuevos"
                else -> "todos"
            }
            aplicarFiltro()
        }
    }

    private fun cargarForos() {
        swipeRefresh.isRefreshing = true

        apiClient.getAllCourses { result ->
            result.onSuccess { allCourses ->
                // Buscar en cursos activos y en cursos que tengan foros registrados
                val courses = allCourses.filter { it.isActivo() || it.foros > 0 }
                if (courses.isEmpty()) {
                    swipeRefresh.isRefreshing = false
                    allForums.clear()
                    aplicarFiltro()
                    return@onSuccess
                }

                allForums.clear()
                val pendingRequests = AtomicInteger(courses.size)

                for (c in courses) {
                    apiClient.getForums(c.idCurso) { forumResult ->
                        forumResult.onSuccess { forums ->
                            for (f in forums) {
                                f.idCurso = c.idCurso
                                f.nombreCurso = c.getNombreLimpio()
                            }
                            synchronized(allForums) {
                                allForums.addAll(forums)
                            }
                        }

                        if (pendingRequests.decrementAndGet() == 0) {
                            swipeRefresh.isRefreshing = false
                            allForums.sortByDescending { it.nuevosComentarios }
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
            "nuevos" -> allForums.filter { it.nuevosComentarios > 0 }
            else -> allForums
        }

        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}

class ForumsAdapter(
    private val onItemClick: (ForumItem) -> Unit
) : ListAdapter<ForumItem, ForumsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forum, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtCourseBadge: TextView = itemView.findViewById(R.id.txtCourseBadge)
        private val badgeNewComments: TextView = itemView.findViewById(R.id.badgeNewComments)
        private val txtForumTitle: TextView = itemView.findViewById(R.id.txtForumTitle)
        private val txtForumSnippet: TextView = itemView.findViewById(R.id.txtForumSnippet)
        private val txtTotalComments: TextView = itemView.findViewById(R.id.txtTotalComments)
        private val txtPoints: TextView = itemView.findViewById(R.id.txtPoints)

        fun bind(item: ForumItem) {
            txtCourseBadge.text = item.nombreCurso
            txtForumTitle.text = item.titulo

            val cleanDesc = HtmlCompat.fromHtml(item.descripcion ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            txtForumSnippet.text = if (cleanDesc.isNotBlank()) cleanDesc else "Sin descripción adicional."

            txtTotalComments.text = "💬 ${item.totalComentarios} comentario${if (item.totalComentarios == 1) "" else "s"}"

            val valor = item.valor ?: 10.0
            val cal = item.calificacion
            if (cal != null) {
                txtPoints.text = "Calificación: ${"%.1f".format(cal)}"
                txtPoints.setTextColor(ContextCompat.getColor(itemView.context, R.color.badge_success_text))
            } else {
                txtPoints.text = "${"%.0f".format(valor)} pts"
                txtPoints.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
            }

            if (item.nuevosComentarios > 0) {
                badgeNewComments.visibility = View.VISIBLE
                badgeNewComments.text = "${item.nuevosComentarios} nuevo${if (item.nuevosComentarios == 1) "" else "s"}"
            } else {
                badgeNewComments.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ForumItem>() {
        override fun areItemsTheSame(oldItem: ForumItem, newItem: ForumItem): Boolean {
            return oldItem.idForo == newItem.idForo
        }

        override fun areContentsTheSame(oldItem: ForumItem, newItem: ForumItem): Boolean {
            return oldItem == newItem
        }
    }
}
