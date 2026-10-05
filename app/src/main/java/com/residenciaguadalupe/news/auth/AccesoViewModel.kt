package com.residenciaguadalupe.news.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.auth.data.RepositorioAutenticacion
import com.residenciaguadalupe.news.auth.domain.validarAcceso
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.red.ErrorApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class EstadoAcceso(
    val cargando: Boolean = false,
    val error: String? = null,
    val pendiente: Boolean = false,
    val aviso: String? = null,
    val registroListo: Boolean = false
)

/** Una instancia por pantalla. Las credenciales no se guardan en su estado. */
class AccesoViewModel(
    private val auth: RepositorioAutenticacion,
    private val gestor: GestorSesion
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoAcceso(aviso = gestor.estado.value.motivo))
    val estado = mutableEstado.asStateFlow()
    private var solicitud = 0L
    private var trabajo: Job? = null

    fun limpiarAcceso() {
        solicitud++
        trabajo?.cancel()
        mutableEstado.value = EstadoAcceso()
    }

    fun entrar(correo: String, metodo: String, secreto: String) =
        enviar(null, correo, metodo, secreto, registro = false)

    fun registrar(nombre: String, correo: String, metodo: String, secreto: String) =
        enviar(nombre, correo, metodo, secreto, registro = true)

    private fun enviar(nombre: String?, correo: String, metodo: String, secreto: String, registro: Boolean) {
        if (estado.value.cargando || gestor.estado.value.sesion != null) return
        val error = validarAcceso(nombre, correo, metodo, secreto, registro)
        if (error != null) {
            mutableEstado.update { it.copy(error = error) }
            return
        }
        val turno = ++solicitud
        mutableEstado.value = EstadoAcceso(cargando = true)
        trabajo = gestor.lanzar(viewModelScope) {
            try {
                if (registro) auth.registrar(nombre!!, correo, metodo, secreto)
                else auth.entrar(correo, metodo, secreto)
                coroutineContext.ensureActive()
                if (turno == solicitud && registro) mutableEstado.update { it.copy(registroListo = true) }
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == solicitud) mutableEstado.update {
                    if (!registro && e.cuentaPendiente) it.copy(pendiente = true)
                    else it.copy(error = e.mensajeCompleto)
                }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargando = false) }
            }
        }
    }

    init {
        var revision = gestor.estado.value.revision
        viewModelScope.launch {
            gestor.estado.collect {
                if (it.revision != revision) {
                    limpiarAcceso()
                    mutableEstado.update { e -> e.copy(aviso = if (it.sesion == null) it.motivo else null) }
                }
                revision = it.revision
            }
        }
    }
}
