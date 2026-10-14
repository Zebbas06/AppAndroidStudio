package com.residenciaguadalupe.news.publications.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.publications.data.RepositorioPublicaciones
import com.residenciaguadalupe.news.publications.data.filtroAgenda
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

data class EstadoAgenda(
    val pestana: PestanaAgenda = PestanaAgenda.HOY,
    val actividades: List<Actividad> = emptyList(),
    val cargando: Boolean = false,
    /** Recarga del mismo rango sin vaciar la lista (deslizar o volver a la app). */
    val recargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val error: ErrorApi? = null,
    val errorMas: ErrorApi? = null,
    val hayMas: Boolean = false
)

/** Agenda paginada. Cambiar de rango nunca conserva filas del rango anterior. */
class AgendaViewModel(
    private val repo: RepositorioPublicaciones,
    private val gestor: GestorSesion,
    private val hoy: () -> LocalDate = Fechas::hoy
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoAgenda())
    val estado = mutableEstado.asStateFlow()
    private var pagina = 0
    private var solicitud = 0L
    private var trabajo: Job? = null
    private var diaConsulta: LocalDate? = null
    private var revisionContenido = -1

    fun refrescarSiHaceFalta() {
        if (diaConsulta != hoy() || revisionContenido != repo.cambios.value) cargarAgenda()
    }

    /** Deslizar hacia abajo: regresa cuando termina la consulta. */
    suspend fun recargar() {
        recargarRango()?.join()
    }

    /** La app volvio a primer plano: otro dispositivo pudo publicar mientras tanto. */
    fun alVolver() {
        if (diaConsulta != null) recargarRango()
    }

    // Con otro dia el rango es distinto: entonces no se conservan las filas viejas.
    private fun recargarRango(): Job? = cargar(reiniciar = true, silencioso = diaConsulta == hoy())

    fun cambiarPestana(pestana: PestanaAgenda) {
        mutableEstado.update { it.copy(pestana = pestana) }
        cargarAgenda()
    }

    fun cargarAgenda() { cargar(reiniciar = true) }
    fun cargarMas() { cargar(reiniciar = false) }

    /** [silencioso] solo aplica al reiniciar: conserva las filas hasta que llega la primera pagina. */
    private fun cargar(reiniciar: Boolean, silencioso: Boolean = false): Job? {
        if (gestor.estado.value.sesion == null) return null
        val actual = estado.value
        if (!reiniciar && (actual.cargando || actual.recargando || actual.cargandoMas || !actual.hayMas)) return null
        // No mezclar paginas de distintos dias ni de distintas revisiones administrativas.
        if (!reiniciar && (diaConsulta != hoy() || revisionContenido != repo.cambios.value)) {
            return cargar(reiniciar = true)
        }
        val turno = ++solicitud
        trabajo?.cancel()
        val pestana = actual.pestana
        val siguiente = if (reiniciar) 1 else pagina + 1
        if (reiniciar) {
            if (!silencioso) pagina = 0
            diaConsulta = hoy()
            revisionContenido = repo.cambios.value
            mutableEstado.value = if (silencioso) actual.copy(recargando = true, cargandoMas = false, error = null, errorMas = null)
                else EstadoAgenda(pestana = pestana, cargando = true)
        } else {
            mutableEstado.update { it.copy(cargandoMas = true, errorMas = null) }
        }
        val filtro = filtroAgenda(pestana, checkNotNull(diaConsulta))
        val nuevo = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(filtro, siguiente, 100)
                coroutineContext.ensureActive()
                if (turno == solicitud) {
                    pagina = p.pagina
                    val filas = p.elementos.map { it.aActividad(conDia = pestana == PestanaAgenda.SEMANA) }
                    mutableEstado.update { it.copy(
                        actividades = ((if (reiniciar) emptyList() else it.actividades) + filas).distinctBy { a -> a.postId },
                        hayMas = p.hayMas
                    ) }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) {
                    // Un fallo al recargar no borra lo que ya se veia; la siguiente pagina recarga desde cero.
                    val conservar = silencioso && estado.value.actividades.isNotEmpty()
                    if (conservar) revisionContenido = -1
                    mutableEstado.update {
                        if (reiniciar && !conservar) it.copy(error = e) else it.copy(errorMas = e)
                    }
                }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargando = false, recargando = false, cargandoMas = false) }
            }
        }
        trabajo = nuevo
        return nuevo
    }

    private fun limpiar() {
        solicitud++
        trabajo?.cancel()
        pagina = 0
        diaConsulta = null
        revisionContenido = -1
        mutableEstado.value = EstadoAgenda()
    }

    init {
        var revision = gestor.estado.value.revision
        viewModelScope.launch {
            gestor.estado.collect {
                if (revision != it.revision) limpiar()
                revision = it.revision
            }
        }
    }
}
