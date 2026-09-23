package com.residenciaguadalupe.news.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.AppViewModel
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 5 — Notificaciones.
 * Las sin leer son tarjetas blancas con contorno laton; las leidas bajan
 * a una fila plana separada por linea. Al vaciarlas aparece el estado vacio.
 */
@Composable
fun AvisosScreen(vm: AppViewModel, onAbrirDetalle: (String) -> Unit, onMuro: () -> Unit) {
    LaunchedEffect(Unit) { vm.cargarNotificaciones() }

    Column(Modifier.fillMaxSize().background(Crema)) {

        Column(
            Modifier.fillMaxWidth().background(Blanco)
                .padding(horizontal = 26.dp).padding(top = 56.dp, bottom = 18.dp)
        ) {
            Rotulo("Su buzón")
            Titulo("Notificaciones", 36, modifier = Modifier.padding(top = 10.dp))
            Cuerpo("Manténgase al día con lo que pasa en la residencia.", modifier = Modifier.padding(top = 10.dp))
        }
        Linea()

        val error = vm.notifError
        if (vm.notifCargando && vm.notificaciones.isEmpty()) {
            Cargando("Cargando su buzón…")
            return@Column
        }
        if (error != null && vm.notificaciones.isEmpty()) {
            EstadoError("No se pudo cargar el buzón", error, vm::cargarNotificaciones)
            return@Column
        }
        if (vm.notificaciones.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Titulo("No tiene notificaciones", 32)
                Cuerpo("Cuando la administración publique un aviso nuevo se lo avisamos aquí. Mientras tanto, todo lo publicado sigue en el muro.",
                    alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(26.dp))
                BotonPrincipal("Ir al muro", onMuro)
            }
            return@Column
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
            item {
                Spacer(Modifier.height(22.dp))
                BotonPrincipal("Marcar todas como leídas", vm::marcarTodasLeidas, habilitado = vm.sinLeer > 0)
                Spacer(Modifier.height(22.dp))
            }
            items(vm.notificaciones, key = { it.id }) { n ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .then(
                            if (n.leida) Modifier
                            else Modifier.clip(RoundedCornerShape(26.dp)).background(Blanco)
                                .border(1.dp, LatonClaro, RoundedCornerShape(26.dp))
                        )
                        .clickable { vm.abrirNotificacion(n, onAbrirDetalle) }
                        .padding(horizontal = if (n.leida) 0.dp else 22.dp, vertical = 20.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Pildora(
                            "${if (n.leida) "Leída" else "Sin leer"} · ${n.tipo}",
                            if (n.leida) Gris else Laton,
                            Color.Transparent,
                            if (n.leida) HairlineGris else LatonClaro
                        )
                        Spacer(Modifier.weight(1f))
                        Cuerpo(n.cuando, 16, Gris)
                    }
                    Cuerpo(n.titulo, 18, Tinta, negrita = true, modifier = Modifier.padding(top = 14.dp))
                    Cuerpo(n.cuerpo, 17, Gris, modifier = Modifier.padding(top = 7.dp))
                    androidx.compose.material3.Text(
                        when {
                            n.postId == null -> if (n.leida) "Ya la leyó" else "Toque para marcarla como leída"
                            n.leida -> "Ya la leyó · toque para volver a verla"
                            else -> "Toque para leer el aviso"
                        },
                        style = nota(17), color = Laton,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (n.leida) Linea(modifier = Modifier.padding(top = 18.dp))
                }
            }
            item {
                if (vm.notifHayMas) {
                    BotonSecundario(if (vm.notifCargando) "Cargando…" else "Ver notificaciones anteriores",
                        vm::cargarMasNotificaciones, habilitado = !vm.notifCargando)
                }
                Cuerpo(
                    when (vm.sinLeer) {
                        0 -> "No le queda ninguna sin leer"
                        1 -> "1 notificación sin leer"
                        else -> "${vm.sinLeer} notificaciones sin leer"
                    },
                    17, alineacion = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                )
                if (vm.notificaciones.any { it.leida }) {
                    Spacer(Modifier.height(20.dp))
                    Linea()
                    Spacer(Modifier.height(20.dp))
                    BotonSecundario("Archivar las leídas", vm::archivarLeidas)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
