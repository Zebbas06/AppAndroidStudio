package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.core.session.Sesion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SesionTest {
    @Test
    fun `la sesion vence exactamente en el instante declarado`() {
        val vencimiento = Instant.parse("2026-10-05T20:00:00Z")
        val usuario = UserDto("6", "Residente QA", "r@example.invalid", "resident", "active", "pin")
        val sesion = Sesion("token-de-prueba", vencimiento, usuario)

        assertFalse(sesion.vencida(vencimiento.minusNanos(1)))
        assertTrue(sesion.vencida(vencimiento))
        assertTrue(sesion.vencida(vencimiento.plusNanos(1)))
    }
}
