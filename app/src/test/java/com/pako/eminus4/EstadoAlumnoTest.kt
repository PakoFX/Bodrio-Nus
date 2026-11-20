package com.pako.eminus4

import com.google.gson.Gson
import com.pako.eminus4.data.model.Activity
import com.pako.eminus4.data.model.EstadoAlumno
import com.pako.eminus4.util.Plazos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Casos tomados de respuestas reales de Eminus (nov-2026). */
class EstadoAlumnoTest {

    private val gson = Gson()
    private fun act(json: String) = gson.fromJson(json, Activity::class.java)

    @Test fun abiertaNuncaTocada_esPorHacer() =
        assertEquals(EstadoAlumno.POR_HACER, act("""{"idActividad":1,"estadoAct":2,"estado":null}""").estadoAlumno)

    @Test fun estadoNullNoSeConfundeConCero() =
        assertNull(act("""{"idActividad":1,"estado":null}""").estado)

    @Test fun abiertaConBorrador_esBorrador() =
        assertEquals(EstadoAlumno.BORRADOR, act("""{"idActividad":1,"estadoAct":2,"estado":0}""").estadoAlumno)

    @Test fun enviadaSinRevisar_esEsperando() =
        assertEquals(EstadoAlumno.ESPERANDO, act("""{"idActividad":1,"estadoAct":2,"estado":1}""").estadoAlumno)

    @Test fun cerradaEnviada_sigueEsperando() =
        assertEquals(EstadoAlumno.ESPERANDO, act("""{"idActividad":1,"estadoAct":1,"estado":1}""").estadoAlumno)

    @Test fun calificada() =
        assertEquals(EstadoAlumno.CALIFICADA, act("""{"idActividad":1,"estadoAct":1,"estado":3,"calificacion":9.5}""").estadoAlumno)

    @Test fun estado3SinNota_esCalificada() =
        assertEquals(EstadoAlumno.CALIFICADA, act("""{"idActividad":1,"estadoAct":1,"estado":3}""").estadoAlumno)

    @Test fun calificacionCeroCuentaComoCalificada() =
        assertEquals(EstadoAlumno.CALIFICADA, act("""{"idActividad":1,"estadoAct":1,"estado":3,"calificacion":0.0}""").estadoAlumno)

    @Test fun cerradaSinEnviar_esNoEntregada() =
        assertEquals(EstadoAlumno.NO_ENTREGADA, act("""{"idActividad":1,"estadoAct":1,"estado":null}""").estadoAlumno)

    @Test fun cerradaConBorrador_esNoEntregada() =
        assertEquals(EstadoAlumno.NO_ENTREGADA, act("""{"idActividad":1,"estadoAct":1,"estado":0}""").estadoAlumno)

    @Test fun prorroga_esPorHacer() =
        assertEquals(EstadoAlumno.POR_HACER, act("""{"idActividad":1,"estadoAct":4,"fechaTermino":"2020-01-01T00:00:00"}""").estadoAlumno)

    @Test fun sinEstadoAct_usaFecha() {
        assertEquals(EstadoAlumno.NO_ENTREGADA, act("""{"idActividad":1,"fechaTermino":"2020-01-01T00:00:00"}""").estadoAlumno)
        assertEquals(EstadoAlumno.POR_HACER, act("""{"idActividad":1,"fechaTermino":"2099-01-01T00:00:00"}""").estadoAlumno)
    }

    @Test fun detalleNuncaTocadaNoEsBorrador() {
        val d = gson.fromJson("""{"idActividad":1,"titulo":"x","estadoAct":2,"estado":null}""",
            com.pako.eminus4.data.model.ActivityDetail::class.java)
        assertNull(d.estado)
        assertEquals(false, d.estaEntregada)
        assertEquals(false, d.estaCerrada)
    }

    @Test fun detalleEnviadaEsEntregadaYAbierta() {
        val d = gson.fromJson("""{"idActividad":1,"titulo":"x","estadoAct":2,"estado":1}""",
            com.pako.eminus4.data.model.ActivityDetail::class.java)
        assertEquals(true, d.estaEntregada)
        assertEquals(false, d.estaCerrada)
    }

    @Test fun plazos() {
        val ahora = System.currentTimeMillis()
        assertEquals(Plazos.Urgencia.VENCIDA, Plazos.plazo(ahora - 1000, ahora).urgencia)
        assertEquals(Plazos.Urgencia.CRITICA, Plazos.plazo(ahora + TimeUnit.MINUTES.toMillis(30), ahora).urgencia)
        assertEquals(Plazos.Urgencia.NORMAL, Plazos.plazo(ahora + TimeUnit.DAYS.toMillis(20), ahora).urgencia)
        assertEquals(Plazos.Urgencia.SIN_FECHA, Plazos.plazo(null, ahora).urgencia)
    }
}
