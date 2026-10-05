package com.residenciaguadalupe.news.publications.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.publications.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class EstadoMuro(
    val usuario: UserDto? = null,
    val filtro: String = "TODO",
    val publicaciones: List<Publicacion> = emptyList(),
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val muroError: ErrorApi? = null,
    val muroHayMas: Boolean = false,
    val bandaHoy: List<Actividad> = emptyList(),
    val bandaCargando: Boolean = false,
    val bandaError: ErrorApi? = null,
    val mensaje: String? = null
) {
    val esResidente get() = usuario?.rol == Rol.RESIDENTE
    val esPersonal get() = usuario?.esPersonal == true
}

/** Filtros, paginacion y resumen de hoy; no administra sesion ni navegacion. */
class MuroViewModel(
    private val repo: RepositorioPublicaciones,
    private val gestor: GestorSesion
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoMuro(usuario = gestor.estado.value.sesion?.usuario))
    val estado = mutableEstado.asStateFlow()
    private var pagina = 1
    private var iniciado = false
    private var desactualizado = true
    private var revisionContenido = repo.cambios.value
    private var solicitud = 0L
    private var solicitudBanda = 0L
    private var trabajo: Job? = null
    private var trabajoBanda: Job? = null

    fun refrescarSiHaceFalta() {
        if (gestor.estado.value.sesion == null) return
        if (!iniciado || desactualizado || revisionContenido != repo.cambios.value) {
            cargarMuro()
            cargarBandaHoy()
        }
    }

    fun cambiarFiltro(nuevo: String) {
        if (nuevo !in setOf("TODO", "FOTO", "AVISO", "ACTIVIDAD", "EVENTO", "NOTICIA")) return
        if (nuevo == estado.value.filtro) return
        mutableEstado.update { it.copy(filtro = nuevo) }
        cargarMuro()
    }

    private fun filtroMuro() = when (val filtro = estado.value.filtro) {
        "TODO" -> FiltroPublicaciones()
        "FOTO" -> FiltroPublicaciones(conImagen = true)
        else -> FiltroPublicaciones(categoria = Categoria.valueOf(filtro))
    }

    fun cargarMuro() {
        if (gestor.estado.value.sesion == null) return
        val turno = ++solicitud
        trabajo?.cancel()
        val filtro = filtroMuro()
        revisionContenido = repo.cambios.value
        desactualizado = false
        iniciado = true
        mutableEstado.update { it.copy(cargando = true, cargandoMas = false, muroError = null, mensaje = null) }
        trabajo = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(filtro, 1)
                coroutineContext.ensureActive()
                if (turno == solicitud) {
                    pagina = p.pagina
                    mutableEstado.update { it.copy(publicaciones = p.elementos, muroHayMas = p.hayMas) }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) mutableEstado.update { it.copy(muroError = e) }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargando = false) }
            }
        }
    }

    fun cargarMasMuro() {
        val actual = estado.value
        if (actual.cargandoMas || actual.cargando || !actual.muroHayMas || gestor.estado.value.sesion == null) return
        val turno = ++solicitud
        val filtro = filtroMuro()
        val siguiente = pagina + 1
        mutableEstado.update { it.copy(cargandoMas = true, mensaje = null) }
        trabajo = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(filtro, siguiente)
                coroutineContext.ensureActive()
                if (turno == solicitud) {
                    pagina = p.pagina
                    mutableEstado.update {
                        it.copy(publicaciones = (it.publicaciones + p.elementos).distinctBy { p -> p.id }, muroHayMas = p.hayMas)
                    }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) mutableEstado.update { it.copy(mensaje = e.mensaje) }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargandoMas = false) }
            }
        }
    }

    fun cargarBandaHoy() {
        val turno = ++solicitudBanda
        trabajoBanda?.cancel()
        val filtro = filtroAgenda(PestanaAgenda.HOY)
        mutableEstado.update { it.copy(bandaCargando = true, bandaError = null) }
        trabajoBanda = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(filtro, 1, 20)
                coroutineContext.ensureActive()
                val ahora = Fechas.ahora()
                val actividades = p.elementos.sortedBy { (it.eventoFin ?: it.eventoInicio)?.isBefore(ahora) == true }
                    .map { it.aActividad(conDia = false) }
                if (turno == solicitudBanda) mutableEstado.update { it.copy(bandaHoy = actividades) }
            } catch (e: ErrorApi) {
                if (turno == solicitudBanda) mutableEstado.update { it.copy(bandaError = e) }
            } finally {
                if (turno == solicitudBanda) mutableEstado.update { it.copy(bandaCargando = false) }
            }
        }
    }

    fun ocultarMensaje() { mutableEstado.update { it.copy(mensaje = null) } }

    private fun limpiar() {
        solicitud++
        solicitudBanda++
        trabajo?.cancel()
        trabajoBanda?.cancel()
        pagina = 1
        iniciado = false
        desactualizado = true
        mutableEstado.value = EstadoMuro(usuario = gestor.estado.value.sesion?.usuario)
    }

    init {
        var revision = gestor.estado.value.revision
        viewModelScope.launch {
            gestor.estado.collect {
                if (revision != it.revision) limpiar()
                revision = it.revision
                mutableEstado.update { e -> e.copy(usuario = it.sesion?.usuario) }
            }
        }
        viewModelScope.launch { repo.cambios.drop(1).collect { desactualizado = true } }
        viewModelScope.launch {
            repo.actualizaciones.collect { cambio ->
                mutableEstado.update { e ->
                    e.copy(publicaciones = e.publicaciones.map { p ->
                        when (cambio) {
                            is CambioPublicacion.Leida -> if (p.id == cambio.id) p.copy(leida = true) else p
                            is CambioPublicacion.Reaccion -> if (p.id == cambio.id) p.copy(reacciones = cambio.reacciones) else p
                        }
                    })
                }
            }
        }
    }
}
