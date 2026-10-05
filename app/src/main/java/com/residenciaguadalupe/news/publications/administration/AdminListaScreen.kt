package com.residenciaguadalupe.news.publications.administration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.data.EstadoAviso
import com.residenciaguadalupe.news.data.Fechas
import com.residenciaguadalupe.news.data.Publicacion
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 8 — Administración · avisos.
 * Admin ve todas las publicaciones; el empleado, solo las suyas (la API
 * filtra status=all por autor). Fijar y archivar levantan el aviso
 * emergente con Deshacer; eliminar pide confirmacion y no se puede deshacer.
 */
@Composable
fun AdminListaScreen(vm: AdminPublicacionesViewModel, onVolver: () -> Unit, onCrear: () -> Unit, onEditar: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.cargarAvisos() }

    Box(Modifier.fillMaxSize().background(Crema)) {

        Column(Modifier.fillMaxSize()) {
            BarraVolver("Volver", onVolver)
            Column(
                Modifier.fillMaxWidth().background(Blanco)
                    .padding(horizontal = 26.dp).padding(top = 18.dp, bottom = 18.dp)
            ) {
                Rotulo("Administración")
                Titulo(if (vm.esAdmin) "Avisos publicados" else "Mis avisos", 36, modifier = Modifier.padding(top = 10.dp))
                Cuerpo(
                    if (vm.esAdmin) "Aquí se edita, se fija y se archiva lo que ven los residentes."
                    else "Aquí edita, archiva y vuelve a publicar los avisos que usted creó.",
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Linea()

            val error = vm.listaError
            when {
                vm.listaCargando && vm.avisos.isEmpty() -> Cargando("Cargando avisos…")
                error != null -> EstadoError("No se pudieron cargar los avisos", error, { vm.cargarAvisos() })
                vm.avisos.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Titulo("No hay avisos publicados", 32)
                    Cuerpo("El muro está vacío para los residentes. Publique el primer aviso del día para que lo vean al abrir la aplicación.",
                        alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(26.dp))
                    BotonPrincipal("Crear aviso nuevo", onCrear)
                }
                else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
                    item {
                        Spacer(Modifier.height(22.dp))
                        BotonPrincipal("Crear aviso nuevo", onCrear)
                        Spacer(Modifier.height(22.dp))
                    }
                    items(vm.avisos, key = { it.id }) { a ->
                        FilaAviso(a, vm, onEditar, Modifier.padding(bottom = 16.dp))
                    }
                    item {
                        if (vm.listaHayMas) {
                            BotonSecundario(if (vm.listaCargando) "Cargando…" else "Cargar más avisos",
                                { vm.cargarAvisos(reiniciar = false) }, habilitado = !vm.listaCargando)
                        }
                        Spacer(Modifier.height(90.dp))
                    }
                }
            }
        }

        // Aviso emergente; Deshacer solo en fijar y archivar
        vm.mensaje?.let {
            AvisoEmergente(it, if (vm.puedeDeshacer) vm::deshacer else null,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 26.dp))
        }

        // Confirmacion de borrado
        vm.porEliminar?.let { a ->
            Box(
                Modifier.fillMaxSize().background(Carbon.copy(alpha = 0.55f))
                    .clickable { vm.pedirEliminar(null) }.padding(26.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Crema)
                        .clickable(enabled = false) {}
                        .padding(horizontal = 26.dp, vertical = 28.dp)
                ) {
                    Pildora("Confirmar", Laton, colorBorde = LatonClaro)
                    Titulo("¿Eliminar «${a.titulo}»?", 30, modifier = Modifier.padding(top = 16.dp))
                    Cuerpo("El aviso se borra para siempre, con su foto, sus reacciones y sus notificaciones, y no se puede recuperar. Si solo quiere quitarlo del muro, archívelo en vez de eliminarlo.",
                        color = TintaSuave, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(24.dp))
                    Linea()
                    Spacer(Modifier.height(20.dp))
                    BotonSecundario("Sí, eliminar", vm::eliminarConfirmado, colorTexto = LadrilloText, colorBorde = Ladrillo)
                    Spacer(Modifier.height(12.dp))
                    BotonPrincipal("Cancelar", { vm.pedirEliminar(null) })
                }
            }
        }
    }
}

@Composable
private fun FilaAviso(a: Publicacion, vm: AdminPublicacionesViewModel, onEditar: (String) -> Unit, modifier: Modifier = Modifier) {
    val ocupado = vm.procesando != null
    Tarjeta(modifier.alpha(if (ocupado) 0.6f else 1f)) {
        Row(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EtiquetaCategoria(a.categoria)
            when (val e = a.estadoAdmin) {
                EstadoAviso.FIJADO -> Pildora(e.etiqueta, Laton, colorBorde = LatonClaro)
                EstadoAviso.PUBLICADO -> Pildora(e.etiqueta, Verde, colorBorde = Salvia)
                EstadoAviso.ARCHIVADO, EstadoAviso.BORRADOR -> Text(e.etiqueta.uppercase(), style = rotulo(11), color = Gris)
            }
        }
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 20.dp)) {
            Cuerpo(a.titulo, 20, Tinta, negrita = true)
            Cuerpo(
                listOfNotNull(
                    a.tipo.etiqueta,
                    a.cuando ?: a.publicadaEn?.let { "${Fechas.diaCorto(it.toLocalDate())} · ${Fechas.hora(it)}" },
                    if (vm.esAdmin) a.autor else null
                ).joinToString(" · "),
                16, Gris, modifier = Modifier.padding(top = 7.dp)
            )
        }
        Linea()
        Row(Modifier.fillMaxWidth()) {
            val archivado = a.estado == "archived"
            val acciones = buildList<Pair<String, () -> Unit>> {
                if (vm.puedeEditar(a)) add("Editar" to { onEditar(a.id) })
                // Fijar: solo admin y solo publicadas (archivar desfija).
                if (vm.esAdmin && !archivado) add((if (a.fijada) "No fijar" else "Fijar") to { vm.alternarFijado(a) })
                add((if (archivado) "Republicar" else "Archivar") to { vm.alternarArchivado(a) })
            }
            acciones.forEachIndexed { i, (etiqueta, accion) ->
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = !ocupado, onClick = accion)
                        .padding(vertical = 14.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) { Text(etiqueta.uppercase(), style = rotulo(12), color = Apagado, maxLines = 1) }
                if (i < acciones.lastIndex || vm.esAdmin) Box(Modifier.width(1.dp).height(48.dp).background(Hairline))
            }
            if (vm.esAdmin) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .clickable(enabled = !ocupado) { vm.pedirEliminar(a) }.padding(vertical = 14.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) { Text("ELIMINAR", style = rotulo(12), color = LadrilloText, maxLines = 1) }
            }
        }
    }
}
