package com.residenciaguadalupe.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.residenciaguadalupe.news.ui.componentes.AvisoEmergente
import com.residenciaguadalupe.news.ui.componentes.BarraInferior
import com.residenciaguadalupe.news.ui.componentes.Cargando
import com.residenciaguadalupe.news.ui.componentes.Destino
import com.residenciaguadalupe.news.ui.pantallas.*
import com.residenciaguadalupe.news.ui.theme.Crema

/** Nombres de ruta en un solo lugar para no escribirlos sueltos. */
object Rutas {
    const val ACCESO = "acceso"
    const val MURO = "muro"
    const val HOY = "hoy"
    const val DETALLE = "detalle/{postId}"
    const val AVISOS = "avisos"
    const val PERFIL = "perfil"
    const val ARCHIVO = "archivo"
    const val ADMIN_LISTA = "admin_lista"
    const val ADMIN_CREAR = "admin_crear"
    const val USUARIOS = "usuarios"
    const val USUARIO = "usuario/{id}"

    fun detalle(postId: String) = "detalle/$postId"
    /** "nuevo" = alta. */
    fun usuario(id: String?) = "usuario/${id ?: "nuevo"}"
}

/**
 * Grafo de navegacion.
 *
 * - Mientras se valida la sesion guardada se muestra una pantalla de carga.
 * - Todos los roles entran al muro. El buzon (Avisos) solo existe para el
 *   residente: admin y empleado reciben 403 en esas rutas.
 * - Administracion se abre desde el Perfil o el encabezado del muro.
 * - Si la sesion se cierra (salir, 401, token vencido) se vuelve al acceso.
 *
 * La barra inferior solo aparece en los destinos principales. Detalle,
 * archivo y las de administracion usan su propio boton "Volver".
 */
@Composable
fun AppResidencia(vm: AppViewModel = viewModel()) {
    if (vm.restaurando) {
        Box(Modifier.fillMaxSize().background(Crema), contentAlignment = Alignment.Center) {
            Cargando("Abriendo el mural…")
        }
        return
    }

    val admin: AdminViewModel = viewModel()
    val usuarios: UsuariosViewModel = viewModel()
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val inicio = remember { if (vm.usuario != null) Rutas.MURO else Rutas.ACCESO }
    val ruta = entrada?.destination?.route ?: inicio

    val destinos = buildList {
        add(Destino("Muro", Rutas.MURO))
        add(Destino("Hoy", Rutas.HOY))
        if (vm.esResidente) add(Destino("Avisos", Rutas.AVISOS, vm.sinLeer))
        add(Destino("Perfil", Rutas.PERFIL))
    }
    val conBarra = ruta in destinos.map { it.ruta }

    // Entrar y salir de la sesion mueve toda la pila.
    LaunchedEffect(vm.usuario == null) {
        val actual = nav.currentDestination?.route
        if (vm.usuario == null && actual != null && actual != Rutas.ACCESO) {
            nav.navigate(Rutas.ACCESO) { popUpTo(nav.graph.id) { inclusive = true } }
        } else if (vm.usuario != null && actual == Rutas.ACCESO) {
            nav.navigate(Rutas.MURO) { popUpTo(Rutas.ACCESO) { inclusive = true } }
        }
    }

    /** Navega sin apilar copias del mismo destino. */
    fun ir(destino: String) {
        nav.navigate(destino) {
            launchSingleTop = true
            if (destino in destinos.map { it.ruta }) popUpTo(Rutas.MURO)
        }
    }

    fun abrirDetalle(postId: String) = nav.navigate(Rutas.detalle(postId))

    Scaffold(
        containerColor = Crema,
        bottomBar = {
            if (conBarra) BarraInferior(destinos, ruta, ::ir)
        }
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            NavHost(
                navController = nav,
                startDestination = inicio,
                modifier = Modifier.fillMaxSize().background(Crema)
            ) {
                composable(Rutas.ACCESO) { AccesoScreen(vm) }
                composable(Rutas.MURO) {
                    MuroScreen(vm,
                        onAbrirDetalle = ::abrirDetalle,
                        onAgenda = { ir(Rutas.HOY) },
                        onArchivo = { nav.navigate(Rutas.ARCHIVO) },
                        onAdministracion = { nav.navigate(Rutas.ADMIN_LISTA) })
                }
                composable(Rutas.HOY) { HoyScreen(vm, ::abrirDetalle) }
                composable(Rutas.DETALLE, listOf(navArgument("postId") { type = NavType.StringType })) { e ->
                    DetalleScreen(vm, e.arguments?.getString("postId").orEmpty(),
                        onVolver = { nav.popBackStack() }, onAgenda = { ir(Rutas.HOY) })
                }
                composable(Rutas.AVISOS) {
                    AvisosScreen(vm, onAbrirDetalle = ::abrirDetalle, onMuro = { ir(Rutas.MURO) })
                }
                composable(Rutas.PERFIL) {
                    PerfilScreen(vm,
                        onAdministrarAvisos = { nav.navigate(Rutas.ADMIN_LISTA) },
                        onAdministrarUsuarios = { nav.navigate(Rutas.USUARIOS) })
                }
                composable(Rutas.ARCHIVO) {
                    ArchivoScreen(vm, onVolver = { nav.popBackStack() }, onAbrirDetalle = ::abrirDetalle)
                }
                composable(Rutas.ADMIN_LISTA) {
                    AdminListaScreen(admin,
                        onVolver = { nav.popBackStack() },
                        onCrear = { admin.nuevoAviso(); nav.navigate(Rutas.ADMIN_CREAR) },
                        onEditar = { nav.navigate(Rutas.ADMIN_CREAR) })
                }
                composable(Rutas.ADMIN_CREAR) {
                    AdminCrearScreen(admin,
                        onVolver = { nav.popBackStack() },
                        onVerMuro = { nav.navigate(Rutas.MURO) { popUpTo(Rutas.MURO) { inclusive = true } } })
                }
                composable(Rutas.USUARIOS) {
                    UsuariosScreen(usuarios,
                        onVolver = { nav.popBackStack() },
                        onAbrir = { nav.navigate(Rutas.usuario(it)) },
                        onNuevo = { nav.navigate(Rutas.usuario(null)) })
                }
                composable(Rutas.USUARIO, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    UsuarioEditarScreen(usuarios, e.arguments?.getString("id")?.takeIf { it != "nuevo" },
                        onVolver = { nav.popBackStack() })
                }
            }

            // Aviso emergente general (errores de acciones, confirmaciones).
            vm.mensaje?.let {
                AvisoEmergente(it, null,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 26.dp))
            }
        }
    }
}
