package com.pako.eminus4.ui.courses

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pako.eminus4.R
import com.pako.eminus4.data.model.Course

class CourseAdapter(
    private val onCourseClick: (Course) -> Unit
) : ListAdapter<Course, CourseAdapter.CourseViewHolder>(CourseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course_card, parent, false)
        return CourseViewHolder(view, onCourseClick)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CourseViewHolder(
        itemView: View,
        private val onCourseClick: (Course) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardHeaderBanner: FrameLayout = itemView.findViewById(R.id.cardHeaderBanner)
        private val imgThumbnail: ImageView = itemView.findViewById(R.id.imgCourseThumbnail)
        private val txtCourseType: TextView = itemView.findViewById(R.id.txtCourseType)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtCourseTitle)
        private val txtDependence: TextView = itemView.findViewById(R.id.txtCourseDependence)
        private val badgeActivities: TextView = itemView.findViewById(R.id.badgeActivities)
        private val badgeForums: TextView = itemView.findViewById(R.id.badgeForums)
        private val badgeStudents: TextView = itemView.findViewById(R.id.badgeStudents)

        fun bind(course: Course) {
            // Fix 1: nombre sin el ID entre paréntesis
            txtTitle.text = course.getNombreLimpio()
            txtDependence.text = course.dependencia ?: "Universidad Veracruzana"

            // Fix 2: chip con estado real Activo / Cerrado
            val activo = course.isActivo()
            txtCourseType.text = if (activo) "ACTIVO" else "CERRADO"
            txtCourseType.setBackgroundResource(
                if (activo) R.drawable.bg_pill_active else R.drawable.bg_pill_closed
            )

            // Asignar color temático al banner
            try {
                val colorHex = course.getColorHexaLimpio()
                cardHeaderBanner.setBackgroundColor(Color.parseColor(colorHex))
            } catch (_: Exception) {
                cardHeaderBanner.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.brand_uv_green))
            }

            // Thumbnail — Coil inyecta el Bearer token globalmente desde EminusApp
            val thumbnailUrl = course.getFullThumbnailUrl()
            android.util.Log.d("EMINUS_BIND", "Curso ${course.idCurso} | activo=${course.isActivo()} | ruta=${course.rutaThumbnail} | url=$thumbnailUrl")
            if (!thumbnailUrl.isNullOrBlank()) {
                imgThumbnail.visibility = View.VISIBLE
                imgThumbnail.load(thumbnailUrl) {
                    crossfade(true)
                    listener(
                        onError = { _, result ->
                            android.util.Log.e("EMINUS_BIND", "Coil error para $thumbnailUrl: ${result.throwable}")
                        }
                    )
                }
            } else {
                imgThumbnail.visibility = View.GONE
            }

            // Contadores y métricas
            badgeActivities.text = "📝 ${course.actividades} Act."
            badgeForums.text = "💬 ${course.foros} Foro${if (course.foros == 1) "" else "s"}"
            badgeStudents.text = "👥 ${course.integrantes}"

            itemView.setOnClickListener {
                onCourseClick(course)
            }
        }
    }

    class CourseDiffCallback : DiffUtil.ItemCallback<Course>() {
        override fun areItemsTheSame(oldItem: Course, newItem: Course): Boolean {
            return oldItem.idCurso == newItem.idCurso
        }

        override fun areContentsTheSame(oldItem: Course, newItem: Course): Boolean {
            return oldItem == newItem
        }
    }
}
