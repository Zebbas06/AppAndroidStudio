package com.residenciaguadalupe.news.publications

import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.publications.agenda.AgendaViewModel
import com.residenciaguadalupe.news.publications.data.filtroAgenda
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest : PruebaPublicaciones() {
    @Test fun `rangos comparten limite exclusivo y semana lunes domingo`() {
        val hoy = LocalDate.of(2026, 12, 31)
        val manana = filtroAgenda(PestanaAgenda.MANANA, hoy)
        assertEquals(Fechas.inicioDelDia(LocalDate.of(2027, 1, 1)), manana.eventoDesde)
        assertEquals(Fechas.inicioDelDia(LocalDate.of(2027, 1, 2)), manana.eventoHasta)
        val semana = filtroAgenda(PestanaAgenda.SEMANA, hoy)
        assertEquals(Fechas.inicioDelDia(LocalDate.of(2026, 12, 28)), semana.eventoDesde)
        assertEquals(Fechas.inicioDelDia(LocalDate.of(2027, 1, 4)), semana.eventoHasta)
        assertEquals(semana, filtroAgenda(PestanaAgenda.SEMANA, LocalDate.of(2027, 1, 3)))
        assertEquals(TipoPublicacion.EVENTO, semana.tipo)
        assertEquals("eventStartAt", semana.orden)
        assertEquals(Fechas.inicioDelDia(hoy.plusDays(1)), filtroAgenda(PestanaAgenda.HOY, hoy).eventoHasta)
    }

    @Test fun `cambiar pestana limpia filas y descarta respuesta anterior`() = runTest {
        val vieja = CompletableDeferred<Pagina<Publicacion>>()
        val nueva = CompletableDeferred<Pagina<Publicacion>>()
        var llamadas = 0
        repo.listar = { _, _, _ ->
            llamadas++
            when (llamadas) {
                1 -> Pagina(listOf(publicacionPrueba("visible")), 1, 100, 1)
                2 -> withContext(NonCancellable) { vieja.await() }
                else -> nueva.await()
            }
        }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cargarAgenda()
        runCurrent()
        assertEquals("visible", vm.estado.value.actividades.single().postId)
        vm.cargarAgenda()
        runCurrent()
        vm.cambiarPestana(PestanaAgenda.MANANA)
        runCurrent()
        assertTrue(vm.estado.value.actividades.isEmpty())
        vieja.complete(Pagina(listOf(publicacionPrueba("vieja")), 1, 100, 1))
        runCurrent()
        assertTrue(vm.estado.value.cargando)
        nueva.complete(Pagina(listOf(publicacionPrueba("nueva")), 1, 100, 1))
        runCurrent()
        assertEquals(PestanaAgenda.MANANA, vm.estado.value.pestana)
        assertEquals("nueva", vm.estado.value.actividades.single().postId)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun `mas de cien eventos permite paginar sin duplicados`() = runTest {
        repo.listar = { _, p, limite ->
            assertEquals(100, limite)
            Pagina(if (p == 1) listOf(publicacionPrueba("1"), publicacionPrueba("2"))
                else listOf(publicacionPrueba("2"), publicacionPrueba("3")), p, limite, 101)
        }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cargarAgenda()
        runCurrent()
        assertTrue(vm.estado.value.hayMas)
        vm.cargarMas()
        vm.cargarMas()
        runCurrent()
        assertEquals(listOf(1, 2), repo.consultas.map { it.second })
        assertEquals(listOf("1", "2", "3"), vm.estado.value.actividades.map { it.postId })
        assertFalse(vm.estado.value.hayMas)
    }

    @Test fun `fallo de pagina conserva filas y reintenta la misma pagina`() = runTest {
        var fallar = true
        repo.listar = { _, p, l ->
            if (p == 2 && fallar) throw ErrorApi(-1, "NETWORK", "Sin conexion.")
            Pagina(listOf(publicacionPrueba(p.toString())), p, l, 101)
        }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cargarAgenda()
        runCurrent()
        vm.cargarMas()
        runCurrent()
        assertNotNull(vm.estado.value.errorMas)
        assertNull(vm.estado.value.error)
        assertEquals("1", vm.estado.value.actividades.single().postId)
        fallar = false
        vm.cargarMas()
        runCurrent()
        assertEquals(listOf(1, 2, 2), repo.consultas.map { it.second })
        assertNull(vm.estado.value.errorMas)
        assertFalse(vm.estado.value.cargandoMas)
    }

    @Test fun `fallo inicial muestra error y reintento lo limpia`() = runTest {
        repo.listar = { _, _, _ -> throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cargarAgenda()
        runCurrent()
        assertNotNull(vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 1) }
        vm.cargarAgenda()
        runCurrent()
        assertNull(vm.estado.value.error)
        assertEquals(1, vm.estado.value.actividades.size)
    }

    @Test fun `volver conserva datos salvo nuevo dia o escritura administrativa`() = runTest {
        var dia = LocalDate.of(2026, 10, 5)
        val vm = conservar(AgendaViewModel(repo, gestor) { dia })
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(1, repo.consultas.size)
        repo.cambios.value++
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(2, repo.consultas.size)
        dia = dia.plusDays(1)
        vm.refrescarSiHaceFalta()
        runCurrent()
        assertEquals(3, repo.consultas.size)
        assertEquals(Fechas.inicioDelDia(dia), repo.consultas.last().first.eventoDesde)
    }

    @Test fun `paginacion reinicia si cambia la revision de publicaciones`() = runTest {
        repo.listar = { _, p, l -> Pagina(listOf(publicacionPrueba()), p, l, 101) }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cargarAgenda()
        runCurrent()
        repo.cambios.value++
        vm.cargarMas()
        runCurrent()
        assertEquals(listOf(1, 1), repo.consultas.map { it.second })
    }

    @Test fun `cambio de cuenta limpia pestana y descarta tareas pendientes`() = runTest {
        val anterior = CompletableDeferred<Pagina<Publicacion>>()
        repo.listar = { _, _, _ -> withContext(NonCancellable) { anterior.await() } }
        val vm = conservar(AgendaViewModel(repo, gestor))
        runCurrent()
        vm.cambiarPestana(PestanaAgenda.SEMANA)
        runCurrent()
        gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent()
        anterior.complete(Pagina(listOf(publicacionPrueba()), 1, 100, 101))
        runCurrent()
        assertEquals(PestanaAgenda.HOY, vm.estado.value.pestana)
        assertTrue(vm.estado.value.actividades.isEmpty())
        assertFalse(vm.estado.value.cargando)
        assertFalse(vm.estado.value.hayMas)
        assertNull(vm.estado.value.error)
    }
}
