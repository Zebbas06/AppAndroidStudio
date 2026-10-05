package com.residenciaguadalupe.news.auth.data

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.core.session.intentarOperacion
import com.residenciaguadalupe.news.data.red.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** El adaptador confirma la sesion; nunca instala respuestas canceladas. */
interface RepositorioAutenticacion {
    suspend fun entrar(correo: String, metodo: String, secreto: String): UserDto
    suspend fun registrar(nombre: String, correo: String, metodo: String, secreto: String): UserDto
    suspend fun yo(): UserDto
    fun salir()
}

class RepositorioAutenticacionApi(
    private val api: ApiServicio,
    private val gestor: GestorSesion,
    private val ambitoAplicacion: CoroutineScope
) : RepositorioAutenticacion {
    override suspend fun entrar(correo: String, metodo: String, secreto: String): UserDto {
        val revision = gestor.estado.value.revision
        val r = llamar { api.entrar(LoginRequest(correo.trim(), metodo, secreto)) }.data
        val vence = runCatching { Instant.parse(r.expiresAt) }.getOrElse {
            throw ErrorApi(-1, "BAD_RESPONSE", "La respuesta del servidor no se pudo leer. Inténtelo de nuevo.")
        }
        gestor.iniciar(Sesion(r.accessToken, vence, r.user), revision)
        return r.user
    }

    override suspend fun registrar(nombre: String, correo: String, metodo: String, secreto: String): UserDto =
        llamar { api.registrar(RegistroRequest(nombre.trim(), correo.trim(), metodo, secreto)) }.data

    override suspend fun yo(): UserDto {
        val token = tokenActual()
        return llamar { api.yo() }.data.also { gestor.actualizarUsuario(token, it) }
    }

    /** El trabajo remoto usa el token capturado y no depende del ViewModel que acaba de salir. */
    override fun salir() {
        val anterior = gestor.cerrar() ?: return
        ambitoAplicacion.launch {
            intentarOperacion { llamarSinCuerpo { api.salir("Bearer ${anterior.token}") } }
        }
    }

    private fun tokenActual() = gestor.estado.value.sesion?.token
        ?: throw kotlinx.coroutines.CancellationException("No hay sesion activa.")
}
