package com.residenciaguadalupe.news.publications.editor

import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.PostEscrituraRequest
import java.time.LocalDate
import java.time.LocalTime

/** Snapshot validable sin Android, red ni estado de pantalla. */
data class FormularioPublicacion(
    val tipo: TipoPublicacion,
    val categoria: Categoria,
    val titulo: String,
    val texto: String,
    val fijar: Boolean,
    val fecha: LocalDate?,
    val horaInicio: LocalTime?,
    val horaFin: LocalTime?,
    val lugar: String,
    val comida: Comida?,
    val mostrarHasta: LocalDate?,
    val adjuntoId: String?
) {
    /** Arma el cuerpo segun el tipo o devuelve el error para el residente. */
    fun cuerpo(esAdmin: Boolean, fijadaOriginal: Boolean?): Result<PostEscrituraRequest> {
        if (titulo.isBlank() || texto.isBlank())
            return Result.failure(IllegalStateException("Falta el título o el texto del aviso. Los residentes necesitan las dos cosas para entenderlo."))
        val vence = mostrarHasta?.let { Fechas.inicioDelDia(it.plusDays(1)) }
        // Empleado: se manda el valor actual (no puede cambiarlo); admin: el del formulario.
        val valorFijado = if (esAdmin) fijar else fijadaOriginal
        val base = PostEscrituraRequest(
            type = tipo.api,
            category = categoria.api,
            title = titulo.trim(),
            body = texto.trim(),
            isPinned = valorFijado,
            attachmentIds = listOfNotNull(adjuntoId),
            expiresAt = vence
        )
        return when (tipo) {
            TipoPublicacion.AVISO -> Result.success(base)
            TipoPublicacion.MENU -> {
                val fecha = fecha ?: return Result.failure(IllegalStateException("Elija el día del menú."))
                val comida = comida ?: return Result.failure(IllegalStateException("Elija si es desayuno, comida, cena o todo el día."))
                Result.success(base.copy(menuDate = fecha.toString(), mealType = comida.api))
            }
            TipoPublicacion.EVENTO -> {
                val fecha = fecha ?: return Result.failure(IllegalStateException("Elija el día del evento."))
                val ini = horaInicio ?: return Result.failure(IllegalStateException("Elija la hora de inicio."))
                val fin = horaFin ?: ini
                if (fin.isBefore(ini)) return Result.failure(IllegalStateException("La hora de fin no puede ser antes del inicio."))
                if (lugar.isBlank()) return Result.failure(IllegalStateException("Escriba dónde será el evento."))
                Result.success(base.copy(
                    eventStartAt = Fechas.aIso(fecha.atTime(ini).atZone(Fechas.ZONA)),
                    eventEndAt = Fechas.aIso(fecha.atTime(fin).atZone(Fechas.ZONA)),
                    eventLocation = lugar.trim()
                ))
            }
        }
    }

}
