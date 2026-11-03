package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class UserProfile(
    @SerializedName("idUsuario") val userId: String = "",
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("paterno") val paterno: String? = null,
    @SerializedName("materno") val materno: String? = null,
    @SerializedName("correo") val userEmail: String = "",
    @SerializedName("correoAlterno") val alternateEmail: String? = null,
    @SerializedName("campus") val userCampus: String = "",
    @SerializedName("ruta") val avatarRuta: String? = null,
    val avatarUrl: String? = null,
    val tokenExpiration: String? = null,
    val userName: String = ""
) {
    /**
     * Nombre completo formateado y legible (ej. Francisco Xavier Viveros Salmones)
     */
    fun getNombreCompleto(): String {
        return if (!paterno.isNullOrBlank() || !materno.isNullOrBlank()) {
            listOfNotNull(nombre, paterno, materno)
                .joinToString(" ")
                .trim()
        } else if (userName.isNotBlank()) {
            userName
        } else {
            nombre
        }
    }

    /**
     * Primer nombre limpio para saludos (ej. Francisco)
     */
    fun getPrimerNombre(): String {
        val n = if (nombre.isNotBlank()) {
            nombre
        } else {
            // Formato UV típico en SharedPreferences: APELLIDO_P APELLIDO_M NOMBRES
            val palabras = userName.split(" ").filter { it.isNotBlank() }
            if (palabras.size >= 3) palabras[2] else palabras.firstOrNull() ?: "Estudiante"
        }
        val partes = n.trim().split(" ").filter { it.isNotBlank() }
        val raw = if (partes.isNotEmpty()) partes.first() else "Estudiante"
        return raw.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * URL completa del avatar en el repositorio estático
     */
    fun getFullAvatarUrl(): String? {
        val target = avatarRuta ?: avatarUrl ?: return null
        if (target.startsWith("http")) return target
        val clean = target.replace("\\", "/").trimStart('/')
        return "https://eminus.uv.mx/eminusapi/$clean"
    }
}
