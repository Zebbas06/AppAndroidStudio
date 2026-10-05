package com.residenciaguadalupe.news.media.data

import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.core.session.GestorSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File

/** Transporte de medios y cache privada; no conoce formularios ni listas. */
class RepositorioMediosApi(
    private val cliente: ClienteApi,
    private val gestor: GestorSesion,
    private val fuente: FuenteArchivos,
    /** cache/adjuntos/: PDFs descargados para abrirlos con otra app. */
    private val carpetaAdjuntos: File
) : RepositorioMedios {
    private val api get() = cliente.api
    // --- Archivos --------------------------------------------------------------

    /**
     * Valida tipo y tamano ANTES de enviar (evita transferencias inutiles) y
     * sube el archivo. El resultado aun no pertenece a ninguna publicacion.
     */
    override suspend fun subir(referencia: String, textoAlternativo: String?): Adjunto = withContext(Dispatchers.IO) {
        val token = gestor.estado.value.sesion?.token
            ?: throw kotlinx.coroutines.CancellationException("No hay sesion activa.")
        val archivo = try {
            fuente.archivo(referencia)
        } catch (e: java.io.IOException) {
            throw ErrorApi(-1, "LOCAL_FILE", "No se pudo leer el archivo elegido.")
        }
        val mime = archivo.mime
        val limite = LIMITES_ADJUNTO[mime]
            ?: throw ErrorApi(415, "UNSUPPORTED_MEDIA_TYPE", "Solo se pueden adjuntar fotografías JPG o PNG, o documentos PDF.")
        if ((archivo.tamano ?: -1L) > limite) throw ErrorApi(413, "PAYLOAD_TOO_LARGE", mensajeTamano(mime!!))
        val bytes = try {
            archivo.abrir().use { leerLimitado(it, limite, mime!!) }
        } catch (e: SecurityException) {
            throw ErrorApi(-1, "LOCAL_FILE", "Ya no hay permiso para leer el archivo. Elija el archivo otra vez.")
        } catch (e: java.io.IOException) {
            throw ErrorApi(-1, "LOCAL_FILE", "No se pudo leer el archivo elegido.")
        }

        val respuesta = llamar { api.subirArchivo(cuerpoSubida(archivo.nombre, mime!!, bytes, textoAlternativo)) }
        gestor.conSesionActual(token) { respuesta.data.aAdjunto(cliente::urlAdjunto) }
    }

    companion object {
        /** Limites del contrato. El PDF es de 9,000,000 (no 10 MB) por Gateway. */
        val LIMITES_ADJUNTO = mapOf(
            "image/jpeg" to 5_000_000L,
            "image/png" to 5_000_000L,
            "application/pdf" to 9_000_000L
        )

        fun mensajeTamano(mime: String) =
            if (mime == "application/pdf") "El PDF pesa más de 9 MB. Elija uno más ligero."
            else "La fotografía pesa más de 5 MB. Elija una más ligera."

        /** Multipart de POST media: campo file y altText opcional, de un solo envio. */
        fun cuerpoSubida(nombre: String, mime: String, bytes: ByteArray, textoAlternativo: String?): RequestBody =
            UnSoloEnvio(
                MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", nombre, bytes.toRequestBody(mime.toMediaType()))
                    .apply {
                        textoAlternativo?.trim()?.take(250)?.takeIf { it.isNotEmpty() }?.let { addFormDataPart("altText", it) }
                    }
                    .build()
            )
    }

    /**
     * OkHttp reintenta solo un 408; con un archivo de varios MB eso repetiria
     * toda la subida tras un minuto de espera. Un cuerpo "one shot" no se
     * reintenta: el error llega a la pantalla y la persona decide.
     */
    private class UnSoloEnvio(private val cuerpo: RequestBody) : RequestBody() {
        override fun contentType() = cuerpo.contentType()
        override fun contentLength() = cuerpo.contentLength()
        override fun writeTo(sink: BufferedSink) = cuerpo.writeTo(sink)
        override fun isOneShot() = true
    }

    /** Solo uploads propios aun sin asociar. */
    override suspend fun eliminarArchivo(id: String) = llamarSinCuerpo { api.eliminarArchivo(id) }

    /** Descarga un PDF con Bearer a cache/adjuntos/ para abrirlo con otra app. */
    override suspend fun descargar(adjunto: Adjunto): File = withContext(Dispatchers.IO) {
        val token = gestor.estado.value.sesion?.token
            ?: throw kotlinx.coroutines.CancellationException("No hay sesion activa.")
        try {
            guardarDescarga(adjunto, token)
        } catch (e: java.io.IOException) {
            coroutineContext.ensureActive()
            throw ErrorApi(-1, "LOCAL_FILE", "No se pudo completar la descarga. Revise el espacio disponible e inténtelo de nuevo.")
        }
    }

    private suspend fun guardarDescarga(adjunto: Adjunto, token: String): File =
        llamar { api.descargar(adjunto.url) }.use { cuerpo ->
            carpetaAdjuntos.mkdirs()
            val temporal = File.createTempFile("descarga-", ".tmp", carpetaAdjuntos)
            try {
                cuerpo.byteStream().use { entrada ->
                    temporal.outputStream().use { salida ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val leidos = entrada.read(buffer)
                            if (leidos < 0) break
                            coroutineContext.ensureActive()
                            salida.write(buffer, 0, leidos)
                        }
                    }
                }
                coroutineContext.ensureActive()
                gestor.conSesionActual(token) {
                    val extension = if (adjunto.esPdf) "pdf" else if (adjunto.mime == "image/png") "png" else "jpg"
                    val destino = File(carpetaAdjuntos, "adjunto-${adjunto.id}.$extension")
                    if (!temporal.renameTo(destino)) throw ErrorApi(-1, "LOCAL_FILE", "No se pudo guardar el archivo descargado.")
                    destino
                }
            } finally {
                temporal.delete()
            }
        }
}
