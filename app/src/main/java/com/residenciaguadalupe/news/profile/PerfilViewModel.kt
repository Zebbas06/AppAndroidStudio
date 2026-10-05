package com.residenciaguadalupe.news.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.auth.domain.validarCredencial
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.PreferenciasDto
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.profile.data.RepositorioPerfil
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class EstadoPerfil(
    val usuario: UserDto? = null,
    val escala: Escala = Escala.NORMAL,
    val preferencias: PreferenciasDto = PreferenciasDto(),
    val preferenciasCargando: Boolean = false,
    val preferenciasGuardando: Boolean = false,
    val preferenciasError: ErrorApi? = null,
    val perfilGuardando: Boolean = false,
    val perfilError: String? = null,
    val mensaje: String? = null
) {
    val esResidente get() = usuario?.rol == Rol.RESIDENTE
    val esAdmin get() = usuario?.rol == Rol.ADMIN
}

class PerfilViewModel(
    private val repo: RepositorioPerfil,
    private val gestor: GestorSesion
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoPerfil(
        usuario = gestor.estado.value.sesion?.usuario,
        preferencias = gestor.estado.value.sesion?.usuario?.preferences ?: PreferenciasDto()
    ))
    val estado = mutableEstado.asStateFlow()
    private var confirmadas = estado.value.preferencias
    private var deseadas = confirmadas
    private var versionCambios = 0L
    private var generacion = 0L
    private var carga = 0L
    private var trabajoCarga: Job? = null
    private var trabajoPreferencias: Job? = null
    private var trabajoPerfil: Job? = null

    /** Accesibilidad local: conserva la escala entre cuentas, no viaja a la API. */
    fun cambiarEscala(escala: Escala) { mutableEstado.update { it.copy(escala = escala) } }

    fun cargarPreferencias() {
        if (!residente() || estado.value.preferenciasGuardando) return
        val turno = ++carga
        val version = versionCambios
        trabajoCarga?.cancel()
        mutableEstado.update { it.copy(preferenciasCargando = true, preferenciasError = null) }
        trabajoCarga = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.preferencias()
                coroutineContext.ensureActive()
                if (turno == carga && version == versionCambios) {
                    confirmadas = p
                    deseadas = p
                    mutableEstado.update { it.copy(preferencias = p) }
                }
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == carga) mutableEstado.update { it.copy(preferenciasError = e) }
            } finally {
                if (turno == carga) mutableEstado.update { it.copy(preferenciasCargando = false) }
            }
        }
    }

    fun cambiarPref(clave: String, valor: Boolean) {
        if (!residente()) return
        val nuevas = when (clave) {
            "avisos" -> deseadas.copy(notifyNotices = valor)
            "actividades" -> deseadas.copy(notifyActivities = valor)
            "fotos" -> deseadas.copy(notifyPhotos = valor)
            else -> return
        }
        if (nuevas == deseadas) return
        deseadas = nuevas
        versionCambios++
        carga++
        trabajoCarga?.cancel()
        mutableEstado.update { it.copy(preferencias = nuevas, preferenciasError = null, preferenciasCargando = false) }
        if (trabajoPreferencias?.isActive == true) return
        val turno = generacion
        mutableEstado.update { it.copy(preferenciasGuardando = true) }
        trabajoPreferencias = gestor.lanzar(viewModelScope) {
            try {
                while (deseadas != confirmadas) {
                    val version = versionCambios
                    val objetivo = deseadas
                    try {
                        val p = repo.guardarPreferencias(objetivo)
                        coroutineContext.ensureActive()
                        confirmadas = p
                        if (version == versionCambios) deseadas = p
                        mutableEstado.update { it.copy(preferencias = deseadas, preferenciasError = null) }
                    } catch (e: ErrorApi) {
                        coroutineContext.ensureActive()
                        if (version == versionCambios) {
                            deseadas = confirmadas
                            mutableEstado.update { it.copy(preferencias = confirmadas, preferenciasError = e) }
                            break
                        }
                        // Hubo cambios nuevos: intentar su estado completo, no restaurar una copia vieja.
                    }
                }
            } finally {
                if (turno == generacion) mutableEstado.update { it.copy(preferenciasGuardando = false) }
            }
        }
    }

    private fun residente() = gestor.estado.value.sesion?.usuario?.rol == Rol.RESIDENTE
    fun limpiarErrorPerfil() { mutableEstado.update { it.copy(perfilError = null) } }
    fun ocultarMensaje() { mutableEstado.update { it.copy(mensaje = null) } }

    fun cambiarNombre(nombre: String, alTerminar: () -> Unit) {
        if (nombre.trim().length !in 2..150) {
            mutableEstado.update { it.copy(perfilError = "Escriba su nombre completo.") }
            return
        }
        guardarPerfil("Su nombre se actualizó.", alTerminar) { repo.cambiarNombre(nombre) }
    }

    fun cambiarCredencial(actual: String, metodo: String, nuevo: String, alTerminar: () -> Unit) {
        val error = if (actual.isEmpty()) "Escriba su contraseña o PIN actual."
            else validarCredencial(metodo, nuevo, nueva = true)
        if (error != null) { mutableEstado.update { it.copy(perfilError = error) }; return }
        guardarPerfil(if (metodo == "pin") "Su PIN se cambió." else "Su contraseña se cambió.", alTerminar) {
            repo.cambiarCredencial(actual, metodo, nuevo)
        }
    }

    private fun guardarPerfil(mensaje: String, alTerminar: () -> Unit, guardar: suspend () -> UserDto) {
        if (gestor.estado.value.sesion == null || estado.value.perfilGuardando) return
        val turno = generacion
        mutableEstado.update { it.copy(perfilGuardando = true, perfilError = null) }
        trabajoPerfil = gestor.lanzar(viewModelScope) {
            try {
                val usuario = guardar()
                coroutineContext.ensureActive()
                mutableEstado.update { it.copy(usuario = usuario, mensaje = mensaje) }
                alTerminar()
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                mutableEstado.update { it.copy(perfilError = e.mensajeCompleto) }
            } finally {
                if (turno == generacion) mutableEstado.update { it.copy(perfilGuardando = false) }
            }
        }
    }

    private fun limpiar() {
        generacion++
        carga++
        trabajoCarga?.cancel()
        trabajoPreferencias?.cancel()
        trabajoPerfil?.cancel()
        versionCambios = 0
        confirmadas = gestor.estado.value.sesion?.usuario?.preferences ?: PreferenciasDto()
        deseadas = confirmadas
        mutableEstado.value = EstadoPerfil(
            usuario = gestor.estado.value.sesion?.usuario, preferencias = confirmadas, escala = estado.value.escala
        )
    }

    init {
        var revision = gestor.estado.value.revision
        var residenteAnterior = residente()
        viewModelScope.launch {
            gestor.estado.collect {
                if (revision != it.revision || residenteAnterior != residente()) limpiar()
                revision = it.revision
                residenteAnterior = residente()
                // Las actualizaciones del usuario no reemplazan elecciones pendientes.
                mutableEstado.update { e -> e.copy(usuario = it.sesion?.usuario) }
            }
        }
    }
}
