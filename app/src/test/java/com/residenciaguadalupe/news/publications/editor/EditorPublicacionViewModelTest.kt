package com.residenciaguadalupe.news.publications.editor

import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.Adjunto
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.media.data.RepositorioMedios
import com.residenciaguadalupe.news.publications.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EditorPublicacionViewModelTest : PruebaPublicaciones() {
    private val adjunto = Adjunto("m1", "https://example.invalid/media/m1", "image/png", "foto.png", "", 20)
    private val medios = MediosFalsos(adjunto)
    private val descartes = mutableListOf<Pair<Adjunto, String>>()
    private fun admin() = gestor.actualizarUsuario("token", usuario.copy(role = "admin"))
    private fun modelo() = conservar(EditorPublicacionViewModel(repo, medios, gestor) { a, t -> descartes.add(a to t) })
    private fun llenar(vm: EditorPublicacionViewModel) { vm.setTitulo("Titulo"); vm.setTexto("Contenido") }

    @Test fun `reintento conserva clave y cambiar cuerpo genera otra`() = runTest {
        admin()
        repo.crear = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo(); vm.inicializar(null); runCurrent(); llenar(vm)
        vm.publicar(); runCurrent(); vm.publicar(); runCurrent()
        assertEquals(repo.creaciones[0].second, repo.creaciones[1].second)
        vm.setTexto("Otro contenido"); vm.publicar(); runCurrent()
        assertNotEquals(repo.creaciones[1].second, repo.creaciones[2].second)
        assertNull(vm.publicado)
        assertFalse(vm.enviando)
    }
    @Test fun `doble publicar no duplica ni permite cambiar snapshot en vuelo`() = runTest {
        admin()
        val respuesta = CompletableDeferred<com.residenciaguadalupe.news.data.Publicacion>()
        repo.crear = { respuesta.await() }
        val vm = modelo(); vm.inicializar(null); runCurrent(); llenar(vm)
        vm.publicar(); vm.publicar(); vm.setTitulo("Cambio tardio"); runCurrent()
        assertEquals(1, repo.creaciones.size)
        assertEquals("Titulo", vm.fTitulo)
        respuesta.complete(publicacionPrueba()); runCurrent()
        assertNotNull(vm.publicado)
        assertFalse(vm.enviando)
    }
    @Test fun `edicion usa version fresca y conserva fijado para empleado`() = runTest {
        gestor.actualizarUsuario("token", usuario.copy(role = "employee"))
        repo.obtener = { publicacionPrueba(it).copy(autorId = usuario.id, fijada = true, version = 8) }
        val vm = modelo(); vm.inicializar("7"); runCurrent()
        vm.setFijar(false); vm.publicar(); runCurrent()
        assertEquals(8, repo.ediciones.single().third)
        assertEquals(true, repo.ediciones.single().second.isPinned)
        assertFalse(vm.publicadoEraNuevo)
    }
    @Test fun `conflicto conserva texto y recarga version explicita`() = runTest {
        admin()
        repo.editar = { _, _, _ -> throw ErrorApi(409, "VERSION_CONFLICT", "Cambio concurrente.") }
        val vm = modelo(); vm.inicializar("7"); runCurrent()
        vm.setTexto("Mi cambio"); vm.publicar(); runCurrent()
        assertTrue(vm.fConflicto)
        assertEquals("Mi cambio", vm.fTexto)
        repo.obtener = { publicacionPrueba(it).copy(texto = "Version nueva", version = 9) }
        vm.recargarEdicion(); runCurrent()
        assertEquals("Version nueva", vm.fTexto)
        assertEquals(9, vm.editando!!.version)
        assertFalse(vm.fConflicto)
    }
    @Test fun `carga fallida no convierte edicion en alta nueva`() = runTest {
        admin()
        repo.obtener = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo(); vm.inicializar("7"); runCurrent(); llenar(vm)
        vm.publicar(); runCurrent()
        assertTrue(repo.creaciones.isEmpty())
        repo.obtener = { publicacionPrueba(it) }
        vm.reintentarCarga(); runCurrent()
        assertEquals("7", vm.editando!!.id)
    }
    @Test fun `salir impide que consulta tardia abra otra ficha`() = runTest {
        admin()
        val respuesta = CompletableDeferred<com.residenciaguadalupe.news.data.Publicacion>()
        repo.obtener = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.inicializar("7"); runCurrent()
        vm.cerrarEditor(); respuesta.complete(publicacionPrueba()); runCurrent()
        assertNull(vm.editando)
        assertEquals("", vm.fTitulo)
        assertFalse(vm.cargandoEdicion)
    }
    @Test fun `upload tardio al salir se descarta sin tocar nuevo formulario`() = runTest {
        admin()
        val respuesta = CompletableDeferred<Adjunto>()
        medios.subida = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.inicializar(null); runCurrent()
        vm.adjuntar("content://archivo"); runCurrent()
        vm.cerrarEditor(); respuesta.complete(adjunto); runCurrent()
        assertNull(vm.fAdjunto)
        assertEquals(listOf(adjunto to "token"), descartes)
        assertFalse(vm.subiendo)
    }
    @Test fun `otra cuenta no recibe upload ni lo elimina con su token`() = runTest {
        admin()
        val respuesta = CompletableDeferred<Adjunto>()
        medios.subida = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.inicializar(null); runCurrent()
        vm.adjuntar("content://archivo"); runCurrent()
        gestor.iniciar(Sesion("otra", Instant.now().plusSeconds(3600), usuario.copy(role = "admin", id = "8")))
        runCurrent(); respuesta.complete(adjunto); runCurrent()
        assertNull(vm.fAdjunto)
        assertTrue(descartes.isEmpty())
    }
    @Test fun `quitar adjunto publicado no lo borra y salir limpia upload nuevo`() = runTest {
        admin()
        repo.obtener = { publicacionPrueba(it, adjunto = adjunto) }
        val vm = modelo(); vm.inicializar("7"); runCurrent()
        vm.quitarAdjunto()
        assertTrue(descartes.isEmpty())
        vm.adjuntar("content://nuevo"); runCurrent()
        vm.cerrarEditor()
        assertEquals(listOf(adjunto to "token"), descartes)
    }
    @Test fun `envio incierto no borra upload que servidor pudo asociar`() = runTest {
        admin()
        repo.crear = { throw ErrorApi(-1, "NETWORK", "Sin conexion.") }
        val vm = modelo(); vm.inicializar(null); runCurrent(); llenar(vm)
        vm.adjuntar("content://nuevo"); runCurrent(); vm.publicar(); runCurrent()
        vm.cerrarEditor()
        assertTrue(descartes.isEmpty())
    }
    @Test fun `publicacion tardia no muestra confirmacion despues de salir`() = runTest {
        admin()
        val respuesta = CompletableDeferred<com.residenciaguadalupe.news.data.Publicacion>()
        repo.crear = { withContext(NonCancellable) { respuesta.await() } }
        val vm = modelo(); vm.inicializar(null); runCurrent(); llenar(vm)
        vm.publicar(); runCurrent(); vm.cerrarEditor()
        respuesta.complete(publicacionPrueba()); runCurrent()
        assertNull(vm.publicado)
        assertFalse(vm.enviando)
    }
    @Test fun `residente y empleado ajeno no pueden escribir`() = runTest {
        val vm = modelo(); vm.inicializar(null); runCurrent(); llenar(vm)
        vm.publicar(); vm.adjuntar("content://archivo"); runCurrent()
        assertTrue(repo.creaciones.isEmpty())
        assertEquals(0, medios.subidas)
        gestor.actualizarUsuario("token", usuario.copy(role = "employee")); runCurrent()
        vm.inicializar("7"); runCurrent(); vm.publicar(); runCurrent()
        assertNull(vm.editando)
        assertTrue(repo.ediciones.isEmpty())
    }

    @Test fun `crear otro tras edicion confirmada es formulario nuevo`() = runTest {
        admin()
        val vm = modelo(); vm.inicializar("7"); runCurrent()
        vm.publicar(); runCurrent(); vm.nuevoAviso()
        assertFalse(vm.esEdicion)
        assertNull(vm.editando)
        assertNull(vm.publicado)
        llenar(vm); vm.publicar(); runCurrent()
        assertEquals(1, repo.creaciones.size)
        assertTrue(vm.publicadoEraNuevo)
    }

    @Test fun `cerrar antes del observador no elimina upload con cuenta nueva`() = runTest {
        admin()
        val vm = modelo(); vm.inicializar(null); runCurrent()
        vm.adjuntar("content://nuevo"); runCurrent()
        gestor.iniciar(Sesion("otra", Instant.now().plusSeconds(3600), usuario.copy(role = "admin", id = "8")))
        vm.cerrarEditor(); runCurrent()
        assertTrue(descartes.isEmpty())
    }
}
private class MediosFalsos(private val adjunto: Adjunto) : RepositorioMedios {
    var subidas = 0
    var subida: suspend () -> Adjunto = { adjunto }
    override suspend fun subir(referencia: String, textoAlternativo: String?): Adjunto { subidas++; return subida() }
    override suspend fun eliminarArchivo(id: String) = Unit
    override suspend fun descargar(adjunto: Adjunto) = File("/tmp/no-usado.pdf")
}
