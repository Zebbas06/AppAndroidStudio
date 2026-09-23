package com.residenciaguadalupe.news.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Todo lo de fechas en un lugar. La zona de negocio es America/Mexico_City
 * sin importar la zona del telefono: "hoy" es el hoy de la residencia.
 *
 * La API recibe y devuelve instantes ISO 8601 en UTC (…Z) y fechas civiles
 * YYYY-MM-DD (menuDate, admissionDate) que no se convierten a instante.
 */
object Fechas {
    /**
     * Mexico abolio el horario de verano en octubre de 2022. Un telefono con
     * tzdata anterior (p. ej. 2019b en Android 10 sin actualizar) aun aplica
     * CDT y correria una hora la agenda y los eventos; en ese caso se usa
     * UTC-6 fijo, que es el offset vigente todo el ano.
     */
    val ZONA: ZoneId = zonaResidencia(ZoneId.of("America/Mexico_City"))

    internal fun zonaResidencia(zona: ZoneId): ZoneId {
        val veranoPosterior = Instant.parse("2023-07-01T12:00:00Z")
        return if (zona.rules.getOffset(veranoPosterior) == ZoneOffset.ofHours(-6)) zona else ZoneOffset.ofHours(-6)
    }
    private val ES = Locale("es", "MX")

    /** Reloj inyectable para las pruebas. */
    var reloj: Clock = Clock.system(ZONA)

    fun ahora(): ZonedDateTime = ZonedDateTime.now(reloj).withZoneSameInstant(ZONA)
    fun hoy(): LocalDate = ahora().toLocalDate()

    fun instante(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
    fun local(iso: String?): ZonedDateTime? = instante(iso)?.atZone(ZONA)
    fun fecha(civil: String?): LocalDate? = civil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Inicio del dia en la residencia, en UTC ISO para la API. */
    fun inicioDelDia(d: LocalDate): String = aIso(d.atStartOfDay(ZONA))
    fun aIso(z: ZonedDateTime): String = z.toInstant().truncatedTo(ChronoUnit.SECONDS).toString()

    /** Rango [desde, hasta) de la agenda. Semana = lunes a domingo (provisional). */
    fun rangoHoy(): Pair<LocalDate, LocalDate> = hoy().let { it to it.plusDays(1) }
    fun rangoManana(): Pair<LocalDate, LocalDate> = hoy().plusDays(1).let { it to it.plusDays(1) }
    fun rangoSemana(): Pair<LocalDate, LocalDate> {
        val lunes = hoy().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return lunes to lunes.plusDays(7)
    }

    // --- Textos para la interfaz ---------------------------------------------

    fun diaSemana(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, ES)
    fun mes(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL, ES)
    private fun mayuscula(s: String) = s.replaceFirstChar { it.titlecase(ES) }

    /** "10:30 a. m." como en el mockup. */
    fun hora(z: ZonedDateTime): String {
        val h = z.hour % 12
        val sufijo = if (z.hour < 12) "a. m." else "p. m."
        return "${if (h == 0) 12 else h}:${"%02d".format(z.minute)} $sufijo"
    }

    /** "Miércoles 16". */
    fun diaCorto(d: LocalDate): String = "${mayuscula(diaSemana(d))} ${d.dayOfMonth}"

    /** "Miércoles 16 de septiembre de 2026". */
    fun fechaLarga(d: LocalDate): String =
        "${mayuscula(diaSemana(d))} ${d.dayOfMonth} de ${mes(d)} de ${d.year}"

    /** "Hoy", "Ayer", "Mañana" o "Lunes 14". */
    fun diaRelativo(d: LocalDate): String = when (ChronoUnit.DAYS.between(hoy(), d)) {
        0L -> "Hoy"
        -1L -> "Ayer"
        1L -> "Mañana"
        else -> diaCorto(d)
    }

    /** "Hoy a las 8:00 a. m." / "Lunes 14 · 5:00 p. m.". */
    fun momento(z: ZonedDateTime): String {
        val dia = diaRelativo(z.toLocalDate())
        return if (dia == "Hoy" || dia == "Ayer" || dia == "Mañana") "$dia a las ${hora(z)}" else "$dia · ${hora(z)}"
    }

    /** "Hace 15 min", "Hace 1 hora", "Ayer", "Lunes 14". */
    fun hace(z: ZonedDateTime): String {
        val minutos = Duration.between(z, ahora()).toMinutes()
        return when {
            minutos < 1 -> "Ahora"
            minutos < 60 -> "Hace $minutos min"
            minutos < 120 -> "Hace 1 hora"
            z.toLocalDate() == hoy() -> "Hace ${minutos / 60} horas"
            else -> diaRelativo(z.toLocalDate())
        }
    }

    /** "Septiembre de 2026" para agrupar el archivo. */
    fun mesAnio(d: LocalDate): String = "${mayuscula(mes(d))} de ${d.year}"

    /** "16/09/2026" en formularios. */
    val FORMATO_CORTO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Saludo segun la hora de la residencia. */
    fun saludo(): String = when (ahora().hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}
