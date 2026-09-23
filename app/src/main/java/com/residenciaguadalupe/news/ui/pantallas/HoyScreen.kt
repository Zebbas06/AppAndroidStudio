package com.residenciaguadalupe.news.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.AppViewModel
import com.residenciaguadalupe.news.data.PestanaAgenda
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 3 — Hoy.
 * Agenda con tres pestanas: eventos (type=event) de hoy, de manana y de
 * la semana lunes-domingo, con los rangos calculados en Mexico_City.
 */
@Composable
fun HoyScreen(vm: AppViewModel, onAbrirDetalle: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.cargarAgenda() }

    Column(Modifier.fillMaxSize().background(Crema)) {

        Column(
            Modifier.fillMaxWidth().background(Blanco)
                .padding(horizontal = 26.dp).padding(top = 56.dp, bottom = 18.dp)
        ) {
            Rotulo("Agenda de la casa")
            Titulo("Actividades y talleres", 36, modifier = Modifier.padding(top = 10.dp))
            Cuerpo("Todo lo que se hace hoy y en los próximos días.", modifier = Modifier.padding(top = 10.dp))
        }
        Linea()

        // Pestanas
        Row(Modifier.fillMaxWidth().background(Blanco)) {
            PestanaAgenda.entries.forEach { p ->
                val activa = vm.pestanaAgenda == p
                Column(
                    Modifier.weight(1f).clickable { vm.cambiarPestana(p) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(p.etiqueta.uppercase(), style = textoBoton(13),
                        color = if (activa) Verde else GrisClaro,
                        modifier = Modifier.padding(vertical = 18.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp)
                        .background(if (activa) Verde else Color.Transparent))
                }
            }
        }
        Linea()

        LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
            item {
                Rotulo(vm.pestanaAgenda.rotulo(), Verde, Modifier.padding(top = 24.dp, bottom = 18.dp))
            }
            val error = vm.agendaError
            if (vm.agendaCargando && vm.actividades.isEmpty()) {
                item { Cargando("Cargando actividades…") }
            } else if (error != null) {
                item { EstadoError("No se pudo cargar la agenda", error, vm::cargarAgenda, Modifier.padding(horizontal = 0.dp)) }
            } else if (vm.actividades.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Titulo("No hay actividades programadas", 32)
                        Cuerpo("Para ese día todavía no se programa nada. En cuanto la administración lo publique aparecerá aquí y en el muro.",
                            alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                        Spacer(Modifier.height(26.dp))
                        BotonPrincipal("Ver las de hoy", { vm.cambiarPestana(PestanaAgenda.HOY) })
                    }
                }
            } else {
                items(vm.actividades, key = { it.postId }) { a ->
                    Tarjeta(Modifier.padding(bottom = 14.dp), radio = 26) {
                        Column(Modifier.clickable { onAbrirDetalle(a.postId) }.padding(horizontal = 22.dp, vertical = 20.dp)) {
                            Rotulo(a.hora, Verde)
                            Rotulo(a.lugar, Laton, Modifier.padding(top = 7.dp))
                            Cuerpo(a.titulo, 19, Tinta, negrita = true, modifier = Modifier.padding(top = 12.dp))
                            Cuerpo(a.nota, 17, Gris, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}
