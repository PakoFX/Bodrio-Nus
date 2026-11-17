package com.pako.eminus4.ui.courses

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pako.eminus4.R
import com.pako.eminus4.data.model.CourseActivityItem
import com.pako.eminus4.data.model.EstadoAlumno
import com.pako.eminus4.util.Plazos
import com.pako.eminus4.util.iconoInicio

/**
 * Pinta una tarjeta `item_all_activity.xml` con el criterio único de [EstadoAlumno].
 * Se usa en la agenda de Avisos y en "Todas las actividades".
 */
object ActividadCardBinder {

    fun colorEstado(context: Context, estado: EstadoAlumno): Int = ContextCompat.getColor(
        context,
        when (estado) {
            EstadoAlumno.POR_HACER, EstadoAlumno.BORRADOR -> R.color.badge_warning_text
            EstadoAlumno.ESPERANDO -> R.color.badge_info_text
            EstadoAlumno.CALIFICADA -> R.color.badge_success_text
            EstadoAlumno.NO_ENTREGADA -> R.color.badge_error_text
        }
    )

    fun colorUrgencia(context: Context, urgencia: Plazos.Urgencia): Int = ContextCompat.getColor(
        context,
        when (urgencia) {
            Plazos.Urgencia.CRITICA, Plazos.Urgencia.VENCIDA -> R.color.badge_error_text
            Plazos.Urgencia.PRONTO -> R.color.badge_warning_text
            else -> R.color.text_secondary
        }
    )

    /**
     * @param compacto en listas agrupadas por curso y filtradas por estado: oculta el nombre
     *                 del curso y la píldora de estado, que ya se ven en el encabezado/pestaña.
     */
    fun bind(view: View, item: CourseActivityItem, compacto: Boolean = false, onClick: (CourseActivityItem) -> Unit) {
        val ctx = view.context
        val act = item.activity
        val estado = act.estadoAlumno

        val stripe = view.findViewById<View>(R.id.viewCourseStripe)
        val txtCourse = view.findViewById<TextView>(R.id.txtCourseBadge)
        val txtTeam = view.findViewById<TextView>(R.id.txtTeamTag)
        val txtTitle = view.findViewById<TextView>(R.id.txtActivityTitle)
        val badge = view.findViewById<TextView>(R.id.badgeStatus)
        val txtWhen = view.findViewById<TextView>(R.id.txtDueDate)
        val txtPoints = view.findViewById<TextView>(R.id.txtPoints)

        val colorCurso = try {
            Color.parseColor(item.courseColor)
        } catch (_: Exception) {
            ContextCompat.getColor(ctx, R.color.brand_uv_green)
        }
        stripe.setBackgroundColor(colorCurso)

        txtCourse.text = item.courseName
        txtTeam.visibility = if (act.esPorEquipos) View.VISIBLE else View.GONE
        txtTeam.iconoInicio(R.drawable.ic_group, tamanoDp = 14, separacionDp = 4)
        txtTitle.text = act.nombre

        val colorEst = colorEstado(ctx, estado)
        badge.text = estado.etiqueta
        badge.setTextColor(colorEst)
        badge.iconoInicio(estado.icono, colorEst, tamanoDp = 14, separacionDp = 4)

        txtCourse.visibility = if (compacto) View.GONE else View.VISIBLE
        badge.visibility = if (compacto) View.GONE else View.VISIBLE
        // Si no queda nada en la fila superior (ni curso ni equipo), se oculta entera
        (txtCourse.parent as View).visibility =
            if (compacto && !act.esPorEquipos) View.GONE else View.VISIBLE
        (txtTeam.layoutParams as? android.widget.LinearLayout.LayoutParams)?.marginStart =
            if (compacto) 0 else (8 * ctx.resources.displayMetrics.density).toInt()

        // Línea de tiempo según la situación
        when (estado) {
            EstadoAlumno.POR_HACER, EstadoAlumno.BORRADOR -> {
                val p = Plazos.plazo(act.terminoMillis)
                txtWhen.text = p.texto
                txtWhen.setTextColor(colorUrgencia(ctx, p.urgencia))
            }
            EstadoAlumno.ESPERANDO -> {
                txtWhen.text = "Enviada el ${Plazos.fechaCorta(Plazos.parseIso(act.fechaEntrega))}"
                txtWhen.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            EstadoAlumno.CALIFICADA -> {
                val rev = Plazos.parseIso(act.fechaRevision)
                txtWhen.text = if (rev != null) "Revisada el ${Plazos.fechaCorta(rev)}" else "Revisada por el docente"
                txtWhen.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            EstadoAlumno.NO_ENTREGADA -> {
                txtWhen.text = "Cerró el ${Plazos.fechaCorta(act.terminoMillis)}"
                txtWhen.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }

        // Puntos o calificación
        val max = act.calificacionMax
        val cal = act.calificacion
        if (cal != null) {
            txtPoints.text = "${formatoNota(cal)}/${formatoNota(max)}"
            txtPoints.textSize = 16f
            txtPoints.setTextColor(
                ContextCompat.getColor(ctx, if (cal >= max * 0.6) R.color.badge_success_text else R.color.badge_error_text)
            )
        } else {
            txtPoints.text = if ((act.valor ?: 0.0) > 0) "${formatoNota(act.valor!!)} pts" else ""
            txtPoints.textSize = 12f
            txtPoints.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }

        view.setOnClickListener { onClick(item) }
    }

    /** 9.0 → "9", 9.5 → "9.5" */
    fun formatoNota(n: Double): String =
        if (n % 1.0 == 0.0) "%.0f".format(n) else "%.1f".format(n)

    fun abrirDetalle(context: Context, item: CourseActivityItem) {
        context.startActivity(Intent(context, ActivityDetailActivity::class.java).apply {
            putExtra(ActivityDetailActivity.EXTRA_COURSE_ID, item.courseId)
            putExtra(ActivityDetailActivity.EXTRA_COURSE_NAME, item.courseName)
            putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_ID, item.activity.idActividad)
            putExtra(ActivityDetailActivity.EXTRA_ACTIVITY_TITLE, item.activity.nombre)
        })
    }
}
