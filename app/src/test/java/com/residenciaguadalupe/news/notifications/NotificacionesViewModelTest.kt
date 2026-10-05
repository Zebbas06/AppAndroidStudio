package com.residenciaguadalupe.news.notifications

import androidx.lifecycle.ViewModelStore
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.notifications.data.*
import com.residenciaguadalupe.news.publications.data.CambioPublicacion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificacionesViewModelTest {
    private val modelos = ViewModelStore()
    private lateinit var gestor: GestorSesion
    private lateinit var repo: NotificacionesFalsas
    private val publicaciones = MutableSharedFlow<CambioPublicacion>()
    private val usuario = UserDto("6", "QA", "qa@example.invalid", "resident", "active", "password")

    @Before fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
        gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        repo = NotificacionesFalsas()
    }
    @After fun terminar() { modelos.clear(); Dispatchers.resetMain() }
    private fun modelo() = NotificacionesViewModel(repo, gestor, publicaciones).also { modelos.put("buzon", it) }

    @Test fun `insignia carga sin abrir buzon y no repite por actualizar perfil`() = runTest {
        repo.contador = 5
        val vm = modelo()
        runCurrent()
        assertEquals(5, vm.estado.value.sinLeer)
        assertTrue(repo.paginas.isEmpty())
        gestor.actualizarUsuario("token", usuario.copy(fullName = "Otro nombre"))
        runCurrent()
        assertEquals(1, repo.consultasContador)
    }

    @Test fun `personal no consulta ni ejecuta acciones del buzon`() = runTest {
        gestor.iniciar(Sesion("admin", Instant.now().plusSeconds(3600), usuario.copy(role = "admin")))
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        vm.actualizarNoLeidas()
        vm.marcarTodasLeidas()
        vm.archivarLeidas()
        runCurrent()
        assertFalse(vm.estado.value.disponible)
        assertEquals(0, repo.consultasContador)
        assertTrue(repo.paginas.isEmpty())
        assertEquals(0, repo.acciones)
    }

    @Test fun `pagina siguiente no duplica y evita doble peticion`() = runTest {
        repo.listar = { p -> PaginaNotificaciones(Pagina(
            if (p == 1) listOf(notificacion("1"), notificacion("2")) else listOf(notificacion("2"), notificacion("3")),
            p, 2, 3), 3) }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.cargarMasNotificaciones()
        vm.cargarMasNotificaciones()
        runCurrent()
        assertEquals(listOf(1, 2), repo.paginas)
        assertEquals(listOf("1", "2", "3"), vm.estado.value.notificaciones.map { it.id })
        assertFalse(vm.estado.value.hayMas)
    }

    @Test fun `pagina fallida conserva filas y reintenta mismo numero`() = runTest {
        var fallar = true
        repo.listar = { p ->
            if (p == 2 && fallar) throw ErrorApi(-1, "NETWORK", "Sin conexion.")
            PaginaNotificaciones(Pagina(listOf(notificacion(p.toString())), p, 1, 2), 2)
        }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.cargarMasNotificaciones()
        runCurrent()
        assertNotNull(vm.estado.value.errorMas)
        assertEquals("1", vm.estado.value.notificaciones.single().id)
        fallar = false
        vm.cargarMasNotificaciones()
        runCurrent()
        assertEquals(listOf(1, 2, 2), repo.paginas)
        assertNull(vm.estado.value.errorMas)
    }

    @Test fun `lectura fallida navega sin marcar fila ni descontar insignia`() = runTest {
        repo.marcar = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        var destino: String? = null
        vm.abrirNotificacion(vm.estado.value.notificaciones.single()) { destino = it }
        assertEquals("7", destino)
        runCurrent()
        assertFalse(vm.estado.value.notificaciones.single().leida)
        assertEquals(1, vm.estado.value.sinLeer)
        assertNotNull(vm.estado.value.mensaje)
        assertFalse(vm.estado.value.accionando)
    }

    @Test fun `lectura confirmada sincroniza fila contador y evita duplicados`() = runTest {
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        val n = vm.estado.value.notificaciones.single()
        vm.abrirNotificacion(n) {}
        vm.abrirNotificacion(n) {}
        runCurrent()
        assertTrue(vm.estado.value.notificaciones.single().leida)
        assertEquals(0, vm.estado.value.sinLeer)
        assertEquals(1, repo.acciones)
        vm.abrirNotificacion(n) {}
        runCurrent()
        assertEquals(1, repo.acciones)
    }

    @Test fun `leer todas bloquea doble envio y solo confirma tras exito`() = runTest {
        val respuesta = CompletableDeferred<Int>()
        repo.todas = { respuesta.await() }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.marcarTodasLeidas()
        vm.marcarTodasLeidas()
        runCurrent()
        assertTrue(vm.estado.value.accionando)
        assertFalse(vm.estado.value.notificaciones.single().leida)
        respuesta.complete(0)
        runCurrent()
        assertEquals(1, repo.acciones)
        assertTrue(vm.estado.value.notificaciones.single().leida)
        assertEquals(0, vm.estado.value.sinLeer)
    }

    @Test fun `archivar recarga primera pagina porque cambiaron offsets`() = runTest {
        repo.filas = listOf(notificacion("1").copy(leida = true), notificacion("2"))
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.archivarLeidas()
        vm.archivarLeidas()
        runCurrent()
        assertEquals(1, repo.acciones)
        assertEquals(listOf(1, 1), repo.paginas)
        assertEquals("2", vm.estado.value.notificaciones.single().id)
        assertNotNull(vm.estado.value.mensaje)
    }

    @Test fun `lectura de publicacion posterior a snapshot actualiza fila y contador`() = runTest {
        val pendiente = CompletableDeferred<PaginaNotificaciones>()
        repo.listar = { pendiente.await() }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        repo.contador = 0
        publicaciones.emit(CambioPublicacion.Leida("7"))
        pendiente.complete(PaginaNotificaciones(Pagina(listOf(notificacion("1")), 1, 30, 1), 1))
        runCurrent()
        assertTrue(vm.estado.value.notificaciones.single().leida)
        assertEquals(0, vm.estado.value.sinLeer)
    }

    @Test fun `error contador conserva valor y reintento lo recupera`() = runTest {
        val vm = modelo()
        runCurrent()
        repo.contar = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        vm.actualizarNoLeidas()
        runCurrent()
        assertEquals(1, vm.estado.value.sinLeer)
        assertNotNull(vm.estado.value.errorContador)
        repo.contar = { 3 }
        vm.actualizarNoLeidas()
        runCurrent()
        assertEquals(3, vm.estado.value.sinLeer)
        assertNull(vm.estado.value.errorContador)
    }

    @Test fun `snapshot del contador se aplica antes del listado posterior`() = runTest {
        val pendiente = CompletableDeferred<Int>()
        repo.contar = { pendiente.await() }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        assertTrue(repo.paginas.isEmpty())
        pendiente.complete(9)
        runCurrent()
        assertEquals(1, vm.estado.value.sinLeer)
        assertEquals(listOf(1), repo.paginas)
    }

    @Test fun `fallo al leer todas conserva filas y contador`() = runTest {
        repo.todas = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.marcarTodasLeidas()
        runCurrent()
        assertFalse(vm.estado.value.notificaciones.single().leida)
        assertEquals(1, vm.estado.value.sinLeer)
        assertNotNull(vm.estado.value.mensaje)
        assertFalse(vm.estado.value.accionando)
    }

    @Test fun `perder permiso cancela accion tardia aunque no cambie token`() = runTest {
        val pendiente = CompletableDeferred<Int>()
        repo.todas = { withContext(NonCancellable) { pendiente.await() } }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        vm.marcarTodasLeidas()
        runCurrent()
        gestor.actualizarUsuario("token", usuario.copy(role = "employee"))
        runCurrent()
        pendiente.complete(4)
        runCurrent()
        assertFalse(vm.estado.value.disponible)
        assertTrue(vm.estado.value.notificaciones.isEmpty())
        assertEquals(0, vm.estado.value.sinLeer)
        assertFalse(vm.estado.value.accionando)
    }

    @Test fun `cierre cancela snapshot tardio y limpia todo el estado`() = runTest {
        val pendiente = CompletableDeferred<PaginaNotificaciones>()
        repo.listar = { withContext(NonCancellable) { pendiente.await() } }
        val vm = modelo()
        runCurrent()
        vm.cargarNotificaciones()
        runCurrent()
        gestor.cerrar()
        runCurrent()
        pendiente.complete(PaginaNotificaciones(Pagina(listOf(notificacion("privada")), 1, 30, 1), 8))
        runCurrent()
        assertFalse(vm.estado.value.disponible)
        assertTrue(vm.estado.value.notificaciones.isEmpty())
        assertEquals(0, vm.estado.value.sinLeer)
        assertFalse(vm.estado.value.cargando)
    }
}

private fun notificacion(id: String) = Notificacion(id, "Nuevo aviso", "Titulo", "Texto", "Hoy", false, "7")

private class NotificacionesFalsas : RepositorioNotificaciones {
    var filas = listOf(notificacion("1"))
    var contador = 1
    var consultasContador = 0
    var acciones = 0
    val paginas = mutableListOf<Int>()
    var listar: suspend (Int) -> PaginaNotificaciones = { p -> PaginaNotificaciones(Pagina(filas, p, 30, filas.size), contador) }
    var contar: suspend () -> Int = { contador }
    var marcar: suspend (String) -> Notificacion = { id ->
        contador = (contador - 1).coerceAtLeast(0)
        filas = filas.map { if (it.id == id) it.copy(leida = true) else it }
        filas.first { it.id == id }
    }
    var todas: suspend () -> Int = { contador = 0; 0 }
    override suspend fun notificaciones(pagina: Int, limite: Int): PaginaNotificaciones {
        paginas.add(pagina)
        return listar(pagina)
    }
    override suspend fun noLeidas(): Int { consultasContador++; return contar() }
    override suspend fun marcarLeida(id: String): Notificacion { acciones++; return marcar(id) }
    override suspend fun leerTodas(): Int { acciones++; return todas() }
    override suspend fun archivarLeidas(): Int {
        acciones++
        val total = filas.count { it.leida }
        filas = filas.filterNot { it.leida }
        return total
    }
}
