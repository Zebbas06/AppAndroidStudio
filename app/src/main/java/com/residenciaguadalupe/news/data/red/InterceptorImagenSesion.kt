package com.residenciaguadalupe.news.data.red

import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.residenciaguadalupe.news.core.session.GestorSesion
import kotlinx.coroutines.CancellationException

/** Aisla claves de cache y descarta imagenes que terminan en una sesion distinta. */
class InterceptorImagenSesion(
    private val gestor: GestorSesion,
    private val cargador: () -> ImageLoader?
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val estado = gestor.estado.value
        if (estado.sesion == null) throw CancellationException("No hay sesion activa.")
        val peticion = chain.request.newBuilder()
            .memoryCacheKey("${estado.revision}:${chain.request.memoryCacheKey?.key ?: chain.request.data}")
            .build()
        val resultado = chain.proceed(peticion)
        if (gestor.estado.value.revision != estado.revision) {
            cargador()?.memoryCache?.clear()
            throw CancellationException("La sesion cambio.")
        }
        return resultado
    }
}
