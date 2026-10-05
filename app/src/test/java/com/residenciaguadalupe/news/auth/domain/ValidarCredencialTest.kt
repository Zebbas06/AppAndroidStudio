package com.residenciaguadalupe.news.auth.domain

import org.junit.Assert.*
import org.junit.Test

class ValidarCredencialTest {
    @Test fun `login no aplica longitud minima de una credencial nueva`() {
        assertNull(validarCredencial("password", "a", nueva = false))
        assertNotNull(validarCredencial("password", "a", nueva = true))
    }

    @Test fun `password nueva respeta ambos limites`() {
        assertNotNull(validarCredencial("password", "a".repeat(7), nueva = true))
        assertNull(validarCredencial("password", "a".repeat(8), nueva = true))
        assertNull(validarCredencial("password", "a".repeat(128), nueva = true))
        assertNotNull(validarCredencial("password", "a".repeat(129), nueva = true))
    }

    @Test fun `pin y metodo se validan igual para perfil y registro`() {
        assertNotNull(validarCredencial("pin", "123", nueva = true))
        assertNull(validarCredencial("pin", "1234", nueva = true))
        assertNull(validarCredencial("pin", "123456", nueva = true))
        assertNotNull(validarCredencial("pin", "1234567", nueva = true))
        assertNotNull(validarCredencial("pin", "12ab", nueva = true))
        assertNotNull(validarCredencial("otro", "12345678", nueva = true))
        assertNotNull(validarCredencial("password", "", nueva = false))
    }
}
