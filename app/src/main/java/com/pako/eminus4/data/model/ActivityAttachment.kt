package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class ActivityAttachment(
    @SerializedName("idArchivosActividades") val idArchivo: Long,
    @SerializedName("nombreOriginal")       val nombreOriginal: String,
    @SerializedName("nombreTratado")        val nombreTratado: String? = null,
    @SerializedName("peso")                 val peso: String? = null,
    @SerializedName("tamano")               val tamano: Long = 0,
    @SerializedName("ruta")                 val ruta: String? = null,
    @SerializedName("idActividad")          val idActividad: Long,
    @SerializedName("idCurso")              val idCurso: Long
) {
    fun getDownloadUrl(): String {
        return "https://eminus.uv.mx/eminusapi/api/actividad/descargaActividadArchivo/$idCurso/$idActividad/$idArchivo"
    }

    fun getIconEmoji(): String {
        val ext = nombreOriginal.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf"        -> "📕"
            "doc", "docx"-> "📘"
            "xls", "xlsx"-> "📗"
            "ppt", "pptx"-> "📙"
            "zip", "rar" -> "📦"
            "png", "jpg", "jpeg" -> "🖼️"
            else         -> "📎"
        }
    }
}
