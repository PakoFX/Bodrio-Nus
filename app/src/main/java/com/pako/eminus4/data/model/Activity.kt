package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

/**
 * Representa una actividad/tarea dentro de un curso de Eminus.
 * Mapeado desde GET /eminusapi/api/Actividad/getActividadesEstudiante/{idCurso}
 */
data class Activity(
    @SerializedName("idActividad")       val idActividad: Long,
    @SerializedName("titulo")            val titulo: String? = null,
    @SerializedName("nombre")            val nombreRaw: String? = null,
    @SerializedName("descripcion")       val descripcion: String? = null,
    @SerializedName("tipo")              val tipo: String? = null,
    @SerializedName("fechaEntrega")      val fechaEntrega: String? = null,
    @SerializedName("fechaTermino")      val fechaTermino: String? = null,
    @SerializedName("fechaInicio")       val fechaInicio: String? = null,
    @SerializedName("calificacion")      val calificacion: Double? = null,
    @SerializedName("valor")             val valor: Double? = null,
    @SerializedName("calificacionMax")   val calificacionMaxRaw: Double? = null,
    @SerializedName("estado")            val estado: Int = 0,
    @SerializedName("estadoEntrega")     val estadoEntrega: Int = 0,
    @SerializedName("idCurso")           val idCurso: Long = 0,
    @SerializedName("orden")             val orden: Int = 0
) {
    val nombre: String
        get() = titulo ?: nombreRaw ?: "Actividad"

    val calificacionMax: Double
        get() = valor ?: calificacionMaxRaw ?: 10.0

    val entregada: Boolean
        get() = estadoEntrega == 2 || estadoEntrega == 3 || estado == 2 || estado == 3 || calificacion != null

    val estaEntregada: Boolean
        get() = entregada

    val estaCalificada: Boolean
        get() = calificacion != null

    val fechaLimite: String?
        get() = fechaTermino ?: fechaEntrega

    /** Devuelve el emoji representativo según el tipo de actividad */
    fun getEmojiTipo(): String = when (tipo?.lowercase()) {
        "tarea"   -> "📝"
        "foro"    -> "💬"
        "examen"  -> "📋"
        "recurso" -> "📄"
        "encuesta"-> "📊"
        else      -> "📝"
    }

    /** Calcula el porcentaje de calificación para una barra de progreso */
    fun getPorcentajeCalificacion(): Int {
        val max = calificacionMax
        val cal = calificacion ?: return -1
        return ((cal / max) * 100).toInt().coerceIn(0, 100)
    }
}
