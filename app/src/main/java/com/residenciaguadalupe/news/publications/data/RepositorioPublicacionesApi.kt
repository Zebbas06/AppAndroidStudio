package com.residenciaguadalupe.news.publications.data

import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Pagina
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Traduce el contrato HTTP; no guarda listas ni estado de pantallas. */
class RepositorioPublicacionesApi(
    private val api: ApiServicio,
    private val urlAdjunto: (String) -> String
) : RepositorioPublicaciones {
    private val revision = MutableStateFlow(0)
    override val cambios = revision.asStateFlow()
    private val eventos = MutableSharedFlow<CambioPublicacion>()
    override val actualizaciones = eventos.asSharedFlow()
    private fun PostDto.modelo() = aPublicacion(urlAdjunto)
    private fun <T> T.avisar(): T = also { revision.update { it + 1 } }

    override suspend fun publicaciones(f: FiltroPublicaciones, pagina: Int, limite: Int): Pagina<Publicacion> {
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
    override suspend fun publicacion(id: String): Publicacion = llamar { api.publicacion(id) }.data.modelo()

    /** Solo residente; conserva el primer readAt y marca su notificacion. */
    override suspend fun marcarLeida(id: String) {
        llamar { api.marcarPublicacionLeida(id) }
        eventos.emit(CambioPublicacion.Leida(id))
    }

    /** tipo null = quitar la reaccion. */
    override suspend fun reaccionar(id: String, tipo: String?): Reacciones {
        val r = (if (tipo == null) llamar { api.quitarReaccion(id) }
            else llamar { api.reaccionar(id, ReaccionRequest(tipo)) }).data.aReacciones()
        eventos.emit(CambioPublicacion.Reaccion(id, r))
        return r
    }

    override suspend fun crear(cuerpo: PostEscrituraRequest, claveIdempotencia: String): Publicacion =
        llamar { api.crearPublicacion(cuerpo, claveIdempotencia) }.data.modelo().avisar()

    /** 409 VERSION_CONFLICT si alguien edito despues de [version]. */
    override suspend fun editar(id: String, cuerpo: PostEscrituraRequest, version: Int): Publicacion =
        llamar { api.editarPublicacion(id, cuerpo.copy(type = null), "\"$version\"") }.data.modelo().avisar()

    override suspend fun cambiarEstado(id: String, publicada: Boolean): Publicacion =
        llamar { api.cambiarEstado(id, EstadoPostRequest(if (publicada) "published" else "archived")) }.data.modelo().avisar()

    override suspend fun fijar(id: String, fijar: Boolean): Publicacion =
        llamar { api.fijar(id, FijarPostRequest(fijar)) }.data.modelo().avisar()

    /** Permanente: elimina adjuntos, lecturas, reacciones y notificaciones. */
    override suspend fun eliminar(id: String) = llamarSinCuerpo { api.eliminarPublicacion(id) }.avisar()

}
