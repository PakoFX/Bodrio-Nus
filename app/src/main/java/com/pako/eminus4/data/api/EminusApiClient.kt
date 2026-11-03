package com.pako.eminus4.data.api

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import com.google.gson.Gson
import com.pako.eminus4.core.Constants
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.model.Activity
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.CourseResponse
import com.pako.eminus4.data.model.NotificationSummary
import com.pako.eminus4.data.model.UserProfile
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class EminusApiClient(private val sessionManager: SessionManager) {

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://eminus.uv.mx/eminus4/page/course/list")

            // 1. Adjuntar Token Bearer
            val token = sessionManager.getAccessToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            // 2. Adjuntar Cookies de sesión de la UV
            try {
                val cookies = CookieManager.getInstance().getCookie("https://eminus.uv.mx")
                if (!cookies.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookies)
                }
            } catch (e: Exception) {
                Log.w("EMINUS_API", "No se pudieron obtener cookies de CookieManager: ${e.message}")
            }

            chain.proceed(requestBuilder.build())
        }
        .build()

    /**
     * Obtiene todos los cursos del usuario autenticado de forma asíncrona.
     */
    fun getAllCourses(onResult: (Result<List<Course>>) -> Unit) {
        val url = "${Constants.API_BASE_URL}${Constants.ENDPOINT_ALL_COURSES}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar cursos", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (code == 401) {
                        Log.e("EMINUS_API", "Error 401: Token o sesión expirada")
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        Log.e("EMINUS_API", "Error del servidor HTTP $code: $bodyString")
                        postResult(onResult, Result.failure(Exception("Error del servidor UV ($code)")))
                        return
                    }

                    val courseResponse = gson.fromJson(bodyString, CourseResponse::class.java)
                    val cursos = courseResponse.contenido?.map { it.curso } ?: emptyList()

                    Log.d("EMINUS_API", "Cursos obtenidos exitosamente: ${cursos.size}")
                    postResult(onResult, Result.success(cursos))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando respuesta de cursos", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene las actividades de un curso específico de forma asíncrona.
     */
    fun getActivities(idCurso: Long, onResult: (Result<List<Activity>>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}${Constants.ENDPOINT_ACTIVITIES}/$idCurso"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar actividades del curso $idCurso", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error del servidor UV ($code)")))
                        return
                    }

                    val listType = object : com.google.gson.reflect.TypeToken<List<Activity>>() {}.type
                    val actividades: List<Activity> = gson.fromJson(bodyString, listType)

                    Log.d("EMINUS_API", "Actividades del curso $idCurso: ${actividades.size}")
                    postResult(onResult, Result.success(actividades))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando actividades del curso $idCurso", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene los detalles completos de una actividad para el estudiante (instrucciones, fechas, ponderación, adjuntos).
     */
    fun getActivityDetail(idCurso: Long, idActividad: Long, onResult: (Result<com.pako.eminus4.data.model.ActivityDetail>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}Actividad/getActividadEstudiante/$idCurso/$idActividad"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar detalle de actividad $idActividad", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error del servidor UV ($code)")))
                        return
                    }

                    val detail = gson.fromJson(bodyString, com.pako.eminus4.data.model.ActivityDetail::class.java)
                    postResult(onResult, Result.success(detail))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando detalle de actividad $idActividad", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene el resumen de notificaciones globales del usuario (mensajes, actividades pendientes, foros, exámenes).
     */
    fun getNotifications(onResult: (Result<NotificationSummary>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}${Constants.ENDPOINT_NOTIFICATIONS}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar notificaciones", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error del servidor UV ($code)")))
                        return
                    }

                    val notif = gson.fromJson(bodyString, NotificationSummary::class.java)
                    postResult(onResult, Result.success(notif))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando notificaciones", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene la información completa del perfil de usuario desde la API institucional.
     */
    fun getUserProfile(onResult: (Result<UserProfile>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}${Constants.ENDPOINT_GET_USER}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar perfil", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error del servidor UV ($code)")))
                        return
                    }

                    val profile = gson.fromJson(bodyString, UserProfile::class.java)
                    postResult(onResult, Result.success(profile))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando perfil de usuario", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    private fun <T> postResult(callback: (T) -> Unit, data: T) {
        mainHandler.post { callback(data) }
    }
}
