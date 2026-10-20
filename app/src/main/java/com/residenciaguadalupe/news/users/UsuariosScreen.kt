package com.residenciaguadalupe.news.users

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.UserDto
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/**
 * PANTALLA 10 — Administración · usuarios (solo admin).
 * Empieza en Pendientes: los registros que recepcion debe confirmar.
 */
@Composable
fun UsuariosScreen(vm: UsuariosViewModel, onVolver: () -> Unit, onAbrir: (String) -> Unit, onNuevo: () -> Unit) {
    LaunchedEffect(Unit) { vm.cargar() }

    Column(Modifier.fillMaxSize().background(Crema)) {
        BarraVolver("Volver", onVolver)
        Column(
            Modifier.fillMaxWidth().background(Blanco)
                .padding(horizontal = 26.dp).padding(top = 18.dp, bottom = 18.dp)
        ) {
            Rotulo("Administración")
            Titulo("Usuarios y residentes", 34, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))
            SelectorPildoras(
                listOf(EstadoCuenta.PENDIENTE to "Pendientes", EstadoCuenta.ACTIVA to "Activas",
                    EstadoCuenta.DESHABILITADA to "Bajas", null to "Todas"),
                vm.filtroEstado, vm::cambiarFiltro, tamano = 13
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                CampoTexto("Buscar", vm.busqueda, vm::cambiarBusqueda, Modifier.weight(1f),
                    placeholder = "Nombre o correo")
                Spacer(Modifier.width(10.dp))
                Box(Modifier.widthIn(min = 110.dp).width(IntrinsicSize.Max)) { BotonSecundario("Buscar", { vm.cargar() }) }
            }
        }
        Linea()

        val error = vm.listaError
        when {
            vm.listaCargando && vm.usuarios.isEmpty() -> Cargando("Cargando usuarios…")
            error != null -> EstadoError("No se pudieron cargar los usuarios", error, { vm.cargar() })
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 26.dp)) {
                item {
                    Spacer(Modifier.height(22.dp))
                    BotonPrincipal("Dar de alta a alguien", onNuevo)
                    Spacer(Modifier.height(22.dp))
                }
                if (vm.usuarios.isEmpty()) {
                    item {
                        Cuerpo(
                            if (vm.filtroEstado == EstadoCuenta.PENDIENTE) "No hay registros esperando confirmación."
                            else "No hay usuarios con este filtro.",
                            alineacion = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                        )
                    }
                }
                items(vm.usuarios, key = { it.id }) { u ->
                    FilaUsuario(u, Modifier.padding(bottom = 14.dp)) { onAbrir(u.id) }
                }
                item {
                    if (vm.listaHayMas) {
                        BotonSecundario(if (vm.listaCargando) "Cargando…" else "Cargar más",
                            { vm.cargar(reiniciar = false) }, habilitado = !vm.listaCargando)
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun FilaUsuario(u: UserDto, modifier: Modifier, onClick: () -> Unit) {
    val estado = EstadoCuenta.desdeApi(u.status)
    Tarjeta(modifier) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pildora(u.rol.etiqueta, if (u.rol == Rol.RESIDENTE) Verde else Laton,
                    colorBorde = if (u.rol == Rol.RESIDENTE) Salvia else LatonClaro)
                Pildora(estado.etiqueta,
                    when (estado) { EstadoCuenta.PENDIENTE -> LadrilloText; EstadoCuenta.ACTIVA -> Verde; else -> Gris },
                    colorBorde = if (estado == EstadoCuenta.PENDIENTE) Ladrillo else HairlineGris)
            }
            Cuerpo(u.fullName, 19, Tinta, negrita = true, modifier = Modifier.padding(top = 12.dp))
            Cuerpo(listOfNotNull(u.email, u.residentProfile?.let { "Hab. ${it.department}" }).joinToString(" · "),
                16, Gris, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/**
 * PANTALLA 11 — Alta, edicion y aprobacion de un usuario.
 * [id] null = alta nueva (la cuenta nace activa).
 */
@Composable
fun UsuarioEditarScreen(vm: UsuariosViewModel, id: String?, onVolver: () -> Unit) {
    DisposableEffect(id) { onDispose { vm.nuevo() } }
    LaunchedEffect(id) { if (id == null) vm.nuevo() else vm.abrir(id) }
    val u = vm.editando
    val nuevo = id == null
    val editable = !vm.guardando && !vm.cargandoUsuario

    Column(Modifier.fillMaxSize().background(Crema)) {
        BarraVolver("Volver a usuarios", onVolver)

        if (vm.cargandoUsuario || (!nuevo && u == null && vm.fError.isEmpty())) {
            Cargando("Cargando usuario…")
            return@Column
        }
        if (!nuevo && u == null) {
            Consejo(vm.fError, Modifier.padding(26.dp))
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp).padding(top = 24.dp, bottom = 30.dp)
        ) {
            val pendiente = u?.status == "pending"
            Rotulo("Administración")
            Titulo(
                when {
                    nuevo -> "Dar de alta"
                    pendiente -> "Confirmar registro"
                    else -> "Editar usuario"
                }, 34, modifier = Modifier.padding(top = 10.dp)
            )
            if (pendiente) {
                Cuerpo("Complete la habitación y la fecha de ingreso, y confirme la cuenta para que pueda entrar.",
                    18, TintaSuave, modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(Modifier.height(22.dp))
            CampoTexto("Nombre completo", vm.fNombre, vm::setNombre, habilitado = editable)
            Spacer(Modifier.height(16.dp))
            CampoTexto("Correo electrónico", vm.fCorreo, vm::setCorreo, teclado = KeyboardType.Email,
                habilitado = nuevo && editable)

            Spacer(Modifier.height(18.dp))
            Rotulo("Tipo de cuenta", modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            SelectorPildoras(listOf(Rol.RESIDENTE to "Residente", Rol.EMPLEADO to "Personal", Rol.ADMIN to "Admin"),
                vm.fRol, vm::setRol, tamano = 14, habilitado = editable)

            if (!nuevo) {
                Spacer(Modifier.height(18.dp))
                Rotulo("Estado", modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                if (pendiente) Pildora("Pendiente de confirmación", LadrilloText)
                else SelectorPildoras(EstadoCuenta.entries.map { it to it.etiqueta }, vm.fEstado, vm::setEstado, tamano = 14, habilitado = editable)
            } else {
                Spacer(Modifier.height(18.dp))
                Rotulo("Entra con", modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                SelectorPildoras(listOf("password" to "Contraseña", "pin" to "PIN"), vm.fMetodo, vm::setMetodo, habilitado = editable)
                Spacer(Modifier.height(16.dp))
                CampoTexto(if (vm.fMetodo == "pin") "PIN inicial" else "Contraseña inicial", vm.fSecreto, vm::setSecreto,
                    placeholder = if (vm.fMetodo == "pin") "4 a 6 números" else "Al menos 8 caracteres",
                    oculto = true, teclado = if (vm.fMetodo == "pin") KeyboardType.NumberPassword else KeyboardType.Password, habilitado = editable)
            }

            if (vm.fRol == Rol.RESIDENTE) {
                Spacer(Modifier.height(22.dp))
                Linea()
                Rotulo("Datos del residente", Verde, Modifier.padding(top = 20.dp, bottom = 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CampoTexto("Habitación", vm.fHabitacion, vm::setHabitacion, Modifier.weight(1f), "112", habilitado = editable)
                    CampoFecha("Ingreso", vm.fIngreso, vm::setIngreso, Modifier.weight(1.3f), habilitado = editable)
                }
                Spacer(Modifier.height(16.dp))
                CampoTexto("Ubicación (opcional)", vm.fUbicacion, vm::setUbicacion, placeholder = "Ala sur, planta baja", habilitado = editable)
                Spacer(Modifier.height(16.dp))
                CampoTexto("Teléfono (opcional)", vm.fTelefono, vm::setTelefono, placeholder = "10 dígitos",
                    teclado = KeyboardType.Phone, habilitado = editable)
                Rotulo("Familiar de contacto (opcional)", Verde, Modifier.padding(top = 22.dp, bottom = 14.dp))
                CampoTexto("Nombre", vm.fFamiliarNombre, vm::setFamiliarNombre, placeholder = "Mariana Ruiz", habilitado = editable)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CampoTexto("Parentesco", vm.fFamiliarParentesco, vm::setFamiliarParentesco, Modifier.weight(1f), "Hija", habilitado = editable)
                    CampoTexto("Teléfono", vm.fFamiliarTelefono, vm::setFamiliarTelefono, Modifier.weight(1.2f),
                        "10 dígitos", teclado = KeyboardType.Phone, habilitado = editable)
                }
            } else {
                Spacer(Modifier.height(16.dp))
                CampoTexto("Área (opcional)", vm.fArea, vm::setArea, placeholder = "Terapia ocupacional", habilitado = editable)
            }

            vm.guardado?.let { Consejo(it, Modifier.padding(top = 20.dp)) }
            if (vm.fError.isNotEmpty()) Consejo(vm.fError, Modifier.padding(top = 20.dp))

            Spacer(Modifier.height(22.dp))
            Linea()
            Spacer(Modifier.height(20.dp))
            if (pendiente) {
                BotonPrincipal(if (vm.guardando) "Guardando…" else "Confirmar cuenta", vm::aprobar, habilitado = !vm.guardando)
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Guardar sin confirmar", vm::guardar, habilitado = !vm.guardando)
            } else {
                BotonPrincipal(if (vm.guardando) "Guardando…" else if (nuevo) "Dar de alta" else "Guardar cambios",
                    vm::guardar, habilitado = !vm.guardando)
            }

            // Reposicion manual de la credencial
            if (!nuevo) {
                Spacer(Modifier.height(26.dp))
                Tarjeta(radio = 30) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                        Rotulo("Reponer contraseña o PIN")
                        Cuerpo("Cierra todas las sesiones de esta persona. Entréguele la nueva credencial en persona.",
                            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
                        SelectorPildoras(listOf("password" to "Contraseña", "pin" to "PIN"), vm.fMetodo, vm::setMetodo, habilitado = editable)
                        Spacer(Modifier.height(14.dp))
                        CampoTexto(if (vm.fMetodo == "pin") "PIN nuevo" else "Contraseña nueva", vm.fSecreto, vm::setSecreto,
                            placeholder = if (vm.fMetodo == "pin") "4 a 6 números" else "Al menos 8 caracteres",
                            oculto = true, teclado = if (vm.fMetodo == "pin") KeyboardType.NumberPassword else KeyboardType.Password, habilitado = editable)
                        Spacer(Modifier.height(16.dp))
                        BotonSecundario("Reponer", vm::reponerCredencial, habilitado = !vm.guardando && vm.fSecreto.isNotEmpty())
                    }
                }
            }
        }
    }
}
