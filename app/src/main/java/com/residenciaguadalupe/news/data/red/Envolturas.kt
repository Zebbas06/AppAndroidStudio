package com.residenciaguadalupe.news.data.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Las tres formas que puede tener una respuesta de la API.
 *
 *  - Exito individual: { "data": OBJETO }
 *  - Lista paginada:   { "data": [ ... ], "meta": { page, limit, total } }
 *  - Error:            { "error": { code, message, requestId, details } }
 *
 * El 204 no trae JSON: esas llamadas devuelven Response<Unit>.
 * AWS puede contestar con otro cuerpo ({"message":"Request Timeout"} o
 * HTML): eso lo resuelve ErrorApi, no estas clases.
 */
@Serializable
data class ApiData<T>(val data: T)

@Serializable
data class PageDto<T>(val data: List<T> = emptyList(), val meta: MetaDto = MetaDto())

@Serializable
data class MetaDto(val page: Int = 1, val limit: Int = 20, val total: Int = 0)

/** El buzon anade unreadCount a meta. */
@Serializable
data class PaginaNotificacionesDto(
    val data: List<NotificacionDto> = emptyList(),
    val meta: MetaNotificacionesDto = MetaNotificacionesDto()
)

@Serializable
data class MetaNotificacionesDto(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val unreadCount: Int = 0
)

/** Cuerpo de error que no es de la aplicacion, por ejemplo el 408 de Gateway. */
@Serializable
data class MensajeDto(val message: String = "")

@Serializable
data class EstadoDto(val status: String = "")

@Serializable
data class SobreErrorDto(val error: ErrorDto)

@Serializable
data class ErrorDto(
    val code: String = "",
    val message: String = "",
    val requestId: String? = null,
    val details: List<DetalleErrorDto>? = null
)

/**
 * details es opcional. field puede venir vacio cuando el problema es del
 * cuerpo completo y no de un campo concreto.
 */
@Serializable
data class DetalleErrorDto(
    @SerialName("field") val campo: String = "",
    @SerialName("message") val mensaje: String = ""
)
