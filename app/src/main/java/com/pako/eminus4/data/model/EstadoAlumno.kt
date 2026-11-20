package com.pako.eminus4.data.model

import androidx.annotation.DrawableRes
import com.pako.eminus4.R

/**
 * Situación de una actividad DESDE EL PUNTO DE VISTA DEL ALUMNO.
 * Es el único criterio que deben usar todas las pantallas (Avisos, lista del curso, detalle).
 *
 * Reglas (valores observados en vivo en Eminus, nov-2026):
 *  - estadoAct: 1 = cerrada, 2 = abierta, 4 = abierta con prórroga.
 *  - estado:    null = nunca tocada, 0 = borrador sin enviar, 1 = enviada sin revisar,
 *               3 = calificada/revisada, 4 = calificada (visto en actividad por equipo).
 */
enum class EstadoAlumno(val etiqueta: String, @DrawableRes val icono: Int) {
    POR_HACER("Por hacer", R.drawable.ic_pending_actions),
    BORRADOR("Borrador sin enviar", R.drawable.ic_edit_note),
    ESPERANDO("Esperando calificación", R.drawable.ic_hourglass_top),
    CALIFICADA("Calificada", R.drawable.ic_star),
    NO_ENTREGADA("No entregada", R.drawable.ic_block);

    /** Cuenta como trabajo pendiente del alumno (lo que aparece en la agenda). */
    val esPendiente: Boolean get() = this == POR_HACER || this == BORRADOR
}
