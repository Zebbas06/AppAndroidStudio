package com.residenciaguadalupe.news.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.ui.theme.*

/** Un destino de la barra inferior. insignia = 0 significa sin conteo. */
data class Destino(val etiqueta: String, val ruta: String, val insignia: Int = 0)

/**
 * Barra inferior de solo texto (el mockup no usa iconos):
 * franja laton de 2dp sobre el destino activo y circulo ladrillo con
 * el numero de notificaciones sin leer junto a "Avisos".
 */
@Composable
fun BarraInferior(
    destinos: List<Destino>,
    rutaActual: String,
    onNavegar: (String) -> Unit
) {
    Surface(color = Blanco, shadowElevation = 12.dp) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                destinos.forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (d.ruta == rutaActual) LatonClaro else Color.Transparent)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                destinos.forEach { d ->
                    val activo = d.ruta == rutaActual
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp)
                            .clickable { onNavegar(d.ruta) }
                            .padding(vertical = 18.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            d.etiqueta.uppercase(),
                            style = textoBoton(13),
                            color = if (activo) Laton else GrisClaro
                        )
                        if (d.insignia > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Ladrillo)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.insignia.toString(), style = cuerpo(13, negrita = true), color = Blanco)
                            }
                        }
                    }
                }
            }
        }
    }
}
