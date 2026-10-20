package com.residenciaguadalupe.news.publications.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*
import java.io.File

/**
 * PANTALLA 9 — Administración · crear o editar aviso.
 * Crear publica de inmediato (no hay borradores). El tipo no se puede
 * cambiar al editar. Al publicar cambia a la confirmacion con la vista
 * previa de como lo veran los residentes.
 */
@Composable
fun AdminCrearScreen(vm: EditorPublicacionViewModel, id: String?, onVolver: () -> Unit, onVerMuro: () -> Unit) {
    LaunchedEffect(id) { vm.inicializar(id) }
    val editable = !vm.enviando && !vm.subiendo && !vm.cargandoEdicion
    val volver = { vm.cerrarEditor(); onVolver() }
    BackHandler(onBack = volver)
    val contexto = LocalContext.current
    // Galeria: selector de fotos del sistema. Cualquier formato se convierte a JPG al subir.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.adjuntar(it.toString()) }
    }
    // Camara: escribe en cache/fotos/; la URI pendiente sobrevive a la rotacion.
    var fotoPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var errorCamara by rememberSaveable { mutableStateOf("") }
    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val uri = fotoPendiente
        fotoPendiente = null
        if (tomada && uri != null) vm.adjuntar(uri)
    }
    // PDF: selector de documentos, solo PDF.
    val elegirPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.adjuntar(it.toString()) }
    }

    Column(Modifier.fillMaxSize().background(Crema)) {

        BarraVolver("Volver a avisos", volver)
        if (!vm.puedePublicar) {
            Consejo("No tiene permiso para administrar publicaciones.", Modifier.padding(26.dp))
            return@Column
        }
        if (vm.cargandoEdicion || (id != null && !vm.inicializado)) {
            Cargando("Cargando aviso…")
            return@Column
        }
        if (vm.esEdicion && vm.editando == null && vm.publicado == null) {
            Column(Modifier.padding(26.dp)) {
                Consejo(vm.fError)
                BotonSecundario("Reintentar", vm::reintentarCarga)
            }
            return@Column
        }

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
                    habilitado = editando == null && editable)
                if (editando != null) {
                    Cuerpo("El tipo no se puede cambiar después de publicar.", 15, Gris,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                }

                Spacer(Modifier.height(20.dp))
                CampoTexto("Título del aviso", vm.fTitulo, vm::setTitulo, placeholder = "Menú especial de domingo",
                    error = vm.fErroresCampo["title"], habilitado = editable)

                Spacer(Modifier.height(20.dp))
                Rotulo("Categoría", modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                SelectorPildoras(Categoria.entries.map { it to it.etiqueta }, vm.fCategoria, vm::setCategoria, habilitado = editable)

                // Campos segun el tipo: fecha y hora concretas, nunca texto libre.
                when (vm.fTipo) {
                    TipoPublicacion.EVENTO -> {
                        Spacer(Modifier.height(20.dp))
                        CampoFecha("Día", vm.fFecha, vm::setFecha, habilitado = editable)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CampoHora("Empieza", vm.fHoraInicio, vm::setHoraInicio, Modifier.weight(1f), habilitado = editable)
                            CampoHora("Termina", vm.fHoraFin, vm::setHoraFin, Modifier.weight(1f), habilitado = editable)
                        }
                        Spacer(Modifier.height(16.dp))
                        CampoTexto("Lugar", vm.fLugar, vm::setLugar, placeholder = "Salón de usos múltiples",
                            error = vm.fErroresCampo["eventLocation"], habilitado = editable)
                    }
                    TipoPublicacion.MENU -> {
                        Spacer(Modifier.height(20.dp))
                        CampoFecha("Día del menú", vm.fFecha, vm::setFecha, habilitado = editable)
                        Spacer(Modifier.height(16.dp))
                        Rotulo("Comida", modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                        SelectorPildoras(Comida.entries.map { it to it.etiqueta }, vm.fComida, vm::setComida, tamano = 14, habilitado = editable)
                    }
                    TipoPublicacion.AVISO -> Unit
                }

                Spacer(Modifier.height(20.dp))
                CampoTexto("Texto del aviso", vm.fTexto, vm::setTexto,
                    placeholder = "Escriba el aviso como quiere que lo lean los residentes.", lineas = 5,
                    error = vm.fErroresCampo["body"], habilitado = editable)

                Spacer(Modifier.height(20.dp))
                CampoFecha("Mostrar en el muro hasta (opcional)", vm.fMostrarHasta, vm::setMostrarHasta,
                    permitirBorrar = true, placeholder = "Sin fecha de retiro", habilitado = editable)

                // Fijar: solo admin
                if (vm.esAdmin) {
                    Spacer(Modifier.height(20.dp))
                    Linea()
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cuerpo("Fijar como importante en el muro", 18, Tinta, negrita = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(14.dp))
                        InterruptorSiNo(vm.fFijar, vm::setFijar, habilitado = editable)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Linea()
                FilaAdjunto(
                    vm,
                    onGaleria = {
                        errorCamara = ""
                        elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onCamara = {
                        errorCamara = ""
                        val uri = nuevaFoto(contexto)
                        fotoPendiente = uri.toString()
                        try {
                            tomarFoto.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            fotoPendiente = null
                            errorCamara = "Este teléfono no tiene una aplicación de cámara disponible."
                        }
                    },
                    onPdf = { errorCamara = ""; elegirPdf.launch(arrayOf("application/pdf")) }
                )
                if (errorCamara.isNotEmpty()) Consejo(errorCamara, Modifier.padding(top = 14.dp))

                if (vm.fError.isNotEmpty()) {
                    Consejo(vm.fError, Modifier.padding(top = 20.dp))
                    if (vm.fConflicto) {
                        Spacer(Modifier.height(12.dp))
                        BotonSecundario("Cargar la versión actual", vm::recargarEdicion, habilitado = editable)
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
                    vm::publicar, habilitado = editable
                )
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Cancelar", volver, habilitado = !vm.enviando)

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
                BotonPrincipal("Ver en el muro", { vm.cerrarEditor(); onVerMuro() })
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Crear otro aviso", vm::nuevoAviso)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BotonEnlace("Volver a avisos", volver)
                }
            }
        }
    }
}

/** Archivo vacio en cache/fotos/ para la camara; borra la foto anterior. */
private fun nuevaFoto(contexto: Context): Uri {
    val carpeta = File(contexto.cacheDir, "fotos").apply { deleteRecursively(); mkdirs() }
    val archivo = File(carpeta, "foto-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
}

/** Un adjunto como maximo: foto (5 MB) de la galeria o la camara, o PDF (9 MB). */
@Composable
private fun FilaAdjunto(
    vm: EditorPublicacionViewModel,
    onGaleria: () -> Unit,
    onCamara: () -> Unit,
    onPdf: () -> Unit
) {
    val a = vm.fAdjunto
    val habilitado = !vm.subiendo && !vm.enviando && !vm.cargandoEdicion
    var eligiendo by rememberSaveable { mutableStateOf(false) }
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
                .clickable(enabled = habilitado) {
                    if (a != null) vm.quitarAdjunto() else eligiendo = !eligiendo
                }
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Text(
                when {
                    a != null -> "QUITAR"
                    eligiendo -> "CERRAR"
                    else -> "ADJUNTAR"
                },
                style = textoBoton(14), color = if (a != null) LadrilloText else Apagado
            )
        }
    }
    if (a == null && eligiendo && !vm.subiendo) {
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Elegir de la galería", { eligiendo = false; onGaleria() }, habilitado = habilitado)
            BotonSecundario("Tomar fotografía", { eligiendo = false; onCamara() }, habilitado = habilitado)
            BotonSecundario("Adjuntar PDF", { eligiendo = false; onPdf() }, habilitado = habilitado)
        }
    }
    if (a != null && a.esImagen) {
        // Vista previa con el mismo Bearer (Coil comparte el OkHttp autenticado).
        AsyncImage(a.url, a.textoAlternativo.ifBlank { null },
            Modifier.padding(top = 14.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(Arena),
            contentScale = ContentScale.Crop)
    }
}
