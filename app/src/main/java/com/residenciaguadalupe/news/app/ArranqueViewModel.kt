package com.residenciaguadalupe.news.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.auth.data.RepositorioAutenticacion
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class EstadoArranque(
    val restaurando: Boolean = true,
    val usuario: UserDto? = null,
    val revisionSesion: Long = 0
)

/** Solo arranque y estado de sesion para el grafo; no tiene formularios. */
class ArranqueViewModel(
    private val auth: RepositorioAutenticacion,
    private val gestor: GestorSesion
) : ViewModel() {
    private val revisionInicial = gestor.estado.value.revision
    private val mutableEstado = MutableStateFlow(EstadoArranque(revisionSesion = revisionInicial))
    val estado = mutableEstado.asStateFlow()

    fun cerrarSesion() = auth.salir()

    private fun restaurar() {
        val s = gestor.estado.value.sesion
        if (s == null) { mutableEstado.update { it.copy(restaurando = false) }; return }
        if (s.vencida()) {
            gestor.cerrar()
            mutableEstado.update { it.copy(restaurando = false) }
            return
        }
        gestor.lanzar(viewModelScope) {
            try {
                auth.yo()
                coroutineContext.ensureActive()
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (e.estado == 401 || e.estado == 403) gestor.invalidar(s.token, e.mensaje)
                // Sin red se conserva la cuenta guardada, como en el flujo anterior.
            } finally {
                mutableEstado.update { it.copy(restaurando = false) }
            }
        }
    }

    init {
        viewModelScope.launch {
            gestor.estado.collect { sesion ->
                mutableEstado.update { it.copy(
                    usuario = sesion.sesion?.usuario,
                    revisionSesion = sesion.revision,
                    restaurando = it.restaurando && sesion.sesion != null && sesion.revision == revisionInicial
                ) }
            }
        }
        restaurar()
    }
}
