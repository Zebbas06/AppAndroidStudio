package com.residenciaguadalupe.news.core.session

import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Coordina transiciones y tareas; no conoce pantallas ni transporte HTTP. */
class GestorSesion(
    private val almacen: AlmacenSesion,
    private val limpiarPrivados: () -> Unit = {}
) {
    private val cerrojo = Any()
    private val mutableEstado = MutableStateFlow(EstadoSesion(almacen.leer()))
    val estado = mutableEstado.asStateFlow()
    private val trabajos = mutableSetOf<Job>()

    fun iniciar(sesion: Sesion, revisionEsperada: Long = estado.value.revision) = synchronized(cerrojo) {
        comprobarRevision(revisionEsperada)
        cancelarTrabajos()
        limpiarPrivados()
        almacen.guardar(sesion)
        mutableEstado.value = EstadoSesion(sesion, estado.value.revision + 1)
    }

    /** Devuelve la sesion anterior para poder revocarla despues del cierre local. */
    fun cerrar(motivo: String? = null): Sesion? = synchronized(cerrojo) {
        cerrarDentroDelCerrojo(motivo)
    }

    fun invalidar(token: String, motivo: String): Boolean = synchronized(cerrojo) {
        if (estado.value.sesion?.token != token) return false
        cerrarDentroDelCerrojo(motivo)
        true
    }

    fun actualizarUsuario(token: String, usuario: UserDto) = synchronized(cerrojo) {
        val actual = estado.value.sesion
        if (actual?.token != token) throw CancellationException("La sesion cambio.")
        val nueva = actual.copy(usuario = usuario)
        almacen.guardar(nueva)
        mutableEstado.value = estado.value.copy(sesion = nueva)
    }

    /** Usa el mismo cerrojo para publicar un archivo y para limpiar al cerrar. */
    fun <T> conSesionActual(token: String, bloque: () -> T): T = synchronized(cerrojo) {
        if (estado.value.sesion?.token != token) throw CancellationException("La sesion cambio.")
        bloque()
    }

    fun lanzar(ambito: CoroutineScope, bloque: suspend CoroutineScope.() -> Unit): Job {
        val trabajo = synchronized(cerrojo) {
            val revision = estado.value.revision
            ambito.launch(start = CoroutineStart.LAZY) {
                coroutineContext.ensureActive()
                synchronized(cerrojo) { comprobarRevision(revision) }
                bloque()
            }.also { trabajos.add(it) }
        }
        trabajo.invokeOnCompletion { synchronized(cerrojo) { trabajos.remove(trabajo) } }
        trabajo.start()
        return trabajo
    }

    private fun cerrarDentroDelCerrojo(motivo: String?): Sesion? {
        val anterior = estado.value.sesion
        cancelarTrabajos()
        almacen.borrar()
        limpiarPrivados()
        mutableEstado.value = EstadoSesion(null, estado.value.revision + 1, motivo)
        return anterior
    }

    private fun cancelarTrabajos() {
        trabajos.toList().forEach { it.cancel() }
        trabajos.clear()
    }

    private fun comprobarRevision(revision: Long) {
        if (estado.value.revision != revision) throw CancellationException("La sesion cambio.")
    }
}
