package com.residenciaguadalupe.news.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.AppViewModel
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/** Que formulario de cuenta esta abierto. */
private enum class Edicion { NINGUNA, NOMBRE, CREDENCIAL }

/**
 * PANTALLA 6 — Perfil.
 * Aqui vive el control de tamano de letra A- / A / A+, que reescala
 * todas las pantallas (ver LocalEscala en Type.kt). Es local: no viaja a
 * la API. Los datos del residente salen de GET auth/me y, si recepcion
 * aun no los completa, se dice asi en vez de inventarlos.
 */
@Composable
fun PerfilScreen(
    vm: AppViewModel,
    onAdministrarAvisos: () -> Unit,
    onAdministrarUsuarios: () -> Unit
) {
    val u = vm.usuario ?: return
    var edicion by remember { mutableStateOf(Edicion.NINGUNA) }

    Column(Modifier.fillMaxSize().background(Crema)) {

        Row(
            Modifier.fillMaxWidth().background(Blanco)
                .padding(horizontal = 26.dp).padding(top = 56.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(Crema)
                    .border(1.dp, LatonClaro, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { Titulo(u.iniciales, 32, Laton) }
            Column(Modifier.padding(start = 16.dp)) {
                Rotulo("Su perfil")
                Titulo(u.fullName, 30, modifier = Modifier.padding(top = 8.dp))
                Cuerpo(subtitulo(u), 17, modifier = Modifier.padding(top = 7.dp))
            }
        }
        Linea()

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp).padding(top = 22.dp, bottom = 30.dp)
        ) {

            // Administracion, solo para el personal
            if (u.esPersonal) {
                Tarjeta(radio = 30) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                        Rotulo("Administración")
                        Cuerpo(
                            if (vm.esAdmin) "Publique avisos, apruebe a los residentes nuevos y administre las cuentas."
                            else "Publique avisos y administre los que usted creó.",
                            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                        )
                        BotonPrincipal("Avisos publicados", onAdministrarAvisos)
                        if (vm.esAdmin) {
                            Spacer(Modifier.height(12.dp))
                            BotonSecundario("Usuarios y residentes", onAdministrarUsuarios)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tamano del texto
            Tarjeta(radio = 30) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                    Rotulo("Tamaño del texto")
                    Cuerpo("Elija el tamaño en que quiere leer toda la aplicación. El cambio se aplica en todas las pantallas.",
                        modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Escala.entries.forEach { e ->
                            val activa = vm.escala == e
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .then(
                                        if (activa) Modifier.background(DegradadoVerde)
                                        else Modifier.background(Blanco)
                                            .border(1.dp, HairlineGris, RoundedCornerShape(999.dp))
                                    )
                                    .clickable { vm.cambiarEscala(e) },
                                contentAlignment = Alignment.Center
                            ) {
                                Cuerpo(e.etiqueta, if (e == Escala.GRANDE) 22 else if (e == Escala.NORMAL) 19 else 17,
                                    if (activa) Blanco else Apagado, negrita = true)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Linea()
                    Cuerpo("Así se verá el texto de los avisos del muro.", 19, TintaSuave,
                        modifier = Modifier.padding(top = 18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Datos de la cuenta
            Tarjeta(radio = 30) {
                val datos = datos(u)
                datos.forEachIndexed { i, (etiqueta, valor) ->
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        Rotulo(etiqueta)
                        Cuerpo(valor ?: "Sin registrar", 18, if (valor == null) Gris else Tinta, negrita = valor != null,
                            modifier = Modifier.padding(top = 9.dp))
                    }
                    if (i < datos.lastIndex) Linea(modifier = Modifier.padding(horizontal = 22.dp))
                }
                if (u.rol == Rol.RESIDENTE && u.residentProfile == null) {
                    Consejo("Recepción todavía no completa sus datos. Si algo falta, pídalo en recepción.",
                        Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp))
                }
            }

            // Preferencias de aviso: el buzon solo existe para residentes.
            if (vm.esResidente) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(listOf(Blanco, SalviaPapel)))
                        .border(1.dp, Salvia, RoundedCornerShape(30.dp))
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                ) {
                    Rotulo("Avisos que quiero recibir", VerdeTinta)
                    val filas = listOf(
                        Triple("avisos", "Avisos de la administración", vm.preferencias.notifyNotices),
                        Triple("actividades", "Actividades y talleres", vm.preferencias.notifyActivities),
                        Triple("fotos", "Fotografías de los eventos", vm.preferencias.notifyPhotos)
                    )
                    filas.forEach { (clave, etiqueta, valor) ->
                        Linea(HairlineVerd, Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cuerpo(etiqueta, 18, Tinta, negrita = true, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(14.dp))
                            InterruptorSiNo(valor) { vm.cambiarPref(clave, it) }
                        }
                    }
                }
            }

            // Cuenta: nombre y credencial
            Spacer(Modifier.height(16.dp))
            Tarjeta(radio = 30) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                    Rotulo("Su cuenta")
                    when (edicion) {
                        Edicion.NINGUNA -> {
                            Spacer(Modifier.height(16.dp))
                            BotonSecundario("Cambiar mi nombre", { vm.limpiarErrorPerfil(); edicion = Edicion.NOMBRE })
                            Spacer(Modifier.height(12.dp))
                            BotonSecundario("Cambiar contraseña o PIN", { vm.limpiarErrorPerfil(); edicion = Edicion.CREDENCIAL })
                        }
                        Edicion.NOMBRE -> FormularioNombre(vm, u.fullName) { edicion = Edicion.NINGUNA }
                        Edicion.CREDENCIAL -> FormularioCredencial(vm, u.authMethod) { edicion = Edicion.NINGUNA }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Linea()
            Spacer(Modifier.height(18.dp))
            BotonSecundario("Cerrar sesión", vm::cerrarSesion)
        }
    }
}

private fun subtitulo(u: UserDto): String = when (u.rol) {
    Rol.RESIDENTE -> u.residentProfile?.let { p ->
        val desde = Fechas.fecha(p.admissionDate)?.year
        "Habitación ${p.roomNumber ?: p.department}" + (desde?.let { " · Desde $it" } ?: "")
    } ?: "Residente"
    else -> listOfNotNull(u.rol.etiqueta, u.staffProfile?.area).joinToString(" · ")
}

/** Etiqueta y valor; null se muestra como "Sin registrar". */
private fun datos(u: UserDto): List<Pair<String, String?>> {
    val p = u.residentProfile
    return if (u.rol == Rol.RESIDENTE) listOf(
        "Habitación" to p?.let { listOfNotNull(it.roomNumber ?: it.department, it.roomLocation).joinToString(" · ") },
        "Correo" to u.email,
        "Teléfono" to telefono(p?.phone),
        "Familiar" to p?.familyContact?.let { f ->
            listOfNotNull(f.fullName, f.relationship.lowercase(), telefono(f.phone)).joinToString(" · ")
        },
        "Ingreso" to Fechas.fecha(p?.admissionDate)?.let(Fechas::fechaLarga)
    ) else listOf(
        "Correo" to u.email,
        "Área" to u.staffProfile?.area,
        "Función" to u.rol.etiqueta
    )
}

@Composable
private fun FormularioNombre(vm: AppViewModel, actual: String, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(actual) }
    Spacer(Modifier.height(16.dp))
    CampoTexto("Nombre completo", nombre, { nombre = it.take(150) })
    vm.perfilError?.let { Consejo(it, Modifier.padding(top = 16.dp)) }
    Spacer(Modifier.height(18.dp))
    BotonPrincipal(if (vm.perfilGuardando) "Guardando…" else "Guardar nombre",
        { vm.cambiarNombre(nombre, onCerrar) }, habilitado = !vm.perfilGuardando)
    Spacer(Modifier.height(12.dp))
    BotonSecundario("Cancelar", onCerrar, habilitado = !vm.perfilGuardando)
}

/** Cambiar la credencial cierra las demas sesiones; esta sigue abierta. */
@Composable
private fun FormularioCredencial(vm: AppViewModel, metodoActual: String, onCerrar: () -> Unit) {
    var actual by remember { mutableStateOf("") }
    var nuevo by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf(metodoActual) }
    val esPin = metodo == "pin"
    Spacer(Modifier.height(16.dp))
    CampoTexto(if (metodoActual == "pin") "PIN actual" else "Contraseña actual", actual, { actual = it.take(128) },
        oculto = true, teclado = if (metodoActual == "pin") KeyboardType.NumberPassword else KeyboardType.Password)
    Spacer(Modifier.height(16.dp))
    Rotulo("Quiero entrar con", modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
    SelectorPildoras(listOf("password" to "Contraseña", "pin" to "PIN"), metodo, { metodo = it; nuevo = "" })
    Spacer(Modifier.height(16.dp))
    CampoTexto(if (esPin) "PIN nuevo" else "Contraseña nueva", nuevo,
        { nuevo = if (esPin) it.filter(Char::isDigit).take(6) else it.take(128) },
        placeholder = if (esPin) "4 a 6 números" else "Al menos 8 caracteres",
        oculto = true, teclado = if (esPin) KeyboardType.NumberPassword else KeyboardType.Password)
    Consejo(vm.perfilError ?: "Al cambiarla se cierra su sesión en los demás teléfonos.", Modifier.padding(top = 16.dp))
    Spacer(Modifier.height(18.dp))
    BotonPrincipal(if (vm.perfilGuardando) "Guardando…" else "Guardar",
        { vm.cambiarCredencial(actual, metodo, nuevo, onCerrar) }, habilitado = !vm.perfilGuardando)
    Spacer(Modifier.height(12.dp))
    BotonSecundario("Cancelar", onCerrar, habilitado = !vm.perfilGuardando)
}
