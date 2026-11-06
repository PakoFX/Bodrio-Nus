package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class StudentCredential(
    @SerializedName("user") val user: String,
    @SerializedName("name") val name: String,
    @SerializedName("lastNames") val lastNames: String = "",
    @SerializedName("program") val program: String = "Estudiante Activo",
    @SerializedName("region") val region: String = "Universidad Veracruzana",
    @SerializedName("monthsValidity") val monthsValidity: String = "FEB - JUL",
    @SerializedName("yearValidity") val yearValidity: String = "2026",
    @SerializedName("expeditionDate") val expeditionDate: String = "",
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("qrCode") val qrCode: String? = null,
    @SerializedName("barCode") val barCode: String? = null
) {
    fun getNombreCompleto(): String {
        return if (lastNames.isNotBlank()) "$name $lastNames".trim() else name
    }

    fun getVigenciaTexto(): String {
        return "$monthsValidity $yearValidity".trim()
    }
}

