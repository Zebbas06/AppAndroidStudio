package com.residenciaguadalupe.news.publications

import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.publications.archive.ArchivoViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivoViewModelTest : PruebaPublicaciones() {
    @Test fun `archivo consulta anteriores a hoy no status archived`() = runTest {
        val dia = LocalDate.of(2026, 10, 5)
        val vm = conservar(ArchivoViewModel(repo, gestor) { dia })
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        val f = repo.consultas.single().first
        assertEquals(Fechas.inicioDelDia(dia), f.publicadasAntesDe)
        assertEquals("-publishedAt", f.orden)
        assertNull(f.estado)
    }

    @Test fun `agrupa por mes y conserva publicaciones sin fecha`() = runTest {
        val octubre = publicacionPrueba("1").copy(publicadaEn = Fechas.local("2026-10-04T18:00:00Z"))
        val septiembre = publicacionPrueba("2").copy(publicadaEn = Fechas.local("2026-09-30T18:00:00Z"))
        repo.listar = { _, p, l -> Pagina(listOf(octubre, septiembre, publicacionPrueba("3")), p, l, 3) }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.cargarArchivo(true)
        runCurrent()
        assertEquals(listOf("1", "2", "3"), vm.estado.value.meses.flatMap { it.entradas }.map { it.postId })
        assertEquals(3, vm.estado.value.meses.size)
        assertEquals("Sin fecha", vm.estado.value.meses.last().mes)
        assertTrue(vm.estado.value.meses.first().entradas.single().meta.contains("Aviso"))
    }

    @Test fun `paginacion une un mismo mes sin duplicar ids`() = runTest {
        val fecha = Fechas.local("2026-10-04T18:00:00Z")
        repo.listar = { _, p, l ->
            assertEquals(30, l)
            val ids = if (p == 1) listOf("1", "2") else listOf("2", "3")
            Pagina(ids.map { publicacionPrueba(it).copy(publicadaEn = fecha) }, p, l, 31)
        }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.cargarArchivo(true)
        runCurrent()
        vm.cargarArchivo(false)
        vm.cargarArchivo(false)
        runCurrent()
        assertEquals(listOf(1, 2), repo.consultas.map { it.second })
        assertEquals(listOf("1", "2", "3"), vm.estado.value.meses.single().entradas.map { it.postId })
        assertFalse(vm.estado.value.hayMas)
    }

    @Test fun `fallo de pagina no pierde archivo ni adelanta contador`() = runTest {
        var fallar = true
        repo.listar = { _, p, l ->
            if (p == 2 && fallar) throw ErrorApi(-1, "NETWORK", "Sin conexion.")
            Pagina(listOf(publicacionPrueba(p.toString())), p, l, 31)
        }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.cargarArchivo(true)
        runCurrent()
        vm.cargarArchivo(false)
        runCurrent()
        assertEquals(1, vm.estado.value.publicaciones.size)
        assertNotNull(vm.estado.value.errorMas)
        assertNull(vm.estado.value.error)
        fallar = false
        vm.cargarArchivo(false)
        runCurrent()
        assertEquals(listOf(1, 2, 2), repo.consultas.map { it.second })
        assertNull(vm.estado.value.errorMas)
        assertFalse(vm.estado.value.cargandoMas)
    }

    @Test fun `recarga descarta pagina pendiente y su finally`() = runTest {
        val vieja = CompletableDeferred<Pagina<Publicacion>>()
        val nueva = CompletableDeferred<Pagina<Publicacion>>()
        var inicial = true
        repo.listar = { _, p, l ->
            if (p == 2) withContext(NonCancellable) { vieja.await() }
            else if (inicial) { inicial = false; Pagina(listOf(publicacionPrueba("1")), p, l, 31) }
            else nueva.await()
        }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.cargarArchivo(true)
        runCurrent()
        vm.cargarArchivo(false)
        runCurrent()
        vm.cargarArchivo(true)
        runCurrent()
        vieja.complete(Pagina(listOf(publicacionPrueba("vieja")), 2, 30, 31))
        runCurrent()
        assertTrue(vm.estado.value.publicaciones.isEmpty())
        assertTrue(vm.estado.value.cargando)
        nueva.complete(Pagina(listOf(publicacionPrueba("nueva")), 1, 30, 1))
        runCurrent()
        assertEquals("nueva", vm.estado.value.publicaciones.single().id)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun `volver de detalle conserva paginas y cambio administrativo recarga`() = runTest {
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba(p.toString())), p, l, 31) }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        vm.cargarArchivo(false)
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(2, repo.consultas.size)
        repo.cambios.value++
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(listOf(1, 2, 1), repo.consultas.map { it.second })
        assertEquals("1", vm.estado.value.publicaciones.single().id)
    }

    @Test fun `nuevo dia reinicia paginas y actualiza corte temporal`() = runTest {
        var dia = LocalDate.of(2026, 10, 5)
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 31) }
        val vm = conservar(ArchivoViewModel(repo, gestor) { dia })
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        dia = dia.plusDays(1)
        vm.cargarArchivo(false)
        runCurrent()
        assertEquals(listOf(1, 1), repo.consultas.map { it.second })
        assertEquals(Fechas.inicioDelDia(dia), repo.consultas.last().first.publicadasAntesDe)
    }

    @Test fun `cierre limpia contenido errores y evita nueva consulta sin sesion`() = runTest {
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 31) }
        val vm = conservar(ArchivoViewModel(repo, gestor))
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        gestor.cerrar()
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertTrue(vm.estado.value.publicaciones.isEmpty())
        assertFalse(vm.estado.value.hayMas)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
        assertEquals(1, repo.consultas.size)
    }
}
