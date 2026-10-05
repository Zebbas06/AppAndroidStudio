package com.residenciaguadalupe.news

import com.residenciaguadalupe.news.app.AppResidencia
import com.residenciaguadalupe.news.app.ArranqueViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.residenciaguadalupe.news.profile.PerfilViewModel
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
            val aplicacion = application as AplicacionResidencia
            val fabricaArranque = remember(aplicacion) {
                viewModelFactory { initializer { ArranqueViewModel(aplicacion.autenticacion, aplicacion.sesion) } }
            }
            val vm: ArranqueViewModel = viewModel(factory = fabricaArranque)
            val fabricaPerfil = remember(aplicacion) {
                viewModelFactory { initializer { PerfilViewModel(aplicacion.perfil, aplicacion.sesion) } }
            }
            val perfil: PerfilViewModel = viewModel(factory = fabricaPerfil)
            val estadoPerfil by perfil.estado.collectAsState()
            MaterialTheme {
                CompositionLocalProvider(LocalEscala provides estadoPerfil.escala.factor) {
                    AppResidencia(vm, perfil)
                }
            }
        }
    }
}
