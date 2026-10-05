package com.residenciaguadalupe.news

import com.residenciaguadalupe.news.notifications.data.RepositorioNotificaciones
import com.residenciaguadalupe.news.notifications.data.RepositorioNotificacionesApi
import com.residenciaguadalupe.news.profile.data.RepositorioPerfil
import com.residenciaguadalupe.news.profile.data.RepositorioPerfilApi
import com.residenciaguadalupe.news.users.data.RepositorioUsuarios
import com.residenciaguadalupe.news.users.data.RepositorioUsuariosApi
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.residenciaguadalupe.news.publications.data.RepositorioPublicaciones
import com.residenciaguadalupe.news.publications.data.RepositorioPublicacionesApi
import com.residenciaguadalupe.news.media.data.*
import com.residenciaguadalupe.news.data.Adjunto
import com.residenciaguadalupe.news.core.session.intentarOperacion
import com.residenciaguadalupe.news.data.red.ClienteApi
import com.residenciaguadalupe.news.data.red.InterceptorImagenSesion
import com.residenciaguadalupe.news.auth.data.RepositorioAutenticacion
import com.residenciaguadalupe.news.auth.data.RepositorioAutenticacionApi
import com.residenciaguadalupe.news.core.session.AlmacenSesionPreferencias
import com.residenciaguadalupe.news.core.session.GestorSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Clase Application declarada en el AndroidManifest (android:name).
 *
 * Crea una sola vez el cliente OkHttp autenticado, Retrofit, el repositorio
 * y el ImageLoader de Coil que comparte ese OkHttp (las imagenes privadas
 * necesitan el Bearer).
 */
class AplicacionResidencia : Application(), ImageLoaderFactory {

    private val ambitoAplicacion = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val carpetaAdjuntos by lazy { File(cacheDir, "adjuntos") }
    val sesion: GestorSesion by lazy {
        GestorSesion(AlmacenSesionPreferencias(this, ClienteApi.json)) {
            if (clienteDelegado.isInitialized()) cliente.okHttp.dispatcher.cancelAll()
            carpetaAdjuntos.listFiles()?.forEach { it.delete() }
            cargador?.memoryCache?.clear()
        }
    }

    private val clienteDelegado: Lazy<ClienteApi> = lazy { ClienteApi(BuildConfig.URL_API, sesion, interceptoresDepuracion()) }
    val cliente: ClienteApi by clienteDelegado

    val autenticacion: RepositorioAutenticacion by lazy { RepositorioAutenticacionApi(cliente.api, sesion, ambitoAplicacion) }

    val perfil: RepositorioPerfil by lazy { RepositorioPerfilApi(cliente.api, sesion) }

    val publicaciones: RepositorioPublicaciones by lazy { RepositorioPublicacionesApi(cliente.api, cliente::urlAdjunto) }

    val notificaciones: RepositorioNotificaciones by lazy { RepositorioNotificacionesApi(cliente.api) }

    val usuarios: RepositorioUsuarios by lazy { RepositorioUsuariosApi(cliente.api) }

    val medios: RepositorioMedios by lazy {
        RepositorioMediosApi(cliente, sesion, FuenteArchivosAndroid(contentResolver), carpetaAdjuntos)
    }

    /** Nunca limpiar un upload anterior con las credenciales de otra cuenta. */
    fun descartarAdjunto(adjunto: Adjunto, token: String) {
        if (sesion.estado.value.sesion?.token != token) return
        sesion.lanzar(ambitoAplicacion) {
            if (sesion.estado.value.sesion?.token == token) {
                intentarOperacion { medios.eliminarArchivo(adjunto.id) }
            }
        }
    }

    private var cargador: ImageLoader? = null

    /**
     * Sin cache en disco: los adjuntos responden private, no-store y no deben
     * quedar en el telefono. La cache en memoria se vacia al cerrar sesion.
     */
    override fun newImageLoader(): ImageLoader = cargador ?: ImageLoader.Builder(this)
        .okHttpClient(cliente.okHttp)
        .components { add(InterceptorImagenSesion(sesion) { cargador }) }
        .diskCache(null)
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(true)
        .build()
        .also { cargador = it }
}
