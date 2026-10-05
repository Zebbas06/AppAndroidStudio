package com.residenciaguadalupe.news.core.session

import com.residenciaguadalupe.news.data.red.UserDto
import java.time.Instant

data class Sesion(val token: String, val venceEn: Instant, val usuario: UserDto) {
    fun vencida(ahora: Instant = Instant.now()) = !ahora.isBefore(venceEn)
}

/** La revision cambia al entrar o salir, incluso si StateFlow omite un estado intermedio. */
data class EstadoSesion(val sesion: Sesion?, val revision: Long = 0, val motivo: String? = null)
