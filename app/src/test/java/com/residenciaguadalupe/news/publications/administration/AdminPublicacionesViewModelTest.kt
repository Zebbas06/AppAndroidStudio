package com.residenciaguadalupe.news.publications.administration

import com.residenciaguadalupe.news.data.Publicacion
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.publications.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminPublicacionesViewModelTest : PruebaPublicaciones() {
    private fun admin() = gestor.actualizarUsuario("token", usuario.copy(role = "admin"))
    private fun modelo() = conservar(AdminPublicacionesViewModel(repo, gestor))

    @Test fun `consulta vieja no reemplaza recarga nueva`() = runTest {
        admin()
        val vieja = CompletableDeferred<Pagina<Publicacion>>()
        repo.listar = { _, p, l -> if (repo.consultas.size == 1) withContext(NonCancellable) { vieja.await() }
            else Pagina(listOf(publicacionPrueba("8")), p, l, 1) }
        val vm = modelo(); vm.cargarAvisos(); runCurrent(); vm.cargarAvisos(); runCurrent()
        vieja.complete(Pagina(listOf(publicacionPrueba()), 1, 20, 1)); runCurrent()
        assertEquals(listOf("8"), vm.avisos.map { it.id })
        assertFalse(vm.listaCargando)
    }
    @Test fun `archivar y deshacer restaura fijado en orden`() = runTest {
        admin()
        val vm = modelo(); runCurrent()
        vm.alternarArchivado(publicacionPrueba().copy(fijada = true)); runCurrent()
        assertTrue(vm.puedeDeshacer)
        vm.deshacer(); runCurrent()
        assertEquals(listOf("7" to false, "7" to true), repo.estados)
        assertEquals(listOf("7" to true), repo.fijados)
        assertTrue(vm.avisos.single().fijada)
    }
    @Test fun `acciones y deshacer no se superponen`() = runTest {
        admin()
        val respuesta = CompletableDeferred<Publicacion>()
        repo.cambiarEstado = { _, _ -> respuesta.await() }
        val vm = modelo(); runCurrent()
        vm.alternarArchivado(publicacionPrueba()); runCurrent()
        vm.alternarFijado(publicacionPrueba("8")); vm.deshacer(); runCurrent()
        assertTrue(repo.fijados.isEmpty())
        assertEquals("7", vm.procesando)
        respuesta.complete(publicacionPrueba().copy(estado = "archived")); runCurrent()
        assertNull(vm.procesando)
    }
    @Test fun `permisos bloquean residente y acciones admin del empleado`() = runTest {
        val vm = modelo(); runCurrent()
        vm.cargarAvisos(); vm.alternarArchivado(publicacionPrueba()); vm.alternarFijado(publicacionPrueba())
        runCurrent(); assertTrue(repo.consultas.isEmpty()); assertTrue(repo.estados.isEmpty())
        gestor.actualizarUsuario("token", usuario.copy(role = "employee")); runCurrent()
        vm.alternarFijado(publicacionPrueba()); vm.pedirEliminar(publicacionPrueba())
        vm.eliminarConfirmado(); vm.alternarArchivado(publicacionPrueba()); runCurrent()
        assertTrue(repo.fijados.isEmpty()); assertTrue(repo.eliminados.isEmpty()); assertTrue(repo.estados.isEmpty())
    }
    @Test fun `salir descarta accion tardia y ventana de deshacer`() = runTest {
        admin()
        val respuesta = CompletableDeferred<Publicacion>()
        repo.fijar = { _, _ -> withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); runCurrent()
        vm.alternarFijado(publicacionPrueba()); runCurrent()
        gestor.cerrar(); runCurrent()
        respuesta.complete(publicacionPrueba().copy(fijada = true)); runCurrent()
        assertTrue(vm.avisos.isEmpty())
        assertNull(vm.mensaje); assertNull(vm.procesando); assertFalse(vm.puedeDeshacer)
    }
    @Test fun `eliminar requiere confirmar y pagina siguiente reinicia offsets`() = runTest {
        admin()
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 50) }
        val vm = modelo(); vm.cargarAvisos(); runCurrent()
        vm.eliminarConfirmado(); runCurrent(); assertTrue(repo.eliminados.isEmpty())
        vm.pedirEliminar(publicacionPrueba()); vm.eliminarConfirmado(); runCurrent()
        assertEquals(listOf("7"), repo.eliminados)
        assertTrue(vm.avisos.isEmpty())
        vm.cargarAvisos(reiniciar = false); runCurrent()
        assertEquals(listOf(1, 1), repo.consultas.map { it.second })
    }

    @Test fun `deshacer parcial refleja republicacion aunque volver a fijar falle`() = runTest {
        admin()
        val vm = modelo(); runCurrent()
        vm.alternarArchivado(publicacionPrueba().copy(fijada = true)); runCurrent()
        repo.fijar = { _, _ -> throw com.residenciaguadalupe.news.data.red.ErrorApi(500, "ERROR", "Fallo al fijar.") }
        vm.deshacer(); runCurrent()
        assertEquals("published", vm.avisos.single().estado)
        assertFalse(vm.avisos.single().fijada)
        assertEquals("Fallo al fijar.", vm.mensaje)
    }

    @Test fun `escritura cancela lista vieja para no deshacer estado confirmado`() = runTest {
        admin()
        val respuesta = CompletableDeferred<Pagina<Publicacion>>()
        repo.listar = { _, _, _ -> withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.cargarAvisos(); runCurrent()
        vm.alternarFijado(publicacionPrueba()); runCurrent()
        respuesta.complete(Pagina(listOf(publicacionPrueba()), 1, 20, 1)); runCurrent()
        assertTrue(vm.avisos.single().fijada)
        assertFalse(vm.listaCargando)
    }
}
