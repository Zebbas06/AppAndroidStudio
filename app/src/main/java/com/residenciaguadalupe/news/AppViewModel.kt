package com.residenciaguadalupe.news

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.PreferenciasDto
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.data.repositorio.FiltroPublicaciones
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

/**
 * Estado compartido de la sesion y de las pantallas del residente: acceso,
 * muro, agenda, detalle, notificaciones, perfil y archivo.
 * Las pantallas de administracion usan AdminViewModel y UsuariosViewModel.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val aplicacion = app as AplicacionResidencia
    private val repo = aplicacion.repositorio
    private val almacen = aplicacion.sesion

    // --- Sesion --------------------------------------------------------------

    /** true mientras se valida la sesion guardada con GET auth/me. */
    var restaurando by mutableStateOf(true)
        private set
    /** null = sin sesion. Lo actualiza el almacen, tambien ante un 401. */
    var usuario by mutableStateOf<UserDto?>(null)
        private set
    val esResidente get() = usuario?.rol == Rol.RESIDENTE
    val esPersonal get() = usuario?.esPersonal == true
    val esAdmin get() = usuario?.rol == Rol.ADMIN

    /**
     * Token guardado y no vencido -> GET auth/me. Un 401 lo cierra el
     * interceptor; sin red se entra con el usuario guardado.
     */
    private fun restaurar() {
        val s = almacen.sesion.value
        if (s == null) { restaurando = false; return }
        if (s.vencida()) { repo.cerrarLocal(null); restaurando = false; return }
        viewModelScope.launch {
            try {
                repo.yo()
            } catch (e: ErrorApi) {
                if (e.estado == 403) repo.cerrarLocal(e.mensaje)
            }
            restaurando = false
            if (usuario != null) alEntrar()
        }
    }

    private fun alEntrar() {
        muroDesactualizado = false
        cargarMuro()
        cargarBandaHoy()
        if (esResidente) {
            actualizarNoLeidas()
            cargarPreferencias()
        }
    }

    private fun limpiarEstado() {
        publicaciones.clear(); muroError = null; filtro = "TODO"
        bandaHoy.clear(); actividades.clear()
        detalle = null; notificaciones.clear(); sinLeer = 0
        archivo.clear(); preferencias = PreferenciasDto()
        leyendoEnVoz = false
    }

    fun cerrarSesion() {
        viewModelScope.launch { repo.salir() }
    }

    // --- Acceso --------------------------------------------------------------

    var accesoCargando by mutableStateOf(false)
        private set
    /** Error del formulario de acceso, listo para un Consejo. */
    var accesoError by mutableStateOf<String?>(null)
        private set
    /** Cuenta registrada que recepcion aun no aprueba (403 ACCOUNT_PENDING). */
    var accesoPendiente by mutableStateOf(false)
        private set
    /** Motivo por el que se cerro la sesion ("Su sesión terminó…"). */
    var accesoAviso by mutableStateOf<String?>(null)
        private set
    var registroListo by mutableStateOf(false)
        private set

    fun limpiarAcceso() {
        accesoError = null; accesoPendiente = false; accesoAviso = null; registroListo = false
    }

    /** Valida en el cliente lo que la API validaria igual. null = correcto. */
    fun validarAcceso(nombre: String?, correo: String, metodo: String, secreto: String, registro: Boolean): String? {
        if (nombre != null && nombre.trim().length !in 2..150) return "Escriba su nombre completo."
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo.trim())) return "Revise su correo electrónico."
        if (secreto.isEmpty()) return if (metodo == "pin") "Escriba su PIN." else "Escriba su contraseña."
        if (metodo == "pin" && !secreto.all { it.isDigit() }) return "El PIN solo lleva números."
        if (registro) {
            if (metodo == "pin" && secreto.length !in 4..6) return "El PIN debe tener de 4 a 6 números."
            if (metodo == "password" && secreto.length !in 8..128) return "La contraseña debe tener al menos 8 caracteres."
        }
        return null
    }

    fun entrar(correo: String, metodo: String, secreto: String) {
        if (accesoCargando) return
        validarAcceso(null, correo, metodo, secreto, registro = false)?.let { accesoError = it; return }
        accesoCargando = true; accesoError = null; accesoPendiente = false; accesoAviso = null
        viewModelScope.launch {
            try {
                repo.entrar(correo, metodo, secreto)
            } catch (e: ErrorApi) {
                if (e.cuentaPendiente) accesoPendiente = true else accesoError = e.mensajeCompleto
            } finally {
                accesoCargando = false
            }
        }
    }

    /** Registro 201 = resident/pending: se muestra la espera, no el muro. */
    fun registrar(nombre: String, correo: String, metodo: String, secreto: String) {
        if (accesoCargando) return
        validarAcceso(nombre, correo, metodo, secreto, registro = true)?.let { accesoError = it; return }
        accesoCargando = true; accesoError = null
        viewModelScope.launch {
            try {
                repo.registrar(nombre, correo, metodo, secreto)
                registroListo = true
            } catch (e: ErrorApi) {
                accesoError = e.mensajeCompleto
            } finally {
                accesoCargando = false
            }
        }
    }

    // --- Aviso emergente general ---------------------------------------------

    var mensaje by mutableStateOf<String?>(null)
        private set
    private var trabajoMensaje: Job? = null

    fun avisar(texto: String) {
        mensaje = texto
        trabajoMensaje?.cancel()
        trabajoMensaje = viewModelScope.launch { delay(4500); mensaje = null }
    }

    fun ocultarMensaje() { trabajoMensaje?.cancel(); mensaje = null }

    // --- Preferencias del residente ------------------------------------------

    /** El tamano de letra es local: no viaja a la API. */
    var escala by mutableStateOf(Escala.NORMAL)
        private set
    fun cambiarEscala(e: Escala) { escala = e }

    var preferencias by mutableStateOf(PreferenciasDto())
        private set

    private fun cargarPreferencias() {
        usuario?.let { preferencias = it.preferences }
        viewModelScope.launch { runCatching { repo.preferencias() }.onSuccess { preferencias = it } }
    }

    /** Guarda al instante; si falla, vuelve al valor anterior. */
    fun cambiarPref(clave: String, valor: Boolean) {
        val antes = preferencias
        val nuevas = when (clave) {
            "avisos" -> antes.copy(notifyNotices = valor)
            "actividades" -> antes.copy(notifyActivities = valor)
            "fotos" -> antes.copy(notifyPhotos = valor)
            else -> return
        }
        preferencias = nuevas
        viewModelScope.launch {
            try {
                preferencias = repo.guardarPreferencias(nuevas)
            } catch (e: ErrorApi) {
                preferencias = antes
                avisar(e.mensaje)
            }
        }
    }

    // --- Perfil: nombre y credencial -----------------------------------------

    var perfilGuardando by mutableStateOf(false)
        private set
    var perfilError by mutableStateOf<String?>(null)
        private set

    fun limpiarErrorPerfil() { perfilError = null }

    fun cambiarNombre(nombre: String, alTerminar: () -> Unit) {
        if (nombre.trim().length !in 2..150) { perfilError = "Escriba su nombre completo."; return }
        perfilGuardando = true; perfilError = null
        viewModelScope.launch {
            try {
                repo.cambiarNombre(nombre)
                avisar("Su nombre se actualizó.")
                alTerminar()
            } catch (e: ErrorApi) {
                perfilError = e.mensajeCompleto
            } finally {
                perfilGuardando = false
            }
        }
    }

    /** Revoca las demas sesiones y conserva esta. currentSecret mal = 422. */
    fun cambiarCredencial(actual: String, metodo: String, nuevo: String, alTerminar: () -> Unit) {
        if (actual.isEmpty()) { perfilError = "Escriba su contraseña o PIN actual."; return }
        validarAcceso(null, usuario?.email ?: "a@b.c", metodo, nuevo, registro = true)?.let { perfilError = it; return }
        perfilGuardando = true; perfilError = null
        viewModelScope.launch {
            try {
                repo.cambiarCredencial(actual, metodo, nuevo)
                avisar(if (metodo == "pin") "Su PIN se cambió." else "Su contraseña se cambió.")
                alTerminar()
            } catch (e: ErrorApi) {
                perfilError = e.mensajeCompleto
            } finally {
                perfilGuardando = false
            }
        }
    }

    // --- Muro ----------------------------------------------------------------

    /** El filtro activo del muro. "TODO", el nombre de una categoria o "FOTO". */
    var filtro by mutableStateOf("TODO")
        private set
    val publicaciones = mutableStateListOf<Publicacion>()
    var cargando by mutableStateOf(false)
        private set
    var cargandoMas by mutableStateOf(false)
        private set
    var muroError by mutableStateOf<ErrorApi?>(null)
        private set
    var muroHayMas by mutableStateOf(false)
        private set
    /** Se enciende cuando administracion cambio algo; MuroScreen recarga. */
    var muroDesactualizado by mutableStateOf(false)
        private set
    private var muroPagina = 1
    private var trabajoMuro: Job? = null

    private fun filtroMuro() = when (filtro) {
        "TODO" -> FiltroPublicaciones()
        // Fotografias no es una categoria: es hasImage=true.
        "FOTO" -> FiltroPublicaciones(conImagen = true)
        else -> FiltroPublicaciones(categoria = Categoria.valueOf(filtro))
    }

    fun cambiarFiltro(nuevo: String) {
        if (nuevo == filtro) return
        filtro = nuevo
        cargarMuro()
    }

    fun cargarMuro() {
        trabajoMuro?.cancel()
        cargando = true; muroError = null; muroDesactualizado = false
        trabajoMuro = viewModelScope.launch {
            try {
                val p = repo.publicaciones(filtroMuro(), 1)
                publicaciones.clear(); publicaciones.addAll(p.elementos)
                muroPagina = 1; muroHayMas = p.hayMas
            } catch (e: ErrorApi) {
                muroError = e
            } finally {
                cargando = false
            }
        }
    }

    fun cargarMasMuro() {
        if (cargandoMas || cargando || !muroHayMas) return
        cargandoMas = true
        trabajoMuro = viewModelScope.launch {
            try {
                val p = repo.publicaciones(filtroMuro(), muroPagina + 1)
                publicaciones.addAll(p.elementos.filter { n -> publicaciones.none { it.id == n.id } })
                muroPagina = p.pagina; muroHayMas = p.hayMas
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            } finally {
                cargandoMas = false
            }
        }
    }

    /** Si administracion cambio algo, recarga muro y banda al volver. */
    fun refrescarSiHaceFalta() {
        if (muroDesactualizado && usuario != null) {
            cargarMuro(); cargarBandaHoy()
        }
    }

    // --- Agenda --------------------------------------------------------------

    /** Las tres proximas actividades de hoy para la banda verde del muro. */
    val bandaHoy = mutableStateListOf<Actividad>()

    private fun filtroAgenda(p: PestanaAgenda): FiltroPublicaciones {
        val (desde, hasta) = p.rango()
        // eventFrom inclusivo, eventTo exclusivo; calculados en Mexico_City.
        return FiltroPublicaciones(
            tipo = TipoPublicacion.EVENTO,
            eventoDesde = Fechas.inicioDelDia(desde),
            eventoHasta = Fechas.inicioDelDia(hasta),
            orden = "eventStartAt"
        )
    }

    private fun cargarBandaHoy() {
        viewModelScope.launch {
            runCatching { repo.publicaciones(filtroAgenda(PestanaAgenda.HOY), 1, 20) }.onSuccess { p ->
                val ahora = Fechas.ahora()
                // Primero lo que aun no termina.
                val proximas = p.elementos.sortedBy { (it.eventoFin ?: it.eventoInicio)?.isBefore(ahora) == true }
                bandaHoy.clear(); bandaHoy.addAll(proximas.map { it.aActividad(conDia = false) })
            }
        }
    }

    var pestanaAgenda by mutableStateOf(PestanaAgenda.HOY)
        private set
    val actividades = mutableStateListOf<Actividad>()
    var agendaCargando by mutableStateOf(false)
        private set
    var agendaError by mutableStateOf<ErrorApi?>(null)
        private set
    private var trabajoAgenda: Job? = null

    fun cambiarPestana(p: PestanaAgenda) {
        pestanaAgenda = p
        cargarAgenda()
    }

    fun cargarAgenda() {
        val p = pestanaAgenda
        trabajoAgenda?.cancel()
        agendaCargando = true; agendaError = null
        trabajoAgenda = viewModelScope.launch {
            try {
                val r = repo.publicaciones(filtroAgenda(p), 1, 100)
                actividades.clear()
                actividades.addAll(r.elementos.map { it.aActividad(conDia = p == PestanaAgenda.SEMANA) })
            } catch (e: ErrorApi) {
                agendaError = e
            } finally {
                agendaCargando = false
            }
        }
    }

    // --- Detalle -------------------------------------------------------------

    var detalle by mutableStateOf<Publicacion?>(null)
        private set
    var detalleCargando by mutableStateOf(false)
        private set
    var detalleError by mutableStateOf<ErrorApi?>(null)
        private set
    var reaccionando by mutableStateOf(false)
        private set
    var abriendoAdjunto by mutableStateOf(false)
        private set

    /**
     * GET posts/{id}. Si es residente, PUT read: abrir el detalle es la
     * lectura real (GET no la registra). Tambien marca su notificacion.
     */
    fun abrirDetalle(id: String, forzar: Boolean = false) {
        if (!forzar && detalle?.id == id && detalleError == null) return
        detalle = null; detalleError = null; detalleCargando = true; leyendoEnVoz = false
        viewModelScope.launch {
            try {
                val p = repo.publicacion(id)
                detalle = p
                if (esResidente && !p.leida) {
                    runCatching { repo.marcarLeida(id) }.onSuccess {
                        detalle = detalle?.copy(leida = true)
                        reemplazar(id) { it.copy(leida = true) }
                        notificaciones.replaceAll { if (it.postId == id) it.copy(leida = true) else it }
                        actualizarNoLeidas()
                    }
                }
            } catch (e: ErrorApi) {
                detalleError = e
            } finally {
                detalleCargando = false
            }
        }
    }

    /** Tocar la reaccion que ya tiene la quita (DELETE devuelve 200). */
    fun reaccionar(tipo: String) {
        val p = detalle ?: return
        if (reaccionando) return
        reaccionando = true
        viewModelScope.launch {
            try {
                val r = repo.reaccionar(p.id, if (p.reacciones.mia == tipo) null else tipo)
                detalle = detalle?.copy(reacciones = r)
                reemplazar(p.id) { it.copy(reacciones = r) }
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            } finally {
                reaccionando = false
            }
        }
    }

    /** Descarga el PDF con Bearer y lo entrega para abrirlo con otra app. */
    fun abrirAdjunto(adjunto: Adjunto, alDescargar: (File) -> Unit) {
        if (abriendoAdjunto) return
        abriendoAdjunto = true
        viewModelScope.launch {
            try {
                alDescargar(repo.descargar(adjunto))
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            } finally {
                abriendoAdjunto = false
            }
        }
    }

    private fun reemplazar(id: String, cambio: (Publicacion) -> Publicacion) {
        val i = publicaciones.indexOfFirst { it.id == id }
        if (i >= 0) publicaciones[i] = cambio(publicaciones[i])
    }

    var leyendoEnVoz by mutableStateOf(false)
        private set
    fun alternarVoz() { leyendoEnVoz = !leyendoEnVoz }

    // --- Notificaciones (solo residente) -------------------------------------

    val notificaciones = mutableStateListOf<Notificacion>()
    /** Insignia de la barra: meta.unreadCount o notifications/unread-count. */
    var sinLeer by mutableStateOf(0)
        private set
    var notifCargando by mutableStateOf(false)
        private set
    var notifError by mutableStateOf<ErrorApi?>(null)
        private set
    var notifHayMas by mutableStateOf(false)
        private set
    private var notifPagina = 1

    fun actualizarNoLeidas() {
        if (!esResidente) return
        viewModelScope.launch { runCatching { repo.noLeidas() }.onSuccess { sinLeer = it } }
    }

    fun cargarNotificaciones() {
        if (!esResidente) return
        notifCargando = true; notifError = null
        viewModelScope.launch {
            try {
                val (p, noLeidas) = repo.notificaciones(1)
                notificaciones.clear(); notificaciones.addAll(p.elementos)
                notifPagina = 1; notifHayMas = p.hayMas; sinLeer = noLeidas
            } catch (e: ErrorApi) {
                notifError = e
            } finally {
                notifCargando = false
            }
        }
    }

    fun cargarMasNotificaciones() {
        if (notifCargando || !notifHayMas) return
        notifCargando = true
        viewModelScope.launch {
            try {
                val (p, noLeidas) = repo.notificaciones(notifPagina + 1)
                notificaciones.addAll(p.elementos.filter { n -> notificaciones.none { it.id == n.id } })
                notifPagina = p.pagina; notifHayMas = p.hayMas; sinLeer = noLeidas
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            } finally {
                notifCargando = false
            }
        }
    }

    /**
     * Marca la notificacion y abre su publicacion. Leer la notificacion no
     * marca el post: eso lo hace el detalle al abrirse.
     */
    fun abrirNotificacion(n: Notificacion, abrir: (String) -> Unit) {
        if (!n.leida) {
            val i = notificaciones.indexOfFirst { it.id == n.id }
            if (i >= 0) notificaciones[i] = n.copy(leida = true)
            sinLeer = (sinLeer - 1).coerceAtLeast(0)
            viewModelScope.launch {
                runCatching { repo.marcarNotificacionLeida(n.id) }.onFailure { actualizarNoLeidas() }
            }
        }
        n.postId?.let(abrir)
    }

    fun marcarTodasLeidas() {
        viewModelScope.launch {
            try {
                sinLeer = repo.leerTodas()
                notificaciones.replaceAll { it.copy(leida = true) }
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            }
        }
    }

    /** Solo archiva las leidas; quedan guardadas en el servidor. */
    fun archivarLeidas() {
        viewModelScope.launch {
            try {
                val n = repo.archivarLeidas()
                notificaciones.removeAll { it.leida }
                avisar(if (n == 1) "Se archivó 1 notificación." else "Se archivaron $n notificaciones.")
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            }
        }
    }

    // --- Archivo -------------------------------------------------------------

    /** Publicadas antes de hoy (no archivadas ni vencidas), agrupadas por mes. */
    private val archivo = mutableStateListOf<Publicacion>()
    var archivoCargando by mutableStateOf(false)
        private set
    var archivoError by mutableStateOf<ErrorApi?>(null)
        private set
    var archivoHayMas by mutableStateOf(false)
        private set
    private var archivoPagina = 0

    val mesesArchivo: List<MesArchivo>
        get() = archivo.groupBy { it.publicadaEn?.toLocalDate()?.withDayOfMonth(1) }
            .map { (mes, lista) ->
                MesArchivo(
                    mes?.let(Fechas::mesAnio) ?: "Sin fecha",
                    lista.map { p ->
                        val meta = listOfNotNull(
                            p.publicadaEn?.let { "${Fechas.diaCorto(it.toLocalDate())} · ${Fechas.hora(it)}" },
                            p.categoria.etiqueta
                        ).joinToString(" · ")
                        EntradaArchivo(p.id, meta, p.titulo)
                    }
                )
            }

    fun cargarArchivo(reiniciar: Boolean) {
        if (archivoCargando) return
        if (!reiniciar && !archivoHayMas) return
        val pagina = if (reiniciar) 1 else archivoPagina + 1
        archivoCargando = true; archivoError = null
        viewModelScope.launch {
            try {
                val p = repo.publicaciones(
                    FiltroPublicaciones(publicadasAntesDe = Fechas.inicioDelDia(Fechas.hoy()), orden = "-publishedAt"),
                    pagina, 30
                )
                if (reiniciar) archivo.clear()
                archivo.addAll(p.elementos.filter { n -> archivo.none { it.id == n.id } })
                archivoPagina = p.pagina; archivoHayMas = p.hayMas
            } catch (e: ErrorApi) {
                if (reiniciar) archivoError = e else avisar(e.mensaje)
            } finally {
                archivoCargando = false
            }
        }
    }

    // Al final a proposito: el init usa propiedades declaradas arriba.
    init {
        viewModelScope.launch {
            almacen.sesion.collect { s ->
                val antes = usuario
                usuario = s?.usuario
                if (s == null && antes != null) {
                    limpiarEstado()
                    accesoAviso = almacen.tomarMotivo()
                } else if (s != null && antes == null && !restaurando) {
                    alEntrar()
                }
            }
        }
        // Cualquier cambio de administracion deja el muro desactualizado.
        viewModelScope.launch { repo.cambios.drop(1).collect { muroDesactualizado = true } }
        restaurar()
    }
}
