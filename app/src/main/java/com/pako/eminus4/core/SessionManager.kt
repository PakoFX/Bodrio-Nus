package com.pako.eminus4.core

import android.content.Context
import android.content.SharedPreferences
import com.pako.eminus4.data.model.UserProfile

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

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

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
