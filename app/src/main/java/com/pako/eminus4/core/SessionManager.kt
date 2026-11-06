package com.pako.eminus4.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.StudentCredential
import com.pako.eminus4.data.model.UserProfile

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val KEY_STUDENT_CREDENTIAL = "key_student_credential_json"
        private const val KEY_CACHED_COURSES = "key_cached_courses_json"
        private const val KEY_SESSION_EXPIRED = "key_session_expired"
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

    /**
     * Cierra la sesión activa. Por defecto preserva la credencial descargada
     * para que el estudiante nunca la pierda.
     */
    fun clearSession(preserveCredential: Boolean = true) {
        val savedCredential = if (preserveCredential) getStudentCredential() else null
        prefs.edit().clear().apply()
        if (savedCredential != null) {
            saveStudentCredential(savedCredential)
        }
    }
}
