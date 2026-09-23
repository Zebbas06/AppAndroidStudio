package com.residenciaguadalupe.news.data.sesion

import android.content.Context
import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Instant

/** Lo que se guarda al entrar: el token, su vencimiento y el ultimo usuario. */
data class Sesion(val token: String, val venceEn: Instant, val usuario: UserDto) {
    fun vencida(ahora: Instant = Instant.now()) = !ahora.isBefore(venceEn)
}

/**
 * Guarda la sesion. La interfaz existe para poder probar el cliente HTTP
 * sin Android (ver SesionEnMemoria en las pruebas).
 */
interface AlmacenSesion {
    val sesion: StateFlow<Sesion?>
    fun guardar(s: Sesion)
    fun actualizarUsuario(u: UserDto)
    /** [motivo] llega a la pantalla de acceso, p. ej. "Su sesión terminó." */
    fun borrar(motivo: String? = null)
    /** Ultimo motivo de cierre no mostrado; se consume al leerlo. */
    fun tomarMotivo(): String?
}

/**
 * Implementacion con SharedPreferences privadas en `sesion_residencia.xml`.
 * Ese archivo esta excluido del respaldo en la nube y de la transferencia
 * entre dispositivos (res/xml/reglas_respaldo.xml y reglas_extraccion.xml),
 * asi que el token no sale del telefono.
 */
class AlmacenSesionPreferencias(contexto: Context, private val json: Json) : AlmacenSesion {

    private val prefs = contexto.getSharedPreferences("sesion_residencia", Context.MODE_PRIVATE)
    private val estado = MutableStateFlow(leer())
    override val sesion: StateFlow<Sesion?> = estado.asStateFlow()

    @Volatile private var motivo: String? = null

    private fun leer(): Sesion? {
        val token = prefs.getString(TOKEN, null) ?: return null
        val vence = prefs.getString(VENCE, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val usuario = prefs.getString(USUARIO, null)?.let {
            runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull()
        }
        if (vence == null || usuario == null) return null
        return Sesion(token, vence, usuario)
    }

    override fun guardar(s: Sesion) {
        prefs.edit()
            .putString(TOKEN, s.token)
            .putString(VENCE, s.venceEn.toString())
            .putString(USUARIO, json.encodeToString(UserDto.serializer(), s.usuario))
            .apply()
        motivo = null
        estado.value = s
    }

    override fun actualizarUsuario(u: UserDto) {
        val actual = estado.value ?: return
        guardar(actual.copy(usuario = u))
    }

    override fun borrar(motivo: String?) {
        prefs.edit().clear().apply()
        if (estado.value != null) this.motivo = motivo
        estado.value = null
    }

    override fun tomarMotivo(): String? = motivo.also { motivo = null }

    private companion object {
        const val TOKEN = "token"
        const val VENCE = "vence_en"
        const val USUARIO = "usuario"
    }
}
