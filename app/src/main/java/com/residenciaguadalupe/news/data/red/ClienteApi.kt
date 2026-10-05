package com.residenciaguadalupe.news.data.red

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.residenciaguadalupe.news.core.session.GestorSesion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Construye, una sola vez, el Json, el OkHttp autenticado y Retrofit.
 * AplicacionResidencia los crea y Coil reutiliza el mismo OkHttp para que
 * las imagenes privadas lleven el Bearer.
 */
class ClienteApi(
    urlBase: String,
    private val sesion: GestorSesion,
    interceptoresExtra: List<Interceptor> = emptyList()
) {
    val base: HttpUrl = urlBase.toHttpUrl()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Un PDF de varios MB en una conexion lenta necesita margen al subir.
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(interceptorToken())
        .apply { interceptoresExtra.forEach { addInterceptor(it) } }
        .build()

    val api: ApiServicio = Retrofit.Builder()
        .baseUrl(base)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiServicio::class.java)

    /**
     * contentUrl es relativa al ORIGEN (/api/v1/media/45/content), no a la
     * base de Retrofit: resolverla contra la base con ruta absoluta evita
     * duplicar /api/v1/.
     */
    fun urlAdjunto(contentUrl: String): String =
        (base.resolve(contentUrl) ?: base).toString()

    /**
     * Pone el Bearer solo en peticiones a nuestro servidor, y ante un 401
     * con token (vencido o revocado) cierra la sesion local. El 401 de
     * auth/login no cuenta: ahi significa credencial incorrecta.
     */
    private fun interceptorToken() = Interceptor { cadena ->
        val peticion = cadena.request()
        val nuestro = peticion.url.scheme == base.scheme && peticion.url.host == base.host && peticion.url.port == base.port
        val logout = peticion.url.encodedPath.endsWith("/auth/logout")
        val explicito = if (logout) peticion.header("Authorization")?.removePrefix("Bearer ") else null
        val actual = sesion.estado.value.sesion
        if (explicito == null && actual?.vencida() == true) {
            sesion.invalidar(actual.token, "Su sesión terminó. Vuelva a entrar.")
        }
        val token = explicito ?: sesion.estado.value.sesion?.token
        val conToken = if (token != null && nuestro)
            peticion.newBuilder().header("Authorization", "Bearer $token").build()
        else peticion

        val respuesta = cadena.proceed(conToken)
        if (respuesta.code == 401 && token != null && nuestro && !peticion.url.encodedPath.endsWith("/auth/login")) {
            sesion.invalidar(token, "Su sesión terminó. Vuelva a entrar.")
        }
        respuesta
    }

    companion object {
        /**
         * - ignoreUnknownKeys: la API puede anadir campos.
         * - encodeDefaults: para que viajen type = "notice", clientType = "android"…
         * - explicitNulls = false: un null se omite. En este contrato omitir un
         *   campo nullable equivale a su default (null), y ademas evita el 422
         *   de residentProfile: null y de type en el PUT.
         */
        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
