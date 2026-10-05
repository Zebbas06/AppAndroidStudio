package com.residenciaguadalupe.news.publications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Pagina
import com.residenciaguadalupe.news.publications.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PruebaPublicaciones {
    private val modelos = ViewModelStore()
    protected lateinit var gestor: GestorSesion
    protected lateinit var repo: PublicacionesFalsas
    protected val usuario = UserDto("6", "Residente QA", "qa@example.invalid", "resident", "active", "password")

    @Before fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
        gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        repo = PublicacionesFalsas()
    }

    @After fun terminar() {
        modelos.clear()
        Dispatchers.resetMain()
    }

    protected fun <T : ViewModel> conservar(vm: T): T = vm.also { modelos.put(it.javaClass.name, it) }
}

fun publicacionPrueba(id: String = "7", leida: Boolean = false, adjunto: Adjunto? = null): Publicacion =
    PostDto(id = id, type = "notice", category = "Aviso", title = "Aviso $id", body = "Texto",
        status = "published", isPinned = false, isRead = leida,
        author = AutorDto("1", "Administracion", null)).aPublicacion { it }.copy(adjunto = adjunto)

class PublicacionesFalsas : RepositorioPublicaciones {
    override val cambios = MutableStateFlow(0)
    override val actualizaciones = MutableSharedFlow<CambioPublicacion>()
    val consultas = mutableListOf<Pair<FiltroPublicaciones, Int>>()
    val lecturas = mutableListOf<String>()
    val reacciones = mutableListOf<Pair<String, String?>>()
    var listar: suspend (FiltroPublicaciones, Int, Int) -> Pagina<Publicacion> = { _, p, l -> Pagina(emptyList(), p, l, 0) }
    var obtener: suspend (String) -> Publicacion = { publicacionPrueba(it) }
    var leer: suspend (String) -> Unit = {}
    var reaccion: suspend (String, String?) -> Reacciones = { _, tipo -> Reacciones(meGusta = 1, total = 1, mia = tipo) }
    val creaciones = mutableListOf<Pair<PostEscrituraRequest, String>>()
    val ediciones = mutableListOf<Triple<String, PostEscrituraRequest, Int>>()
    val estados = mutableListOf<Pair<String, Boolean>>()
    val fijados = mutableListOf<Pair<String, Boolean>>()
    val eliminados = mutableListOf<String>()
    var crear: suspend (PostEscrituraRequest) -> Publicacion = { publicacionPrueba().copy(titulo = it.title) }
    var editar: suspend (String, PostEscrituraRequest, Int) -> Publicacion = { id, c, v -> publicacionPrueba(id).copy(titulo = c.title, version = v + 1) }
    var cambiarEstado: suspend (String, Boolean) -> Publicacion = { id, publicada -> publicacionPrueba(id).copy(estado = if (publicada) "published" else "archived") }
    var fijar: suspend (String, Boolean) -> Publicacion = { id, valor -> publicacionPrueba(id).copy(fijada = valor) }
    var eliminar: suspend (String) -> Unit = {}

    override suspend fun publicaciones(f: FiltroPublicaciones, pagina: Int, limite: Int): Pagina<Publicacion> {
        consultas.add(f to pagina)
        return listar(f, pagina, limite)
    }
    override suspend fun publicacion(id: String) = obtener(id)
    override suspend fun marcarLeida(id: String) {
        lecturas.add(id)
        leer(id)
        actualizaciones.emit(CambioPublicacion.Leida(id))
    }
    override suspend fun reaccionar(id: String, tipo: String?): Reacciones {
        reacciones.add(id to tipo)
        return reaccion(id, tipo).also { actualizaciones.emit(CambioPublicacion.Reaccion(id, it)) }
    }
    override suspend fun crear(cuerpo: PostEscrituraRequest, claveIdempotencia: String): Publicacion {
        creaciones.add(cuerpo to claveIdempotencia)
        return crear(cuerpo)
    }
    override suspend fun editar(id: String, cuerpo: PostEscrituraRequest, version: Int): Publicacion {
        ediciones.add(Triple(id, cuerpo, version))
        return editar.invoke(id, cuerpo, version)
    }
    override suspend fun cambiarEstado(id: String, publicada: Boolean): Publicacion {
        estados.add(id to publicada)
        return cambiarEstado.invoke(id, publicada)
    }
    override suspend fun fijar(id: String, fijar: Boolean): Publicacion {
        fijados.add(id to fijar)
        return this.fijar.invoke(id, fijar)
    }
    override suspend fun eliminar(id: String) { eliminados.add(id); eliminar.invoke(id) }
}
