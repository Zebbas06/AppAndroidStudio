package com.residenciaguadalupe.news.core.session

import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GestorSesionTest {
    private fun sesion(token: String = "token-a") = Sesion(token,
        Instant.parse("2030-01-01T00:00:00Z"),
        UserDto(token, "Usuario QA", "qa@example.invalid", "resident", "active", "pin"))

    @Test
    fun `restaura la sesion persistida sin reemplazarla`() {
        val guardada = sesion()
        val gestor = GestorSesion(SesionEnMemoria(guardada))
        assertEquals(guardada, gestor.estado.value.sesion)
        assertEquals(0L, gestor.estado.value.revision)
    }

    @Test
    fun `cerrar borra persistencia limpia privados y publica el motivo`() {
        val almacen = SesionEnMemoria(sesion())
        var limpiezas = 0
        val gestor = GestorSesion(almacen) { limpiezas++ }
        val anterior = gestor.cerrar("Sesion terminada")
        assertEquals("token-a", anterior!!.token)
        assertNull(almacen.leer())
        assertNull(gestor.estado.value.sesion)
        assertEquals("Sesion terminada", gestor.estado.value.motivo)
        assertEquals(1, limpiezas)
    }

    @Test
    fun `invalidar token anterior no borra ni limpia la cuenta nueva`() {
        var limpiezas = 0
        val gestor = GestorSesion(SesionEnMemoria(sesion())) { limpiezas++ }
        gestor.iniciar(sesion("token-b"))
        assertFalse(gestor.invalidar("token-a", "Vencida"))
        assertEquals("token-b", gestor.estado.value.sesion!!.token)
        assertEquals(1, limpiezas)
    }

    @Test
    fun `un perfil tardio no se guarda en la cuenta nueva`() {
        val gestor = GestorSesion(SesionEnMemoria(sesion()))
        gestor.iniciar(sesion("token-b"))
        assertThrows(CancellationException::class.java) {
            gestor.actualizarUsuario("token-a", sesion().usuario.copy(fullName = "Nombre anterior"))
        }
        assertEquals(sesion("token-b").usuario, gestor.estado.value.sesion!!.usuario)
    }

    @Test
    fun `perfil propio conserva la revision y el token`() {
        val gestor = GestorSesion(SesionEnMemoria(sesion()))
        gestor.actualizarUsuario("token-a", sesion().usuario.copy(fullName = "Nombre actualizado"))
        assertEquals(0L, gestor.estado.value.revision)
        assertEquals("token-a", gestor.estado.value.sesion!!.token)
        assertEquals("Nombre actualizado", gestor.estado.value.sesion!!.usuario.fullName)
    }

    @Test
    fun `login tardio no reemplaza otra sesion`() {
        val gestor = GestorSesion(SesionEnMemoria())
        val revision = gestor.estado.value.revision
        gestor.iniciar(sesion("token-b"))
        assertThrows(CancellationException::class.java) { gestor.iniciar(sesion(), revision) }
        assertEquals("token-b", gestor.estado.value.sesion!!.token)
    }

    @Test
    fun `cerrar cancela operaciones y no ejecuta su continuacion`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria(sesion()))
        var respuestaAplicada = false
        var finalizado = false
        val trabajo = gestor.lanzar(this) {
            try {
                intentarOperacion { awaitCancellation() }.onFailure { respuestaAplicada = true }
            } finally { finalizado = true }
        }
        runCurrent()
        gestor.cerrar()
        runCurrent()
        assertTrue(trabajo.isCancelled)
        assertTrue(finalizado)
        assertFalse(respuestaAplicada)
    }

    @Test
    fun `una tarea en cola no empieza despues de cambiar de cuenta`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria(sesion()))
        var ejecutada = false
        val trabajo = gestor.lanzar(this) { ejecutada = true }
        gestor.iniciar(sesion("token-b"))
        runCurrent()
        assertTrue(trabajo.isCancelled)
        assertFalse(ejecutada)
    }

    @Test
    fun `un archivo anterior no puede publicarse despues del cierre`() {
        val gestor = GestorSesion(SesionEnMemoria(sesion()))
        gestor.cerrar()
        var publicado = false
        assertThrows(CancellationException::class.java) {
            gestor.conSesionActual("token-a") { publicado = true }
        }
        assertFalse(publicado)
    }
}
