package com.pako.eminus4.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.StudentCredential
import com.pako.eminus4.data.model.UserProfile

class SessionManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val KEY_STUDENT_CREDENTIAL = "key_student_credential_json"
        private const val KEY_CACHED_COURSES = "key_cached_courses_json"
        private const val KEY_SESSION_EXPIRED = "key_session_expired"
        const val KEY_DARK_MODE = "key_dark_mode_enabled"
    }

    fun saveSession(
        accessToken: String,
        userName: String? = null,
        firstName: String? = null,
        userId: String? = null,
        userEmail: String? = null,
        userCampus: String? = null,
        avatarUrl: String? = null,
        tokenExpiration: String? = null,
        userRoles: String? = null
    ) {
        prefs.edit().apply {
            putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            userName?.let { putString(Constants.KEY_USER_NAME, it) }
            firstName?.let { putString(Constants.KEY_FIRST_NAME, it) }
            userId?.let { putString(Constants.KEY_USER_ID, it) }
            userEmail?.let { putString(Constants.KEY_USER_EMAIL, it) }
            userCampus?.let { putString(Constants.KEY_USER_CAMPUS, it) }
            avatarUrl?.let { putString(Constants.KEY_AVATAR_URL, it) }
            tokenExpiration?.let { putString(Constants.KEY_TOKEN_EXPIRATION, it) }
            userRoles?.let { putString(Constants.KEY_USER_ROLES, it) }
            apply()
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
    }

    fun getUserId(): String? {
        return prefs.getString(Constants.KEY_USER_ID, null)
    }

    fun getUserProfile(): UserProfile {
        return UserProfile(
            userId = prefs.getString(Constants.KEY_USER_ID, "") ?: "",
            nombre = prefs.getString(Constants.KEY_FIRST_NAME, "") ?: "",
            userName = prefs.getString(Constants.KEY_USER_NAME, "Estudiante") ?: "Estudiante",
            userEmail = prefs.getString(Constants.KEY_USER_EMAIL, "") ?: "",
            userCampus = prefs.getString(Constants.KEY_USER_CAMPUS, "") ?: "",
            avatarUrl = prefs.getString(Constants.KEY_AVATAR_URL, null),
            tokenExpiration = prefs.getString(Constants.KEY_TOKEN_EXPIRATION, null)
        )
    }

    fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        return !token.isNullOrBlank() && token != "null"
    }

    /**
     * Determina si el dispositivo tiene una cuenta configurada (incluso si el token caducó).
     * Permite acceder a la app en modo sin conexión para ver credencial y cursos guardados.
     */
    fun hasSession(): Boolean {
        val userId = getUserId()
        return (!userId.isNullOrBlank() && userId != "null") || isLoggedIn() || hasStudentCredential()
    }

    fun isSessionExpired(): Boolean {
        return prefs.getBoolean(KEY_SESSION_EXPIRED, false)
    }

    fun setSessionExpired(expired: Boolean) {
        prefs.edit().putBoolean(KEY_SESSION_EXPIRED, expired).apply()
    }

    fun saveCachedCourses(courses: List<Course>) {
        val json = gson.toJson(courses)
        prefs.edit().putString(KEY_CACHED_COURSES, json).apply()
    }

    fun getCachedCourses(): List<Course> {
        val json = prefs.getString(KEY_CACHED_COURSES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Course>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStudentCredential(credential: StudentCredential) {
        val json = gson.toJson(credential)
        prefs.edit().putString(KEY_STUDENT_CREDENTIAL, json).apply()
    }

    fun getStudentCredential(): StudentCredential? {
        val json = prefs.getString(KEY_STUDENT_CREDENTIAL, null) ?: return null
        return try {
            gson.fromJson(json, StudentCredential::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun hasStudentCredential(): Boolean {
        return getStudentCredential() != null
    }

    /** Matrícula oficial de quien tiene la sesión ("zs24022198" -> "S24022198"), o null. */
    fun getMatriculaSesion(): String? {
        val id = getUserId()?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val upper = id.uppercase()
        return if (upper.startsWith("Z") && upper.length > 1 && upper[1].isLetter()) upper.substring(1) else upper
    }

    /**
     * Credencial guardada SOLO si pertenece a quien tiene la sesión.
     * Si no hay sesión (modo sin conexión tras cerrar sesión) se devuelve la guardada.
     */
    fun getCredencialDelUsuarioActual(): StudentCredential? {
        val cred = getStudentCredential() ?: return null
        val matricula = getMatriculaSesion() ?: return cred
        return if (cred.getMatriculaOficial() == matricula) cred else null
    }

    /**
     * Guarda en la credencial del usuario actual los datos oficiales leídos de MiUV.
     * Solo se sobrescribe lo que MiUV sí trajo.
     */
    fun aplicarDatosMiuv(
        datos: com.pako.eminus4.data.MiuvSync.DatosMiuv,
        nombreRespaldo: String?,
        matriculaRespaldo: String?
    ) {
        // Recién iniciada la sesión, Eminus a veces aún no ha reportado el usuario:
        // se usa la matrícula que la persona escribió en el login.
        val matricula = getMatriculaSesion() ?: normalizarMatricula(matriculaRespaldo) ?: return
        if (getMatriculaSesion() == null) {
            prefs.edit().putString(Constants.KEY_USER_ID, matriculaRespaldo!!.trim().lowercase()).apply()
        }
        val base = getCredencialDelUsuarioActual() ?: StudentCredential(
            user = matricula,
            name = nombreRespaldo.orEmpty(),
            qrCode = "https://dsia.uv.mx/credencial/validar?u=$matricula",
            barCode = matricula
        )
        saveStudentCredential(
            base.copy(
                // Recién iniciada la sesión el nombre puede faltar: se completa con el de la sesión
                name = base.name.ifBlank { nombreRespaldo ?: getUserProfile().getNombreCompleto() },
                program = datos.programa ?: base.program,
                region = datos.campus ?: base.region,
                facultad = datos.facultad ?: base.facultad,
                area = datos.area ?: base.area,
                nivel = datos.nivel ?: base.nivel,
                photo = datos.fotoLocal ?: base.photo,
                actualizadoMiuv = System.currentTimeMillis()
            )
        )
    }

    /** "zS24022198" / "zs24022198" -> "S24022198"; null si viene vacío. */
    fun normalizarMatricula(id: String?): String? {
        val upper = id?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "NULL" } ?: return null
        return if (upper.startsWith("Z") && upper.length > 1 && upper[1].isLetter()) upper.substring(1) else upper
    }

    /** Borra la credencial guardada si es de otra persona (al iniciar sesión con otra cuenta). */
    fun descartarCredencialAjena(nuevoUserId: String?) {
        val cred = getStudentCredential() ?: return
        val nueva = normalizarMatricula(nuevoUserId) ?: return
        if (cred.getMatriculaOficial() != nueva) {
            prefs.edit().remove(KEY_STUDENT_CREDENTIAL).apply()
            // Fotos oficiales descargadas de MiUV de la persona anterior
            java.io.File(appContext.filesDir, "credencial").listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Cierra la sesión activa. Por defecto preserva la credencial descargada
     * para que el estudiante nunca la pierda.
     */
    fun clearSession(preserveCredential: Boolean = true) {
        val savedCredential = if (preserveCredential) getStudentCredential() else null
        val darkMode = isDarkModeEnabled()
        prefs.edit().clear().apply()
        if (savedCredential != null) {
            saveStudentCredential(savedCredential)
        }
        // Preservar también la preferencia de tema
        setDarkModeEnabled(darkMode)
    }

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
}
