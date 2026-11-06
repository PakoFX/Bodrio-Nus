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
import com.pako.eminus4.data.model.ExamItem
import com.pako.eminus4.data.model.ForumItem
import com.pako.eminus4.data.model.MessageDetail
import com.pako.eminus4.data.model.MessageItem
import com.pako.eminus4.data.model.NotificationSummary
import com.pako.eminus4.data.model.StudentDelivery
import com.pako.eminus4.data.model.UserProfile
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
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

    /**
     * Obtiene la revisión / entrega de una actividad por parte de un estudiante.
     * Incluye archivos entregados, archivos de revisión y comentarios.
     */
    fun getStudentDelivery(idCurso: Long, idActividad: Long, idUsuario: String, onResult: (Result<StudentDelivery>) -> Unit) {
        val url = "${Constants.API_BASE_URL}Activity/getActUsuarioRevision/$idCurso/$idActividad/$idUsuario"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al consultar entrega de usuario $idUsuario", e)
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
                        postResult(onResult, Result.failure(Exception("Error al consultar entrega ($code)")))
                        return
                    }

                    val delivery = gson.fromJson(bodyString, StudentDelivery::class.java)
                    postResult(onResult, Result.success(delivery))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando entrega de usuario", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Descarga un archivo binario autenticado desde Eminus hacia un archivo de destino en disco.
     */
    fun downloadFile(url: String, destinationFile: File, onResult: (Result<File>) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error descargando archivo $url", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    postResult(onResult, Result.failure(Exception("Error del servidor UV (${response.code})")))
                    return
                }

                val body = response.body
                if (body == null) {
                    postResult(onResult, Result.failure(Exception("Respuesta vacía del servidor")))
                    return
                }

                try {
                    destinationFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    postResult(onResult, Result.success(destinationFile))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error guardando archivo en disco", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene el listado de mensajes institucionales del buzón (carpeta 1 = recibidos, 2 = enviados).
     */
    fun getMessages(folderId: Int = 1, onResult: (Result<List<MessageItem>>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}centromensajes/GetListadoMensajes/$folderId/0"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error al obtener mensajes", e)
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
                        postResult(onResult, Result.failure(Exception("Error al consultar mensajes ($code)")))
                        return
                    }

                    val type = object : TypeToken<List<MessageItem>>() {}.type
                    val messages: List<MessageItem> = gson.fromJson(bodyString, type) ?: emptyList()
                    postResult(onResult, Result.success(messages))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando mensajes", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene el detalle completo de un mensaje institucional por su ID.
     */
    fun getMessageDetail(messageId: Long, onResult: (Result<MessageDetail>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}centromensajes/GetMensajeDetalle/$messageId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error al obtener detalle de mensaje", e)
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
                        postResult(onResult, Result.failure(Exception("Error al consultar detalle ($code)")))
                        return
                    }

                    val type = object : TypeToken<List<MessageDetail>>() {}.type
                    val list: List<MessageDetail> = gson.fromJson(bodyString, type) ?: emptyList()
                    if (list.isNotEmpty()) {
                        postResult(onResult, Result.success(list[0]))
                    } else {
                        postResult(onResult, Result.failure(Exception("Mensaje no encontrado")))
                    }
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando detalle de mensaje", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene los exámenes/evaluaciones disponibles para un estudiante en un curso específico.
     */
    fun getExams(courseId: Long, onResult: (Result<List<ExamItem>>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}Examen/getExamenesEst/$courseId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error al consultar exámenes", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    if (code == 204) {
                        postResult(onResult, Result.success(emptyList()))
                        return
                    }

                    val bodyString = response.body?.string() ?: ""
                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error al consultar exámenes ($code)")))
                        return
                    }

                    if (bodyString.isBlank() || bodyString.trim() == "[]") {
                        postResult(onResult, Result.success(emptyList()))
                        return
                    }

                    val type = object : TypeToken<List<ExamItem>>() {}.type
                    val exams: List<ExamItem> = gson.fromJson(bodyString, type) ?: emptyList()
                    postResult(onResult, Result.success(exams))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando exámenes", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Obtiene los foros de discusión de un curso específico.
     */
    fun getForums(courseId: Long, onResult: (Result<List<ForumItem>>) -> Unit) {
        val url = "${Constants.API_GLOBAL_URL}Foro/getForos/$courseId/0"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error al consultar foros", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    if (code == 204) {
                        postResult(onResult, Result.success(emptyList()))
                        return
                    }

                    val bodyString = response.body?.string() ?: ""
                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }

                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error al consultar foros ($code)")))
                        return
                    }

                    if (bodyString.isBlank() || bodyString.trim() == "[]") {
                        postResult(onResult, Result.success(emptyList()))
                        return
                    }

                    val type = object : TypeToken<List<ForumItem>>() {}.type
                    val forums: List<ForumItem> = gson.fromJson(bodyString, type) ?: emptyList()
                    postResult(onResult, Result.success(forums))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando foros", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Paso 1 de entrega: Sube el archivo físico como multipart a Eminus.
     * El servidor responde con el id del archivo adjuntado (campo `idActividadEntregas` en el JSON).
     * Si la entrega ya existe (idActividadEntregas > 0), lo usa para reemplazar el archivo.
     */
    fun uploadDeliveryFile(
        idCurso: Long,
        idActividad: Long,
        idActividadEntregas: Long,
        archivo: File,
        mimeType: String,
        onResult: (Result<Long>) -> Unit
    ) {
        val url = "${Constants.API_GLOBAL_URL}actividad/adjuntarEntrega" +
                "?idCurso=$idCurso&idActividad=$idActividad&idActividadEntregas=$idActividadEntregas"

        val fileBody: RequestBody = archivo.asRequestBody(mimeType.toMediaTypeOrNull())
        val multipartBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", archivo.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al subir entrega", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""
                    Log.d("EMINUS_API", "adjuntarEntrega HTTP $code: $bodyString")

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }
                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error al subir archivo ($code)")))
                        return
                    }

                    // La API devuelve el id del adjunto — puede ser un número directo o un JSON
                    val idAdjunto: Long = try {
                        val json = JSONObject(bodyString)
                        json.optLong("idActividadEntregas", json.optLong("id", 0L))
                    } catch (_: Exception) {
                        bodyString.trim().toLongOrNull() ?: 0L
                    }

                    postResult(onResult, Result.success(idAdjunto))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error parseando respuesta de adjuntarEntrega", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    /**
     * Paso 2 de entrega: Confirma/registra la entrega en Eminus.
     * Se llama DESPUÉS de uploadDeliveryFile exitoso.
     */
    fun insertDelivery(
        idCurso: Long,
        idActividad: Long,
        idActividadEntregas: Long,
        userId: String,
        comentario: String = "",
        onResult: (Result<Boolean>) -> Unit
    ) {
        val url = "${Constants.API_GLOBAL_URL}Actividad/InsertaActividadEntrega"

        val jsonPayload = JSONObject().apply {
            put("idCurso", idCurso)
            put("idActividad", idActividad)
            put("idActividadEntregas", idActividadEntregas)
            put("idUsuario", userId)
            put("descEntrega", comentario)
        }.toString()

        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EMINUS_API", "Error de red al confirmar entrega", e)
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val code = response.code
                    val bodyString = response.body?.string() ?: ""
                    Log.d("EMINUS_API", "InsertaActividadEntrega HTTP $code: $bodyString")

                    if (code == 401) {
                        postResult(onResult, Result.failure(Exception("Sesión expirada (401)")))
                        return
                    }
                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(Exception("Error al confirmar entrega ($code)")))
                        return
                    }

                    postResult(onResult, Result.success(true))
                } catch (e: Exception) {
                    Log.e("EMINUS_API", "Error en InsertaActividadEntrega", e)
                    postResult(onResult, Result.failure(e))
                }
            }
        })
    }

    private fun <T> postResult(callback: (T) -> Unit, data: T) {
        mainHandler.post { callback(data) }
    }
}
