package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class Course(
    @SerializedName("idCurso") val idCurso: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("idUsuario") val idProfesor: String? = null,
    @SerializedName("dependencia") val dependencia: String? = null,
    @SerializedName("codigoHexa") val codigoHexa: String? = null,
    @SerializedName("ruta") val rutaThumbnail: String? = null,
    @SerializedName("actividades") val actividades: Int = 0,
    @SerializedName("examenes") val examenes: Int = 0,
    @SerializedName("foros") val foros: Int = 0,
    @SerializedName("integrantes") val integrantes: Int = 0,
    @SerializedName("tipoCurso") val tipoCurso: String? = null,
    @SerializedName("tipoPerfil") val tipoPerfil: String? = null,
    @SerializedName("fechaInicioEpoch") val fechaInicioEpoch: Long = 0,
    @SerializedName("fechaTerminoEpoch") val fechaTerminoEpoch: Long = 0,
    @SerializedName("fechaCreacionEpoch") val fechaCreacionEpoch: Long = 0
) {
    /**
     * Determina si el curso está activo comparando su fecha de término
     * con el timestamp actual en segundos.
     */
    fun isActivo(): Boolean {
        val nowSeconds = System.currentTimeMillis() / 1000
        return fechaTerminoEpoch == 0L || fechaTerminoEpoch >= nowSeconds
    }

    /**
     * Devuelve el color de acento limpio (hexadecimal), con fallback al color oficial de la UV.
     */
    fun getColorHexaLimpio(): String {
        return if (!codigoHexa.isNullOrBlank() && codigoHexa.startsWith("#")) {
            codigoHexa
        } else {
            "#00897B" // Verde esmeralda UV por defecto
        }
    }

    /**
     * Obtiene la URL completa del thumbnail si existe.
     * Los thumbnails residen en REPOSITORY_URL (https://eminus.uv.mx/eminusapi/)
     */
    fun getFullThumbnailUrl(): String? {
        val r = rutaThumbnail ?: return null
        return if (r.startsWith("http")) {
            r
        } else {
            val cleanPath = if (r.startsWith("/")) r.substring(1) else r
            "https://eminus.uv.mx/eminusapi/$cleanPath"
        }
    }

    /**
     * Nombre del curso sin el ID entre paréntesis al final.
     * "ADMINISTRACION DE PROYECTOS DE (11986)" → "ADMINISTRACION DE PROYECTOS DE"
     */
    fun getNombreLimpio(): String {
        return nombre.replace(Regex("""\s*\(\d+\)\s*$"""), "").trim()
    }
}
