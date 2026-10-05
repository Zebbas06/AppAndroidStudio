package com.residenciaguadalupe.news.app

import com.residenciaguadalupe.news.auth.PruebaAutenticacion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.red.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ArranqueViewModelTest : PruebaAutenticacion() {
    private fun iniciar(vence: Instant = Instant.now().plusSeconds(3600)) = gestor.iniciar(Sesion("token", vence, usuario))
    private fun modelo() = conservar(ArranqueViewModel(auth, gestor))

    @Test fun `sin sesion termina arranque sin consultar red`() = runTest {
        val vm = modelo(); runCurrent()
        assertFalse(vm.estado.value.restaurando)
        assertNull(vm.estado.value.usuario)
        assertEquals(0, auth.consultas)
    }
    @Test fun `sesion vencida se borra antes de consultar`() = runTest {
        iniciar(Instant.now().minusSeconds(1))
        val vm = modelo(); runCurrent()
        assertNull(gestor.estado.value.sesion)
        assertNull(vm.estado.value.usuario)
        assertFalse(vm.estado.value.restaurando)
        assertEquals(0, auth.consultas)
    }
    @Test fun `espera validacion y observa usuario fresco del gestor`() = runTest {
        iniciar()
        val respuesta = CompletableDeferred<UserDto>()
        auth.consultar = { respuesta.await().also { gestor.actualizarUsuario("token", it) } }
        val vm = modelo(); runCurrent()
        assertTrue(vm.estado.value.restaurando)
        respuesta.complete(usuario.copy(fullName = "Nombre actualizado")); runCurrent()
        assertFalse(vm.estado.value.restaurando)
        assertEquals("Nombre actualizado", vm.estado.value.usuario!!.fullName)
    }
    @Test fun `sin red conserva sesion guardada y termina carga`() = runTest {
        iniciar(); auth.consultar = { throw ErrorApi(0, "NETWORK", "Sin conexion.") }
        val vm = modelo(); runCurrent()
        assertEquals(usuario, vm.estado.value.usuario)
        assertEquals("token", gestor.estado.value.sesion!!.token)
        assertFalse(vm.estado.value.restaurando)
    }
    @Test fun `403 invalida cuenta deshabilitada`() = runTest {
        iniciar(); auth.consultar = { throw ErrorApi(403, "ACCOUNT_DISABLED", "Cuenta deshabilitada.") }
        val vm = modelo(); runCurrent()
        assertNull(vm.estado.value.usuario)
        assertEquals("Cuenta deshabilitada.", gestor.estado.value.motivo)
        assertFalse(vm.estado.value.restaurando)
    }
    @Test fun `401 no deja arranque bloqueado incluso sin interceptor falso`() = runTest {
        iniciar(); auth.consultar = { throw ErrorApi(401, "UNAUTHORIZED", "Sesion terminada.") }
        val vm = modelo(); runCurrent()
        assertNull(vm.estado.value.usuario)
        assertFalse(vm.estado.value.restaurando)
    }
    @Test fun `respuesta tardia no reemplaza cuenta nueva`() = runTest {
        iniciar()
        val respuesta = CompletableDeferred<UserDto>()
        auth.consultar = {
            withContext(NonCancellable) { respuesta.await() }.also { gestor.actualizarUsuario("token", it) }
        }
        val vm = modelo(); runCurrent()
        gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8"))); runCurrent()
        respuesta.complete(usuario.copy(fullName = "Usuario viejo")); runCurrent()
        assertEquals("8", vm.estado.value.usuario!!.id)
        assertEquals(gestor.estado.value.revision, vm.estado.value.revisionSesion)
        assertFalse(vm.estado.value.restaurando)
    }
    @Test fun `cerrar sesion se delega y actualiza raiz`() = runTest {
        iniciar(); auth.salir = { gestor.cerrar() }
        val vm = modelo(); runCurrent()
        vm.cerrarSesion(); runCurrent()
        assertEquals(1, auth.salidas)
        assertNull(vm.estado.value.usuario)
    }
    @Test fun `metadatos de usuario cambian sin inventar revision`() = runTest {
        iniciar(); val vm = modelo(); runCurrent()
        val revision = vm.estado.value.revisionSesion
        gestor.actualizarUsuario("token", usuario.copy(role = "admin")); runCurrent()
        assertEquals("admin", vm.estado.value.usuario!!.role)
        assertEquals(revision, vm.estado.value.revisionSesion)
    }

    @Test fun `cambiar cuenta antes de iniciar tarea no deja restauracion bloqueada`() = runTest {
        iniciar()
        val vm = modelo()
        gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent()
        assertFalse(vm.estado.value.restaurando)
        assertEquals("8", vm.estado.value.usuario!!.id)
        assertEquals(0, auth.consultas)
    }
}
