package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class MessageItem(
    @SerializedName("idMensaje") val idMensaje: Long,
    @SerializedName("titulo") val titulo: String? = null,
    @SerializedName("destinatariosRemitentes") val destinatariosRemitentes: String? = null,
    @SerializedName("nombreCompleto") val nombreCompleto: String? = null,
    @SerializedName("contenidoRecortado") val contenidoRecortado: String? = null,
    @SerializedName("fechaEnviado") val fechaEnviado: String? = null,
    @SerializedName("leido") val leido: Int = 1,
    @SerializedName("curso") val curso: String? = null
) {
    val esNoLeido: Boolean get() = leido == 0

    fun getNombreRemitente(): String {
        val n = nombreCompleto ?: destinatariosRemitentes ?: "Profesor / Alumno"
        return n.trim().replace(Regex("\\s+"), " ")
    }

    fun getCursoLimpio(): String {
        val c = curso ?: return "Mensaje General"
        return c.replace(Regex("""\s*\(\d+\)\s*$"""), "").trim()
    }
}

