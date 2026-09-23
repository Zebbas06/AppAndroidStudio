package com.residenciaguadalupe.news.ui.pantallas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.residenciaguadalupe.news.AdminViewModel
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 9 — Administración · crear o editar aviso.
 * Crear publica de inmediato (no hay borradores). El tipo no se puede
 * cambiar al editar. Al publicar cambia a la confirmacion con la vista
 * previa de como lo veran los residentes.
 */
@Composable
fun AdminCrearScreen(vm: AdminViewModel, onVolver: () -> Unit, onVerMuro: () -> Unit) {
    // Selector del sistema: solo JPG, PNG o PDF.
    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::adjuntar)
    }

    Column(Modifier.fillMaxSize().background(Crema)) {

        BarraVolver("Volver a avisos", onVolver)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp).padding(top = 24.dp, bottom = 30.dp)
        ) {
            val publicado = vm.publicado
            val editando = vm.editando

            if (publicado == null) {
                Rotulo("Administración")
                Titulo(if (editando != null) "Editar aviso" else "Crear aviso nuevo", 34,
                    modifier = Modifier.padding(top = 10.dp))

                Spacer(Modifier.height(24.dp))
                Rotulo("Qué publica", modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                SelectorPildoras(TipoPublicacion.entries.map { it to it.etiqueta }, vm.fTipo, vm::setTipo,
                    habilitado = editando == null)
                if (editando != null) {
                    Cuerpo("El tipo no se puede cambiar después de publicar.", 15, Gris,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                }

                Spacer(Modifier.height(20.dp))
                CampoTexto("Título del aviso", vm.fTitulo, vm::setTitulo, placeholder = "Menú especial de domingo",
                    error = vm.fErroresCampo["title"])

                Spacer(Modifier.height(20.dp))
                Rotulo("Categoría", modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                SelectorPildoras(Categoria.entries.map { it to it.etiqueta }, vm.fCategoria, vm::setCategoria)

                // Campos segun el tipo: fecha y hora concretas, nunca texto libre.
                when (vm.fTipo) {
                    TipoPublicacion.EVENTO -> {
                        Spacer(Modifier.height(20.dp))
                        CampoFecha("Día", vm.fFecha, vm::setFecha)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CampoHora("Empieza", vm.fHoraInicio, vm::setHoraInicio, Modifier.weight(1f))
                            CampoHora("Termina", vm.fHoraFin, vm::setHoraFin, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        CampoTexto("Lugar", vm.fLugar, vm::setLugar, placeholder = "Salón de usos múltiples",
                            error = vm.fErroresCampo["eventLocation"])
                    }
                    TipoPublicacion.MENU -> {
                        Spacer(Modifier.height(20.dp))
                        CampoFecha("Día del menú", vm.fFecha, vm::setFecha)
                        Spacer(Modifier.height(16.dp))
                        Rotulo("Comida", modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                        SelectorPildoras(Comida.entries.map { it to it.etiqueta }, vm.fComida, vm::setComida, tamano = 14)
                    }
                    TipoPublicacion.AVISO -> Unit
                }

                Spacer(Modifier.height(20.dp))
                CampoTexto("Texto del aviso", vm.fTexto, vm::setTexto,
                    placeholder = "Escriba el aviso como quiere que lo lean los residentes.", lineas = 5,
                    error = vm.fErroresCampo["body"])

                Spacer(Modifier.height(20.dp))
                CampoFecha("Mostrar en el muro hasta (opcional)", vm.fMostrarHasta, vm::setMostrarHasta,
                    permitirBorrar = true, placeholder = "Sin fecha de retiro")

                // Fijar: solo admin
                if (vm.esAdmin) {
                    Spacer(Modifier.height(20.dp))
                    Linea()
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cuerpo("Fijar como importante en el muro", 18, Tinta, negrita = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(14.dp))
                        InterruptorSiNo(vm.fFijar, vm::setFijar)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Linea()
                FilaAdjunto(vm) { elegirArchivo.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }

                if (vm.fError.isNotEmpty()) {
                    Consejo(vm.fError, Modifier.padding(top = 20.dp))
                    if (vm.fConflicto) {
                        Spacer(Modifier.height(12.dp))
                        BotonSecundario("Cargar la versión actual", vm::recargarEdicion)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Linea()
                Spacer(Modifier.height(20.dp))
                BotonPrincipal(
                    when {
                        vm.enviando -> "Enviando…"
                        editando != null -> "Guardar cambios"
                        else -> "Publicar aviso"
                    },
                    vm::publicar, habilitado = !vm.enviando && !vm.subiendo
                )
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Cancelar", onVolver, habilitado = !vm.enviando)

            } else {
                Pildora(if (vm.publicadoEraNuevo) "Publicado" else "Guardado", Verde, colorBorde = Salvia)
                Titulo(if (vm.publicadoEraNuevo) "El aviso ya está en el muro" else "Se guardaron los cambios", 34,
                    modifier = Modifier.padding(top = 16.dp))
                Cuerpo(
                    if (vm.publicadoEraNuevo) "«${publicado.titulo}» se publicó y les llegó a los residentes como notificación, según sus preferencias."
                    else "«${publicado.titulo}» ya se ve corregido. Editar no vuelve a enviar notificaciones.",
                    19, TintaSuave, modifier = Modifier.padding(top = 12.dp)
                )

                Rotulo("Así se verá", modifier = Modifier.padding(top = 24.dp, bottom = 14.dp))
                Tarjeta {
                    Row(
                        Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EtiquetaCategoria(publicado.categoria)
                        if (publicado.fijada) Pildora("Fijado · Importante", Laton, colorBorde = LatonClaro)
                        else Pildora("Nuevo")
                    }
                    Column(Modifier.padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 22.dp)) {
                        Titulo(publicado.titulo, 26)
                        Cuerpo(publicado.meta, 16, Gris, modifier = Modifier.padding(top = 9.dp))
                        publicado.cuando?.let { Cuerpo(it, 16, Verde, negrita = true, modifier = Modifier.padding(top = 6.dp)) }
                        Cuerpo(extracto(publicado.texto, 220), 18, TintaSuave, modifier = Modifier.padding(top = 12.dp))
                    }
                    publicado.adjunto?.takeIf { it.esImagen }?.let { a ->
                        AsyncImage(a.url, a.textoAlternativo.ifBlank { null },
                            Modifier.fillMaxWidth().height(180.dp).background(Arena), contentScale = ContentScale.Crop)
                    }
                }

                Spacer(Modifier.height(26.dp))
                Linea()
                Spacer(Modifier.height(20.dp))
                BotonPrincipal("Ver en el muro", onVerMuro)
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Crear otro aviso", vm::nuevoAviso)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BotonEnlace("Volver a avisos", onVolver)
                }
            }
        }
    }
}

/** Un adjunto como maximo: foto JPG/PNG (5 MB) o PDF (9 MB). */
@Composable
private fun FilaAdjunto(vm: AdminViewModel, onElegir: () -> Unit) {
    val a = vm.fAdjunto
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Cuerpo("Fotografía o PDF", 18, Tinta, negrita = true)
            Cuerpo(
                when {
                    vm.subiendo -> "Subiendo archivo…"
                    a != null -> a.nombre
                    else -> "Foto hasta 5 MB · PDF hasta 9 MB"
                },
                15, Gris, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Blanco)
                .border(1.dp, HairlineGris, RoundedCornerShape(999.dp))
                .clickable(enabled = !vm.subiendo) { if (a != null) vm.quitarAdjunto() else onElegir() }
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Text(if (a != null) "QUITAR" else "ADJUNTAR", style = textoBoton(14),
                color = if (a != null) LadrilloText else Apagado)
        }
    }
    if (a != null && a.esImagen) {
        // Vista previa con el mismo Bearer (Coil comparte el OkHttp autenticado).
        AsyncImage(a.url, a.textoAlternativo.ifBlank { null },
            Modifier.padding(top = 14.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(Arena),
            contentScale = ContentScale.Crop)
    }
}
