package com.residenciaguadalupe.news.publications.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.publications.data.FiltroPublicaciones
import com.residenciaguadalupe.news.publications.data.RepositorioPublicaciones
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

data class EstadoArchivo(
    val publicaciones: List<Publicacion> = emptyList(),
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val error: ErrorApi? = null,
    val errorMas: ErrorApi? = null,
    val hayMas: Boolean = false
) {
    val meses: List<MesArchivo>
        get() = publicaciones.groupBy { it.publicadaEn?.toLocalDate()?.withDayOfMonth(1) }
            .map { (mes, lista) ->
                MesArchivo(mes?.let(Fechas::mesAnio) ?: "Sin fecha", lista.map { p ->
                    val meta = listOfNotNull(
                        p.publicadaEn?.let { "${Fechas.diaCorto(it.toLocalDate())} · ${Fechas.hora(it)}" },
                        p.categoria.etiqueta
                    ).joinToString(" · ")
                    EntradaArchivo(p.id, meta, p.titulo)
                })
            }
}

/** Archivo de publicaciones anteriores, no publicaciones con status=archived. */
class ArchivoViewModel(
    private val repo: RepositorioPublicaciones,
    private val gestor: GestorSesion,
    private val hoy: () -> LocalDate = Fechas::hoy
) : ViewModel() {
    private val mutableEstado = MutableStateFlow(EstadoArchivo())
    val estado = mutableEstado.asStateFlow()
    private var pagina = 0
    private var solicitud = 0L
    private var trabajo: Job? = null
    private var diaConsulta: LocalDate? = null
    private var revisionContenido = -1

    fun refrescarSiHaceFalta() {
        if (diaConsulta != hoy() || revisionContenido != repo.cambios.value) cargarArchivo(reiniciar = true)
    }

    fun cargarArchivo(reiniciar: Boolean) {
        if (gestor.estado.value.sesion == null) return
        val actual = estado.value
        if (!reiniciar && (actual.cargando || actual.cargandoMas || !actual.hayMas)) return
        if (!reiniciar && (diaConsulta != hoy() || revisionContenido != repo.cambios.value)) {
            cargarArchivo(reiniciar = true)
            return
        }
        val turno = ++solicitud
        trabajo?.cancel()
        val siguiente = if (reiniciar) 1 else pagina + 1
        if (reiniciar) {
            pagina = 0
            diaConsulta = hoy()
            revisionContenido = repo.cambios.value
            mutableEstado.value = EstadoArchivo(cargando = true)
        } else {
            mutableEstado.update { it.copy(cargandoMas = true, errorMas = null) }
        }
        val filtro = FiltroPublicaciones(
            publicadasAntesDe = Fechas.inicioDelDia(checkNotNull(diaConsulta)), orden = "-publishedAt"
        )
        trabajo = gestor.lanzar(viewModelScope) {
            try {
                val p = repo.publicaciones(filtro, siguiente, 30)
                coroutineContext.ensureActive()
                if (turno == solicitud) {
                    pagina = p.pagina
                    mutableEstado.update { it.copy(
                        publicaciones = (it.publicaciones + p.elementos).distinctBy { p -> p.id },
                        hayMas = p.hayMas
                    ) }
                }
            } catch (e: ErrorApi) {
                if (turno == solicitud) mutableEstado.update {
                    if (reiniciar) it.copy(error = e) else it.copy(errorMas = e)
                }
            } finally {
                if (turno == solicitud) mutableEstado.update { it.copy(cargando = false, cargandoMas = false) }
            }
        }
    }

    private fun limpiar() {
        solicitud++
        trabajo?.cancel()
        pagina = 0
        diaConsulta = null
        revisionContenido = -1
        mutableEstado.value = EstadoArchivo()
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
