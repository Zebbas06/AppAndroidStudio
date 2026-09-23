package com.residenciaguadalupe.news

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.data.red.PostEscrituraRequest
import com.residenciaguadalupe.news.data.repositorio.FiltroPublicaciones
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Administracion de publicaciones (admin y empleado).
 *
 * Permisos del contrato: el empleado crea, edita, archiva y republica solo
 * lo suyo (status=all ya le devuelve solo lo suyo); fijar y eliminar son de
 * admin. La API los aplica igual aunque la interfaz se equivocara.
 */
class AdminViewModel(app: Application) : AndroidViewModel(app) {

    private val aplicacion = app as AplicacionResidencia
    private val repo = aplicacion.repositorio
    private val almacen = aplicacion.sesion

    val esAdmin get() = almacen.sesion.value?.usuario?.rol == Rol.ADMIN

    // --- Lista ---------------------------------------------------------------

    val avisos = mutableStateListOf<Publicacion>()
    var listaCargando by mutableStateOf(false)
        private set
    var listaError by mutableStateOf<ErrorApi?>(null)
        private set
    var listaHayMas by mutableStateOf(false)
        private set
    private var listaPagina = 1
    /** id de la publicacion con una operacion en curso (desactiva sus botones). */
    var procesando by mutableStateOf<String?>(null)
        private set

    fun cargarAvisos(reiniciar: Boolean = true) {
        if (listaCargando) return
        if (!reiniciar && !listaHayMas) return
        val pagina = if (reiniciar) 1 else listaPagina + 1
        listaCargando = true; if (reiniciar) listaError = null
        viewModelScope.launch {
            try {
                val p = repo.publicaciones(FiltroPublicaciones(estado = "all", orden = "-publishedAt"), pagina)
                if (reiniciar) avisos.clear()
                avisos.addAll(p.elementos.filter { n -> avisos.none { it.id == n.id } })
                listaPagina = p.pagina; listaHayMas = p.hayMas
            } catch (e: ErrorApi) {
                if (reiniciar) listaError = e else avisar(e.mensaje)
            } finally {
                listaCargando = false
            }
        }
    }

    private fun reemplazar(p: Publicacion) {
        val i = avisos.indexOfFirst { it.id == p.id }
        if (i >= 0) avisos[i] = p else avisos.add(0, p)
    }

    // --- Aviso emergente con Deshacer ----------------------------------------

    /** Texto del aviso emergente inferior; null = oculto. */
    var mensaje by mutableStateOf<String?>(null)
        private set
    /** Operacion inversa; null = el aviso no ofrece Deshacer. */
    private var inversa: (suspend () -> Unit)? = null
    var puedeDeshacer by mutableStateOf(false)
        private set
    private var trabajoMensaje: Job? = null

    private fun avisar(texto: String, deshacer: (suspend () -> Unit)? = null) {
        mensaje = texto
        inversa = deshacer
        puedeDeshacer = deshacer != null
        trabajoMensaje?.cancel()
        trabajoMensaje = viewModelScope.launch {
            delay(5000)
            mensaje = null; inversa = null; puedeDeshacer = false
        }
    }

    /** Deshacer de fijar/archivar: envia la operacion inversa a la API. */
    fun deshacer() {
        val accion = inversa ?: return
        trabajoMensaje?.cancel()
        mensaje = null; inversa = null; puedeDeshacer = false
        viewModelScope.launch {
            try {
                accion()
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            }
        }
    }

    private fun operar(p: Publicacion, bloque: suspend () -> Unit) {
        if (procesando != null) return
        procesando = p.id
        viewModelScope.launch {
            try {
                bloque()
            } catch (e: ErrorApi) {
                avisar(e.mensaje)
            } finally {
                procesando = null
            }
        }
    }

    /** Solo admin, y solo publicadas: archivar ya desfija. */
    fun alternarFijado(p: Publicacion) = operar(p) {
        val fijar = !p.fijada
        reemplazar(repo.fijar(p.id, fijar))
        avisar(if (fijar) "Se fijó como importante en el muro." else "Se quitó de los fijados.") {
            reemplazar(repo.fijar(p.id, !fijar))
        }
    }

    /**
     * Archivar desfija; republicar no vuelve a fijar ni vuelve a notificar.
     * Deshacer un archivado de algo fijado lo republica y lo vuelve a fijar.
     */
    fun alternarArchivado(p: Publicacion) = operar(p) {
        val archivar = p.estado != "archived"
        reemplazar(repo.cambiarEstado(p.id, publicada = !archivar))
        avisar(if (archivar) "El aviso se archivó." else "El aviso volvió al muro.") {
            var r = repo.cambiarEstado(p.id, publicada = archivar)
            if (archivar && p.fijada && esAdmin) r = repo.fijar(p.id, true)
            reemplazar(r)
        }
    }

    /** Aviso que espera confirmacion de borrado; null = sin dialogo. */
    var porEliminar by mutableStateOf<Publicacion?>(null)
        private set
    fun pedirEliminar(p: Publicacion?) { porEliminar = p }

    /** DELETE permanente al confirmar. Sin Deshacer: la API no tiene restore. */
    fun eliminarConfirmado() {
        val p = porEliminar ?: return
        porEliminar = null
        operar(p) {
            repo.eliminar(p.id)
            avisos.removeAll { it.id == p.id }
            avisar("Se eliminó «${p.titulo}».")
        }
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

    fun setTipo(v: TipoPublicacion) { if (editando == null) fTipo = v }
    fun setCategoria(v: Categoria) { fCategoria = v }
    fun setTitulo(v: String) { fTitulo = v.take(150) }
    fun setTexto(v: String) { fTexto = v.take(5000) }
    fun setFijar(v: Boolean) { fFijar = v }
    fun setFecha(v: LocalDate?) { fFecha = v }
    fun setHoraInicio(v: LocalTime?) { fHoraInicio = v; if (fHoraFin == null || fHoraFin!!.isBefore(v)) fHoraFin = v?.plusHours(1)?.coerceAtMost(LocalTime.of(23, 59)) }
    fun setHoraFin(v: LocalTime?) { fHoraFin = v }
    fun setLugar(v: String) { fLugar = v.take(120) }
    fun setComida(v: Comida) { fComida = v }
    fun setMostrarHasta(v: LocalDate?) { fMostrarHasta = v }

    private fun LocalTime.coerceAtMost(max: LocalTime) = if (isAfter(max)) max else this

    fun nuevoAviso() {
        descartarAdjuntoNuevo()
        editando = null
        fTipo = TipoPublicacion.AVISO; fCategoria = Categoria.AVISO
        fTitulo = ""; fTexto = ""; fFijar = false
        fFecha = null; fHoraInicio = null; fHoraFin = null; fLugar = ""
        fComida = null; fMostrarHasta = null; fAdjunto = null; adjuntoNuevo = false
        fError = ""; fErroresCampo = emptyMap(); fConflicto = false; publicado = null
        claveIdempotencia = null; cuerpoConClave = null
    }

    fun editarAviso(p: Publicacion) {
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

    /** 409: descarta lo escrito y carga la version actual del servidor. */
    fun recargarEdicion() {
        val id = editando?.id ?: return
        viewModelScope.launch {
            try {
                editarAviso(repo.publicacion(id))
            } catch (e: ErrorApi) {
                fError = e.mensaje
            }
        }
    }

    // --- Adjunto -------------------------------------------------------------

    /** Sube al elegir: POST media valida tipo y tamano en el cliente primero. */
    fun adjuntar(uri: Uri) {
        if (subiendo) return
        subiendo = true; fError = ""
        viewModelScope.launch {
            try {
                val nuevo = repo.subir(uri, fTitulo.takeIf { it.isNotBlank() })
                descartarAdjuntoNuevo()
                fAdjunto = nuevo; adjuntoNuevo = true
            } catch (e: ErrorApi) {
                fError = e.mensaje
            } finally {
                subiendo = false
            }
        }
    }

    /**
     * Quitar: un upload nuevo se borra (DELETE media); uno que ya era de la
     * publicacion solo se omite en attachmentIds y la API lo desasocia.
     */
    fun quitarAdjunto() {
        descartarAdjuntoNuevo()
        fAdjunto = null; adjuntoNuevo = false
    }

    private fun descartarAdjuntoNuevo() {
        val a = fAdjunto
        if (adjuntoNuevo && a != null) {
            viewModelScope.launch { runCatching { repo.eliminarArchivo(a.id) } }
        }
        adjuntoNuevo = false
    }

    // --- Publicar --------------------------------------------------------------

    /** Arma el cuerpo segun el tipo o devuelve el error para el residente. */
    private fun cuerpo(): Result<PostEscrituraRequest> {
        if (fTitulo.isBlank() || fTexto.isBlank())
            return Result.failure(IllegalStateException("Falta el título o el texto del aviso. Los residentes necesitan las dos cosas para entenderlo."))
        val vence = fMostrarHasta?.let { Fechas.inicioDelDia(it.plusDays(1)) }
        // Empleado: se manda el valor actual (no puede cambiarlo); admin: el del formulario.
        val fijar = if (esAdmin) fFijar else editando?.fijada
        val base = PostEscrituraRequest(
            type = fTipo.api,
            category = fCategoria.api,
            title = fTitulo.trim(),
            body = fTexto.trim(),
            isPinned = fijar,
            attachmentIds = listOfNotNull(fAdjunto?.id),
            expiresAt = vence
        )
        return when (fTipo) {
            TipoPublicacion.AVISO -> Result.success(base)
            TipoPublicacion.MENU -> {
                val fecha = fFecha ?: return Result.failure(IllegalStateException("Elija el día del menú."))
                val comida = fComida ?: return Result.failure(IllegalStateException("Elija si es desayuno, comida, cena o todo el día."))
                Result.success(base.copy(menuDate = fecha.toString(), mealType = comida.api))
            }
            TipoPublicacion.EVENTO -> {
                val fecha = fFecha ?: return Result.failure(IllegalStateException("Elija el día del evento."))
                val ini = fHoraInicio ?: return Result.failure(IllegalStateException("Elija la hora de inicio."))
                val fin = fHoraFin ?: ini
                if (fin.isBefore(ini)) return Result.failure(IllegalStateException("La hora de fin no puede ser antes del inicio."))
                if (fLugar.isBlank()) return Result.failure(IllegalStateException("Escriba dónde será el evento."))
                Result.success(base.copy(
                    eventStartAt = Fechas.aIso(fecha.atTime(ini).atZone(Fechas.ZONA)),
                    eventEndAt = Fechas.aIso(fecha.atTime(fin).atZone(Fechas.ZONA)),
                    eventLocation = fLugar.trim()
                ))
            }
        }
    }

    fun publicar() {
        if (enviando || subiendo) return
        val c = cuerpo().getOrElse { fError = it.message.orEmpty(); return }
        fError = ""; fErroresCampo = emptyMap(); fConflicto = false
        enviando = true
        val original = editando
        viewModelScope.launch {
            try {
                val r = if (original == null) {
                    if (c != cuerpoConClave) {
                        claveIdempotencia = UUID.randomUUID().toString()
                        cuerpoConClave = c
                    }
                    repo.crear(c, claveIdempotencia!!)
                } else {
                    repo.editar(original.id, c, original.version)
                }
                adjuntoNuevo = false          // ya pertenece a la publicacion
                claveIdempotencia = null; cuerpoConClave = null
                reemplazar(r)
                publicadoEraNuevo = original == null
                publicado = r
            } catch (e: ErrorApi) {
                fConflicto = e.conflictoVersion
                fError = if (e.conflictoVersion)
                    "Alguien más cambió este aviso mientras usted lo editaba. Cargue la versión actual y vuelva a hacer su cambio."
                else e.mensajeCompleto
                fErroresCampo = e.detalles.filter { it.campo.isNotBlank() }.associate { it.campo to it.mensaje }
            } finally {
                enviando = false
            }
        }
    }
    // Un upload que quede sin asociar (p. ej. al cerrar la app) lo limpia el
    // mantenimiento horario del servidor a las 24 h.
}
