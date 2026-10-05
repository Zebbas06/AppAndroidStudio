package com.residenciaguadalupe.news.profile.data

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.intentarOperacion
import com.residenciaguadalupe.news.data.red.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RepositorioPerfil {
    suspend fun cambiarNombre(nombre: String): UserDto
    suspend fun cambiarCredencial(actual: String, metodo: String, nuevo: String): UserDto
    suspend fun preferencias(): PreferenciasDto
    suspend fun guardarPreferencias(p: PreferenciasDto): PreferenciasDto
}

/** Perfil propio. Serializa operaciones y actualiza solo la sesion que las inicio. */
class RepositorioPerfilApi(
    private val api: ApiServicio,
    private val gestor: GestorSesion
) : RepositorioPerfil {
    private val operaciones = Mutex()

    private suspend fun <T> operar(bloque: suspend (String) -> T): T {
        val token = gestor.estado.value.sesion?.token ?: throw CancellationException("No hay sesion activa.")
        return operaciones.withLock {
            gestor.conSesionActual(token) {}
            bloque(token)
        }
    }

    override suspend fun cambiarNombre(nombre: String): UserDto = operar { token ->
        llamar { api.cambiarNombre(NombreRequest(nombre.trim())) }.data
            .also { gestor.actualizarUsuario(token, it) }
    }

    override suspend fun cambiarCredencial(actual: String, metodo: String, nuevo: String): UserDto = operar { token ->
        llamarSinCuerpo { api.cambiarCredencial(CambioCredencialRequest(actual, metodo, nuevo)) }
        // El 204 confirma el nuevo metodo aunque el refresco posterior falle.
        val confirmado = gestor.conSesionActual(token) {
            val usuario = checkNotNull(gestor.estado.value.sesion).usuario.copy(authMethod = metodo)
            gestor.actualizarUsuario(token, usuario)
            usuario
        }
        val usuario = intentarOperacion { llamar { api.yo() }.data }.getOrElse { confirmado }
        gestor.actualizarUsuario(token, usuario)
        usuario
    }

    override suspend fun preferencias(): PreferenciasDto = operar { token ->
        llamar { api.preferencias() }.data.also { guardarEnSesion(token, it) }
    }

    override suspend fun guardarPreferencias(p: PreferenciasDto): PreferenciasDto = operar { token ->
        llamar { api.guardarPreferencias(p) }.data.also { guardarEnSesion(token, it) }
    }

    private fun guardarEnSesion(token: String, p: PreferenciasDto) {
        gestor.conSesionActual(token) {
            val usuario = checkNotNull(gestor.estado.value.sesion).usuario
            gestor.actualizarUsuario(token, usuario.copy(preferences = p))
        }
    }
}
