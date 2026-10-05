package com.residenciaguadalupe.news.app

import com.residenciaguadalupe.news.publications.administration.AdminPublicacionesViewModel
import com.residenciaguadalupe.news.publications.administration.AdminListaScreen
import com.residenciaguadalupe.news.publications.editor.EditorPublicacionViewModel
import com.residenciaguadalupe.news.publications.editor.AdminCrearScreen
import com.residenciaguadalupe.news.users.UsuariosViewModel
import com.residenciaguadalupe.news.users.UsuariosScreen
import com.residenciaguadalupe.news.users.UsuarioEditarScreen
import com.residenciaguadalupe.news.profile.PerfilViewModel
import com.residenciaguadalupe.news.profile.PerfilScreen
import com.residenciaguadalupe.news.notifications.NotificacionesViewModel
import com.residenciaguadalupe.news.notifications.AvisosScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.residenciaguadalupe.news.publications.agenda.AgendaViewModel
import com.residenciaguadalupe.news.publications.agenda.HoyScreen
import com.residenciaguadalupe.news.publications.archive.ArchivoViewModel
import com.residenciaguadalupe.news.publications.archive.ArchivoScreen
import com.residenciaguadalupe.news.publications.feed.MuroViewModel
import com.residenciaguadalupe.news.publications.feed.MuroScreen
import com.residenciaguadalupe.news.publications.detail.DetalleViewModel
import com.residenciaguadalupe.news.publications.detail.DetalleScreen
import androidx.compose.runtime.collectAsState
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
import com.residenciaguadalupe.news.auth.AccesoScreen
import com.residenciaguadalupe.news.auth.AccesoViewModel
import com.residenciaguadalupe.news.AplicacionResidencia
import com.residenciaguadalupe.news.data.Rol
import com.residenciaguadalupe.news.data.rol
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
    const val ADMIN_CREAR = "admin_crear?postId={postId}"
    const val USUARIOS = "usuarios"
    const val USUARIO = "usuario/{id}"

    fun editor(postId: String? = null) = if (postId == null) "admin_crear" else "admin_crear?postId=${android.net.Uri.encode(postId)}"
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
fun AppResidencia(vm: ArranqueViewModel, perfil: PerfilViewModel) {
    val estadoArranque by vm.estado.collectAsState()
    val estadoPerfil by perfil.estado.collectAsState()
    if (estadoArranque.restaurando) {
        Box(Modifier.fillMaxSize().background(Crema), contentAlignment = Alignment.Center) {
            Cargando("Abriendo el mural…")
        }
        return
    }

    val aplicacion = LocalContext.current.applicationContext as AplicacionResidencia
    val fabricaAcceso = remember(aplicacion) {
        viewModelFactory { initializer { AccesoViewModel(aplicacion.autenticacion, aplicacion.sesion) } }
    }
    val fabricaNotificaciones = remember(aplicacion) {
        viewModelFactory {
            initializer { NotificacionesViewModel(aplicacion.notificaciones, aplicacion.sesion, aplicacion.publicaciones.actualizaciones) }
        }
    }
    val notificaciones: NotificacionesViewModel = viewModel(factory = fabricaNotificaciones)
    val estadoNotificaciones by notificaciones.estado.collectAsState()
    val fabricaMuro = remember(aplicacion) {
        viewModelFactory { initializer { MuroViewModel(aplicacion.publicaciones, aplicacion.sesion) } }
    }
    val fabricaAgenda = remember(aplicacion) {
        viewModelFactory { initializer { AgendaViewModel(aplicacion.publicaciones, aplicacion.sesion) } }
    }
    val fabricaArchivo = remember(aplicacion) {
        viewModelFactory { initializer { ArchivoViewModel(aplicacion.publicaciones, aplicacion.sesion) } }
    }
    val fabricaDetalle = remember(aplicacion) {
        viewModelFactory {
            initializer { DetalleViewModel(aplicacion.publicaciones, aplicacion.sesion, aplicacion.medios::descargar) }
        }
    }
    val muro: MuroViewModel = viewModel(factory = fabricaMuro)
    val estadoMuro by muro.estado.collectAsState()
    val agenda: AgendaViewModel = viewModel(factory = fabricaAgenda)
    val fabricaAdmin = remember(aplicacion) {
        viewModelFactory { initializer { AdminPublicacionesViewModel(aplicacion.publicaciones, aplicacion.sesion) } }
    }
    val fabricaEditor = remember(aplicacion) {
        viewModelFactory {
            initializer {
                EditorPublicacionViewModel(aplicacion.publicaciones, aplicacion.medios,
                    aplicacion.sesion, aplicacion::descartarAdjunto)
            }
        }
    }
    val fabricaUsuarios = remember(aplicacion) {
        viewModelFactory { initializer { UsuariosViewModel(aplicacion.usuarios, aplicacion.sesion) } }
    }
    val usuarios: UsuariosViewModel = viewModel(factory = fabricaUsuarios)
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val inicio = remember { if (estadoArranque.usuario != null) Rutas.MURO else Rutas.ACCESO }
    val ruta = entrada?.destination?.route ?: inicio

    val destinos = buildList {
        add(Destino("Muro", Rutas.MURO))
        add(Destino("Hoy", Rutas.HOY))
        if ((estadoArranque.usuario?.rol == Rol.RESIDENTE)) add(Destino("Avisos", Rutas.AVISOS, estadoNotificaciones.sinLeer))
        add(Destino("Perfil", Rutas.PERFIL))
    }
    val conBarra = ruta in destinos.map { it.ruta }

    // Entrar y salir de la sesion mueve toda la pila.
    var revisionNavegada by remember { mutableStateOf(estadoArranque.revisionSesion) }
    LaunchedEffect(estadoArranque.revisionSesion, estadoArranque.usuario == null) {
        val cambioCuenta = revisionNavegada != estadoArranque.revisionSesion
        revisionNavegada = estadoArranque.revisionSesion
        val actual = nav.currentDestination?.route
        if (estadoArranque.usuario == null && actual != null && actual != Rutas.ACCESO) {
            nav.navigate(Rutas.ACCESO) { popUpTo(nav.graph.id) { inclusive = true } }
        } else if (estadoArranque.usuario != null && (actual == Rutas.ACCESO || cambioCuenta)) {
            nav.navigate(Rutas.MURO) { popUpTo(nav.graph.id) { inclusive = true } }
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
                composable(Rutas.ACCESO) { e ->
                    val acceso: AccesoViewModel = viewModel(viewModelStoreOwner = e, factory = fabricaAcceso)
                    AccesoScreen(acceso)
                }
                composable(Rutas.MURO) {
                    MuroScreen(muro,
                        onAbrirDetalle = ::abrirDetalle,
                        onAgenda = { ir(Rutas.HOY) },
                        onArchivo = { nav.navigate(Rutas.ARCHIVO) },
                        onAdministracion = { nav.navigate(Rutas.ADMIN_LISTA) })
                }
                composable(Rutas.HOY) { HoyScreen(agenda, ::abrirDetalle) }
                composable(Rutas.DETALLE, listOf(navArgument("postId") { type = NavType.StringType })) { e ->
                    val detalle: DetalleViewModel = viewModel(viewModelStoreOwner = e, factory = fabricaDetalle)
                    DetalleScreen(detalle, e.arguments?.getString("postId").orEmpty(),
                        onVolver = { nav.popBackStack() }, onAgenda = { ir(Rutas.HOY) })
                }
                composable(Rutas.AVISOS) {
                    AvisosScreen(notificaciones, onAbrirDetalle = ::abrirDetalle, onMuro = { ir(Rutas.MURO) })
                }
                composable(Rutas.PERFIL) {
                    PerfilScreen(perfil,
                        onAdministrarAvisos = { nav.navigate(Rutas.ADMIN_LISTA) },
                        onAdministrarUsuarios = { nav.navigate(Rutas.USUARIOS) },
                        onCerrarSesion = vm::cerrarSesion)
                }
                composable(Rutas.ARCHIVO) { e ->
                    val archivo: ArchivoViewModel = viewModel(viewModelStoreOwner = e, factory = fabricaArchivo)
                    ArchivoScreen(archivo, onVolver = { nav.popBackStack() }, onAbrirDetalle = ::abrirDetalle)
                }
                composable(Rutas.ADMIN_LISTA) { e ->
                    val admin: AdminPublicacionesViewModel = viewModel(viewModelStoreOwner = e, factory = fabricaAdmin)
                    AdminListaScreen(admin,
                        onVolver = { nav.popBackStack() },
                        onCrear = { nav.navigate(Rutas.editor()) },
                        onEditar = { nav.navigate(Rutas.editor(it)) })
                }
                composable(Rutas.ADMIN_CREAR, listOf(navArgument("postId") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })) { e ->
                    val editor: EditorPublicacionViewModel = viewModel(viewModelStoreOwner = e, factory = fabricaEditor)
                    AdminCrearScreen(editor, e.arguments?.getString("postId"),
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
            val mensaje = when (ruta) {
                Rutas.MURO -> estadoMuro.mensaje
                Rutas.AVISOS -> estadoNotificaciones.mensaje
                Rutas.PERFIL -> estadoPerfil.mensaje
                else -> null
            }
            LaunchedEffect(estadoPerfil.mensaje) {
                if (estadoPerfil.mensaje != null) { kotlinx.coroutines.delay(4500); perfil.ocultarMensaje() }
            }
            LaunchedEffect(estadoNotificaciones.mensaje) {
                if (estadoNotificaciones.mensaje != null) { kotlinx.coroutines.delay(4500); notificaciones.ocultarMensaje() }
            }
            LaunchedEffect(estadoMuro.mensaje) {
                if (estadoMuro.mensaje != null) { kotlinx.coroutines.delay(4500); muro.ocultarMensaje() }
            }
            mensaje?.let {
                AvisoEmergente(it, null,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 26.dp))
            }
        }
    }
}
