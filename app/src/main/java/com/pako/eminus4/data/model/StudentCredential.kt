package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

data class StudentCredential(
    @SerializedName("user") val user: String,
    @SerializedName("name") val name: String,
    @SerializedName("lastNames") val lastNames: String = "",
    @SerializedName("program") val program: String = "",
    @SerializedName("region") val region: String = "",
    @SerializedName("monthsValidity") val monthsValidity: String = "AGO-ENE",
    @SerializedName("yearValidity") val yearValidity: String = "2027",
    @SerializedName("expeditionDate") val expeditionDate: String = "",
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("qrCode") val qrCode: String? = null,
    @SerializedName("barCode") val barCode: String? = null,
    // Datos oficiales leídos de MiUV (szigral.aspx); null si aún no se sincronizó
    @SerializedName("facultad") val facultad: String? = null,
    @SerializedName("area") val area: String? = null,
    @SerializedName("nivel") val nivel: String? = null,
    @SerializedName("actualizadoMiuv") val actualizadoMiuv: Long? = null
) {
    fun getNombreCompleto(): String {
        return if (lastNames.isNotBlank()) "$name $lastNames".trim() else name
    }

    /**
     * Formatea el nombre en Title Case oficial si viene en MAYÚSCULAS desde el API
     * Ej: "FRANCISCO XAVIER VIVEROS SALMONES" -> "Francisco Xavier Viveros Salmones"
     */
    fun getNombreFormateado(): String {
        val raw = getNombreCompleto()
        return if (raw == raw.uppercase(Locale.getDefault())) {
            raw.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                word.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
            }
        } else {
            raw
        }
    }

    /**
     * Devuelve la matrícula oficial sin la 'Z' inicial de login (ej: "ZS24022198" -> "S24022198")
     */
    fun getMatriculaOficial(): String {
        val upper = user.uppercase(Locale.getDefault()).trim()
        return if (upper.startsWith("Z") && upper.length > 1 && upper[1].isLetter()) {
            upper.substring(1)
        } else {
            upper
        }
    }

    /**
     * Devuelve el campus oficial (ej: "Coatzacoalcos-Minatitlán" -> "Coatzacoalcos")
     */
    fun getCampusOficial(): String {
        if (region.contains("-")) {
            return region.split("-").first().trim()
        }
        return region
    }

    /**
     * Devuelve el programa oficial tal como viene guardado.
     * Solo sustituye el texto genérico "Estudiante Activo" que a veces devuelve el API.
     */
    fun getProgramaOficial(): String {
        return if (program.equals("Estudiante Activo", ignoreCase = true)) "" else program
    }

    fun getVigenciaTexto(): String {
        return "$monthsValidity $yearValidity".trim()
    }

    /**
     * Fotografía para la credencial oficial:
     *  1. Foto institucional guardada (no el avatar casual de Eminus).
     *  2. La foto empaquetada en la app SOLO para su dueño (temporal, hasta traerla de MiUV).
     *  3. Silueta genérica para cualquier otra persona: nunca mostrar la cara de alguien más.
     */
    fun getFotoValida(): Any {
        val p = photo
        return when {
            !p.isNullOrBlank() && !esAvatarDeEminus(p) -> p
            getMatriculaOficial() == MATRICULA_FOTO_EMPAQUETADA -> com.pako.eminus4.R.drawable.foto_credencial_oficial
            else -> com.pako.eminus4.R.drawable.ic_nav_profile
        }
    }

    /** Foto oficial (MiUV o archivo local), o null si no hay. Para la tarjeta del Perfil. */
    fun getFotoOficial(): String? = photo?.takeIf { it.isNotBlank() && !esAvatarDeEminus(it) }

    /**
     * Avatar casual subido a Eminus (no sirve como foto oficial).
     * OJO: no basta con buscar "eminus" en el texto: las fotos locales viven en
     * /data/user/0/com.pako.eminus4/..., que también lo contiene.
     */
    private fun esAvatarDeEminus(p: String): Boolean =
        p.startsWith("http", ignoreCase = true) && p.contains("eminus.uv.mx", ignoreCase = true)

    companion object {
        /** Dueño de `foto_credencial_oficial.png`. Quitar cuando la foto venga de MiUV. */
        private const val MATRICULA_FOTO_EMPAQUETADA = "S24022198"
    }
}
