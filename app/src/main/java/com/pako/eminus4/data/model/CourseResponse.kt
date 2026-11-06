package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class CourseResponse(
    @SerializedName("codigo") val codigo: Int,
    @SerializedName("mensaje") val mensaje: String?,
    @SerializedName("contenido") val contenido: List<CourseContainer>?
) {
    fun getCursos(): List<Course> = contenido?.map { it.curso } ?: emptyList()
}

data class CourseContainer(
    @SerializedName("curso") val curso: Course,
    @SerializedName("listaMetadatos") val listaMetadatos: List<String>? = null
)
