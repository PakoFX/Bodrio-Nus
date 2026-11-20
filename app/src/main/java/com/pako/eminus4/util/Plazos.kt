package com.pako.eminus4.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fechas legibles para humanos: "Vence mañana, 23:45", "Vence en 3 días", "hace 2 h".
 * Eminus envía fechas locales sin zona: "2026-11-18T23:45:00".
 */
object Plazos {

    enum class Urgencia { VENCIDA, CRITICA, PRONTO, NORMAL, SIN_FECHA }

    data class Plazo(val texto: String, val urgencia: Urgencia)

    private val es = Locale("es", "MX")

    fun parseIso(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso.length < 19) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.substring(0, 19))?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun fmt(patron: String, millis: Long): String =
        SimpleDateFormat(patron, es).format(millis).replace(".", "")

    /** Diferencia en días de calendario (hoy = 0, mañana = 1…). */
    private fun diasCalendario(desde: Long, hasta: Long): Int {
        fun inicio(m: Long) = Calendar.getInstance().apply {
            timeInMillis = m
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(inicio(hasta) - inicio(desde)).toInt()
    }

    /** Texto de vencimiento con su nivel de urgencia. */
    fun plazo(terminoMillis: Long?, ahora: Long = System.currentTimeMillis()): Plazo {
        if (terminoMillis == null) return Plazo("Sin fecha límite", Urgencia.SIN_FECHA)
        val restante = terminoMillis - ahora
        val hora = fmt("HH:mm", terminoMillis)
        if (restante < 0) return Plazo("Venció el ${fmt("d MMM", terminoMillis)}", Urgencia.VENCIDA)
        if (restante < TimeUnit.HOURS.toMillis(1)) {
            return Plazo("Vence en ${TimeUnit.MILLISECONDS.toMinutes(restante).coerceAtLeast(1)} min", Urgencia.CRITICA)
        }
        return when (val dias = diasCalendario(ahora, terminoMillis)) {
            0 -> Plazo("Vence hoy, $hora", Urgencia.CRITICA)
            1 -> Plazo("Vence mañana, $hora", Urgencia.CRITICA)
            2, 3 -> Plazo("Vence en $dias días · ${fmt("EEE d", terminoMillis)}, $hora", Urgencia.PRONTO)
            in 4..7 -> Plazo("Vence en $dias días · ${fmt("EEE d MMM", terminoMillis)}", Urgencia.NORMAL)
            else -> Plazo("Vence el ${fechaCorta(terminoMillis)}", Urgencia.NORMAL)
        }
    }

    /** "16 nov", o "16 nov 2025" si no es del año actual. */
    fun fechaCorta(millis: Long?): String {
        if (millis == null) return "—"
        val mismoAnio = Calendar.getInstance().get(Calendar.YEAR) ==
            Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
        return fmt(if (mismoAnio) "d MMM" else "d MMM yyyy", millis)
    }

    /** "hace 5 min", "hace 3 h", "ayer", "hace 4 días", o la fecha corta. */
    fun haceCuanto(millis: Long?, ahora: Long = System.currentTimeMillis()): String {
        if (millis == null) return ""
        val diff = ahora - millis
        if (diff < 0) return fechaCorta(millis)
        val min = TimeUnit.MILLISECONDS.toMinutes(diff)
        val dias = diasCalendario(millis, ahora)
        return when {
            min < 1 -> "ahora"
            min < 60 -> "hace $min min"
            dias == 0 -> "hace ${min / 60} h"
            dias == 1 -> "ayer"
            dias < 7 -> "hace $dias días"
            else -> fechaCorta(millis)
        }
    }

    /** "lunes 16 de noviembre" */
    fun hoyLargo(): String = fmt("EEEE d 'de' MMMM", System.currentTimeMillis())
        .replaceFirstChar { it.uppercase() }
}
