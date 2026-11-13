package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

data class StudentCredential(
    @SerializedName("user") val user: String,
    @SerializedName("name") val name: String,
    @SerializedName("lastNames") val lastNames: String = "",
    @SerializedName("program") val program: String = "Ingeniería de Software",
    @SerializedName("region") val region: String = "Coatzacoalcos",
    @SerializedName("monthsValidity") val monthsValidity: String = "AGO-ENE",
    @SerializedName("yearValidity") val yearValidity: String = "2027",
    @SerializedName("expeditionDate") val expeditionDate: String = "",
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("qrCode") val qrCode: String? = null,
    @SerializedName("barCode") val barCode: String? = null
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
     * Devuelve la fotografía válida para la credencial oficial:
     * Si la foto almacenada proviene de Eminus (avatar casual), se descarta y se usa
     * la fotografía oficial institucional de MIUV.
     */
    fun getFotoValida(): Any {
        val p = photo
        return if (!p.isNullOrBlank() && !p.contains("eminus", ignoreCase = true)) {
            p
        } else {
            com.pako.eminus4.R.drawable.foto_credencial_oficial
        }
    }
}
