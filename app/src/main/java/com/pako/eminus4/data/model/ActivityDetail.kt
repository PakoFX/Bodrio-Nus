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
    @SerializedName("idCurso")               val idCurso: Long = 0
) {
    val estaEntregada: Boolean
        get() = estadoEntrega == 2 || estadoEntrega == 3 || estado == 2 || estado == 3 || calificacion != null

    val estaCalificada: Boolean
        get() = calificacion != null

    val estaCerrada: Boolean
        get() {
            if (calificacion != null) return true
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

