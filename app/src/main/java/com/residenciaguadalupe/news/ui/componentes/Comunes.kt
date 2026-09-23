package com.residenciaguadalupe.news.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.residenciaguadalupe.news.data.Categoria
import com.residenciaguadalupe.news.data.Fechas
import com.residenciaguadalupe.news.data.red.ErrorApi
import com.residenciaguadalupe.news.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Piezas que se repiten en las nueve pantallas. Todas toman sus colores y
 * tamanos del tema; ninguna declara valores sueltos.
 */

/** Degradado verde de los botones principales, igual que en el mockup. */
val DegradadoVerde = Brush.verticalGradient(listOf(VerdeClaro, Verde))

/** Rotulo en versalitas color laton: "SU PERFIL", "ARCHIVO", "ADMINISTRACIÓN". */
@Composable
fun Rotulo(texto: String, color: Color = Laton, modifier: Modifier = Modifier) {
    Text(texto.uppercase(), style = rotulo(), color = color, modifier = modifier)
}

/** Titulo Playfair italica. */
@Composable
fun Titulo(texto: String, tamano: Int = 34, color: Color = Tinta, modifier: Modifier = Modifier) {
    Text(texto, style = titulo(tamano), color = color, modifier = modifier)
}

/** Parrafo de cuerpo (Source Sans semibold). */
@Composable
fun Cuerpo(
    texto: String,
    tamano: Int = 18,
    color: Color = Apagado,
    negrita: Boolean = false,
    alineacion: TextAlign? = null,
    modifier: Modifier = Modifier
) {
    Text(texto, style = cuerpo(tamano, negrita), color = color, textAlign = alineacion, modifier = modifier)
}

/** Linea dorada de separacion. */
@Composable
fun Linea(color: Color = Hairline, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * Boton principal: pildora verde con degradado.
 * Altura minima 60dp para que sea comodo de tocar.
 */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Int = 16,
    habilitado: Boolean = true
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .alpha(if (habilitado) 1f else 0.5f)
            .clip(RoundedCornerShape(999.dp))
            .background(DegradadoVerde)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto.uppercase(), style = textoBoton(tamano), color = Blanco, textAlign = TextAlign.Center)
    }
}

/** Boton secundario: pildora blanca con contorno fino. */
@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorTexto: Color = Apagado,
    colorBorde: Color = HairlineGris,
    habilitado: Boolean = true
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (habilitado) 1f else 0.5f)
            .clip(RoundedCornerShape(999.dp))
            .background(Blanco)
            .border(1.dp, colorBorde, RoundedCornerShape(999.dp))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto.uppercase(), style = textoBoton(), color = colorTexto, textAlign = TextAlign.Center)
    }
}

/** Boton de texto subrayado (enlaces tipo "¿Olvidó su contraseña?"). */
@Composable
fun BotonEnlace(texto: String, onClick: () -> Unit, color: Color = LadrilloText, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = cuerpo(16, negrita = true),
        color = color,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)
    )
}

/** Etiqueta redonda de categoria: verde para actividad/evento, laton para el resto. */
@Composable
fun EtiquetaCategoria(categoria: Categoria) {
    val verde = categoria.esVerde
    Pildora(
        texto = categoria.etiqueta,
        colorTexto = if (verde) Verde else Laton,
        colorFondo = if (verde) SalviaPapel else LatonPapel,
        colorBorde = if (verde) Salvia else LatonClaro
    )
}

/** Pildora de estado generica ("Fijado · Importante", "Nuevo", "Sin leer"). */
@Composable
fun Pildora(
    texto: String,
    colorTexto: Color = Apagado,
    colorFondo: Color = Color.Transparent,
    colorBorde: Color = HairlineGris
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colorFondo)
            .border(1.dp, colorBorde, RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(texto.uppercase(), style = rotulo(11), color = colorTexto, maxLines = 1)
    }
}

/**
 * Campo de texto con forma de pildora. Se usa BasicTextField en vez del
 * TextField de Material para poder controlar el borde y el radio exactos.
 * [oculto] enmascara el texto sin alterar el valor (contrasenas y PIN).
 */
@Composable
fun CampoTexto(
    etiqueta: String,
    valor: String,
    onValor: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    lineas: Int = 1,
    oculto: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    error: String? = null,
    habilitado: Boolean = true,
    /** Si no es null, la tecla ✓ del teclado lo ejecuta (p. ej. iniciar sesion). */
    alTerminar: (() -> Unit)? = null
) {
    val radio = if (lineas > 1) 26.dp else 999.dp
    Column(modifier) {
        Rotulo(etiqueta, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radio))
                .background(if (habilitado) Blanco else Crema)
                .border(1.dp, if (error != null) Ladrillo else HairlineGris, RoundedCornerShape(radio))
                .padding(horizontal = 24.dp, vertical = 17.dp)
        ) {
            if (valor.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = cuerpo(19), color = GrisClaro)
            }
            BasicTextField(
                value = valor,
                onValueChange = onValor,
                textStyle = cuerpo(19).copy(color = if (habilitado) Tinta else Gris),
                cursorBrush = SolidColor(Ladrillo),
                singleLine = lineas == 1,
                minLines = lineas,
                enabled = habilitado,
                visualTransformation = if (oculto) PasswordVisualTransformation('•') else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = teclado,
                    autoCorrect = !oculto && teclado == KeyboardType.Text,
                    imeAction = if (alTerminar != null) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(onDone = { alTerminar?.invoke() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (error != null) {
            Text(error, style = cuerpo(15, negrita = true), color = LadrilloText,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp))
        }
    }
}

/**
 * Campo que abre un dialogo (fecha u hora). Se ve igual que CampoTexto.
 */
@Composable
fun CampoBoton(
    etiqueta: String,
    valor: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Elegir",
    habilitado: Boolean = true
) {
    Column(modifier) {
        Rotulo(etiqueta, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (habilitado) Blanco else Crema)
                .border(1.dp, HairlineGris, RoundedCornerShape(999.dp))
                .clickable(enabled = habilitado, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 17.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(valor ?: placeholder, style = cuerpo(19), color = if (valor == null) GrisClaro else Tinta, maxLines = 1)
        }
    }
}

/** Campo de fecha civil con el selector de Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    etiqueta: String,
    fecha: LocalDate?,
    onFecha: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    permitirBorrar: Boolean = false,
    placeholder: String = "Elegir día"
) {
    var abierto by remember { mutableStateOf(false) }
    CampoBoton(etiqueta, fecha?.let { Fechas.diaCorto(it) + " · " + it.format(Fechas.FORMATO_CORTO) },
        { abierto = true }, modifier, placeholder)
    if (abierto) {
        // DatePicker trabaja en milisegundos UTC del inicio del dia.
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = (fecha ?: Fechas.hoy()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton({
                    abierto = false
                    onFecha(estado.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                }) { Text("ACEPTAR", style = textoBoton(14), color = Verde) }
            },
            dismissButton = {
                Row {
                    if (permitirBorrar && fecha != null) {
                        TextButton({ abierto = false; onFecha(null) }) { Text("QUITAR", style = textoBoton(14), color = LadrilloText) }
                    }
                    TextButton({ abierto = false }) { Text("CANCELAR", style = textoBoton(14), color = Apagado) }
                }
            }
        ) { DatePicker(estado) }
    }
}

/** Campo de hora con el selector de Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(etiqueta: String, hora: LocalTime?, onHora: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    CampoBoton(etiqueta, hora?.let { Fechas.hora(Fechas.hoy().atTime(it).atZone(Fechas.ZONA)) },
        { abierto = true }, modifier, "Elegir hora")
    if (abierto) {
        val estado = rememberTimePickerState(hora?.hour ?: 10, hora?.minute ?: 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton({ abierto = false; onHora(LocalTime.of(estado.hour, estado.minute)) }) {
                    Text("ACEPTAR", style = textoBoton(14), color = Verde)
                }
            },
            dismissButton = {
                TextButton({ abierto = false }) { Text("CANCELAR", style = textoBoton(14), color = Apagado) }
            },
            text = { TimePicker(estado) }
        )
    }
}

/**
 * Fila de pildoras para elegir una opcion (categoria, tipo, rol…).
 * Sin [habilitado] la opcion activa se ve pero no se puede cambiar.
 */
@Composable
fun <T> SelectorPildoras(
    opciones: List<Pair<T, String>>,
    seleccion: T?,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    tamano: Int = 15
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (valor, etiqueta) ->
            val activa = seleccion == valor
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (activa) Modifier.background(if (habilitado) DegradadoVerde else SolidColor(Apagado))
                        else Modifier.background(Blanco).border(1.dp, HairlineGris, RoundedCornerShape(999.dp))
                    )
                    .clickable(enabled = habilitado) { onSeleccion(valor) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Cuerpo(etiqueta, tamano, if (activa) Blanco else Apagado, negrita = true, alineacion = TextAlign.Center)
            }
        }
    }
}

/** Estado de error con el mensaje de la API y Reintentar. */
@Composable
fun EstadoError(
    titulo: String,
    error: ErrorApi,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 44.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Titulo(titulo, 30)
        Cuerpo(error.mensaje, alineacion = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        error.referencia?.takeIf { error.estado >= 500 || error.estado == -1 }?.let {
            Cuerpo("Referencia para soporte: $it", 14, Gris, alineacion = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
        BotonPrincipal("Reintentar", onReintentar)
    }
}

/** Indicador de carga centrado, con texto. */
@Composable
fun Cargando(texto: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Verde, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        Cuerpo(texto, 18, alineacion = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Encabezado blanco con el boton "← VOLVER …" de las pantallas secundarias. */
@Composable
fun BarraVolver(texto: String, onVolver: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Blanco).padding(top = 56.dp, bottom = 14.dp, start = 20.dp)) {
        Text("← ${texto.uppercase()}", style = textoBoton(15), color = Laton,
            modifier = Modifier.clickable(onClick = onVolver).padding(8.dp))
    }
    Linea()
}

/** Interruptor Sí / No de dos pildoras pegadas. */
@Composable
fun InterruptorSiNo(valor: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, HairlineGris, RoundedCornerShape(999.dp))
            .background(Blanco)
    ) {
        listOf(true, false).forEach { opcion ->
            val activo = valor == opcion
            Box(
                Modifier
                    .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
                    .background(if (activo) Verde else Color.Transparent)
                    .clickable { onCambio(opcion) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (opcion) "Sí" else "No",
                    style = cuerpo(16, negrita = true),
                    color = if (activo) Blanco else Apagado
                )
            }
        }
    }
}

/** Consejo en Playfair italica con la barra dorada a la izquierda. */
@Composable
fun Consejo(texto: String, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(LatonClaro))
        Text(texto, style = nota(), color = Laton, modifier = Modifier.padding(start = 14.dp))
    }
}

/** Tarjeta blanca con sombra suave, el contenedor base del sistema. */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    radio: Int = 28,
    borde: BorderStroke? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radio.dp),
        color = Blanco,
        shadowElevation = 6.dp,
        border = borde
    ) {
        Column(content = contenido)
    }
}
