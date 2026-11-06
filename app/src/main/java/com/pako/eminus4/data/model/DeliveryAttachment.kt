package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class DeliveryAttachment(
    @SerializedName("idArchivosEntregas") val idArchivo: Long,
    @SerializedName("nombreOriginal")     val nombreOriginal: String,
    @SerializedName("nombreTratado")      val nombreTratado: String? = null,
    @SerializedName("peso")               val peso: String? = null,
    @SerializedName("tamano")             val tamano: Long = 0,
    @SerializedName("ruta")               val ruta: String? = null,
    @SerializedName("idActividadEntrega") val idActividadEntrega: Long = 0,
    @SerializedName("idCurso")            val idCurso: Long = 0
) {
    fun getDownloadUrl(activityId: Long): String {
        return "https://eminus.uv.mx/eminusapi/api/actividad/descargaEntregaArchivo/$idCurso/$activityId/$idArchivo"
    }

    fun getIconEmoji(): String {
        val ext = nombreOriginal.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf"        -> "📕"
            "doc", "docx"-> "📘"
            "xls", "xlsx"-> "📗"
            "ppt", "pptx"-> "📙"
            "zip", "rar" -> "📦"
            "png", "jpg", "jpeg", "webp" -> "🖼️"
            else         -> "📎"
        }
    }
}

