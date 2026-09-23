package com.residenciaguadalupe.news.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.AppViewModel
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 7 — Publicaciones anteriores.
 * Lo publicado antes de hoy (publishedBefore), agrupado por mes, sin
 * tarjetas: solo lineas doradas. No incluye archivadas ni vencidas.
 */
@Composable
fun ArchivoScreen(vm: AppViewModel, onVolver: () -> Unit, onAbrirDetalle: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.cargarArchivo(reiniciar = true) }

    Column(Modifier.fillMaxSize().background(Crema)) {

        BarraVolver("Volver al muro", onVolver)

        Column(
            Modifier.fillMaxWidth().background(Blanco)
                .padding(horizontal = 26.dp).padding(top = 22.dp, bottom = 18.dp)
        ) {
            Rotulo("Archivo")
            Titulo("Publicaciones anteriores", 34, modifier = Modifier.padding(top = 10.dp))
            Cuerpo("Todo lo publicado, agrupado por mes.", modifier = Modifier.padding(top = 10.dp))
        }
        Linea()

        val meses = vm.mesesArchivo
        val error = vm.archivoError
        when {
            vm.archivoCargando && meses.isEmpty() -> Cargando("Cargando publicaciones anteriores…")
            error != null -> EstadoError("No se pudo cargar el archivo", error, { vm.cargarArchivo(reiniciar = true) })
            meses.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Titulo("Todavía no hay publicaciones anteriores", 30)
                Cuerpo("Lo que se publique a partir de hoy aparecerá aquí en los próximos días.",
                    alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(24.dp))
                BotonPrincipal("Volver al muro", onVolver)
            }
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
                meses.forEachIndexed { indiceMes, mes ->
                    item(key = "mes-${mes.mes}") {
                        Rotulo(mes.mes, Verde,
                            Modifier.padding(top = if (indiceMes == 0) 22.dp else 34.dp, bottom = 8.dp))
                    }
                    items(mes.entradas.size, key = { mes.entradas[it].postId }) { i ->
                        val e = mes.entradas[i]
                        Column(Modifier.clickable { onAbrirDetalle(e.postId) }) {
                            Linea()
                            Column(Modifier.padding(vertical = 18.dp)) {
                                Cuerpo(e.meta, 16, Gris)
                                Cuerpo(e.titulo, 18, Tinta, negrita = true, modifier = Modifier.padding(top = 7.dp))
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    if (vm.archivoHayMas) {
                        BotonSecundario(if (vm.archivoCargando) "Cargando…" else "Cargar meses anteriores",
                            { vm.cargarArchivo(reiniciar = false) }, habilitado = !vm.archivoCargando)
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
