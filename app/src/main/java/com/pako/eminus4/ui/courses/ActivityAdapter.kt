package com.pako.eminus4.ui.courses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pako.eminus4.R
import com.pako.eminus4.data.model.Activity
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityAdapter : ListAdapter<Activity, ActivityAdapter.ActivityViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_card, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val txtEmoji: TextView  = itemView.findViewById(R.id.txtActivityEmoji)
        private val txtName: TextView   = itemView.findViewById(R.id.txtActivityName)
        private val txtDate: TextView   = itemView.findViewById(R.id.txtActivityDate)
        private val txtScore: TextView  = itemView.findViewById(R.id.txtActivityScore)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtActivityStatus)

        private val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        private val outputFmt = SimpleDateFormat("dd MMM yyyy", Locale("es"))

        fun bind(activity: Activity) {
            txtEmoji.text = activity.getEmojiTipo()
            txtName.text  = activity.nombre

            // Fecha de entrega / límite formateada
            txtDate.text = formatearFecha(activity.fechaLimite)

            // Calificación
            val cal = activity.calificacion
            val max = activity.calificacionMax ?: 10.0
            if (cal != null) {
                txtScore.text = "${"%.1f".format(cal)}/${"%.0f".format(max)}"
                txtScore.setTextColor(
                    if (cal >= max * 0.6) 0xFF16A34A.toInt() else 0xFFDC2626.toInt()
                )
                txtStatus.text   = "Calificada"
                txtStatus.setTextColor(0xFF16A34A.toInt())
            } else {
                txtScore.text = "—"
                txtScore.setTextColor(0xFF64748B.toInt())
                if (activity.entregada) {
                    txtStatus.text = "Entregada"
                    txtStatus.setTextColor(0xFF2563EB.toInt())
                } else {
                    txtStatus.text = "Pendiente"
                    txtStatus.setTextColor(0xFFF59E0B.toInt())
                }
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
