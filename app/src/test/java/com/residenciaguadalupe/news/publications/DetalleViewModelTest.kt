package com.residenciaguadalupe.news.publications

import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.publications.detail.DetalleViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModelTest : PruebaPublicaciones() {
    private fun modelo(descargar: suspend (Adjunto) -> File = { error("No esperado") }) =
        conservar(DetalleViewModel(repo, gestor, descargar))

    @Test fun `residente registra lectura una sola vez al abrir`() = runTest {
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        assertEquals(listOf("7"), repo.lecturas)
        assertTrue(vm.estado.value.detalle!!.leida)
    }

    @Test fun `personal no registra lectura ni envia reacciones`() = runTest {
        gestor.iniciar(Sesion("admin", Instant.now().plusSeconds(3600), usuario.copy(role = "admin")))
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        vm.reaccionar("like")
        runCurrent()
        assertTrue(repo.lecturas.isEmpty())
        assertTrue(repo.reacciones.isEmpty())
        assertFalse(vm.estado.value.esResidente)
    }

    @Test fun `detalle A tardio no reemplaza B ni apaga su carga`() = runTest {
        val a = CompletableDeferred<Publicacion>()
        val b = CompletableDeferred<Publicacion>()
        repo.obtener = { id -> if (id == "A") withContext(NonCancellable) { a.await() } else b.await() }
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("A")
        runCurrent()
        vm.abrirDetalle("B")
        runCurrent()
        a.complete(publicacionPrueba("A"))
        runCurrent()
        assertNull(vm.estado.value.detalle)
        assertTrue(vm.estado.value.detalleCargando)
        b.complete(publicacionPrueba("B"))
        runCurrent()
        assertEquals("B", vm.estado.value.detalle!!.id)
        assertEquals(listOf("B"), repo.lecturas)
        assertFalse(vm.estado.value.detalleCargando)
    }

    @Test fun `reaccion repetida quita la seleccion`() = runTest {
        repo.obtener = { publicacionPrueba(it, leida = true).copy(reacciones = Reacciones(total = 1, mia = "like")) }
        repo.reaccion = { _, _ -> Reacciones() }
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        vm.reaccionar("like")
        runCurrent()
        assertEquals(listOf("7" to null), repo.reacciones)
        assertNull(vm.estado.value.detalle!!.reacciones.mia)
        assertFalse(vm.estado.value.reaccionando)
    }

    @Test fun `reaccion tardia de A no modifica B`() = runTest {
        repo.obtener = { publicacionPrueba(it, leida = true) }
        val reaccion = CompletableDeferred<Reacciones>()
        repo.reaccion = { _, _ -> withContext(NonCancellable) { reaccion.await() } }
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("A")
        runCurrent()
        vm.reaccionar("love")
        runCurrent()
        vm.abrirDetalle("B")
        runCurrent()
        reaccion.complete(Reacciones(total = 1, mia = "love"))
        runCurrent()
        assertEquals("B", vm.estado.value.detalle!!.id)
        assertEquals(Reacciones(), vm.estado.value.detalle!!.reacciones)
    }

    @Test fun `salir del detalle cancela la apertura del PDF`() = runTest {
        val adjunto = Adjunto("pdf", "/pdf", "application/pdf", "Aviso.pdf", "", 12)
        repo.obtener = { publicacionPrueba(it, leida = true, adjunto = adjunto) }
        val archivo = CompletableDeferred<File>()
        var abierto = false
        val vm = modelo { withContext(NonCancellable) { archivo.await() } }
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        vm.abrirAdjunto(adjunto) { abierto = true }
        runCurrent()
        vm.cerrarDetalle()
        archivo.complete(File("no-se-abre.pdf"))
        runCurrent()
        assertFalse(abierto)
        assertNull(vm.estado.value.detalle)
        assertFalse(vm.estado.value.abriendoAdjunto)
    }

    @Test fun `error al registrar lectura conserva contenido y ofrece aviso`() = runTest {
        repo.leer = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        assertNotNull(vm.estado.value.detalle)
        assertFalse(vm.estado.value.detalle!!.leida)
        assertNull(vm.estado.value.detalleError)
        assertNotNull(vm.estado.value.mensaje)
    }

    @Test fun `cierre de sesion borra contenido y acciones pendientes`() = runTest {
        val vm = modelo()
        runCurrent()
        vm.abrirDetalle("7")
        runCurrent()
        gestor.cerrar()
        runCurrent()
        assertNull(vm.estado.value.detalle)
        assertNull(vm.estado.value.mensaje)
        assertFalse(vm.estado.value.esResidente)
        assertFalse(vm.estado.value.detalleCargando)
    }
}
