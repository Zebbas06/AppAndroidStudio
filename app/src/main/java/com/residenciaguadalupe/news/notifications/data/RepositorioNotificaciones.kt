package com.residenciaguadalupe.news.notifications.data

import com.residenciaguadalupe.news.data.Notificacion
import com.residenciaguadalupe.news.data.aNotificacion
import com.residenciaguadalupe.news.data.red.ApiServicio
import com.residenciaguadalupe.news.data.red.llamar
import com.residenciaguadalupe.news.data.repositorio.Pagina

data class PaginaNotificaciones(val pagina: Pagina<Notificacion>, val sinLeer: Int)

interface RepositorioNotificaciones {
    suspend fun notificaciones(pagina: Int = 1, limite: Int = 30): PaginaNotificaciones
    suspend fun noLeidas(): Int
    suspend fun marcarLeida(id: String): Notificacion
    suspend fun leerTodas(): Int
    suspend fun archivarLeidas(): Int
}

/** Traduce HTTP sin estado de pantalla ni dependencia de Application. */
class RepositorioNotificacionesApi(private val api: ApiServicio) : RepositorioNotificaciones {
    override suspend fun notificaciones(pagina: Int, limite: Int): PaginaNotificaciones {
        val r = llamar { api.notificaciones(pagina, limite) }
        return PaginaNotificaciones(
            Pagina(r.data.map { it.aNotificacion() }, r.meta.page, r.meta.limit, r.meta.total),
            r.meta.unreadCount
        )
    }
    override suspend fun noLeidas(): Int = llamar { api.noLeidas() }.data.unreadCount
    override suspend fun marcarLeida(id: String): Notificacion = llamar { api.marcarNotificacionLeida(id) }.data.aNotificacion()
    override suspend fun leerTodas(): Int = llamar { api.leerTodas() }.data.unreadCount
    override suspend fun archivarLeidas(): Int = llamar { api.archivarLeidas() }.data.archived
}
