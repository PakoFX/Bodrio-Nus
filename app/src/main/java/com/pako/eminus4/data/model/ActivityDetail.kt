package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class ActivityDetail(
    @SerializedName("idActividad")           val idActividad: Long,
    @SerializedName("titulo")                val titulo: String,
    @SerializedName("descripcion")           val descripcion: String? = null,
    @SerializedName("valor")                 val valor: Double? = null,
    @SerializedName("calificacion")          val calificacion: Double? = null,
    @SerializedName("fechaInicio")           val fechaInicio: String? = null,
    @SerializedName("fechaTermino")          val fechaTermino: String? = null,
    @SerializedName("fechaEntrega")          val fechaEntrega: String? = null,
    @SerializedName("fechaRevision")         val fechaRevision: String? = null,
    @SerializedName("estado")                val estado: Int = 0,
    @SerializedName("estadoEntrega")         val estadoEntrega: Int = 0,
    @SerializedName("tieneAdjuntos")         val tieneAdjuntos: Int = 0,
    @SerializedName("archivosActsConTamano") val archivosDocente: List<ActivityAttachment>? = null,
    @SerializedName("idCurso")               val idCurso: Long = 0,
    // Estado de la ACTIVIDAD según Eminus web: 2 = abierta, 4 = abierta con prórroga.
    // Nulo si la API no lo envía (se usa entonces la regla por fecha límite).
    @SerializedName("estadoAct")             val estadoAct: Int? = null,
    @SerializedName("porEquipos")            val porEquipos: Int = 0
) {
    val esPorEquipos: Boolean
        get() = porEquipos == 1

    /** Regla de Eminus web: solo se puede entregar si estadoAct es 2 o 4. */
    val admiteEntregaSegunEminus: Boolean?
        get() = estadoAct?.let { it == 2 || it == 4 }

    val estaEntregada: Boolean
        // estado 1 = enviada sin revisar (observado en vivo: tiene fechaEntrega y no está calificada)
        get() = estado in 1..3 || estadoEntrega in 1..3 || calificacion != null

    val estaCalificada: Boolean
        get() = calificacion != null

    val estaCerrada: Boolean
        get() {
            if (calificacion != null || estado == 3) return true
            // Si Eminus nos dice el estado real de la actividad, ese manda (respeta prórrogas)
            admiteEntregaSegunEminus?.let { return !it }
            if (estado == 2 || estado == 3 || estadoEntrega == 2 || estadoEntrega == 3) return true
            fechaTermino?.let { iso ->
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    val limit = sdf.parse(iso.substring(0, 19))
                    if (limit != null && limit.before(java.util.Date())) {
                        return true
                    }
                } catch (_: Exception) {}
            }
            return false
        }
}

