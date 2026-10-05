package com.residenciaguadalupe.news.publications.administration

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.publications.data.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/** Lista y acciones administrativas; no posee campos ni uploads del editor. */
class AdminPublicacionesViewModel(
    private val repo: RepositorioPublicaciones,
    private val gestor: GestorSesion
) : ViewModel() {
    val esAdmin get() = gestor.estado.value.sesion?.usuario?.rol == Rol.ADMIN
    private fun autorizado() = gestor.estado.value.sesion?.usuario?.rol in setOf(Rol.ADMIN, Rol.EMPLEADO)
    fun puedeEditar(p: Publicacion) = esAdmin ||
        (autorizado() && p.autorId == gestor.estado.value.sesion?.usuario?.id)

    private val lista = mutableStateListOf<Publicacion>()
    val avisos: List<Publicacion> get() = lista
    var listaCargando by mutableStateOf(false); private set
    var listaError by mutableStateOf<ErrorApi?>(null); private set
    var listaHayMas by mutableStateOf(false); private set
    var procesando by mutableStateOf<String?>(null); private set
    var mensaje by mutableStateOf<String?>(null); private set
    var puedeDeshacer by mutableStateOf(false); private set
    var porEliminar by mutableStateOf<Publicacion?>(null); private set
    private var listaPagina = 1
    private var solicitud = 0L
    private var ciclo = 0L
    private var listaDesactualizada = false
    private var trabajoLista: Job? = null
    private var trabajoOperacion: Job? = null
    private var trabajoMensaje: Job? = null
    private var inversa: (suspend () -> Unit)? = null
    private var idInversa: String? = null

    fun cargarAvisos(reiniciar: Boolean = true) {
        if (!autorizado()) return
        val desdeInicio = reiniciar || listaDesactualizada
        if (!desdeInicio && (listaCargando || !listaHayMas)) return
        val turno = ++solicitud
        trabajoLista?.cancel()
        val pagina = if (desdeInicio) 1 else listaPagina + 1
        listaCargando = true
        listaError = null
        trabajoLista = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(FiltroPublicaciones(estado = "all", orden = "-publishedAt"), pagina)
                coroutineContext.ensureActive()
                if (turno != solicitud) return@lanzar
                if (desdeInicio) lista.clear()
                lista.addAll(p.elementos.filter { n -> lista.none { it.id == n.id } })
                listaPagina = p.pagina
                listaHayMas = p.hayMas
                listaDesactualizada = false
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == solicitud) {
                    if (desdeInicio) listaError = e else avisar(e.mensaje)
                }
            } finally {
                if (turno == solicitud) listaCargando = false
            }
        }
    }

    private fun invalidarLista() {
        solicitud++
        trabajoLista?.cancel()
        listaCargando = false
        listaDesactualizada = true
    }

    private suspend fun reemplazar(p: Publicacion) {
        coroutineContext.ensureActive()
        invalidarLista()
        val i = lista.indexOfFirst { it.id == p.id }
        if (i >= 0) lista[i] = p else lista.add(0, p)
    }

    private fun avisar(texto: String, id: String? = null, deshacer: (suspend () -> Unit)? = null) {
        mensaje = texto; inversa = deshacer; idInversa = id
        puedeDeshacer = deshacer != null
        trabajoMensaje?.cancel()
        trabajoMensaje = gestor.lanzar(viewModelScope) {
            delay(5000)
            mensaje = null; inversa = null; idInversa = null; puedeDeshacer = false
        }
    }

    fun deshacer() {
        if (!autorizado() || procesando != null) return
        val accion = inversa ?: return
        val id = idInversa ?: return
        trabajoMensaje?.cancel()
        mensaje = null; inversa = null; idInversa = null; puedeDeshacer = false
        operar(id, accion)
    }

    private fun operar(id: String, bloque: suspend () -> Unit) {
        if (!autorizado() || procesando != null) return
        val turno = ciclo
        procesando = id
        // Una accion nueva reemplaza la ventana de Deshacer anterior.
        trabajoMensaje?.cancel()
        inversa = null; idInversa = null; puedeDeshacer = false; mensaje = null
        trabajoOperacion = gestor.lanzar(viewModelScope) {
            try {
                bloque()
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == ciclo) avisar(e.mensaje)
            } finally {
                if (turno == ciclo) procesando = null
            }
        }
    }

    fun alternarFijado(p: Publicacion) {
        if (!esAdmin || p.estado != "published") return
        operar(p.id) {
            val fijar = !p.fijada
            reemplazar(repo.fijar(p.id, fijar))
            avisar(if (fijar) "Se fijó como importante en el muro." else "Se quitó de los fijados.", p.id) {
                reemplazar(repo.fijar(p.id, !fijar))
            }
        }
    }

    fun alternarArchivado(p: Publicacion) {
        if (!puedeEditar(p)) return
        operar(p.id) {
            val archivar = p.estado != "archived"
            reemplazar(repo.cambiarEstado(p.id, publicada = !archivar))
            avisar(if (archivar) "El aviso se archivó." else "El aviso volvió al muro.", p.id) {
                reemplazar(repo.cambiarEstado(p.id, publicada = archivar))
                // Si volver a fijar falla, la republicacion ya esta confirmada.
                if (archivar && p.fijada && esAdmin) reemplazar(repo.fijar(p.id, true))
            }
        }
    }

    fun pedirEliminar(p: Publicacion?) {
        if (p == null) porEliminar = null
        else if (esAdmin && procesando == null) porEliminar = p
    }

    fun eliminarConfirmado() {
        val p = porEliminar ?: return
        if (!esAdmin || procesando != null) return
        porEliminar = null
        operar(p.id) {
            repo.eliminar(p.id)
            coroutineContext.ensureActive()
            invalidarLista()
            lista.removeAll { it.id == p.id }
            avisar("Se eliminó «${p.titulo}».")
        }
    }

    private fun limpiarEstado() {
        ciclo++
        invalidarLista()
        trabajoOperacion?.cancel(); trabajoMensaje?.cancel()
        lista.clear(); listaPagina = 1; listaHayMas = false; listaError = null
        procesando = null; porEliminar = null; mensaje = null
        inversa = null; idInversa = null; puedeDeshacer = false
    }

    init {
        var revision = gestor.estado.value.revision
        var rol = gestor.estado.value.sesion?.usuario?.role
        viewModelScope.launch {
            gestor.estado.collect {
                val nuevoRol = it.sesion?.usuario?.role
                if (revision != it.revision || rol != nuevoRol) limpiarEstado()
                revision = it.revision
                rol = nuevoRol
            }
        }
    }
}
