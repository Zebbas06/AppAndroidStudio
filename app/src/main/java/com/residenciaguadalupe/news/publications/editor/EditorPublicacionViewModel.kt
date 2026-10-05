package com.residenciaguadalupe.news.publications.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.PostEscrituraRequest
import com.residenciaguadalupe.news.publications.data.RepositorioPublicaciones
import com.residenciaguadalupe.news.media.data.RepositorioMedios
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Una instancia por entrada de editor; no escribe en el ViewModel de lista. */
class EditorPublicacionViewModel(
    private val publicacionesRepo: RepositorioPublicaciones,
    private val medios: RepositorioMedios,
    private val gestor: GestorSesion,
    private val descartar: (Adjunto, String) -> Unit
) : ViewModel() {
    val esAdmin get() = gestor.estado.value.sesion?.usuario?.rol == Rol.ADMIN
    val puedePublicar get() = autorizado()
    private fun autorizado() = gestor.estado.value.sesion?.usuario?.rol in setOf(Rol.ADMIN, Rol.EMPLEADO)
    private fun puedeEditar(p: Publicacion) = esAdmin ||
        (autorizado() && p.autorId == gestor.estado.value.sesion?.usuario?.id)
    private fun editable() = autorizado() && !enviando && !subiendo && !cargandoEdicion && publicado == null
    private var formulario = 0L
    private var trabajoCarga: Job? = null
    private var trabajoSubida: Job? = null
    private var trabajoEnvio: Job? = null
    var inicializado by mutableStateOf(false); private set
    private var idEdicion by mutableStateOf<String?>(null)
    val esEdicion get() = idEdicion != null
    private var tokenAdjunto: String? = null
    private val adjuntosEnviados = mutableSetOf<String>()
    var cargandoEdicion by mutableStateOf(false); private set

    fun inicializar(id: String?) {
        if (inicializado || !autorizado()) return
        inicializado = true
        idEdicion = id
        if (id == null) nuevoAviso() else cargarEdicion(id)
    }

    private fun reiniciarFormulario(limpiarAdjunto: Boolean) {
        formulario++
        // Una escritura sin respuesta puede haber asociado ya el archivo.
        if (limpiarAdjunto && !enviando) descartarAdjuntoNuevo()
        adjuntoNuevo = false
        tokenAdjunto = null
        adjuntosEnviados.clear()
        trabajoCarga?.cancel(); trabajoSubida?.cancel(); trabajoEnvio?.cancel()
        cargandoEdicion = false; subiendo = false; enviando = false
        idEdicion = null
    }

    // --- Formulario de crear / editar ----------------------------------------

    var editando by mutableStateOf<Publicacion?>(null); private set
    var fTipo by mutableStateOf(TipoPublicacion.AVISO); private set
    var fCategoria by mutableStateOf(Categoria.AVISO); private set
    var fTitulo by mutableStateOf(""); private set
    var fTexto by mutableStateOf(""); private set
    var fFijar by mutableStateOf(false); private set
    /** Evento: dia, hora de inicio y fin (zona de la residencia) y lugar. */
    var fFecha by mutableStateOf<LocalDate?>(null); private set
    var fHoraInicio by mutableStateOf<LocalTime?>(null); private set
    var fHoraFin by mutableStateOf<LocalTime?>(null); private set
    var fLugar by mutableStateOf(""); private set
    /** Menu: fecha civil y comida. */
    var fComida by mutableStateOf<Comida?>(null); private set
    /** Opcional: ultimo dia visible en el muro (vence al terminar ese dia). */
    var fMostrarHasta by mutableStateOf<LocalDate?>(null); private set
    var fAdjunto by mutableStateOf<Adjunto?>(null); private set
    /** true si fAdjunto se subio en este formulario (aun sin asociar). */
    private var adjuntoNuevo = false
    var subiendo by mutableStateOf(false); private set
    var enviando by mutableStateOf(false); private set
    var fError by mutableStateOf(""); private set
    /** Mensajes por campo que devolvio la API (details). */
    var fErroresCampo by mutableStateOf<Map<String, String>>(emptyMap()); private set
    /** 409 VERSION_CONFLICT: ofrecer cargar la version actual. */
    var fConflicto by mutableStateOf(false); private set
    /** Si no es null, el formulario muestra la pantalla de confirmacion. */
    var publicado by mutableStateOf<Publicacion?>(null); private set
    var publicadoEraNuevo by mutableStateOf(true); private set

    // Idempotency-Key: la misma para reintentar el mismo cuerpo; otra si cambia.
    private var claveIdempotencia: String? = null
    private var cuerpoConClave: PostEscrituraRequest? = null

    fun setTipo(v: TipoPublicacion) { if (editable() && editando == null) fTipo = v }
    fun setCategoria(v: Categoria) { if (!editable()) return; fCategoria = v }
    fun setTitulo(v: String) { if (editable()) fTitulo = v.take(150) }
    fun setTexto(v: String) { if (editable()) fTexto = v.take(5000) }
    fun setFijar(v: Boolean) { if (editable() && esAdmin) fFijar = v }
    fun setFecha(v: LocalDate?) { if (!editable()) return; fFecha = v }
    fun setHoraInicio(v: LocalTime?) {
        if (!editable()) return
        fHoraInicio = v
        if (fHoraFin == null || (v != null && fHoraFin!!.isBefore(v))) {
            fHoraFin = v?.plusHours(1)?.coerceAtMost(LocalTime.of(23, 59))
        }
    }
    fun setHoraFin(v: LocalTime?) { if (!editable()) return; fHoraFin = v }
    fun setLugar(v: String) { if (editable()) fLugar = v.take(120) }
    fun setComida(v: Comida) { if (!editable()) return; fComida = v }
    fun setMostrarHasta(v: LocalDate?) { if (!editable()) return; fMostrarHasta = v }

    private fun LocalTime.coerceAtMost(max: LocalTime) = if (isAfter(max)) max else this

    fun nuevoAviso() {
        reiniciarFormulario(limpiarAdjunto = true)
        editando = null
        fTipo = TipoPublicacion.AVISO; fCategoria = Categoria.AVISO
        fTitulo = ""; fTexto = ""; fFijar = false
        fFecha = null; fHoraInicio = null; fHoraFin = null; fLugar = ""
        fComida = null; fMostrarHasta = null; fAdjunto = null; adjuntoNuevo = false
        fError = ""; fErroresCampo = emptyMap(); fConflicto = false; publicado = null
        claveIdempotencia = null; cuerpoConClave = null
    }

    private fun editarAviso(p: Publicacion) {
        descartarAdjuntoNuevo()
        editando = p
        fTipo = p.tipo; fCategoria = p.categoria
        fTitulo = p.titulo; fTexto = p.texto; fFijar = p.fijada
        fFecha = if (p.tipo == TipoPublicacion.MENU) p.fechaMenu else p.eventoInicio?.toLocalDate()
        fHoraInicio = p.eventoInicio?.toLocalTime()?.withSecond(0)?.withNano(0)
        fHoraFin = p.eventoFin?.toLocalTime()?.withSecond(0)?.withNano(0)
        fLugar = p.eventoLugar.orEmpty()
        fComida = p.comida
        // expiresAt se guardo como el inicio del dia siguiente.
        fMostrarHasta = p.venceEn?.let { v ->
            val d = v.toLocalDate()
            if (v.toLocalTime() == LocalTime.MIDNIGHT) d.minusDays(1) else d
        }
        fAdjunto = p.adjunto; adjuntoNuevo = false
        fError = ""; fErroresCampo = emptyMap(); fConflicto = false; publicado = null
    }

    /** 409: consulta fresca; no puede reemplazar otro formulario. */
    fun recargarEdicion() {
        val id = editando?.id ?: return
        if (!editable()) return
        cargarEdicion(id)
    }

    private fun cargarEdicion(id: String) {
        val turno = formulario
        cargandoEdicion = true
        fError = ""
        trabajoCarga?.cancel()
        trabajoCarga = gestor.lanzar(viewModelScope) {
            try {
                val p = publicacionesRepo.publicacion(id)
                coroutineContext.ensureActive()
                if (turno != formulario) return@lanzar
                if (!puedeEditar(p)) {
                    fError = "No tiene permiso para editar este aviso."
                    return@lanzar
                }
                editarAviso(p)
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == formulario) fError = e.mensaje
            } finally {
                if (turno == formulario) cargandoEdicion = false
            }
        }
    }

    fun reintentarCarga() {
        if (editable()) idEdicion?.let { cargarEdicion(it) }
    }

    // --- Adjunto -------------------------------------------------------------

    /** Sube al elegir: POST media valida tipo y tamano en el cliente primero. */
    fun adjuntar(referencia: String) {
        if (!editable()) return
        val turno = formulario
        val token = gestor.estado.value.sesion?.token ?: return
        val texto = fTitulo.takeIf { it.isNotBlank() }
        subiendo = true; fError = ""
        trabajoSubida = gestor.lanzar(viewModelScope) {
            try {
                val nuevo = medios.subir(referencia, texto)
                if (turno != formulario || !coroutineContext.isActive) {
                    if (gestor.estado.value.sesion?.token == token) descartar(nuevo, token)
                    return@lanzar
                }
                descartarAdjuntoNuevo()
                fAdjunto = nuevo; adjuntoNuevo = true; tokenAdjunto = token
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno == formulario) fError = e.mensaje
            } finally {
                if (turno == formulario) subiendo = false
            }
        }
    }

    /** Un adjunto existente se desasocia solo al confirmar la edicion. */
    fun quitarAdjunto() {
        if (!editable()) return
        descartarAdjuntoNuevo()
        fAdjunto = null
    }

    private fun descartarAdjuntoNuevo() {
        val a = fAdjunto
        val token = tokenAdjunto
        if (adjuntoNuevo && a != null && a.id !in adjuntosEnviados && token != null &&
            token == gestor.estado.value.sesion?.token) descartar(a, token)
        adjuntoNuevo = false
        tokenAdjunto = null
    }

    // --- Publicar --------------------------------------------------------------

    private fun cuerpo() = FormularioPublicacion(
        fTipo, fCategoria, fTitulo, fTexto, fFijar, fFecha,
        fHoraInicio, fHoraFin, fLugar, fComida, fMostrarHasta, fAdjunto?.id
    ).cuerpo(esAdmin, editando?.fijada)

    fun publicar() {
        if (!editable() || (editando == null && idEdicion != null)) return
        val c = cuerpo().getOrElse { fError = it.message.orEmpty(); return }
        fError = ""; fErroresCampo = emptyMap(); fConflicto = false
        enviando = true
        val original = editando
        val turno = formulario
        // Incluso sin respuesta, el servidor puede haber asociado este archivo.
        adjuntosEnviados.addAll(c.attachmentIds.orEmpty())
        trabajoEnvio = gestor.lanzar(viewModelScope) {
            try {
                val r = if (original == null) {
                    if (c != cuerpoConClave) {
                        claveIdempotencia = UUID.randomUUID().toString()
                        cuerpoConClave = c
                    }
                    publicacionesRepo.crear(c, claveIdempotencia!!)
                } else {
                    publicacionesRepo.editar(original.id, c, original.version)
                }
                coroutineContext.ensureActive()
                if (turno != formulario) return@lanzar
                adjuntoNuevo = false          // ya pertenece a la publicacion
                claveIdempotencia = null; cuerpoConClave = null
                publicadoEraNuevo = original == null
                publicado = r
            } catch (e: ErrorApi) {
                coroutineContext.ensureActive()
                if (turno != formulario) return@lanzar
                fConflicto = e.conflictoVersion
                fError = if (e.conflictoVersion)
                    "Alguien más cambió este aviso mientras usted lo editaba. Cargue la versión actual y vuelva a hacer su cambio."
                else e.mensajeCompleto
                fErroresCampo = e.detalles.filter { it.campo.isNotBlank() }.associate { it.campo to it.mensaje }
            } finally {
                if (turno == formulario) enviando = false
            }
        }
    }

    /** La limpieza de uploads usa el ambito de aplicacion, no el editor destruido. */
    fun cerrarEditor() { nuevoAviso() }

    override fun onCleared() {
        reiniciarFormulario(limpiarAdjunto = true)
        super.onCleared()
    }

    init {
        var revision = gestor.estado.value.revision
        var rol = gestor.estado.value.sesion?.usuario?.role
        viewModelScope.launch {
            gestor.estado.collect { estado ->
                val nuevoRol = estado.sesion?.usuario?.role
                if (revision != estado.revision || rol != nuevoRol) {
                    reiniciarFormulario(limpiarAdjunto = false)
                    fAdjunto = null
                    nuevoAviso()
                    inicializado = false
                }
                revision = estado.revision
                rol = nuevoRol
            }
        }
    }
}
