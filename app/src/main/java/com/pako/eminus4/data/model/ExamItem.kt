package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

data class ExamItem(
    @SerializedName("idExamen") val idExamen: Long,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("valor") val valor: Double? = 0.0,
    @SerializedName("fechaInicio") val fechaInicio: String? = null,
    @SerializedName("fechaTermino") val fechaTermino: String? = null,
    @SerializedName("estado") val estado: Int = 0,
    @SerializedName("calificacion") val calificacion: Double? = null,
    var idCurso: Long = 0L,
    var nombreCurso: String = ""
) {
    val estaCalificado: Boolean get() = calificacion != null

    val estaVencido: Boolean
        get() {
            if (fechaTermino.isNullOrBlank()) return false
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(fechaTermino.substring(0, 19))
                date != null && System.currentTimeMillis() > date.time
            } catch (_: Exception) {
                false
            }
        }

    fun getEstadoTexto(): String {
        return when {
            calificacion != null -> "Calificado (${"%.1f".format(calificacion)}/${"%.0f".format(valor ?: 10.0)})"
            estaVencido -> "Concluido"
            else -> "Disponible"
        }
    }
}

