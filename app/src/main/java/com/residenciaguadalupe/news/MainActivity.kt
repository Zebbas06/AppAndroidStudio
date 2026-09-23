package com.residenciaguadalupe.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.residenciaguadalupe.news.ui.theme.LocalEscala

/**
 * Unica Activity de la aplicacion. Toda la interfaz es Compose.
 *
 * El CompositionLocalProvider de abajo es lo que hace que los botones
 * A- / A / A+ del Perfil reescalen las nueve pantallas: la escala elegida
 * viaja por el arbol y cada estilo de Type.kt la multiplica.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            MaterialTheme {
                CompositionLocalProvider(LocalEscala provides vm.escala.factor) {
                    AppResidencia(vm)
                }
            }
        }
    }
}
