package com.residenciaguadalupe.news.publications.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.publications.data.RepositorioPublicaciones
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

data class EstadoDetalle(
    val detalle: Publicacion? = null,
    val detalleCargando: Boolean = false,
    val detalleError: ErrorApi? = null,
    val reaccionando: Boolean = false,
    val abriendoAdjunto: Boolean = false,
    val esResidente: Boolean = false,
    val leyendoEnVoz: Boolean = false,
    val mensaje: String? = null
)

/** Una instancia por entrada de detalle. No escribe en otros ViewModels. */
class DetalleViewModel(
    private val repo: RepositorioPublicaciones,
    private val gestor: GestorSesion,
    private val descargar: suspend (Adjunto) -> File
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoDetalle(esResidente = residente()))
    val estado = mutableEstado.asStateFlow()
    private var solicitud = 0L
    private var trabajoDetalle: Job? = null
    private var trabajoReaccion: Job? = null
    private var trabajoAdjunto: Job? = null
    private fun residente() = gestor.estado.value.sesion?.usuario?.rol == Rol.RESIDENTE

    fun abrirDetalle(id: String, forzar: Boolean = false) {
        if (gestor.estado.value.sesion == null || id.isBlank()) return
        if (!forzar && estado.value.detalle?.id == id && estado.value.detalleError == null) return
        cerrarDetalle()
        val turno = solicitud
        mutableEstado.value = EstadoDetalle(detalleCargando = true, esResidente = residente())
        trabajoDetalle = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicacion(id)
                coroutineContext.ensureActive()
                if (turno != solicitud) return@lanzar
                mutableEstado.update { it.copy(detalle = p) }
                if (residente() && !p.leida) {
                    try {
                        repo.marcarLeida(id)
                        coroutineContext.ensureActive()
                        if (turno == solicitud) mutableEstado.update { it.copy(detalle = it.detalle?.copy(leida = true)) }
                    } catch (e: ErrorApi) {
                        if (turno == solicitud) avisar("No se pudo registrar la lectura. ${e.mensaje}")
                    }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) mutableEstado.update { it.copy(detalleError = e) }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(detalleCargando = false) }
            }
        }
    }

    fun reaccionar(tipo: String) {
        val p = estado.value.detalle ?: return
        if (!residente() || estado.value.reaccionando || tipo !in setOf("like", "love", "happy")) return
        val turno = solicitud
        mutableEstado.update { it.copy(reaccionando = true, mensaje = null) }
        trabajoReaccion = gestor.lanzar(viewModelScope) {
            try {
                val r = repo.reaccionar(p.id, if (p.reacciones.mia == tipo) null else tipo)
                coroutineContext.ensureActive()
                if (turno == solicitud) mutableEstado.update { it.copy(detalle = it.detalle?.copy(reacciones = r)) }
            } catch (e: ErrorApi) {
                if (turno == solicitud) avisar(e.mensaje)
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(reaccionando = false) }
            }
        }
    }

    fun abrirAdjunto(adjunto: Adjunto, alDescargar: (File) -> Unit) {
        if (estado.value.abriendoAdjunto || estado.value.detalle?.adjunto?.id != adjunto.id) return
        val turno = solicitud
        mutableEstado.update { it.copy(abriendoAdjunto = true, mensaje = null) }
        trabajoAdjunto = gestor.lanzar(viewModelScope) {
            try {
                val archivo = descargar(adjunto)
                coroutineContext.ensureActive()
                if (turno == solicitud) alDescargar(archivo)
            } catch (e: ErrorApi) {
                if (turno == solicitud) avisar(e.mensaje)
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(abriendoAdjunto = false) }
            }
        }
    }

    fun avisar(texto: String) { mutableEstado.update { it.copy(mensaje = texto) } }
    fun ocultarMensaje() { mutableEstado.update { it.copy(mensaje = null) } }

    // Se conserva el indicador existente; la integracion real con TTS sigue pendiente.
    fun alternarVoz() { mutableEstado.update { it.copy(leyendoEnVoz = !it.leyendoEnVoz) } }

    /** Cancela tambien reacciones y descargas al salir o cambiar de publicacion. */
    fun cerrarDetalle() {
        solicitud++
        trabajoDetalle?.cancel()
        trabajoReaccion?.cancel()
        trabajoAdjunto?.cancel()
        mutableEstado.value = EstadoDetalle(esResidente = residente())
    }

    init {
        var revision = gestor.estado.value.revision
        viewModelScope.launch {
            gestor.estado.collect {
                if (revision != it.revision) cerrarDetalle()
                revision = it.revision
                mutableEstado.update { e -> e.copy(esResidente = residente()) }
            }
        }
    }
}
