package com.residenciaguadalupe.news.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.residenciaguadalupe.news.ui.theme.Blanco
import com.residenciaguadalupe.news.ui.theme.Verde

/**
 * Deslizar hacia abajo para volver a consultar la API. [onRecargar] regresa
 * cuando termina la consulta (o enseguida si no arranco), y entonces se
 * oculta el indicador. El contenido debe ser desplazable (LazyColumn o
 * verticalScroll) para recibir el gesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeslizarParaRecargar(
    onRecargar: suspend () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit
) {
    val estado = rememberPullToRefreshState()
    val recargar by rememberUpdatedState(onRecargar)
    if (estado.isRefreshing) {
        LaunchedEffect(Unit) {
            try { recargar() } finally { estado.endRefresh() }
        }
    }
    Box(modifier.nestedScroll(estado.nestedScrollConnection).clipToBounds()) {
        contenido()
        PullToRefreshContainer(estado, Modifier.align(Alignment.TopCenter),
            containerColor = Blanco, contentColor = Verde)
    }
}
