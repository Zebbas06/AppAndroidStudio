package com.residenciaguadalupe.news.auth

import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.red.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccesoViewModelTest : PruebaAutenticacion() {
    private fun modelo() = conservar(AccesoViewModel(auth, gestor))
    private fun entrar(vm: AccesoViewModel) = vm.entrar("qa@example.invalid", "password", "secreto")

    @Test fun `credenciales invalidas no llaman repositorio`() = runTest {
        val vm = modelo(); runCurrent()
        vm.entrar("sin-correo", "password", "secreto")
        assertNotNull(vm.estado.value.error)
        vm.registrar("", "qa@example.invalid", "password", "secreto123")
        runCurrent()
        assertEquals(0, auth.entradas); assertEquals(0, auth.registros)
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun `login acepta contrasena existente corta y evita doble envio`() = runTest {
        val respuesta = CompletableDeferred<UserDto>()
        auth.login = { respuesta.await() }
        val vm = modelo(); runCurrent()
        vm.entrar("qa@example.invalid", "password", "abc")
        entrar(vm); runCurrent()
        assertEquals(1, auth.entradas)
        assertTrue(vm.estado.value.cargando)
        respuesta.complete(usuario); runCurrent()
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun `registro confirmado muestra espera sin instalar sesion`() = runTest {
        val vm = modelo(); runCurrent()
        vm.registrar("Nombre QA", "qa@example.invalid", "password", "secreto123"); runCurrent()
        assertTrue(vm.estado.value.registroListo)
        assertNull(gestor.estado.value.sesion)
        assertEquals(1, auth.registros)
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun `cuenta pendiente se distingue de error y no inicia sesion`() = runTest {
        auth.login = { throw ErrorApi(403, "ACCOUNT_PENDING", "Pendiente.") }
        val vm = modelo(); runCurrent(); entrar(vm); runCurrent()
        assertTrue(vm.estado.value.pendiente)
        assertNull(vm.estado.value.error)
        assertNull(gestor.estado.value.sesion)
        vm.limpiarAcceso()
        assertEquals(EstadoAcceso(), vm.estado.value)
    }
    @Test fun `fallo de red permite reintentar sin conservar error anterior`() = runTest {
        auth.login = { throw ErrorApi(0, "NETWORK", "Sin conexion.") }
        val vm = modelo(); runCurrent(); entrar(vm); runCurrent()
        assertEquals("Sin conexion.", vm.estado.value.error)
        auth.login = { usuario }
        entrar(vm); runCurrent()
        assertNull(vm.estado.value.error)
        assertEquals(2, auth.entradas)
    }
    @Test fun `cambiar formulario ignora respuesta de registro anterior`() = runTest {
        val respuesta = CompletableDeferred<UserDto>()
        auth.registro = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); runCurrent()
        vm.registrar("Nombre QA", "qa@example.invalid", "pin", "1234"); runCurrent()
        vm.limpiarAcceso(); respuesta.complete(usuario.copy(status = "pending")); runCurrent()
        assertEquals(EstadoAcceso(), vm.estado.value)
    }
    @Test fun `fallo tardio no detiene otra peticion`() = runTest {
        val vieja = CompletableDeferred<UserDto>()
        val nueva = CompletableDeferred<UserDto>()
        auth.login = { if (auth.entradas == 1) withContext(NonCancellable) { vieja.await() } else nueva.await() }
        val vm = modelo(); runCurrent(); entrar(vm); runCurrent()
        vm.limpiarAcceso(); entrar(vm); runCurrent()
        vieja.completeExceptionally(ErrorApi(0, "NETWORK", "Fallo viejo.")); runCurrent()
        assertTrue(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
        nueva.complete(usuario); runCurrent()
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun `inicio confirmado limpia estado al cambiar revision`() = runTest {
        auth.login = {
            gestor.iniciar(Sesion("token", Instant.now().plusSeconds(3600), usuario))
            usuario
        }
        val vm = modelo(); runCurrent(); entrar(vm); runCurrent()
        assertEquals(usuario, gestor.estado.value.sesion!!.usuario)
        assertEquals(EstadoAcceso(), vm.estado.value)
    }
    @Test fun `otra cuenta impide que registro tardio muestre confirmacion`() = runTest {
        val respuesta = CompletableDeferred<UserDto>()
        auth.registro = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); runCurrent()
        vm.registrar("Nombre QA", "qa@example.invalid", "pin", "1234"); runCurrent()
        gestor.iniciar(Sesion("otra", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent(); respuesta.complete(usuario); runCurrent()
        assertFalse(vm.estado.value.registroListo)
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun `motivo de cierre se muestra al crear acceso y al invalidar`() = runTest {
        gestor.cerrar("Sesion terminada.")
        val vm = modelo(); runCurrent()
        assertEquals("Sesion terminada.", vm.estado.value.aviso)
        gestor.iniciar(Sesion("token", Instant.now().plusSeconds(3600), usuario)); runCurrent()
        gestor.invalidar("token", "Token vencido."); runCurrent()
        assertEquals("Token vencido.", vm.estado.value.aviso)
    }
    @Test fun `estado no retiene secretos ni permite login con cuenta activa`() = runTest {
        val vm = modelo(); runCurrent()
        vm.registrar("Nombre QA", "qa@example.invalid", "password", "secreto123"); runCurrent()
        assertFalse(vm.estado.value.toString().contains("secreto123"))
        gestor.iniciar(Sesion("token", Instant.now().plusSeconds(3600), usuario)); runCurrent()
        entrar(vm); runCurrent()
        assertEquals(0, auth.entradas)
    }
}
