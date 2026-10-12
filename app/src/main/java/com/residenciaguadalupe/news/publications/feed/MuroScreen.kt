package com.residenciaguadalupe.news.publications.feed

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

private val FILTROS = listOf("TODO" to "Todo", "AVISO" to "Avisos", "ACTIVIDAD" to "Actividades",
    "EVENTO" to "Eventos", "NOTICIA" to "Noticias", "FOTO" to "Fotografías")

/**
 * PANTALLA 2 — Muro.
 * Encabezado, tarjeta verde "Hoy en la residencia", filtros y las
 * publicaciones paginadas. Incluye los estados de carga, error y vacio.
 */
@Composable
fun MuroScreen(
    vm: MuroViewModel,
    onAbrirDetalle: (String) -> Unit,
    onAgenda: () -> Unit,
    onArchivo: () -> Unit,
    onAdministracion: () -> Unit
) {
    val estado by vm.estado.collectAsState()
    // Si administracion publico o cambio algo, se recarga al volver.
    LaunchedEffect(Unit) { vm.refrescarSiHaceFalta() }

    Column(Modifier.fillMaxSize().background(Crema)) {

        // Encabezado fijo
        Column(
            Modifier.fillMaxWidth().background(Blanco).padding(horizontal = 26.dp)
                .padding(top = 56.dp, bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Rotulo("${Fechas.saludo()} · ${Fechas.diaCorto(Fechas.hoy())}", modifier = Modifier.weight(1f))
                if (estado.esPersonal) {
                    Text("ADMINISTRAR →", style = textoBoton(13), color = LadrilloText,
                        modifier = Modifier.clickable(onClick = onAdministracion).padding(vertical = 6.dp))
                }
            }
            Titulo(estado.usuario?.nombrePila.orEmpty(), 36, modifier = Modifier.padding(top = 10.dp))
            Cuerpo("Estas son las novedades de su residencia.", modifier = Modifier.padding(top = 10.dp))
        }
        Linea()

        LazyColumn(Modifier.weight(1f)) {

            item { BandaHoy(vm, onAbrirDetalle, onAgenda) }

            item { FilaFiltros(vm) }

            val error = estado.muroError
            when {
                estado.cargando -> item { TarjetaEsqueleto() }
                error != null -> item { EstadoError("No se pudo cargar el muro", error, vm::cargarMuro) }
                estado.publicaciones.isEmpty() -> item { SinResultados(vm, onArchivo) }
                else -> {
                    items(estado.publicaciones, key = { it.id }) { p ->
                        TarjetaPublicacion(p, estado.esResidente, { onAbrirDetalle(p.id) },
                            Modifier.padding(horizontal = 26.dp, vertical = 9.dp))
                    }
                    item {
                        Column(Modifier.padding(26.dp)) {
                            if (estado.muroHayMas) {
                                BotonPrincipal(if (estado.cargandoMas) "Cargando…" else "Cargar más publicaciones",
                                    vm::cargarMasMuro, habilitado = !estado.cargandoMas)
                                Spacer(Modifier.height(12.dp))
                            }
                            BotonSecundario("Ver publicaciones anteriores", onArchivo)
                            Cuerpo("Fundación sin fines de lucro · Cuernavaca", 16, Gris,
                                alineacion = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta verde oscura con las tres proximas actividades de hoy. */
@Composable
private fun BandaHoy(vm: MuroViewModel, onDetalle: (String) -> Unit, onAgenda: () -> Unit) {
    val estado by vm.estado.collectAsState()
    val items = estado.bandaHoy
    Box(Modifier.padding(horizontal = 26.dp).padding(top = 20.dp, bottom = 20.dp)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(VerdeCard1, VerdeCard2)))
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)
        ) {
            Rotulo("Hoy en la residencia", Oro, Modifier.padding(bottom = 16.dp))

            if (estado.bandaCargando) {
                Cuerpo("Cargando actividades…", 18, Hueso)
            } else if (estado.bandaError != null) {
                Cuerpo("No se pudieron cargar las actividades de hoy.", 18, Hueso)
                Text("REINTENTAR", style = textoBoton(14), color = Oro,
                    modifier = Modifier.clickable { vm.cargarBandaHoy() }.padding(vertical = 12.dp))
            } else if (items.isEmpty()) {
                Cuerpo("Hoy no hay actividades programadas.", 18, Hueso.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }

            // La primera actividad va en el boton de ladrillo
            items.firstOrNull()?.let { a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(LadrilloAlto, Ladrillo)))
                        .clickable { onDetalle(a.postId) }
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Text(a.hora.uppercase(), style = rotulo(12), color = Blanco.copy(alpha = 0.88f))
                    Cuerpo(a.titulo, 20, Blanco, negrita = true, modifier = Modifier.padding(top = 8.dp))
                    Cuerpo(a.lugar, 17, Blanco.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                }
            }

            items.drop(1).take(2).forEach { a ->
                Linea(Oro.copy(alpha = 0.28f), Modifier.padding(top = 6.dp))
                Column(Modifier.fillMaxWidth().clickable { onDetalle(a.postId) }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(a.hora.uppercase(), style = rotulo(12), color = Oro)
                    Cuerpo(a.titulo, 20, Hueso, negrita = true, modifier = Modifier.padding(top = 8.dp))
                    Cuerpo(a.lugar, 17, Hueso.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                }
            }

            Linea(Oro.copy(alpha = 0.28f))
            Text(
                "VER PRÓXIMAS ACTIVIDADES",
                style = textoBoton(14),
                color = Oro,
                modifier = Modifier.clickable(onClick = onAgenda).padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }
    }
}

/** Fila de filtros con subrayado laton en el activo. */
@Composable
private fun FilaFiltros(vm: MuroViewModel) {
    val estado by vm.estado.collectAsState()
    Column {
        Row(
            Modifier.fillMaxWidth().background(Blanco).horizontalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FILTROS.forEach { (clave, etiqueta) ->
                val activo = estado.filtro == clave
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { vm.cambiarFiltro(clave) }) {
                    Text(etiqueta.uppercase(), style = textoBoton(14),
                        color = if (activo) Laton else GrisClaro,
                        modifier = Modifier.padding(vertical = 10.dp))
                    Box(Modifier.height(2.dp).width(if (activo) 40.dp else 0.dp).background(LatonClaro))
                }
            }
        }
        Linea()
    }
}

/** Estado de carga: tarjeta esqueleto que late. */
@Composable
private fun TarjetaEsqueleto() {
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    val opacidad by transicion.animateFloat(
        initialValue = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "opacidad"
    )
    Column(Modifier.padding(horizontal = 26.dp, vertical = 24.dp)) {
        Tarjeta {
            Column(Modifier.padding(24.dp)) {
                listOf(96.dp to 13.dp, 0.dp to 26.dp, 0.dp to 26.dp, 0.dp to 14.dp, 0.dp to 150.dp).forEach { (ancho, alto) ->
                    Box(
                        Modifier
                            .then(if (ancho > 0.dp) Modifier.width(ancho) else Modifier.fillMaxWidth())
                            .height(alto)
                            .padding(top = 0.dp)
                            .alpha(opacidad)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Arena)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Cuerpo("Cargando publicaciones…", 18, alineacion = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/** Estado sin resultados, con las dos salidas. */
@Composable
private fun SinResultados(vm: MuroViewModel, onArchivo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 44.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Titulo("No hay nada en este filtro", 32)
        Cuerpo("Todavía no se publica nada aquí. Puede ver todo el muro o buscar en las publicaciones anteriores.",
            alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(26.dp))
        BotonPrincipal("Ver todo el muro", { vm.cambiarFiltro("TODO") })
        Spacer(Modifier.height(12.dp))
        BotonSecundario("Ver anteriores", onArchivo)
    }
}

/** Tarjeta de publicacion del muro. "Nuevo" = el residente aun no la lee. */
@Composable
fun TarjetaPublicacion(p: Publicacion, esResidente: Boolean, onAbrir: () -> Unit, modifier: Modifier = Modifier) {
    Tarjeta(
        modifier = modifier,
        borde = if (p.fijada) androidx.compose.foundation.BorderStroke(1.dp, LatonClaro) else null
    ) {
        Row(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EtiquetaCategoria(p.categoria)
            if (p.fijada) Pildora("Fijado · Importante", Laton, colorBorde = LatonClaro)
            if (esResidente && !p.leida && !p.fijada) Pildora("Nuevo")
        }
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 16.dp)) {
            Titulo(p.titulo, 28)
            Cuerpo(p.meta, 17, Gris, modifier = Modifier.padding(top = 9.dp))
            p.cuando?.let { Cuerpo(it + (p.eventoLugar?.let { l -> " · $l" } ?: ""), 17, Verde, negrita = true,
                modifier = Modifier.padding(top = 6.dp)) }
            Cuerpo(extracto(p.texto, 220), 19, TintaSuave, modifier = Modifier.padding(top = 12.dp))
        }
        p.adjunto?.let { a ->
            if (a.esImagen) {
                AsyncImage(
                    model = a.url,
                    contentDescription = a.textoAlternativo.ifBlank { null },
                    modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(210.dp).background(Arena),
                    contentScale = ContentScale.Crop
                )
            } else if (a.esPdf) {
                Box(Modifier.padding(horizontal = 22.dp).padding(top = 14.dp)) {
                    Pildora("Documento PDF adjunto", Verde, colorBorde = Salvia)
                }
            }
        }
        Linea(modifier = Modifier.padding(top = 18.dp))
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPrincipal("Leer aviso", onAbrir, Modifier.weight(1f), tamano = 15)
            BotonSecundario("Escuchar", onAbrir, Modifier.weight(1f))
        }
    }
}
