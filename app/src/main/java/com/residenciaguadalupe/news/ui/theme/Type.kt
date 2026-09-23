package com.residenciaguadalupe.news.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.residenciaguadalupe.news.R

/**
 * Tipografia. Dos voces, como en el mockup:
 *
 *  - Playfair Display en italica para los titulos.
 *  - Source Sans 3 en semibold (600) para TODO el cuerpo de texto.
 *
 * El cambio de cuerpo a palo seco en semibold es exactamente el ajuste que
 * pidieron los socios para que la letra se lea mejor.
 *
 * Se descargan de Google Fonts en tiempo de ejecucion; no hay que meter .ttf
 * en res/font. Requiere el proveedor declarado en res/values/font_certs.xml.
 */

private val proveedor = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val playfair = GoogleFont("Playfair Display")
private val sourceSans = GoogleFont("Source Sans 3")

val FuenteTitulo = FontFamily(
    Font(googleFont = playfair, fontProvider = proveedor, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = playfair, fontProvider = proveedor, weight = FontWeight.SemiBold, style = FontStyle.Italic)
)

val FuenteCuerpo = FontFamily(
    Font(googleFont = sourceSans, fontProvider = proveedor, weight = FontWeight.Normal),
    Font(googleFont = sourceSans, fontProvider = proveedor, weight = FontWeight.SemiBold),
    Font(googleFont = sourceSans, fontProvider = proveedor, weight = FontWeight.Bold)
)

/**
 * Escala de texto A- / A / A+ del Perfil.
 * Se inyecta con CompositionLocalProvider desde MainActivity y multiplica
 * cada tamano, asi que afecta a las nueve pantallas por igual.
 */
val LocalEscala = compositionLocalOf { 1f }

@Composable
fun esc(sp: TextUnit): TextUnit = sp * LocalEscala.current

// --- Estilos --------------------------------------------------------------

/** Titulo grande en Playfair italica. */
@Composable
fun titulo(tamano: Int = 34): TextStyle = TextStyle(
    fontFamily = FuenteTitulo,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = esc(tamano.sp),
    lineHeight = esc((tamano * 1.05f).sp),
    letterSpacing = (-0.015 * tamano).sp
)

/** Rotulo en versalitas con mucho tracking: "SU PERFIL", "ARCHIVO"... */
@Composable
fun rotulo(tamano: Int = 12): TextStyle = TextStyle(
    fontFamily = FuenteCuerpo,
    fontWeight = FontWeight.Bold,
    fontSize = esc(tamano.sp),
    lineHeight = esc((tamano * 1.2f).sp),
    letterSpacing = esc((tamano * 0.2f).sp)
)

/** Cuerpo de texto: semibold, minimo 17sp. */
@Composable
fun cuerpo(tamano: Int = 18, negrita: Boolean = false): TextStyle = TextStyle(
    fontFamily = FuenteCuerpo,
    fontWeight = if (negrita) FontWeight.Bold else FontWeight.SemiBold,
    fontSize = esc(tamano.sp),
    lineHeight = esc((tamano * 1.5f).sp)
)

/** Texto de boton: versalitas, bold. */
@Composable
fun textoBoton(tamano: Int = 16): TextStyle = TextStyle(
    fontFamily = FuenteCuerpo,
    fontWeight = FontWeight.Bold,
    fontSize = esc(tamano.sp),
    lineHeight = esc((tamano * 1.1f).sp),
    letterSpacing = esc((tamano * 0.14f).sp)
)

/** Nota en Playfair italica (los consejos color laton). */
@Composable
fun nota(tamano: Int = 18): TextStyle = TextStyle(
    fontFamily = FuenteTitulo,
    fontStyle = FontStyle.Italic,
    fontSize = esc(tamano.sp),
    lineHeight = esc((tamano * 1.45f).sp)
)
