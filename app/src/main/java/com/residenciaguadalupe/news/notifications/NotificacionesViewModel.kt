package com.residenciaguadalupe.news.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.Notificacion
import com.residenciaguadalupe.news.data.Rol
import com.residenciaguadalupe.news.data.rol
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.notifications.data.RepositorioNotificaciones
import com.residenciaguadalupe.news.publications.data.CambioPublicacion
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

data class EstadoNotificaciones(
    val disponible: Boolean = false,
    val notificaciones: List<Notificacion> = emptyList(),
    val sinLeer: Int = 0,
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val accionando: Boolean = false,
    val error: ErrorApi? = null,
    val errorMas: ErrorApi? = null,
    val errorContador: ErrorApi? = null,
    val hayMas: Boolean = false,
    val mensaje: String? = null
)

/** Unico propietario del buzon y de la insignia; no controla navegacion. */
class NotificacionesViewModel(
    private val repo: RepositorioNotificaciones,
    private val gestor: GestorSesion,
    private val publicaciones: Flow<CambioPublicacion>
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoNotificaciones(disponible = residente()))
    val estado = mutableEstado.asStateFlow()
    // Serializa snapshots y escrituras para que un contador anterior no revierta una lectura.
    private val operaciones = Mutex()
    private var pagina = 0
    private var solicitud = 0L
    private var trabajoLista: Job? = null
    private var trabajoContador: Job? = null
    private var trabajoAccion: Job? = null
    private val trabajosLectura = mutableSetOf<Job>()
    private var generacion = 0L
    private fun residente() = gestor.estado.value.sesion?.usuario?.rol == Rol.RESIDENTE

    fun actualizarNoLeidas() {
        if (!residente()) return
        trabajoContador?.cancel()
        trabajoContador = gestor.lanzar(viewModelScope) {
            operaciones.withLock { consultarContador() }
        }
    }

    private suspend fun consultarContador() {
        try {
            val total = repo.noLeidas()
            coroutineContext.ensureActive()
            mutableEstado.update { it.copy(sinLeer = total.coerceAtLeast(0), errorContador = null) }
        } catch (e: ErrorApi) {
            coroutineContext.ensureActive()
            mutableEstado.update { it.copy(errorContador = e) }
        }
    }

    fun cargarNotificaciones() = cargar(reiniciar = true)
    fun cargarMasNotificaciones() = cargar(reiniciar = false)

    private fun cargar(reiniciar: Boolean) {
        if (!residente() || estado.value.accionando) return
        val actual = estado.value
        if (!reiniciar && (actual.cargando || actual.cargandoMas || !actual.hayMas)) return
        val turno = ++solicitud
        trabajoLista?.cancel()
        val siguiente = if (reiniciar) 1 else pagina + 1
        mutableEstado.update { it.copy(cargando = reiniciar, cargandoMas = !reiniciar, error = null, errorMas = null) }
        trabajoLista = gestor.lanzar(viewModelScope) {
            try {
                operaciones.withLock {
                    val r = repo.notificaciones(siguiente)
                    coroutineContext.ensureActive()
                    if (turno == solicitud) {
                        pagina = r.pagina.pagina
                        mutableEstado.update { it.copy(
                            notificaciones = ((if (reiniciar) emptyList() else it.notificaciones) + r.pagina.elementos).distinctBy { n -> n.id },
                            hayMas = r.pagina.hayMas, sinLeer = r.sinLeer.coerceAtLeast(0), errorContador = null
                        ) }
                    }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) mutableEstado.update { if (reiniciar) it.copy(error = e) else it.copy(errorMas = e) }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargando = false, cargandoMas = false) }
            }
        }
    }

    /** Navega inmediatamente; solo confirma la lectura local si el servidor la acepta. */
    fun abrirNotificacion(n: Notificacion, abrir: (String) -> Unit) {
        val actual = estado.value.notificaciones.firstOrNull { it.id == n.id } ?: return
        if (!residente() || estado.value.accionando) return
        if (!actual.leida) {
            accion {
                val leida = repo.marcarLeida(actual.id)
                coroutineContext.ensureActive()
                mutableEstado.update { it.copy(notificaciones = it.notificaciones.map { n -> if (n.id == leida.id) leida else n }) }
                consultarContador()
            }
        }
        actual.postId?.let(abrir)
    }

    fun marcarTodasLeidas() {
        if (estado.value.sinLeer <= 0) return
        accion {
            val total = repo.leerTodas()
            coroutineContext.ensureActive()
            mutableEstado.update { it.copy(
                sinLeer = total.coerceAtLeast(0), errorContador = null,
                notificaciones = it.notificaciones.map { n -> n.copy(leida = true) }
            ) }
        }
    }

    fun archivarLeidas() {
        if (estado.value.notificaciones.none { it.leida }) return
        accion(recargar = true) {
            val total = repo.archivarLeidas()
            coroutineContext.ensureActive()
            mutableEstado.update { it.copy(
                notificaciones = it.notificaciones.filterNot { n -> n.leida },
                mensaje = if (total == 1) "Se archivó 1 notificación." else "Se archivaron $total notificaciones."
            ) }
        }
    }

    private fun accion(recargar: Boolean = false, bloque: suspend () -> Unit) {
        if (!residente() || estado.value.accionando) return
        val generacionAccion = generacion
        mutableEstado.update { it.copy(accionando = true, mensaje = null) }
        trabajoAccion = gestor.lanzar(viewModelScope) {
            var exito = false
            try {
                operaciones.withLock {
                    bloque()
                    coroutineContext.ensureActive()
                    exito = true
                }
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                mutableEstado.update { it.copy(mensaje = e.mensaje) }
            } finally {
                if (generacionAccion == generacion) mutableEstado.update { it.copy(accionando = false) }
            }
            // Archivar cambia offsets: no continuar con el numero de pagina anterior.
            if (exito && recargar) cargarNotificaciones()
        }
    }

    fun ocultarMensaje() { mutableEstado.update { it.copy(mensaje = null) } }

    private fun limpiar() {
        generacion++
        solicitud++
        trabajoLista?.cancel()
        trabajoContador?.cancel()
        trabajoAccion?.cancel()
        trabajosLectura.toList().forEach { it.cancel() }
        trabajosLectura.clear()
        pagina = 0
        mutableEstado.value = EstadoNotificaciones(disponible = residente())
    }

    init {
        var revision = gestor.estado.value.revision
        viewModelScope.launch {
            gestor.estado.collect {
                val cambio = revision != it.revision || estado.value.disponible != residente()
                if (cambio) limpiar()
                revision = it.revision
                if (residente() && (cambio || trabajoContador == null)) actualizarNoLeidas()
            }
        }
        viewModelScope.launch {
            publicaciones.collect { cambio ->
                if (cambio is CambioPublicacion.Leida && residente()) {
                    val trabajo = gestor.lanzar(viewModelScope) {
                        operaciones.withLock {
                            mutableEstado.update { it.copy(notificaciones = it.notificaciones.map { n ->
                                if (n.postId == cambio.id) n.copy(leida = true) else n
                            }) }
                            consultarContador()
                        }
                    }
                    trabajosLectura.add(trabajo)
                    trabajo.invokeOnCompletion { trabajosLectura.remove(trabajo) }
                }
            }
        }
    }
}
