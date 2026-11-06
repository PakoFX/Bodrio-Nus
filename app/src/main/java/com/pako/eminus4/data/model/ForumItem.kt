package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class ForumItem(
    @SerializedName("idForo") val idForo: Long,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("valor") val valor: Double? = 0.0,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("totalComentarios") val totalComentarios: Int = 0,
    @SerializedName("nuevosComentarios") val nuevosComentarios: Int = 0,
    @SerializedName("estado") val estado: Int = 1,
    @SerializedName("calificacion") val calificacion: Double? = null,
    var idCurso: Long = 0L,
    var nombreCurso: String = ""
)

