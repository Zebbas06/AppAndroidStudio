package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.data.sesion.AlmacenSesion
import com.residenciaguadalupe.news.data.sesion.Sesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** AlmacenSesion sin Android para las pruebas. */
class SesionEnMemoria(inicial: Sesion? = null) : AlmacenSesion {
    private val estado = MutableStateFlow(inicial)
    override val sesion: StateFlow<Sesion?> = estado
    var motivo: String? = null
    override fun guardar(s: Sesion) { estado.value = s }
    override fun actualizarUsuario(u: UserDto) { estado.value = estado.value?.copy(usuario = u) }
    override fun borrar(motivo: String?) { this.motivo = motivo; estado.value = null }
    override fun tomarMotivo(): String? = motivo.also { motivo = null }
}
