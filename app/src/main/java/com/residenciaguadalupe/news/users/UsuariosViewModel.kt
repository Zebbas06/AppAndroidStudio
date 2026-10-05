package com.residenciaguadalupe.news.users

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.residenciaguadalupe.news.users.data.RepositorioUsuarios
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.auth.domain.validarCredencial
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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
class UsuariosViewModel(
    private val repo: RepositorioUsuarios,
    private val gestor: GestorSesion
) : ViewModel() {
    private fun esAdmin() = gestor.estado.value.sesion?.usuario?.rol == Rol.ADMIN
    private var solicitudLista = 0L
    private var formulario = 0L
    private var trabajoUsuario: Job? = null
    private var trabajoGuardado: Job? = null

    // --- Lista ---------------------------------------------------------------

    /** null = todos. Empieza en pendientes: es lo que recepcion atiende. */
    var filtroEstado by mutableStateOf<EstadoCuenta?>(EstadoCuenta.PENDIENTE)
        private set
    var busqueda by mutableStateOf("")
        private set
    private val listaUsuarios = mutableStateListOf<UserDto>()
    val usuarios: List<UserDto> get() = listaUsuarios
    var listaCargando by mutableStateOf(false)
        private set
    var listaError by mutableStateOf<ErrorApi?>(null)
        private set
    var listaHayMas by mutableStateOf(false)
        private set
    private var listaPagina = 1
    private var trabajoLista: Job? = null
    private var busquedaCargada = ""

    fun cambiarFiltro(e: EstadoCuenta?) { filtroEstado = e; listaUsuarios.clear(); cargar() }
    fun cambiarBusqueda(v: String) { busqueda = v.take(100) }

    fun cargar(reiniciar: Boolean = true) {
        if (!esAdmin()) return
        if (!reiniciar && busqueda.trim() != busquedaCargada) { cargar(); return }
        if (!reiniciar && (listaCargando || !listaHayMas)) return
        val turno = ++solicitudLista
        val estadoConsulta = filtroEstado
        val busquedaConsulta = busqueda
        trabajoLista?.cancel()
        val pagina = if (reiniciar) 1 else listaPagina + 1
        listaCargando = true; if (reiniciar) listaError = null
        trabajoLista = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.usuarios(estadoConsulta, busquedaConsulta, pagina)
                coroutineContext.ensureActive()
                if (turno != solicitudLista) return@lanzar
                if (reiniciar) listaUsuarios.clear()
                busquedaCargada = busquedaConsulta.trim()
                listaUsuarios.addAll(p.elementos.filter { n -> usuarios.none { it.id == n.id } })
                listaPagina = p.pagina; listaHayMas = p.hayMas
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == solicitudLista) listaError = e
            } finally {
                if (turno == solicitudLista) listaCargando = false
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

    fun setNombre(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fNombre = v.take(150) }
    fun setCorreo(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fCorreo = v.trim() }
    fun setRol(v: Rol) { if (guardando || cargandoUsuario || !esAdmin()) return; fRol = v }
    fun setEstado(v: EstadoCuenta) { if (guardando || cargandoUsuario || !esAdmin()) return; if (editando?.status == "pending") return; fEstado = v }
    fun setMetodo(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fMetodo = v; fSecreto = "" }
    fun setSecreto(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fSecreto = if (fMetodo == "pin") v.filter(Char::isDigit).take(6) else v.take(128) }
    fun setHabitacion(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fHabitacion = v.take(50) }
    fun setIngreso(v: LocalDate?) { if (guardando || cargandoUsuario || !esAdmin()) return; fIngreso = v }
    fun setUbicacion(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fUbicacion = v.take(150) }
    fun setTelefono(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fTelefono = v.filter(Char::isDigit).take(10) }
    fun setFamiliarNombre(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fFamiliarNombre = v.take(150) }
    fun setFamiliarParentesco(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fFamiliarParentesco = v.take(50) }
    fun setFamiliarTelefono(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fFamiliarTelefono = v.filter(Char::isDigit).take(10) }
    fun setArea(v: String) { if (guardando || cargandoUsuario || !esAdmin()) return; fArea = v.take(120) }

    fun nuevo() {
        formulario++
        trabajoUsuario?.cancel(); trabajoGuardado?.cancel()
        cargandoUsuario = false; guardando = false
        editando = null
        fNombre = ""; fCorreo = ""; fRol = Rol.RESIDENTE; fEstado = EstadoCuenta.ACTIVA
        fMetodo = "password"; fSecreto = ""
        llenarPerfil(null, null)
        fError = ""; guardado = null
    }

    /** Carga el usuario fresco (GET users/{id}) antes de editar. */
    fun abrir(id: String) {
        if (!esAdmin()) return
        nuevo()
        val turno = formulario
        cargandoUsuario = true
        trabajoUsuario = gestor.lanzar(viewModelScope) {
            try {
                val u = repo.usuario(id)
                coroutineContext.ensureActive()
                if (turno == formulario) llenar(u)
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == formulario) fError = e.mensaje
            } finally {
                if (turno == formulario) cargandoUsuario = false
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
    fun aprobar() {
        if (editando?.status == "pending") guardar(confirmar = true)
    }

    fun guardar() = guardar(confirmar = false)

    private fun guardar(confirmar: Boolean) {
        if (!esAdmin() || guardando || cargandoUsuario) return
        val turno = formulario
        fError = ""; guardado = null
        val u = editando
        val estadoObjetivo = when {
            confirmar -> EstadoCuenta.ACTIVA
            u?.status == "pending" -> EstadoCuenta.PENDIENTE
            else -> fEstado
        }
        val cuerpoError = runCatching {
            if (fNombre.trim().length < 2) error("Escriba el nombre completo.")
            val residente = fRol == Rol.RESIDENTE
            // Un residente activo necesita su perfil; si ya lo tiene, omitirlo lo conserva.
            val obligatorio = residente && estadoObjetivo == EstadoCuenta.ACTIVA && u?.residentProfile == null
            val perfil = if (residente) perfilResidente(obligatorio) else null
            val personal = if (!residente) PerfilPersonalRequest(fArea.trim().ifEmpty { null }) else null
            if (u == null) {
                if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(fCorreo)) error("Revise el correo electrónico.")
                validarSecreto()?.let { error(it) }
                CrearUsuarioRequest(fNombre.trim(), fCorreo, fRol.api, fMetodo, fSecreto, perfil, personal)
            } else {
                EditarUsuarioRequest(fNombre.trim(), fRol.api, estadoObjetivo.api, perfil, personal)
            }
        }
        val cuerpo = cuerpoError.getOrElse { fError = it.message.orEmpty(); return }
        guardando = true
        trabajoGuardado = gestor.lanzar(viewModelScope) {
            try {
                val r = when (cuerpo) {
                    is CrearUsuarioRequest -> repo.crearUsuario(cuerpo)
                    is EditarUsuarioRequest -> repo.editarUsuario(u!!.id, cuerpo)
                    else -> error("cuerpo")
                }
                coroutineContext.ensureActive()
                if (turno != formulario) return@lanzar
                guardado = if (u == null) "Se dio de alta a ${r.fullName}." else "Se guardaron los cambios de ${r.fullName}."
                llenar(r)
                // La escritura cambia filtros y offsets; el listado se recarga al volver.
                solicitudLista++
                trabajoLista?.cancel()
                listaCargando = false; listaError = null
                listaUsuarios.clear(); listaHayMas = false
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == formulario) fError = e.mensajeCompleto
            } finally {
                if (turno == formulario) guardando = false
            }
        }
    }

    private fun validarSecreto() = validarCredencial(fMetodo, fSecreto, nueva = true)

    /** Reposicion manual en recepcion: revoca todas las sesiones de esa cuenta. */
    fun reponerCredencial() {
        val u = editando ?: return
        if (!esAdmin() || guardando || cargandoUsuario) return
        val turno = formulario
        val metodo = fMetodo
        val secreto = fSecreto
        validarSecreto()?.let { fError = it; return }
        fError = ""; guardado = null; guardando = true
        trabajoGuardado = gestor.lanzar(viewModelScope) {
            try {
                repo.reponerCredencial(u.id, metodo, secreto)
                coroutineContext.ensureActive()
                if (turno != formulario) return@lanzar
                editando = u.copy(authMethod = metodo)
                fSecreto = ""
                guardado = if (fMetodo == "pin") "Se repuso el PIN. Entrégueselo en persona." else "Se repuso la contraseña. Entréguesela en persona."
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == formulario) fError = e.mensajeCompleto
            } finally {
                if (turno == formulario) guardando = false
            }
        }
    }

    private fun limpiarEstado() {
        solicitudLista++
        trabajoLista?.cancel()
        listaUsuarios.clear(); filtroEstado = EstadoCuenta.PENDIENTE; busqueda = ""
        listaCargando = false; listaError = null; listaHayMas = false; listaPagina = 1
        trabajoLista = null; cargandoUsuario = false; guardando = false
        nuevo()
    }

    init {
        var revision = gestor.estado.value.revision
        var admin = esAdmin()
        viewModelScope.launch {
            gestor.estado.collect { estado ->
                if (estado.revision != revision || admin != esAdmin()) limpiarEstado()
                admin = esAdmin()
                revision = estado.revision
            }
        }
    }
}
