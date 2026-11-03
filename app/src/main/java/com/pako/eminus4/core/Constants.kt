package com.pako.eminus4.core

object Constants {
    // API REST interna de Eminus (v8: Cursos y Actividades)
    const val API_BASE_URL = "https://eminus.uv.mx/eminusapi8/api/"

    // API REST global de Eminus (v1/Global: Notificaciones y Perfil)
    const val API_GLOBAL_URL = "https://eminus.uv.mx/eminusapi/api/"

    // Repositorio estático (Thumbnails, perfiles, adjuntos)
    const val REPOSITORY_URL = "https://eminus.uv.mx/eminusapi/"

    // URL de Login y Portal Eminus
    const val EMINUS_LOGIN_URL = "https://eminus.uv.mx/eminus4/"

    // Endpoints específicos
    const val ENDPOINT_ALL_COURSES  = "Course/getAllCourses"
    const val ENDPOINT_ACTIVITIES   = "Actividad/getActividadesEstudiante"
    const val ENDPOINT_NOTIFICATIONS = "Global/NotificacionesUsuario"
    const val ENDPOINT_GET_USER     = "Usuario/getUsuario"

    // SharedPreferences
    const val PREFS_NAME = "eminus_native_prefs"
    const val KEY_ACCESS_TOKEN = "key_access_token"
    const val KEY_USER_NAME = "key_user_name"
    const val KEY_FIRST_NAME = "key_first_name"
    const val KEY_USER_ID = "key_user_id"
    const val KEY_USER_EMAIL = "key_user_email"
    const val KEY_USER_CAMPUS = "key_user_campus"
    const val KEY_AVATAR_URL = "key_avatar_url"
    const val KEY_TOKEN_EXPIRATION = "key_token_expiration"
    const val KEY_USER_ROLES = "key_user_roles"
}
