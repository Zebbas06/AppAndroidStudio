package com.residenciaguadalupe.news.publications.data

import com.residenciaguadalupe.news.data.Fechas
import com.residenciaguadalupe.news.data.PestanaAgenda
import com.residenciaguadalupe.news.data.TipoPublicacion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Un mismo rango para agenda y banda de hoy; eventTo es exclusivo. */
fun filtroAgenda(pestana: PestanaAgenda, hoy: LocalDate = Fechas.hoy()): FiltroPublicaciones {
    val desde = when (pestana) {
        PestanaAgenda.HOY -> hoy
        PestanaAgenda.MANANA -> hoy.plusDays(1)
        PestanaAgenda.SEMANA -> hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val hasta = desde.plusDays(if (pestana == PestanaAgenda.SEMANA) 7 else 1)
    return FiltroPublicaciones(
        tipo = TipoPublicacion.EVENTO,
        eventoDesde = Fechas.inicioDelDia(desde),
        eventoHasta = Fechas.inicioDelDia(hasta),
        orden = "eventStartAt"
    )
}
