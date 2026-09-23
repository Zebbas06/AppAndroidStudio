package com.residenciaguadalupe.news

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.residenciaguadalupe.news.data.repositorio.Repositorio
import com.residenciaguadalupe.news.data.red.ClienteApi
import com.residenciaguadalupe.news.data.sesion.AlmacenSesionPreferencias
import java.io.File

/**
 * Clase Application declarada en el AndroidManifest (android:name).
 *
 * Crea una sola vez el cliente OkHttp autenticado, Retrofit, el repositorio
 * y el ImageLoader de Coil que comparte ese OkHttp (las imagenes privadas
 * necesitan el Bearer).
 */
class AplicacionResidencia : Application(), ImageLoaderFactory {

    val sesion by lazy { AlmacenSesionPreferencias(this, ClienteApi.json) }

    val cliente by lazy { ClienteApi(BuildConfig.URL_API, sesion, interceptoresDepuracion()) }

    val repositorio by lazy {
        Repositorio(cliente, sesion, contentResolver, File(cacheDir, "adjuntos")) {
            imageLoader().memoryCache?.clear()
        }
    }

    private var cargador: ImageLoader? = null

    private fun imageLoader(): ImageLoader = cargador ?: newImageLoader().also { cargador = it }

    /**
     * Sin cache en disco: los adjuntos responden private, no-store y no deben
     * quedar en el telefono. La cache en memoria se vacia al cerrar sesion.
     */
    override fun newImageLoader(): ImageLoader = cargador ?: ImageLoader.Builder(this)
        .okHttpClient(cliente.okHttp)
        .diskCache(null)
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(true)
        .build()
        .also { cargador = it }
}
