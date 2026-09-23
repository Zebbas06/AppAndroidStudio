package com.residenciaguadalupe.news.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.ui.theme.*

/**
 * Aviso emergente inferior. Con [onDeshacer] muestra el boton Deshacer
 * (fijar y archivar); eliminar no lo ofrece porque la API no tiene restore.
 * Se dibuja encima del contenido dentro de un Box.
 */
@Composable
fun AvisoEmergente(texto: String, onDeshacer: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Carbon)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, style = cuerpo(17), color = Hueso, modifier = Modifier.weight(1f))
        if (onDeshacer == null) return@Row
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, OroClaro.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                .clickable(onClick = onDeshacer)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("DESHACER", style = textoBoton(14), color = OroClaro)
        }
    }
}
