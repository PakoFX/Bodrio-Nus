package com.pako.eminus4.data

import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.Course
import com.pako.eminus4.data.model.CourseActivityItem

/**
 * Reúne las actividades de todos los cursos ACTIVOS (periodo actual).
 * Los callbacks del cliente ya llegan en el hilo principal, así que no hace falta sincronizar.
 */
class AgendaLoader(
    private val sessionManager: SessionManager,
    private val apiClient: EminusApiClient
) {

    data class Resultado(
        val cursos: List<Course>,
        val actividades: List<CourseActivityItem>,
        /** Cuántos cursos no se pudieron consultar (sin conexión, sesión caducada…). */
        val cursosConError: Int,
        val sesionExpirada: Boolean,
        /** Los cursos vienen de la caché del teléfono porque Eminus no respondió. */
        val desdeCache: Boolean
    )

    companion object {
        /** Última agenda cargada en esta sesión: permite pintar al instante al cambiar de pantalla. */
        @Volatile var ultimo: Resultado? = null
            private set
    }

    fun cargar(onResultFinal: (Resultado) -> Unit) {
        val onResult: (Resultado) -> Unit = { r ->
            if (r.actividades.isNotEmpty()) ultimo = r
            onResultFinal(r)
        }
        apiClient.getAllCourses { result ->
            result.onSuccess { courses ->
                sessionManager.saveCachedCourses(courses)
                cargarActividades(courses.filter { it.isActivo() }, desdeCache = false, onResult)
            }.onFailure { e ->
                val cache = sessionManager.getCachedCourses().filter { it.isActivo() }
                if (cache.isEmpty()) {
                    onResult(Resultado(emptyList(), emptyList(), 0, esExpirada(e), desdeCache = true))
                } else {
                    cargarActividades(cache, desdeCache = true, onResult, sesionExpiradaPrevia = esExpirada(e))
                }
            }
        }
    }

    private fun cargarActividades(
        cursos: List<Course>,
        desdeCache: Boolean,
        onResult: (Resultado) -> Unit,
        sesionExpiradaPrevia: Boolean = false
    ) {
        if (cursos.isEmpty()) {
            onResult(Resultado(cursos, emptyList(), 0, sesionExpiradaPrevia, desdeCache))
            return
        }
        val items = mutableListOf<CourseActivityItem>()
        var pendientes = cursos.size
        var errores = 0
        var expirada = sesionExpiradaPrevia

        for (c in cursos) {
            apiClient.getActivities(c.idCurso) { r ->
                r.onSuccess { acts ->
                    acts.filter { !it.titulo.isNullOrBlank() || !it.nombreRaw.isNullOrBlank() }
                        .mapTo(items) { CourseActivityItem(it, c.idCurso, c.getNombreLimpio(), c.getColorHexaLimpio()) }
                }.onFailure { e ->
                    errores++
                    if (esExpirada(e)) expirada = true
                }
                if (--pendientes == 0) onResult(Resultado(cursos, items, errores, expirada, desdeCache))
            }
        }
    }

    private fun esExpirada(e: Throwable) = e.message?.contains("401") == true
}
