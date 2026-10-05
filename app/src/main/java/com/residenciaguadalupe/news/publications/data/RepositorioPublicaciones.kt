package com.residenciaguadalupe.news.publications.data

import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Pagina
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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


sealed interface CambioPublicacion {
    data class Leida(val id: String) : CambioPublicacion
    data class Reaccion(val id: String, val reacciones: Reacciones) : CambioPublicacion
}

/** Contrato compartido: las pantallas no conocen Retrofit ni sus DTO de respuesta. */
interface RepositorioPublicaciones {
    val cambios: StateFlow<Int>
    val actualizaciones: Flow<CambioPublicacion>
    suspend fun publicaciones(f: FiltroPublicaciones, pagina: Int = 1, limite: Int = 20): Pagina<Publicacion>
    suspend fun publicacion(id: String): Publicacion
    suspend fun marcarLeida(id: String)
    suspend fun reaccionar(id: String, tipo: String?): Reacciones
    suspend fun crear(cuerpo: PostEscrituraRequest, claveIdempotencia: String): Publicacion
    suspend fun editar(id: String, cuerpo: PostEscrituraRequest, version: Int): Publicacion
    suspend fun cambiarEstado(id: String, publicada: Boolean): Publicacion
    suspend fun fijar(id: String, fijar: Boolean): Publicacion
    suspend fun eliminar(id: String)
}
