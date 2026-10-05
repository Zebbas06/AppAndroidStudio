package com.residenciaguadalupe.news.publications.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.residenciaguadalupe.news.R
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*
import java.io.File

/**
 * PANTALLA 4 — Detalle de la publicacion.
 * Sustituye la barra inferior por "Volver al muro" y ofrece la lectura
 * en voz alta. Al abrirse registra la lectura del residente.
 */
@Composable
fun DetalleScreen(vm: DetalleViewModel, postId: String, onVolver: () -> Unit, onAgenda: () -> Unit) {
    val estado by vm.estado.collectAsState()
    DisposableEffect(postId) { onDispose { vm.cerrarDetalle() } }
    LaunchedEffect(postId) { vm.abrirDetalle(postId) }
    val contexto = LocalContext.current

    Box(Modifier.fillMaxSize().background(Crema)) {
        Column(Modifier.fillMaxSize()) {

            BarraVolver("Volver al muro", onVolver)

            val p = estado.detalle?.takeIf { it.id == postId }
            val error = estado.detalleError
            when {
                error != null -> {
                    EstadoError(
                        if (error.estado == 404) "Esta publicación ya no está" else "No se pudo abrir la publicación",
                        error, { vm.abrirDetalle(postId, forzar = true) }
                    )
                    Box(Modifier.padding(horizontal = 26.dp)) { BotonSecundario("Volver al muro", onVolver) }
                }
                p == null -> Cargando("Abriendo la publicación…")
                else -> Contenido(vm, p, onVolver, onAgenda) { a ->
                    vm.abrirAdjunto(a) { archivo -> abrirConOtraApp(contexto, archivo, a.mime) { vm.avisar(it) } }
                }
            }
        }
        estado.mensaje?.let {
            AvisoEmergente(it, null, Modifier.align(Alignment.BottomCenter).padding(20.dp))
            LaunchedEffect(it) { kotlinx.coroutines.delay(4500); vm.ocultarMensaje() }
        }
    }
}

@Composable
private fun ColumnScope.Contenido(
    vm: DetalleViewModel,
    p: Publicacion,
    onVolver: () -> Unit,
    onAgenda: () -> Unit,
    onAbrirAdjunto: (Adjunto) -> Unit
) {
    val estado by vm.estado.collectAsState()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

        // Quien publica
        Row(
            Modifier.padding(horizontal = 26.dp).padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Blanco)
                    .border(1.dp, LatonClaro, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Image(painterResource(R.drawable.logo), null, Modifier.size(42.dp)) }
            Column(Modifier.padding(start = 14.dp)) {
                Cuerpo(p.autor, 19, Tinta, negrita = true)
                Cuerpo(listOfNotNull(p.publicadaEn?.let(Fechas::momento), p.autorArea).joinToString(" · "),
                    16, Gris, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Row(
            Modifier.padding(horizontal = 26.dp).padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EtiquetaCategoria(p.categoria)
            if (p.fijada) Pildora("Fijado · Importante", Laton, colorBorde = LatonClaro)
        }

        Titulo(p.titulo, 34, modifier = Modifier.padding(horizontal = 26.dp).padding(top = 16.dp))

        // El cuerpo respeta los parrafos que escribio administracion.
        p.texto.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEachIndexed { i, parrafo ->
            Cuerpo(parrafo.trim(), 19, TintaSuave,
                modifier = Modifier.padding(horizontal = 26.dp).padding(top = if (i == 0) 18.dp else 14.dp))
        }

        p.adjunto?.let { a ->
            if (a.esImagen) {
                AsyncImage(
                    model = a.url,
                    contentDescription = a.textoAlternativo.ifBlank { p.titulo },
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)
                        .background(Arena),
                    contentScale = ContentScale.Crop
                )
            } else if (a.esPdf) {
                Tarjeta(Modifier.padding(horizontal = 26.dp).padding(top = 24.dp), radio = 26) {
                    Column(Modifier.padding(22.dp)) {
                        Rotulo("Documento adjunto")
                        Cuerpo(a.nombre, 18, Tinta, negrita = true, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
                        BotonPrincipal(if (estado.abriendoAdjunto) "Descargando…" else "Abrir PDF",
                            { onAbrirAdjunto(a) }, habilitado = !estado.abriendoAdjunto)
                    }
                }
            }
        }

        // Reacciones: el residente reacciona; el personal solo ve los conteos.
        FilaReacciones(vm, p)

        // Lectura en voz alta
        Column(
            Modifier.padding(horizontal = 26.dp).padding(top = 24.dp)
                .clip(RoundedCornerShape(26.dp)).background(SalviaPapel)
                .border(1.dp, Salvia, RoundedCornerShape(26.dp)).padding(22.dp)
        ) {
            Rotulo("Lectura en voz alta", VerdeTinta)
            Cuerpo("Si prefiere escuchar el aviso, la aplicación lo lee en voz alta.",
                18, VerdeTinta, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
            if (estado.leyendoEnVoz) BotonSecundario("Detener lectura", vm::alternarVoz, colorTexto = Verde, colorBorde = Salvia)
            else BotonPrincipal("Escuchar aviso", vm::alternarVoz)
        }

        // Ficha de datos: solo lo que la publicacion trae.
        val filas = listOfNotNull(
            p.cuando?.let { "Cuándo" to it },
            p.eventoLugar?.let { "Dónde" to it },
            ("Publica" to listOfNotNull(p.autor, p.autorArea).joinToString(" · "))
        )
        Tarjeta(Modifier.padding(horizontal = 26.dp).padding(top = 26.dp), radio = 26) {
            filas.forEachIndexed { i, (etiqueta, valor) ->
                Row(
                    Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Rotulo(etiqueta, modifier = Modifier.padding(top = 3.dp, end = 16.dp))
                    Cuerpo(valor, 18, Tinta, negrita = true, alineacion = TextAlign.End,
                        modifier = Modifier.weight(1f))
                }
                if (i < filas.lastIndex) Linea(modifier = Modifier.padding(horizontal = 22.dp))
            }
        }

        Column(Modifier.padding(26.dp)) {
            BotonPrincipal("Volver al mural", onVolver)
            Spacer(Modifier.height(12.dp))
            BotonSecundario("Ver próximas actividades", onAgenda)
        }
    }
}

private val REACCIONES = listOf(
    Triple("like", "Me gusta", { r: Reacciones -> r.meGusta }),
    Triple("love", "Me encanta", { r: Reacciones -> r.meEncanta }),
    Triple("happy", "Me alegra", { r: Reacciones -> r.meAlegra })
)

@Composable
private fun FilaReacciones(vm: DetalleViewModel, p: Publicacion) {
    val estado by vm.estado.collectAsState()
    if (!estado.esResidente && p.reacciones.total == 0) return
    Column(Modifier.padding(horizontal = 26.dp).padding(top = 24.dp)) {
        Rotulo(if (estado.esResidente) "¿Qué le pareció?" else "Reacciones de los residentes",
            modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REACCIONES.forEach { (tipo, etiqueta, conteo) ->
                val mia = p.reacciones.mia == tipo
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .then(
                            if (mia) Modifier.background(DegradadoVerde)
                            else Modifier.background(Blanco).border(1.dp, HairlineGris, RoundedCornerShape(22.dp))
                        )
                        .clickable(enabled = estado.esResidente && !estado.reaccionando) { vm.reaccionar(tipo) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Cuerpo(etiqueta, 15, if (mia) Blanco else Apagado, negrita = true)
                    Cuerpo(conteo(p.reacciones).toString(), 15, if (mia) Blanco else Gris)
                }
            }
        }
    }
}

/** Comparte el archivo descargado mediante el FileProvider de cache/adjuntos/. */
private fun abrirConOtraApp(contexto: Context, archivo: File, mime: String, alFallar: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    val intento = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        contexto.startActivity(intento)
    } catch (e: ActivityNotFoundException) {
        alFallar("No hay una aplicación para abrir PDF en este teléfono.")
    }
}
