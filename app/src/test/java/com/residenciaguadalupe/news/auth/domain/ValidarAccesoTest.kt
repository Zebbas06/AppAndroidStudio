package com.residenciaguadalupe.news.auth.domain

import org.junit.Assert.*
import org.junit.Test

class ValidarAccesoTest {
    @Test fun `registro exige nombre y correo valido`() {
        assertNotNull(validarAcceso(null, "qa@example.invalid", "password", "secreto123", true))
        assertNotNull(validarAcceso("A", "qa@example.invalid", "password", "secreto123", true))
        assertNotNull(validarAcceso("Nombre", "sin correo", "password", "secreto123", true))
        assertNull(validarAcceso(" Nombre ", " qa@example.invalid ", "password", "secreto123", true))
    }
    @Test fun `login no impone minimo nuevo a contrasena existente`() {
        assertNull(validarAcceso(null, "qa@example.invalid", "password", "abc", false))
        assertNotNull(validarAcceso("Nombre", "qa@example.invalid", "password", "abc", true))
    }
    @Test fun `pin valida digitos y longitud en ambos flujos`() {
        assertNull(validarAcceso(null, "qa@example.invalid", "pin", "1234", false))
        assertNotNull(validarAcceso(null, "qa@example.invalid", "pin", "12a4", false))
        assertNotNull(validarAcceso("Nombre", "qa@example.invalid", "pin", "123", true))
    }
    @Test fun `metodo desconocido y secreto vacio se rechazan`() {
        assertNotNull(validarAcceso(null, "qa@example.invalid", "otro", "secreto123", false))
        assertNotNull(validarAcceso(null, "qa@example.invalid", "password", "", false))
    }
}
