package com.pako.eminus4.ui.courses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pako.eminus4.R
import androidx.core.content.ContextCompat
import com.pako.eminus4.data.model.Activity
import com.pako.eminus4.data.model.EstadoAlumno
import com.pako.eminus4.util.Plazos
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityAdapter(
    private val onActivityClick: (Activity) -> Unit
) : ListAdapter<Activity, ActivityAdapter.ActivityViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_card, parent, false)
        return ActivityViewHolder(view, onActivityClick)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActivityViewHolder(
        itemView: View,
        private val onActivityClick: (Activity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val imgIcono: ImageView = itemView.findViewById(R.id.imgActivityIcon)
        private val txtName: TextView   = itemView.findViewById(R.id.txtActivityName)
        private val txtDate: TextView   = itemView.findViewById(R.id.txtActivityDate)
        private val txtScore: TextView  = itemView.findViewById(R.id.txtActivityScore)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtActivityStatus)

        private val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        private val outputFmt = SimpleDateFormat("dd MMM yyyy", Locale("es"))

        fun bind(activity: Activity) {
            imgIcono.setImageResource(activity.getIconoTipo())
            txtName.text  = activity.nombre

            val ctx = itemView.context
            val estado = activity.estadoAlumno

            // Fecha: plazo relativo si aún hay que hacerla, fecha normal en otro caso
            if (estado.esPendiente) {
                val p = Plazos.plazo(activity.terminoMillis)
                txtDate.text = p.texto + if (activity.esPorEquipos) " · En equipo" else ""
                txtDate.setTextColor(ActividadCardBinder.colorUrgencia(ctx, p.urgencia))
            } else {
                txtDate.text = formatearFecha(activity.fechaLimite) + if (activity.esPorEquipos) " · En equipo" else ""
                txtDate.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }

            // Calificación
            val cal = activity.calificacion
            val max = activity.calificacionMax
            if (cal != null) {
                txtScore.text = "${ActividadCardBinder.formatoNota(cal)}/${ActividadCardBinder.formatoNota(max)}"
                txtScore.setTextColor(ContextCompat.getColor(ctx,
                    if (cal >= max * 0.6) R.color.badge_success_text else R.color.badge_error_text))
            } else {
                txtScore.text = "—"
                txtScore.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
            }

            // Estado con el criterio único (EstadoAlumno)
            txtStatus.text = when (estado) {
                EstadoAlumno.ESPERANDO -> "En revisión"   // corto: la columna es angosta
                EstadoAlumno.BORRADOR -> "Borrador"
                else -> estado.etiqueta
            }
            txtStatus.setTextColor(ActividadCardBinder.colorEstado(ctx, estado))

            itemView.setOnClickListener {
                onActivityClick(activity)
            }
        }

        private fun formatearFecha(iso: String?): String {
            if (iso.isNullOrBlank()) return "Sin fecha de entrega"
            return try {
                val date = inputFmt.parse(iso.substring(0, 19)) ?: return iso
                "Entrega: ${outputFmt.format(date)}"
            } catch (_: Exception) {
                iso
            }
        }
    }

    class ActivityDiffCallback : DiffUtil.ItemCallback<Activity>() {
        override fun areItemsTheSame(oldItem: Activity, newItem: Activity) =
            oldItem.idActividad == newItem.idActividad
        override fun areContentsTheSame(oldItem: Activity, newItem: Activity) =
            oldItem == newItem
    }
}
