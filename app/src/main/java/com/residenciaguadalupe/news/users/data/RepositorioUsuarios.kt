package com.residenciaguadalupe.news.users.data

import com.residenciaguadalupe.news.data.EstadoCuenta
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Pagina

interface RepositorioUsuarios {
    suspend fun usuarios(estado: EstadoCuenta?, busqueda: String?, pagina: Int = 1): Pagina<UserDto>
    suspend fun usuario(id: String): UserDto
    suspend fun crearUsuario(c: CrearUsuarioRequest): UserDto
    suspend fun editarUsuario(id: String, c: EditarUsuarioRequest): UserDto
    suspend fun reponerCredencial(id: String, metodo: String, secreto: String)
}

/** Contrato de administracion de cuentas; los permisos tambien se validan en la API. */
class RepositorioUsuariosApi(private val api: ApiServicio) : RepositorioUsuarios {
    override suspend fun usuarios(estado: EstadoCuenta?, busqueda: String?, pagina: Int): Pagina<UserDto> {
        val r = llamar {
            api.usuarios(pagina = pagina, limite = 30, estado = estado?.api, busqueda = busqueda?.trim()?.takeIf { it.isNotEmpty() })
        }
        return Pagina(r.data, r.meta.page, r.meta.limit, r.meta.total)
    }

    override suspend fun usuario(id: String): UserDto = llamar { api.usuario(id) }.data
    override suspend fun crearUsuario(c: CrearUsuarioRequest): UserDto = llamar { api.crearUsuario(c) }.data
    override suspend fun editarUsuario(id: String, c: EditarUsuarioRequest): UserDto = llamar { api.editarUsuario(id, c) }.data
    override suspend fun reponerCredencial(id: String, metodo: String, secreto: String) =
        llamarSinCuerpo { api.reponerCredencial(id, ReponerCredencialRequest(metodo, secreto)) }

}
