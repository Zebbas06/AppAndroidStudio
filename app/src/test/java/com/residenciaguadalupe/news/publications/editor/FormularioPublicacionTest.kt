package com.residenciaguadalupe.news.publications.editor

import com.residenciaguadalupe.news.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FormularioPublicacionTest {
    private val base = FormularioPublicacion(TipoPublicacion.AVISO, Categoria.AVISO, " Titulo ", " Texto ",
        true, null, null, null, "", null, null, "m1")
    @Test fun `aviso recorta textos y conserva adjuntos`() {
        val c = base.cuerpo(true, null).getOrThrow()
        assertEquals("Titulo", c.title); assertEquals("Texto", c.body)
        assertEquals(listOf("m1"), c.attachmentIds); assertEquals(true, c.isPinned)
        assertNull(c.eventStartAt); assertNull(c.menuDate)
    }
    @Test fun `empleado conserva fijado original sin poder cambiarlo`() {
        assertEquals(true, base.copy(fijar = false).cuerpo(false, true).getOrThrow().isPinned)
        assertNull(base.cuerpo(false, null).getOrThrow().isPinned)
    }
    @Test fun `menu exige fecha y comida`() {
        val menu = base.copy(tipo = TipoPublicacion.MENU)
        assertTrue(menu.cuerpo(true, null).isFailure)
        val dia = LocalDate.of(2026, 10, 5)
        assertTrue(menu.copy(fecha = dia).cuerpo(true, null).isFailure)
        val c = menu.copy(fecha = dia, comida = Comida.entries.first()).cuerpo(true, null).getOrThrow()
        assertEquals("2026-10-05", c.menuDate)
        assertEquals(Comida.entries.first().api, c.mealType)
    }
    @Test fun `evento valida lugar y orden de horas en zona de residencia`() {
        val evento = base.copy(tipo = TipoPublicacion.EVENTO, fecha = LocalDate.of(2026, 10, 5),
            horaInicio = LocalTime.of(10, 0), horaFin = LocalTime.of(9, 0), lugar = " Salon ")
        assertTrue(evento.cuerpo(true, null).isFailure)
        assertTrue(evento.copy(horaFin = null, lugar = "").cuerpo(true, null).isFailure)
        val c = evento.copy(horaFin = null).cuerpo(true, null).getOrThrow()
        assertEquals(c.eventStartAt, c.eventEndAt)
        assertEquals(Fechas.aIso(evento.fecha!!.atTime(evento.horaInicio).atZone(Fechas.ZONA)), c.eventStartAt)
        assertEquals("Salon", c.eventLocation)
    }
    @Test fun `vencimiento es inicio del siguiente dia y textos vacios se rechazan`() {
        val dia = LocalDate.of(2026, 10, 5)
        assertEquals(Fechas.inicioDelDia(dia.plusDays(1)),
            base.copy(mostrarHasta = dia).cuerpo(true, null).getOrThrow().expiresAt)
        assertTrue(base.copy(texto = " ").cuerpo(true, null).isFailure)
        assertTrue(base.copy(titulo = " ").cuerpo(true, null).isFailure)
    }
}
