package com.residenciaguadalupe.news.users

import androidx.lifecycle.ViewModelStore
import com.residenciaguadalupe.news.core.session.*
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.users.data.RepositorioUsuarios
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class UsuariosViewModelTest {
    private val modelos = ViewModelStore()
    private val pendiente = UserDto("2", "Persona QA", "qa@example.invalid", "resident", "pending", "password")
    private lateinit var gestor: GestorSesion
    private lateinit var repo: UsuariosFalso
    @Before fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
        gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600),
            pendiente.copy(id = "1", role = "admin", status = "active"))))
        repo = UsuariosFalso(pendiente)
    }
    @After fun terminar() { modelos.clear(); Dispatchers.resetMain() }
    private fun modelo() = UsuariosViewModel(repo, gestor).also { modelos.put("usuarios", it) }
    private fun completar(vm: UsuariosViewModel) {
        vm.setHabitacion("112"); vm.setIngreso(LocalDate.of(2026, 10, 1))
    }

    @Test fun `confirmar sin perfil no activa ni envia`() = runTest {
        val vm = modelo(); vm.abrir("2"); runCurrent()
        vm.aprobar(); runCurrent()
        assertTrue(repo.ediciones.isEmpty())
        assertEquals(EstadoCuenta.PENDIENTE, vm.fEstado)
        assertTrue(vm.fError.isNotEmpty())
    }
    @Test fun `fallo de confirmacion no contamina guardar sin confirmar`() = runTest {
        val vm = modelo(); vm.abrir("2"); runCurrent(); completar(vm)
        repo.editar = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        vm.aprobar(); runCurrent()
        assertEquals("active", repo.ediciones.last().status)
        assertEquals(EstadoCuenta.PENDIENTE, vm.fEstado)
        repo.editar = { pendiente.copy(status = it.status) }
        vm.guardar(); runCurrent()
        assertEquals("pending", repo.ediciones.last().status)
        assertEquals("pending", vm.editando!!.status)
    }
    @Test fun `solo confirma despues de respuesta y evita doble envio`() = runTest {
        val respuesta = CompletableDeferred<UserDto>()
        repo.editar = { respuesta.await() }
        val vm = modelo(); vm.abrir("2"); runCurrent(); completar(vm)
        vm.aprobar(); vm.aprobar(); runCurrent()
        assertEquals(1, repo.ediciones.size)
        assertEquals(EstadoCuenta.PENDIENTE, vm.fEstado)
        respuesta.complete(pendiente.copy(status = "active")); runCurrent()
        assertEquals(EstadoCuenta.ACTIVA, vm.fEstado)
        assertFalse(vm.guardando)
    }
    @Test fun `selector no puede saltarse confirmacion`() = runTest {
        val vm = modelo(); vm.abrir("2"); runCurrent()
        vm.setEstado(EstadoCuenta.ACTIVA); vm.guardar(); runCurrent()
        assertEquals("pending", repo.ediciones.single().status)
    }
    @Test fun `respuesta de ficha anterior no reemplaza nueva`() = runTest {
        val vieja = CompletableDeferred<UserDto>()
        repo.consultar = { id -> if (id == "2") withContext(NonCancellable) { vieja.await() } else pendiente.copy(id = id) }
        val vm = modelo(); vm.abrir("2"); runCurrent()
        vm.abrir("3"); runCurrent()
        vieja.complete(pendiente); runCurrent()
        assertEquals("3", vm.editando!!.id)
        assertFalse(vm.cargandoUsuario)
    }
    @Test fun `salir limpia secreto e ignora guardado tardio`() = runTest {
        val respuesta = CompletableDeferred<UserDto>()
        repo.editar = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.abrir("2"); runCurrent()
        vm.setSecreto("secreto123"); vm.guardar(); runCurrent()
        vm.nuevo()
        respuesta.complete(pendiente); runCurrent()
        assertNull(vm.editando)
        assertNull(vm.guardado)
        assertEquals("", vm.fSecreto)
        assertFalse(vm.guardando)
    }
    @Test fun `perder permiso limpia ficha y bloquea operaciones`() = runTest {
        val vm = modelo(); vm.abrir("2"); runCurrent(); vm.setSecreto("secreto123")
        gestor.actualizarUsuario("token", pendiente.copy(id = "1", status = "active"))
        runCurrent()
        vm.cargar(); vm.abrir("2"); vm.guardar(); vm.reponerCredencial(); runCurrent()
        assertNull(vm.editando)
        assertEquals("", vm.fSecreto)
        assertTrue(repo.ediciones.isEmpty())
        assertTrue(repo.paginas.isEmpty())
    }
    @Test fun `cargar mas con otra busqueda reinicia pagina`() = runTest {
        val vm = modelo(); vm.cargar(); runCurrent()
        vm.cambiarBusqueda("nuevo"); vm.cargar(reiniciar = false); runCurrent()
        assertEquals(listOf(1, 1), repo.paginas.map { it.second })
        assertEquals("nuevo", repo.paginas.last().first)
        assertEquals(1, vm.usuarios.size)
    }
    @Test fun `credencial solo se limpia al confirmar y valida metodo`() = runTest {
        val respuesta = CompletableDeferred<Unit>()
        repo.reponer = { respuesta.await() }
        val vm = modelo(); vm.abrir("2"); runCurrent()
        vm.setMetodo("otro"); vm.setSecreto("secreto123"); vm.reponerCredencial(); runCurrent()
        assertEquals(0, repo.reposiciones)
        vm.setMetodo("pin"); vm.setSecreto("1234"); vm.reponerCredencial(); runCurrent()
        assertEquals("1234", vm.fSecreto)
        respuesta.complete(Unit); runCurrent()
        assertEquals("", vm.fSecreto)
        assertEquals("pin", vm.editando!!.authMethod)
        assertEquals(1, repo.reposiciones)
    }
}
private class UsuariosFalso(private val usuario: UserDto) : RepositorioUsuarios {
    val ediciones = mutableListOf<EditarUsuarioRequest>()
    val paginas = mutableListOf<Pair<String?, Int>>()
    var reposiciones = 0
    var consultar: suspend (String) -> UserDto = { usuario.copy(id = it) }
    var editar: suspend (EditarUsuarioRequest) -> UserDto = { usuario.copy(status = it.status) }
    var reponer: suspend () -> Unit = {}
    override suspend fun usuarios(estado: EstadoCuenta?, busqueda: String?, pagina: Int): Pagina<UserDto> {
        paginas.add(busqueda to pagina)
        return Pagina(listOf(usuario), pagina, 30, 60)
    }
    override suspend fun usuario(id: String) = consultar(id)
    override suspend fun crearUsuario(c: CrearUsuarioRequest) = usuario.copy(status = "active")
    override suspend fun editarUsuario(id: String, c: EditarUsuarioRequest): UserDto {
        ediciones.add(c); return editar(c)
    }
    override suspend fun reponerCredencial(id: String, metodo: String, secreto: String) {
        reposiciones++; reponer()
    }
}
