package com.residenciaguadalupe.news.publications

import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.publications.feed.MuroViewModel
import com.residenciaguadalupe.news.publications.data.CambioPublicacion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MuroViewModelTest : PruebaPublicaciones() {
    @Test fun `fotografias filtra por imagen y no por categoria`() = runTest {
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cambiarFiltro("FOTO")
        runCurrent()
        assertEquals(true, repo.consultas.single().first.conImagen)
        assertNull(repo.consultas.single().first.categoria)
        assertEquals("FOTO", vm.estado.value.filtro)
    }

    @Test fun `pagina siguiente no duplica publicaciones y termina en total`() = runTest {
        repo.listar = { _, p, _ -> Pagina(
            if (p == 1) listOf(publicacionPrueba("1"), publicacionPrueba("2"))
            else listOf(publicacionPrueba("2"), publicacionPrueba("3")), p, 2, 3) }
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cargarMuro()
        runCurrent()
        assertTrue(vm.estado.value.muroHayMas)
        vm.cargarMasMuro()
        vm.cargarMasMuro()
        runCurrent()
        assertEquals(listOf("1", "2", "3"), vm.estado.value.publicaciones.map { it.id })
        assertEquals(listOf(1, 2), repo.consultas.map { it.second })
        assertFalse(vm.estado.value.muroHayMas)
        assertFalse(vm.estado.value.cargandoMas)
    }

    @Test fun `filtro nuevo descarta respuesta y finally anteriores`() = runTest {
        val anterior = CompletableDeferred<Pagina<Publicacion>>()
        val nuevo = CompletableDeferred<Pagina<Publicacion>>()
        repo.listar = { f, _, _ ->
            if (f.categoria == null) withContext(NonCancellable) { anterior.await() } else nuevo.await()
        }
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cargarMuro()
        runCurrent()
        vm.cambiarFiltro("AVISO")
        runCurrent()
        anterior.complete(Pagina(listOf(publicacionPrueba("vieja")), 1, 20, 1))
        runCurrent()
        assertTrue(vm.estado.value.cargando)
        assertTrue(vm.estado.value.publicaciones.isEmpty())
        nuevo.complete(Pagina(listOf(publicacionPrueba("nueva")), 1, 20, 1))
        runCurrent()
        assertEquals("nueva", vm.estado.value.publicaciones.single().id)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun `error de banda no se presenta como dia sin actividades`() = runTest {
        repo.listar = { _, _, _ -> throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cargarBandaHoy()
        runCurrent()
        assertNotNull(vm.estado.value.bandaError)
        assertFalse(vm.estado.value.bandaCargando)
    }

    @Test fun `muro recibe lectura y reaccion sin acoplarse al detalle`() = runTest {
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 1) }
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cargarMuro()
        runCurrent()
        repo.actualizaciones.emit(CambioPublicacion.Leida("7"))
        repo.actualizaciones.emit(CambioPublicacion.Reaccion("7", Reacciones(total = 1, mia = "love")))
        runCurrent()
        assertTrue(vm.estado.value.publicaciones.single().leida)
        assertEquals("love", vm.estado.value.publicaciones.single().reacciones.mia)
    }

    @Test fun `volver recarga despues de una escritura administrativa`() = runTest {
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(2, repo.consultas.size)
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(2, repo.consultas.size)
        repo.cambios.value++
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(4, repo.consultas.size)
    }

    @Test fun `cambio de cuenta limpia filtros listas errores y paginacion`() = runTest {
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 100) }
        val vm = conservar(MuroViewModel(repo, gestor))
        runCurrent()
        vm.cambiarFiltro("FOTO")
        runCurrent()
        gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent()
        assertEquals("TODO", vm.estado.value.filtro)
        assertTrue(vm.estado.value.publicaciones.isEmpty())
        assertFalse(vm.estado.value.muroHayMas)
        assertNull(vm.estado.value.muroError)
        assertEquals("8", vm.estado.value.usuario!!.id)
    }
}
