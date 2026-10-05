package com.residenciaguadalupe.news.core.session

import android.content.Context
import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.serialization.json.Json
import java.time.Instant

/** Mantiene el nombre y las claves existentes para conservar las sesiones instaladas. */
class AlmacenSesionPreferencias(contexto: Context, private val json: Json) : AlmacenSesion {
    private val prefs = contexto.getSharedPreferences("sesion_residencia", Context.MODE_PRIVATE)

    override fun leer(): Sesion? {
        val token = prefs.getString(TOKEN, null) ?: return null
        val vence = prefs.getString(VENCE, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val usuario = prefs.getString(USUARIO, null)?.let {
            runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull()
        }
        if (vence == null || usuario == null) return null
        return Sesion(token, vence, usuario)
    }

    override fun guardar(sesion: Sesion) {
        prefs.edit()
            .putString(TOKEN, sesion.token)
            .putString(VENCE, sesion.venceEn.toString())
            .putString(USUARIO, json.encodeToString(UserDto.serializer(), sesion.usuario))
            .apply()
    }

    override fun borrar() { prefs.edit().clear().apply() }

    private companion object {
        const val TOKEN = "token"
        const val VENCE = "vence_en"
        const val USUARIO = "usuario"
    }
}
