package com.residenciaguadalupe.news.profile

import androidx.lifecycle.ViewModelStore
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.Escala
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.profile.data.RepositorioPerfil
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {
    private val modelos = ViewModelStore()
    private lateinit var gestor: GestorSesion
    private lateinit var repo: PerfilFalso
    private val usuario = UserDto("6", "QA", "qa@example.invalid", "resident", "active", "password",
        preferences = PreferenciasDto(notifyPhotos = true))
    @Before fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
        gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        repo = PerfilFalso(usuario)
    }
    @After fun terminar() { modelos.clear(); Dispatchers.resetMain() }
    private fun modelo() = PerfilViewModel(repo, gestor).also { modelos.put("perfil", it) }

    @Test fun `cambios rapidos se envian en serie sin retroceso visual`() = runTest {
        val primera = CompletableDeferred<PreferenciasDto>()
        repo.guardar = { p -> if (repo.envios.size == 1) primera.await() else p }
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("avisos", false)
        runCurrent()
        vm.cambiarPref("fotos", false)
        runCurrent()
        assertEquals(1, repo.envios.size)
        assertFalse(vm.estado.value.preferencias.notifyPhotos)
        primera.complete(repo.envios.first())
        runCurrent()
        assertEquals(2, repo.envios.size)
        assertFalse(repo.envios.last().notifyNotices)
        assertFalse(repo.envios.last().notifyPhotos)
        assertFalse(vm.estado.value.preferencias.notifyPhotos)
        assertFalse(vm.estado.value.preferenciasGuardando)
    }

    @Test fun `fallo viejo intenta estado nuevo sin restaurar copia anterior`() = runTest {
        val primera = CompletableDeferred<PreferenciasDto>()
        repo.guardar = { p -> if (repo.envios.size == 1) primera.await() else p }
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("avisos", false)
        runCurrent()
        vm.cambiarPref("actividades", false)
        primera.completeExceptionally(ErrorApi(-1, "NETWORK", "Sin conexion."))
        runCurrent()
        assertEquals(2, repo.envios.size)
        assertFalse(vm.estado.value.preferencias.notifyNotices)
        assertFalse(vm.estado.value.preferencias.notifyActivities)
        assertNull(vm.estado.value.preferenciasError)
    }

    @Test fun `fallo mas reciente restaura ultimo estado confirmado no defaults`() = runTest {
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("avisos", false)
        runCurrent()
        repo.guardar = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        vm.cambiarPref("fotos", false)
        runCurrent()
        assertFalse(vm.estado.value.preferencias.notifyNotices)
        assertTrue(vm.estado.value.preferencias.notifyPhotos)
        assertNotNull(vm.estado.value.preferenciasError)
        assertFalse(vm.estado.value.preferenciasGuardando)
    }

    @Test fun `deshacer toggle durante envio vuelve a guardar valor original`() = runTest {
        val primera = CompletableDeferred<PreferenciasDto>()
        repo.guardar = { p -> if (repo.envios.size == 1) primera.await() else p }
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("avisos", false)
        runCurrent()
        vm.cambiarPref("avisos", true)
        primera.complete(repo.envios.first())
        runCurrent()
        assertEquals(listOf(false, true), repo.envios.map { it.notifyNotices })
        assertTrue(vm.estado.value.preferencias.notifyNotices)
    }

    @Test fun `consulta tardia no pisa eleccion pendiente`() = runTest {
        val consulta = CompletableDeferred<PreferenciasDto>()
        repo.cargar = { withContext(NonCancellable) { consulta.await() } }
        val vm = modelo()
        runCurrent()
        vm.cargarPreferencias()
        runCurrent()
        vm.cambiarPref("fotos", false)
        runCurrent()
        consulta.complete(usuario.preferences)
        runCurrent()
        assertFalse(vm.estado.value.preferencias.notifyPhotos)
        assertFalse(vm.estado.value.preferenciasCargando)
    }

    @Test fun `actualizar usuario no reemplaza preferencias pendientes`() = runTest {
        val pendiente = CompletableDeferred<PreferenciasDto>()
        repo.guardar = { pendiente.await() }
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("fotos", false)
        runCurrent()
        gestor.actualizarUsuario("token", usuario.copy(fullName = "Nombre nuevo"))
        runCurrent()
        assertEquals("Nombre nuevo", vm.estado.value.usuario!!.fullName)
        assertFalse(vm.estado.value.preferencias.notifyPhotos)
        pendiente.complete(repo.envios.first())
        runCurrent()
    }

    @Test fun `otra cuenta limpia estado e ignora preferencia tardia`() = runTest {
        val pendiente = CompletableDeferred<PreferenciasDto>()
        repo.guardar = { withContext(NonCancellable) { pendiente.await() } }
        val vm = modelo()
        runCurrent()
        vm.cambiarPref("fotos", false)
        runCurrent()
        gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent()
        pendiente.complete(PreferenciasDto(notifyPhotos = false))
        runCurrent()
        assertEquals("8", vm.estado.value.usuario!!.id)
        assertTrue(vm.estado.value.preferencias.notifyPhotos)
        assertFalse(vm.estado.value.preferenciasGuardando)
        assertNull(vm.estado.value.preferenciasError)
    }

    @Test fun `personal no consulta ni escribe preferencias de residente`() = runTest {
        gestor.iniciar(Sesion("admin", Instant.now().plusSeconds(3600), usuario.copy(role = "admin")))
        val vm = modelo()
        runCurrent()
        vm.cargarPreferencias()
        vm.cambiarPref("avisos", false)
        runCurrent()
        assertEquals(0, repo.consultas)
        assertTrue(repo.envios.isEmpty())
    }

    @Test fun `nombre invalido no envia y doble guardar solo ejecuta una vez`() = runTest {
        val pendiente = CompletableDeferred<UserDto>()
        repo.nombre = { pendiente.await() }
        val vm = modelo()
        runCurrent()
        vm.cambiarNombre("a") {}
        assertNotNull(vm.estado.value.perfilError)
        assertEquals(0, repo.ediciones)
        var terminado = 0
        vm.cambiarNombre("Nombre nuevo") { terminado++ }
        vm.cambiarNombre("Nombre nuevo") { terminado++ }
        runCurrent()
        pendiente.complete(usuario.copy(fullName = "Nombre nuevo"))
        runCurrent()
        assertEquals(1, repo.ediciones)
        assertEquals(1, terminado)
        assertEquals("Nombre nuevo", vm.estado.value.usuario!!.fullName)
        assertFalse(vm.estado.value.perfilGuardando)
    }

    @Test fun `credencial valida confirma metodo y no almacena secretos en estado`() = runTest {
        val vm = modelo()
        runCurrent()
        vm.cambiarCredencial("", "pin", "1234") {}
        assertEquals(0, repo.ediciones)
        vm.cambiarCredencial("actual", "otro", "12345678") {}
        assertEquals(0, repo.ediciones)
        var terminado = false
        vm.cambiarCredencial("actual", "pin", "1234") { terminado = true }
        runCurrent()
        assertTrue(terminado)
        assertEquals("pin", vm.estado.value.usuario!!.authMethod)
        assertNull(vm.estado.value.perfilError)
    }

    @Test fun `error de credencial mantiene formulario y no confirma`() = runTest {
        repo.credencial = { _, _, _ -> throw ErrorApi(422, "VALIDATION_FAILED", "Credencial actual incorrecta.") }
        val vm = modelo()
        runCurrent()
        var terminado = false
        vm.cambiarCredencial("actual", "password", "nuevo123") { terminado = true }
        runCurrent()
        assertFalse(terminado)
        assertNotNull(vm.estado.value.perfilError)
        assertFalse(vm.estado.value.perfilGuardando)
        assertEquals("password", vm.estado.value.usuario!!.authMethod)
    }

    @Test fun `salir cancela edicion tardia y conserva escala accesible`() = runTest {
        val pendiente = CompletableDeferred<UserDto>()
        repo.nombre = { withContext(NonCancellable) { pendiente.await() } }
        val vm = modelo()
        runCurrent()
        vm.cambiarEscala(Escala.GRANDE)
        var terminado = false
        vm.cambiarNombre("Nombre nuevo") { terminado = true }
        runCurrent()
        gestor.cerrar()
        runCurrent()
        pendiente.complete(usuario.copy(fullName = "Nombre nuevo"))
        runCurrent()
        assertFalse(terminado)
        assertNull(vm.estado.value.usuario)
        assertEquals(Escala.GRANDE, vm.estado.value.escala)
        assertFalse(vm.estado.value.perfilGuardando)
        assertNull(vm.estado.value.mensaje)
    }
}

private class PerfilFalso(private val usuario: UserDto) : RepositorioPerfil {
    val envios = mutableListOf<PreferenciasDto>()
    var consultas = 0
    var ediciones = 0
    var guardar: suspend (PreferenciasDto) -> PreferenciasDto = { it }
    var cargar: suspend () -> PreferenciasDto = { usuario.preferences }
    var nombre: suspend (String) -> UserDto = { usuario.copy(fullName = it.trim()) }
    var credencial: suspend (String, String, String) -> UserDto = { _, metodo, _ -> usuario.copy(authMethod = metodo) }
    override suspend fun preferencias(): PreferenciasDto { consultas++; return cargar() }
    override suspend fun guardarPreferencias(p: PreferenciasDto): PreferenciasDto { envios.add(p); return guardar(p) }
    override suspend fun cambiarNombre(nombre: String): UserDto { ediciones++; return this.nombre(nombre) }
    override suspend fun cambiarCredencial(actual: String, metodo: String, nuevo: String): UserDto {
        ediciones++
        return credencial(actual, metodo, nuevo)
    }
}
