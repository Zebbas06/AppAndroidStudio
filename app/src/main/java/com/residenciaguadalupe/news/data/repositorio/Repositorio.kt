package com.residenciaguadalupe.news.data.repositorio

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.sesion.AlmacenSesion
import com.residenciaguadalupe.news.data.sesion.Sesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.time.Instant

/** Una pagina ya convertida. [hayMas] segun meta.total. */
data class Pagina<T>(val elementos: List<T>, val pagina: Int, val limite: Int, val total: Int) {
    val hayMas get() = pagina * limite < total
}

/** Filtros de GET posts. Los null no se envian. */
data class FiltroPublicaciones(
    val estado: String? = null,
    val tipo: TipoPublicacion? = null,
    val categoria: Categoria? = null,
    val conImagen: Boolean? = null,
    val eventoDesde: String? = null,
    val eventoHasta: String? = null,
    val publicadasAntesDe: String? = null,
    val orden: String? = null
)

/**
 * Unico punto de entrada a la API para los ViewModels. Convierte DTOs en
 * modelos y todo fallo en [ErrorApi].
 */
class Repositorio(
    private val cliente: ClienteApi,
    private val almacen: AlmacenSesion,
    private val resolver: ContentResolver,
    /** cache/adjuntos/: PDFs descargados para abrirlos con otra app. */
    private val carpetaAdjuntos: File,
    /** Se llama al cerrar sesion, p. ej. para vaciar la memoria de Coil. */
    private val alCerrarSesion: () -> Unit = {}
) {
    private val api get() = cliente.api
    private fun PostDto.modelo() = aPublicacion(cliente::urlAdjunto)

    /** Sube cada vez que se crea, edita, fija, archiva o elimina: el muro se recarga. */
    val cambios = MutableStateFlow(0)
    private fun <T> T.avisar(): T = also { cambios.value++ }

    // --- Sesion ----------------------------------------------------------------

    suspend fun entrar(correo: String, metodo: String, secreto: String): UserDto {
        val r = llamar { api.entrar(LoginRequest(correo.trim(), metodo, secreto)) }.data
        val vence = runCatching { Instant.parse(r.expiresAt) }.getOrElse { Instant.now().plusSeconds(8 * 3600) }
        almacen.guardar(Sesion(r.accessToken, vence, r.user))
        return r.user
    }

    /** 201 con usuario resident/pending, sin token: no se entra al muro. */
    suspend fun registrar(nombre: String, correo: String, metodo: String, secreto: String): UserDto =
        llamar { api.registrar(RegistroRequest(nombre.trim(), correo.trim(), metodo, secreto)) }.data

    /** GET auth/me al restaurar; actualiza el usuario guardado. */
    suspend fun yo(): UserDto = llamar { api.yo() }.data.also(almacen::actualizarUsuario)

    /**
     * Revoca la sesion en el servidor si se puede, y siempre borra la local,
     * los adjuntos descargados y las imagenes en memoria.
     */
    suspend fun salir() {
        runCatching { llamarSinCuerpo { api.salir() } }
        cerrarLocal(null)
    }

    fun cerrarLocal(motivo: String?) {
        almacen.borrar(motivo)
        carpetaAdjuntos.listFiles()?.forEach { it.delete() }
        alCerrarSesion()
    }

    suspend fun cambiarNombre(nombre: String): UserDto =
        llamar { api.cambiarNombre(NombreRequest(nombre.trim())) }.data.also(almacen::actualizarUsuario)

    suspend fun cambiarCredencial(actual: String, metodo: String, nuevo: String) {
        llamarSinCuerpo { api.cambiarCredencial(CambioCredencialRequest(actual, metodo, nuevo)) }
        // authMethod del usuario guardado cambia; lo refresca /auth/me.
        runCatching { yo() }
    }

    suspend fun preferencias(): PreferenciasDto = llamar { api.preferencias() }.data

    suspend fun guardarPreferencias(p: PreferenciasDto): PreferenciasDto =
        llamar { api.guardarPreferencias(p) }.data

    // --- Publicaciones ---------------------------------------------------------

    suspend fun publicaciones(f: FiltroPublicaciones, pagina: Int = 1, limite: Int = 20): Pagina<Publicacion> {
        val r = llamar {
            api.publicaciones(
                pagina = pagina, limite = limite, estado = f.estado, tipo = f.tipo?.api,
                categoria = f.categoria?.api, conImagen = f.conImagen, eventoDesde = f.eventoDesde,
                eventoHasta = f.eventoHasta, publicadasAntesDe = f.publicadasAntesDe, orden = f.orden
            )
        }
        return Pagina(r.data.map { it.modelo() }, r.meta.page, r.meta.limit, r.meta.total)
    }

    /** GET no marca lectura. */
    suspend fun publicacion(id: String): Publicacion = llamar { api.publicacion(id) }.data.modelo()

    /** Solo residente; conserva el primer readAt y marca su notificacion. */
    suspend fun marcarLeida(id: String) {
        llamar { api.marcarPublicacionLeida(id) }
    }

    /** tipo null = quitar la reaccion. */
    suspend fun reaccionar(id: String, tipo: String?): Reacciones =
        (if (tipo == null) llamar { api.quitarReaccion(id) } else llamar { api.reaccionar(id, ReaccionRequest(tipo)) })
            .data.aReacciones()

    suspend fun crear(cuerpo: PostEscrituraRequest, claveIdempotencia: String): Publicacion =
        llamar { api.crearPublicacion(cuerpo, claveIdempotencia) }.data.modelo().avisar()

    /** 409 VERSION_CONFLICT si alguien edito despues de [version]. */
    suspend fun editar(id: String, cuerpo: PostEscrituraRequest, version: Int): Publicacion =
        llamar { api.editarPublicacion(id, cuerpo.copy(type = null), "\"$version\"") }.data.modelo().avisar()

    suspend fun cambiarEstado(id: String, publicada: Boolean): Publicacion =
        llamar { api.cambiarEstado(id, EstadoPostRequest(if (publicada) "published" else "archived")) }.data.modelo().avisar()

    suspend fun fijar(id: String, fijar: Boolean): Publicacion =
        llamar { api.fijar(id, FijarPostRequest(fijar)) }.data.modelo().avisar()

    /** Permanente: elimina adjuntos, lecturas, reacciones y notificaciones. */
    suspend fun eliminar(id: String) = llamarSinCuerpo { api.eliminarPublicacion(id) }.avisar()

    // --- Archivos --------------------------------------------------------------

    /**
     * Valida tipo y tamano ANTES de enviar (evita transferencias inutiles) y
     * sube el archivo. El resultado aun no pertenece a ninguna publicacion.
     */
    suspend fun subir(uri: Uri, textoAlternativo: String?): Adjunto = withContext(Dispatchers.IO) {
        val mime = resolver.getType(uri)
        val limite = LIMITES_ADJUNTO[mime]
            ?: throw ErrorApi(415, "UNSUPPORTED_MEDIA_TYPE", "Solo se pueden adjuntar fotografías JPG o PNG, o documentos PDF.")
        var nombre = "adjunto"
        var tamano = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { nombre = it }
                if (!c.isNull(1)) tamano = c.getLong(1)
            }
        }
        if (tamano > limite) throw ErrorApi(413, "PAYLOAD_TOO_LARGE", mensajeTamano(mime!!))
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ErrorApi(-1, "LOCAL_FILE", "No se pudo leer el archivo elegido.")
        if (bytes.size > limite) throw ErrorApi(413, "PAYLOAD_TOO_LARGE", mensajeTamano(mime!!))

        llamar { api.subirArchivo(cuerpoSubida(nombre, mime!!, bytes, textoAlternativo)) }.data.aAdjunto(cliente::urlAdjunto)
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
    suspend fun eliminarArchivo(id: String) = llamarSinCuerpo { api.eliminarArchivo(id) }

    /** Descarga un PDF con Bearer a cache/adjuntos/ para abrirlo con otra app. */
    suspend fun descargar(adjunto: Adjunto): File = withContext(Dispatchers.IO) {
        val cuerpo = llamar { api.descargar(adjunto.url) }
        carpetaAdjuntos.mkdirs()
        val extension = if (adjunto.esPdf) "pdf" else if (adjunto.mime == "image/png") "png" else "jpg"
        val destino = File(carpetaAdjuntos, "adjunto-${adjunto.id}.$extension")
        cuerpo.byteStream().use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        destino
    }

    // --- Notificaciones --------------------------------------------------------

    /** Devuelve la pagina y el total de no leidas. */
    suspend fun notificaciones(pagina: Int = 1, limite: Int = 30): Pair<Pagina<Notificacion>, Int> {
        val r = llamar { api.notificaciones(pagina, limite) }
        return Pagina(r.data.map { it.aNotificacion() }, r.meta.page, r.meta.limit, r.meta.total) to r.meta.unreadCount
    }

    suspend fun noLeidas(): Int = llamar { api.noLeidas() }.data.unreadCount

    suspend fun marcarNotificacionLeida(id: String) {
        llamar { api.marcarNotificacionLeida(id) }
    }

    /** Devuelve el nuevo total de no leidas. */
    suspend fun leerTodas(): Int = llamar { api.leerTodas() }.data.unreadCount

    suspend fun archivarLeidas(): Int = llamar { api.archivarLeidas() }.data.archived

    // --- Usuarios (admin) ------------------------------------------------------

    suspend fun usuarios(estado: EstadoCuenta?, busqueda: String?, pagina: Int = 1): Pagina<UserDto> {
        val r = llamar {
            api.usuarios(pagina = pagina, limite = 30, estado = estado?.api, busqueda = busqueda?.trim()?.takeIf { it.isNotEmpty() })
        }
        return Pagina(r.data, r.meta.page, r.meta.limit, r.meta.total)
    }

    suspend fun usuario(id: String): UserDto = llamar { api.usuario(id) }.data
    suspend fun crearUsuario(c: CrearUsuarioRequest): UserDto = llamar { api.crearUsuario(c) }.data
    suspend fun editarUsuario(id: String, c: EditarUsuarioRequest): UserDto = llamar { api.editarUsuario(id, c) }.data
    suspend fun reponerCredencial(id: String, metodo: String, secreto: String) =
        llamarSinCuerpo { api.reponerCredencial(id, ReponerCredencialRequest(metodo, secreto)) }

}
