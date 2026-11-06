package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName
import com.pako.eminus4.core.Constants

data class MessageDetail(
    @SerializedName("idMensaje") val idMensaje: Long,
    @SerializedName("mensaje") val mensaje: String? = null,
    @SerializedName("remitente") val remitente: MessageSender? = null,
    @SerializedName("destinatarios") val destinatarios: List<MessageRecipient>? = null,
    @SerializedName("asunto") val asunto: String? = null,
    @SerializedName("fecha") val fecha: String? = null,
    @SerializedName("tieneAdjuntos") val tieneAdjuntos: Int = 0,
    @SerializedName("listaArchivos") val listaArchivos: List<MessageAttachment>? = null
)

data class MessageSender(
    @SerializedName("idUsuario") val idUsuario: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("correo") val correo: String? = null,
    @SerializedName("foto") val foto: String? = null,
    @SerializedName("idCurso") val idCurso: Long? = null,
    @SerializedName("nombreCurso") val nombreCurso: String? = null
) {
    fun getFotoUrl(): String? {
        val f = foto ?: return null
        val clean = f.replace("\\", "/").trimStart('/')
        return "${Constants.REPOSITORY_URL}$clean"
    }
}

data class MessageRecipient(
    @SerializedName("idUsuario") val idUsuario: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("correo") val correo: String? = null
)

data class MessageAttachment(
    @SerializedName("idArchivosMensajes") val idArchivosMensajes: Long = 0,
    @SerializedName("nombreOriginal") val nombreOriginal: String = "Archivo adjunto",
    @SerializedName("peso") val peso: String? = null,
    @SerializedName("tamano") val tamano: Long = 0L
) {
    fun getDownloadUrl(idCurso: Long, idMensaje: Long): String {
        return "${Constants.API_GLOBAL_URL}centromensajes/descargaCentroMensajesArchivo/$idCurso/$idMensaje/$idArchivosMensajes"
    }

    fun getTamanoLegible(): String {
        if (!peso.isNullOrBlank()) return peso
        if (tamano <= 0) return "Archivo adjunto"
        val kb = tamano / 1024.0
        return if (kb < 1024) {
            "%.1f KB".format(kb)
        } else {
            "%.2f MB".format(kb / 1024.0)
        }
    }

    fun getIconEmoji(): String {
        val ext = nombreOriginal.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf" -> "📕"
            "doc", "docx" -> "📘"
            "ppt", "pptx" -> "📙"
            "xls", "xlsx" -> "📗"
            "zip", "rar", "7z" -> "📦"
            "png", "jpg", "jpeg", "webp" -> "🖼️"
            else -> "📎"
        }
    }
}
