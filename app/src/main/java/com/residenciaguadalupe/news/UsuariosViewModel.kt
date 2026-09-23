package com.residenciaguadalupe.news

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Gestion de usuarios (solo admin): aprobar registros pendientes con su
 * perfil, dar de alta, editar, deshabilitar y reponer la credencial.
 */
class UsuariosViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AplicacionResidencia).repositorio

    // --- Lista ---------------------------------------------------------------

    /** null = todos. Empieza en pendientes: es lo que recepcion atiende. */
    var filtroEstado by mutableStateOf<EstadoCuenta?>(EstadoCuenta.PENDIENTE)
        private set
    var busqueda by mutableStateOf("")
        private set
    val usuarios = mutableStateListOf<UserDto>()
    var listaCargando by mutableStateOf(false)
        private set
    var listaError by mutableStateOf<ErrorApi?>(null)
        private set
    var listaHayMas by mutableStateOf(false)
        private set
    private var listaPagina = 1
    private var trabajoLista: Job? = null

    fun cambiarFiltro(e: EstadoCuenta?) { filtroEstado = e; cargar() }
    fun cambiarBusqueda(v: String) { busqueda = v.take(100) }

    fun cargar(reiniciar: Boolean = true) {
        if (!reiniciar && (listaCargando || !listaHayMas)) return
        trabajoLista?.cancel()
        val pagina = if (reiniciar) 1 else listaPagina + 1
        listaCargando = true; if (reiniciar) listaError = null
        trabajoLista = viewModelScope.launch {
            try {
                val p = repo.usuarios(filtroEstado, busqueda, pagina)
                if (reiniciar) usuarios.clear()
                usuarios.addAll(p.elementos.filter { n -> usuarios.none { it.id == n.id } })
                listaPagina = p.pagina; listaHayMas = p.hayMas
            } catch (e: ErrorApi) {
                listaError = e
            } finally {
                listaCargando = false
            }
        }
    }

    // --- Formulario ----------------------------------------------------------

    /** null = alta nueva. */
    var editando by mutableStateOf<UserDto?>(null); private set
    var cargandoUsuario by mutableStateOf(false); private set
    var fNombre by mutableStateOf(""); private set
    var fCorreo by mutableStateOf(""); private set
    var fRol by mutableStateOf(Rol.RESIDENTE); private set
    var fEstado by mutableStateOf(EstadoCuenta.ACTIVA); private set
    var fMetodo by mutableStateOf("password"); private set
    var fSecreto by mutableStateOf(""); private set
    // Perfil de residente
    var fHabitacion by mutableStateOf(""); private set
    var fIngreso by mutableStateOf<LocalDate?>(null); private set
    var fUbicacion by mutableStateOf(""); private set
    var fTelefono by mutableStateOf(""); private set
    var fFamiliarNombre by mutableStateOf(""); private set
    var fFamiliarParentesco by mutableStateOf(""); private set
    var fFamiliarTelefono by mutableStateOf(""); private set
    // Perfil de personal
    var fArea by mutableStateOf(""); private set

    var guardando by mutableStateOf(false); private set
    var fError by mutableStateOf(""); private set
    var guardado by mutableStateOf<String?>(null); private set

    fun setNombre(v: String) { fNombre = v.take(150) }
    fun setCorreo(v: String) { fCorreo = v.trim() }
    fun setRol(v: Rol) { fRol = v }
    fun setEstado(v: EstadoCuenta) { fEstado = v }
    fun setMetodo(v: String) { fMetodo = v; fSecreto = "" }
    fun setSecreto(v: String) { fSecreto = if (fMetodo == "pin") v.filter(Char::isDigit).take(6) else v.take(128) }
    fun setHabitacion(v: String) { fHabitacion = v.take(50) }
    fun setIngreso(v: LocalDate?) { fIngreso = v }
    fun setUbicacion(v: String) { fUbicacion = v.take(150) }
    fun setTelefono(v: String) { fTelefono = v.filter(Char::isDigit).take(10) }
    fun setFamiliarNombre(v: String) { fFamiliarNombre = v.take(150) }
    fun setFamiliarParentesco(v: String) { fFamiliarParentesco = v.take(50) }
    fun setFamiliarTelefono(v: String) { fFamiliarTelefono = v.filter(Char::isDigit).take(10) }
    fun setArea(v: String) { fArea = v.take(120) }

    fun nuevo() {
        editando = null
        fNombre = ""; fCorreo = ""; fRol = Rol.RESIDENTE; fEstado = EstadoCuenta.ACTIVA
        fMetodo = "password"; fSecreto = ""
        llenarPerfil(null, null)
        fError = ""; guardado = null
    }

    /** Carga el usuario fresco (GET users/{id}) antes de editar. */
    fun abrir(id: String) {
        editando = null; fError = ""; guardado = null; cargandoUsuario = true
        viewModelScope.launch {
            try {
                llenar(repo.usuario(id))
            } catch (e: ErrorApi) {
                fError = e.mensaje
            } finally {
                cargandoUsuario = false
            }
        }
    }

    private fun llenar(u: UserDto) {
        editando = u
        fNombre = u.fullName; fCorreo = u.email
        fRol = u.rol; fEstado = EstadoCuenta.desdeApi(u.status)
        fMetodo = u.authMethod; fSecreto = ""
        llenarPerfil(u.residentProfile, u.staffProfile)
    }

    private fun llenarPerfil(r: ResidentProfileDto?, s: StaffProfileDto?) {
        fHabitacion = r?.department.orEmpty()
        fIngreso = Fechas.fecha(r?.admissionDate)
        fUbicacion = r?.roomLocation.orEmpty()
        fTelefono = r?.phone.orEmpty()
        fFamiliarNombre = r?.familyContact?.fullName.orEmpty()
        fFamiliarParentesco = r?.familyContact?.relationship.orEmpty()
        fFamiliarTelefono = r?.familyContact?.phone.orEmpty()
        fArea = s?.area.orEmpty()
    }

    /**
     * Perfil de residente para enviar, o null para omitirlo (se conserva el
     * que haya). Lanza el mensaje de error si esta a medias.
     */
    private fun perfilResidente(obligatorio: Boolean): PerfilResidenteRequest? {
        val vacio = fHabitacion.isBlank() && fIngreso == null && fUbicacion.isBlank() && fTelefono.isBlank() &&
            fFamiliarNombre.isBlank() && fFamiliarParentesco.isBlank() && fFamiliarTelefono.isBlank()
        if (vacio && !obligatorio) return null
        if (fHabitacion.isBlank()) error("Escriba la habitación del residente.")
        val ingreso = fIngreso ?: error("Elija la fecha de ingreso.")
        if (fTelefono.isNotEmpty() && fTelefono.length != 10) error("El teléfono debe tener 10 dígitos.")
        val hayFamiliar = fFamiliarNombre.isNotBlank() || fFamiliarParentesco.isNotBlank() || fFamiliarTelefono.isNotBlank()
        val familiar = if (!hayFamiliar) null else {
            if (fFamiliarNombre.trim().length < 2 || fFamiliarParentesco.isBlank() || fFamiliarTelefono.length != 10)
                error("Del familiar se necesitan nombre, parentesco y un teléfono de 10 dígitos.")
            FamiliarDto(fFamiliarNombre.trim(), fFamiliarParentesco.trim(), fFamiliarTelefono)
        }
        return PerfilResidenteRequest(
            department = fHabitacion.trim(),
            admissionDate = ingreso.toString(),
            phone = fTelefono.ifEmpty { null },
            roomLocation = fUbicacion.trim().ifEmpty { null },
            familyContact = familiar
        )
    }

    /** Aprobar = activar, con la habitacion y la fecha de ingreso completas. */
    fun aprobar() { fEstado = EstadoCuenta.ACTIVA; guardar() }

    fun guardar() {
        if (guardando) return
        fError = ""; guardado = null
        val u = editando
        val cuerpoError = runCatching {
            if (fNombre.trim().length < 2) error("Escriba el nombre completo.")
            val residente = fRol == Rol.RESIDENTE
            // Un residente activo necesita su perfil; si ya lo tiene, omitirlo lo conserva.
            val obligatorio = residente && fEstado == EstadoCuenta.ACTIVA && u?.residentProfile == null
            val perfil = if (residente) perfilResidente(obligatorio) else null
            val personal = if (!residente) PerfilPersonalRequest(fArea.trim().ifEmpty { null }) else null
            if (u == null) {
                if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(fCorreo)) error("Revise el correo electrónico.")
                validarSecreto()?.let { error(it) }
                CrearUsuarioRequest(fNombre.trim(), fCorreo, fRol.api, fMetodo, fSecreto, perfil, personal)
            } else {
                EditarUsuarioRequest(fNombre.trim(), fRol.api, fEstado.api, perfil, personal)
            }
        }
        val cuerpo = cuerpoError.getOrElse { fError = it.message.orEmpty(); return }
        guardando = true
        viewModelScope.launch {
            try {
                val r = when (cuerpo) {
                    is CrearUsuarioRequest -> repo.crearUsuario(cuerpo)
                    is EditarUsuarioRequest -> repo.editarUsuario(u!!.id, cuerpo)
                    else -> error("cuerpo")
                }
                guardado = if (u == null) "Se dio de alta a ${r.fullName}." else "Se guardaron los cambios de ${r.fullName}."
                llenar(r)
                val i = usuarios.indexOfFirst { it.id == r.id }
                if (i >= 0) {
                    // Si ya no corresponde al filtro (p. ej. se aprobo un pendiente), sale de la lista.
                    if (filtroEstado != null && r.status != filtroEstado!!.api) usuarios.removeAt(i) else usuarios[i] = r
                } else if (filtroEstado == null || r.status == filtroEstado!!.api) usuarios.add(0, r)
            } catch (e: ErrorApi) {
                fError = e.mensajeCompleto
            } finally {
                guardando = false
            }
        }
    }

    private fun validarSecreto(): String? = when {
        fMetodo == "pin" && fSecreto.length !in 4..6 -> "El PIN debe tener de 4 a 6 números."
        fMetodo == "password" && fSecreto.length !in 8..128 -> "La contraseña debe tener al menos 8 caracteres."
        else -> null
    }

    /** Reposicion manual en recepcion: revoca todas las sesiones de esa cuenta. */
    fun reponerCredencial() {
        val u = editando ?: return
        if (guardando) return
        validarSecreto()?.let { fError = it; return }
        fError = ""; guardado = null; guardando = true
        viewModelScope.launch {
            try {
                repo.reponerCredencial(u.id, fMetodo, fSecreto)
                fSecreto = ""
                guardado = if (fMetodo == "pin") "Se repuso el PIN. Entrégueselo en persona." else "Se repuso la contraseña. Entréguesela en persona."
            } catch (e: ErrorApi) {
                fError = e.mensajeCompleto
            } finally {
                guardando = false
            }
        }
    }
}
