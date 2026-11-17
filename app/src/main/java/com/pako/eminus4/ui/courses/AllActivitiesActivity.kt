package com.pako.eminus4.ui.courses

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.AgendaLoader
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.CourseActivityItem
import com.pako.eminus4.data.model.EstadoAlumno
import com.pako.eminus4.util.Plazos

/**
 * "Mis actividades": todas las actividades de los cursos activos separadas por la situación
 * real del alumno (ver [EstadoAlumno]).
 */
class AllActivitiesActivity : AppCompatActivity() {

    enum class Pestana { POR_HACER, ESPERANDO, CALIFICADAS, NO_ENTREGADAS }

    companion object {
        const val EXTRA_PESTANA = "extra_pestana"

        fun start(context: Context, pestana: Pestana = Pestana.POR_HACER) {
            context.startActivity(Intent(context, AllActivitiesActivity::class.java).apply {
                putExtra(EXTRA_PESTANA, pestana.name)
            })
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient
    private lateinit var loader: AgendaLoader

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerActivities: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var imgEmptyIcon: ImageView
    private lateinit var txtEmptyTitle: TextView
    private lateinit var txtEmptyDesc: TextView

    private var allActivities: List<CourseActivityItem> = emptyList()
    private lateinit var adapter: FilasAdapter
    private var pestana = Pestana.POR_HACER
    private var cargado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_activities)

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)
        loader = AgendaLoader(sessionManager, apiClient)
        pestana = intent.getStringExtra(EXTRA_PESTANA)
            ?.let { runCatching { Pestana.valueOf(it) }.getOrNull() } ?: Pestana.POR_HACER

        inicializarVistas()

        // Pintar al instante lo que Avisos ya cargó; luego se actualiza en segundo plano
        AgendaLoader.ultimo?.let {
            allActivities = it.actividades
            cargado = true
            actualizarConteos()
            aplicarFiltro()
        }
        cargarTodasLasActividades()
    }

    override fun onRestart() {
        super.onRestart()
        // Al volver del detalle (p. ej. tras entregar) se refresca la situación
        if (cargado) cargarTodasLasActividades()
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
        imgEmptyIcon = findViewById(R.id.imgEmptyIcon)

        // Ícono de cada pestaña con el color de su estado
        listOf(
            R.id.chipPorHacer to EstadoAlumno.POR_HACER,
            R.id.chipEsperando to EstadoAlumno.ESPERANDO,
            R.id.chipCalificadas to EstadoAlumno.CALIFICADA,
            R.id.chipNoEntregadas to EstadoAlumno.NO_ENTREGADA
        ).forEach { (id, estado) ->
            findViewById<Chip>(id).apply {
                setChipIconResource(estado.icono)
                chipIconTint = android.content.res.ColorStateList.valueOf(
                    ActividadCardBinder.colorEstado(this@AllActivitiesActivity, estado)
                )
                isChipIconVisible = true
                isCheckedIconVisible = false // el fondo ya marca la selección; evita encimar íconos
            }
        }
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle)
        txtEmptyDesc = findViewById(R.id.txtEmptyDesc)

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_uv_green))
        swipeRefresh.setOnRefreshListener { cargarTodasLasActividades() }

        adapter = FilasAdapter { item -> ActividadCardBinder.abrirDetalle(this, item) }
        recyclerActivities.layoutManager = LinearLayoutManager(this)
        recyclerActivities.adapter = adapter

        chipGroupFilter.check(chipDe(pestana))
        chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                group.check(chipDe(pestana)) // siempre debe haber una pestaña elegida
                return@setOnCheckedStateChangeListener
            }
            pestana = when (checkedIds.first()) {
                R.id.chipEsperando -> Pestana.ESPERANDO
                R.id.chipCalificadas -> Pestana.CALIFICADAS
                R.id.chipNoEntregadas -> Pestana.NO_ENTREGADAS
                else -> Pestana.POR_HACER
            }
            aplicarFiltro()
            recyclerActivities.scrollToPosition(0)
        }
    }

    private fun chipDe(p: Pestana) = when (p) {
        Pestana.POR_HACER -> R.id.chipPorHacer
        Pestana.ESPERANDO -> R.id.chipEsperando
        Pestana.CALIFICADAS -> R.id.chipCalificadas
        Pestana.NO_ENTREGADAS -> R.id.chipNoEntregadas
    }

    private fun cargarTodasLasActividades() {
        swipeRefresh.isRefreshing = true
        loader.cargar { r ->
            if (isFinishing || isDestroyed) return@cargar
            swipeRefresh.isRefreshing = false
            cargado = true
            allActivities = r.actividades
            actualizarConteos()
            aplicarFiltro()
            when {
                r.sesionExpirada -> Toast.makeText(this, "Tu sesión de Eminus caducó; la información puede estar incompleta.", Toast.LENGTH_LONG).show()
                r.cursosConError > 0 || r.desdeCache -> Toast.makeText(this, "Sin conexión con Eminus; algunos cursos no se actualizaron.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filtrar(p: Pestana): List<CourseActivityItem> = allActivities.filter {
        val e = it.activity.estadoAlumno
        when (p) {
            Pestana.POR_HACER -> e.esPendiente
            Pestana.ESPERANDO -> e == EstadoAlumno.ESPERANDO
            Pestana.CALIFICADAS -> e == EstadoAlumno.CALIFICADA
            Pestana.NO_ENTREGADAS -> e == EstadoAlumno.NO_ENTREGADA
        }
    }

    private fun actualizarConteos() {
        fun etiqueta(id: Int, base: String, p: Pestana) {
            val n = filtrar(p).size
            findViewById<Chip>(id).text = if (n > 0) "$base ($n)" else base
        }
        etiqueta(R.id.chipPorHacer, "Por hacer", Pestana.POR_HACER)
        etiqueta(R.id.chipEsperando, "Esperando calificación", Pestana.ESPERANDO)
        etiqueta(R.id.chipCalificadas, "Calificadas", Pestana.CALIFICADAS)
        etiqueta(R.id.chipNoEntregadas, "No entregadas", Pestana.NO_ENTREGADAS)
    }

    private fun aplicarFiltro() {
        val lista = filtrar(pestana)
        val filas: List<Fila> = when (pestana) {
            // Lo urgente primero, sin agrupar: la fecha manda
            Pestana.POR_HACER -> lista
                .sortedBy { it.activity.terminoMillis ?: Long.MAX_VALUE }
                .map { Fila.Item(it) }
            Pestana.ESPERANDO -> agrupar(lista) { -(Plazos.parseIso(it.activity.fechaEntrega) ?: 0L) }
            Pestana.CALIFICADAS -> agrupar(lista) { -(Plazos.parseIso(it.activity.fechaRevision) ?: it.activity.terminoMillis ?: 0L) }
            Pestana.NO_ENTREGADAS -> agrupar(lista) { -(it.activity.terminoMillis ?: 0L) }
        }
        adapter.submitList(filas)

        if (lista.isEmpty() && cargado) {
            layoutEmpty.visibility = View.VISIBLE
            val (icono, titulo, desc) = when (pestana) {
                Pestana.POR_HACER -> Triple(R.drawable.ic_task_alt, "Nada por hacer", "No tienes entregas pendientes en tus cursos activos.")
                Pestana.ESPERANDO -> Triple(R.drawable.ic_inbox, "Nada en espera", "Todo lo que entregaste ya fue revisado.")
                Pestana.CALIFICADAS -> Triple(R.drawable.ic_star, "Aún sin calificaciones", "Cuando tu docente califique algo, aparecerá aquí.")
                Pestana.NO_ENTREGADAS -> Triple(R.drawable.ic_emoji_events, "Ninguna sin entregar", "No dejaste pasar ninguna actividad este periodo.")
            }
            imgEmptyIcon.setImageResource(icono)
            txtEmptyTitle.text = titulo
            txtEmptyDesc.text = desc
        } else {
            layoutEmpty.visibility = View.GONE
        }
    }

    /** Agrupa por curso (cursos con más actividades primero) y ordena dentro de cada grupo. */
    private fun agrupar(lista: List<CourseActivityItem>, orden: (CourseActivityItem) -> Long): List<Fila> =
        lista.groupBy { it.courseId }
            .values
            .sortedByDescending { it.size }
            .flatMap { grupo ->
                val primero = grupo.first()
                listOf<Fila>(Fila.Encabezado(primero.courseId, primero.courseName, primero.courseColor, grupo.size)) +
                    grupo.sortedBy(orden).map { Fila.Item(it, compacto = true) }
            }
}

sealed class Fila {
    data class Encabezado(val courseId: Long, val nombre: String, val color: String, val total: Int) : Fila()
    data class Item(val item: CourseActivityItem, val compacto: Boolean = false) : Fila()
}

class FilasAdapter(
    private val onItemClick: (CourseActivityItem) -> Unit
) : ListAdapter<Fila, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int) = if (getItem(position) is Fila.Encabezado) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = if (viewType == 0) R.layout.item_section_header else R.layout.item_all_activity
        return object : RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val fila = getItem(position)) {
            is Fila.Encabezado -> {
                val v = holder.itemView
                v.findViewById<TextView>(R.id.txtHeaderTitle).text = fila.nombre
                v.findViewById<TextView>(R.id.txtHeaderCount).text = fila.total.toString()
                val dot = v.findViewById<View>(R.id.viewHeaderDot)
                try {
                    dot.background.mutate().setTint(Color.parseColor(fila.color))
                } catch (_: Exception) { }
            }
            is Fila.Item -> ActividadCardBinder.bind(holder.itemView, fila.item, fila.compacto, onItemClick)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Fila>() {
        override fun areItemsTheSame(oldItem: Fila, newItem: Fila): Boolean = when {
            oldItem is Fila.Encabezado && newItem is Fila.Encabezado -> oldItem.courseId == newItem.courseId
            oldItem is Fila.Item && newItem is Fila.Item ->
                oldItem.item.activity.idActividad == newItem.item.activity.idActividad
            else -> false
        }

        override fun areContentsTheSame(oldItem: Fila, newItem: Fila) = oldItem == newItem
    }
}
