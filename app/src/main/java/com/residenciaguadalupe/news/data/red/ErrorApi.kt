package com.residenciaguadalupe.news.data.red

import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Cualquier fallo de una llamada, ya traducido para la interfaz.
 *
 * [estado] es el status HTTP, o 0 si no hubo respuesta (sin red, tiempo
 * agotado). [mensaje] siempre se puede mostrar al residente: nunca es HTML
 * ni texto tecnico de AWS. [referencia] es el requestId para reportar la
 * incidencia al backend; nunca contiene el token.
 */
class ErrorApi(
    val estado: Int,
    val codigo: String,
    val mensaje: String,
    val referencia: String? = null,
    val detalles: List<DetalleErrorDto> = emptyList(),
    /** Segundos de Retry-After en un 429. */
    val reintentarEn: Int? = null
) : Exception(mensaje) {

    val sinConexion get() = estado == 0
    val sesionTerminada get() = estado == 401
    val cuentaPendiente get() = codigo == "ACCOUNT_PENDING"
    val conflictoVersion get() = estado == 409 && codigo == "VERSION_CONFLICT"

    /** Mensaje del campo indicado en details, si la API lo marco. */
    fun mensajeDe(campo: String): String? = detalles.firstOrNull { it.campo == campo }?.mensaje

    /** Mensaje + mensajes por campo, listo para un Consejo. */
    val mensajeCompleto: String
        get() = buildString {
            append(mensaje)
            detalles.map { it.mensaje }.filter { it.isNotBlank() && it != mensaje }.distinct().forEach {
                append("\n• ").append(it)
            }
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Traduce una respuesta no exitosa. Tolera cuerpos que no son JSON. */
        fun desde(respuesta: Response<*>): ErrorApi {
            val estado = respuesta.code()
            val texto = runCatching { respuesta.errorBody()?.string() }.getOrNull().orEmpty()
            val referenciaCabecera = respuesta.headers()["X-Request-Id"] ?: respuesta.headers()["apigw-requestid"]
            val reintentar = respuesta.headers()["Retry-After"]?.trim()?.toIntOrNull()
            val error = runCatching { json.decodeFromString(SobreErrorDto.serializer(), texto).error }.getOrNull()

            return if (error != null && error.message.isNotBlank()) {
                ErrorApi(
                    estado = estado,
                    codigo = error.code,
                    mensaje = if (estado == 429) mensajeEspera(reintentar) else error.message,
                    referencia = error.requestId ?: referenciaCabecera,
                    detalles = error.details.orEmpty(),
                    reintentarEn = reintentar
                )
            } else {
                ErrorApi(estado, "HTTP_$estado", mensajePorEstado(estado, reintentar), referenciaCabecera,
                    reintentarEn = reintentar)
            }
        }

        fun sinRed(causa: IOException) = ErrorApi(
            0, "NETWORK",
            if (causa is java.net.SocketTimeoutException)
                "La conexión tardó demasiado. Revise su internet e inténtelo de nuevo."
            else "No hay conexión con la residencia. Revise su internet e inténtelo de nuevo."
        )

        private fun mensajeEspera(segundos: Int?) =
            if (segundos != null && segundos > 0) "Demasiados intentos. Espere $segundos segundos y vuelva a intentarlo."
            else "Demasiados intentos. Espere un momento y vuelva a intentarlo."

        /** Para cuerpos de AWS/infraestructura sin error.code. */
        internal fun mensajePorEstado(estado: Int, reintentar: Int?) = when (estado) {
            400, 422 -> "Revise los datos e inténtelo de nuevo."
            401 -> "Su sesión terminó. Vuelva a entrar."
            403 -> "No tiene permiso para hacer esto."
            404 -> "Esta publicación ya no está disponible."
            408, 504 -> "La conexión tardó demasiado. Inténtelo de nuevo con una mejor conexión."
            409 -> "Alguien más hizo cambios. Vuelva a cargar e inténtelo de nuevo."
            413 -> "El archivo es demasiado grande."
            415 -> "Ese tipo de archivo no se admite. Use JPG, PNG o PDF."
            429 -> mensajeEspera(reintentar)
            in 500..599 -> "El servidor de la residencia no responde. Inténtelo en unos minutos."
            else -> "Algo salió mal. Inténtelo de nuevo."
        }
    }
}

/**
 * Ejecuta una llamada y devuelve el cuerpo o lanza [ErrorApi].
 * Para 204 usar [llamarSinCuerpo].
 */
suspend fun <T> llamar(bloque: suspend () -> Response<T>): T {
    val respuesta = ejecutar(bloque)
    return respuesta.body() ?: throw ErrorApi(respuesta.code(), "EMPTY_BODY", ErrorApi.mensajePorEstado(500, null))
}

suspend fun llamarSinCuerpo(bloque: suspend () -> Response<Unit>) {
    ejecutar(bloque)
}

private suspend fun <T> ejecutar(bloque: suspend () -> Response<T>): Response<T> {
    val respuesta = try {
        bloque()
    } catch (e: IOException) {
        throw ErrorApi.sinRed(e)
    } catch (e: kotlinx.serialization.SerializationException) {
        // Un 2xx con cuerpo inesperado: no mostrar el detalle tecnico.
        throw ErrorApi(-1, "BAD_RESPONSE", "La respuesta del servidor no se pudo leer. Inténtelo de nuevo.")
    }
    if (!respuesta.isSuccessful) throw ErrorApi.desde(respuesta)
    return respuesta
}
