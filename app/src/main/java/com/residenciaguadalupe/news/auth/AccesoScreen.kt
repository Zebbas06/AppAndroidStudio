package com.residenciaguadalupe.news.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.residenciaguadalupe.news.R
import com.residenciaguadalupe.news.data.Fechas
import com.residenciaguadalupe.news.ui.componentes.*
import com.residenciaguadalupe.news.ui.theme.*

/** Los tres momentos de la pantalla de acceso. */
private enum class Modo { LOGIN, REGISTRO, LISTO }

/**
 * PANTALLA 1 — Acceso.
 * Inicio de sesion, alta de cuenta y confirmacion, en la misma pantalla.
 * Al entrar, Navegacion lleva al muro cuando la sesion queda guardada.
 * El registro NO entra: la cuenta queda pendiente hasta que recepcion la aprueba.
 */
@Composable
fun AccesoScreen(vm: AccesoViewModel) {
    val estado by vm.estado.collectAsState()
    val editable = !estado.cargando
    var modo by remember { mutableStateOf(Modo.LOGIN) }
    var verPass by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("password") }
    var olvido by remember { mutableStateOf(false) }

    LaunchedEffect(estado.registroListo, estado.pendiente) {
        if (estado.registroListo) modo = Modo.LISTO
        if (estado.registroListo || estado.pendiente) { pass = ""; verPass = false }
    }

    fun cambiarModo(nuevo: Modo) {
        vm.limpiarAcceso(); olvido = false; pass = ""; verPass = false; modo = nuevo
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Blanco, 0.46f to Crema))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(top = 64.dp, bottom = 34.dp)
    ) {
        // Marca
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(999.dp)).background(Blanco)
                    .border(1.dp, LatonClaro, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(78.dp))
            }
            Titulo("Residencia Guadalupe A.C.", 30, modifier = Modifier.padding(top = 18.dp).width(196.dp))
            Rotulo("Mural de la residencia", modifier = Modifier.padding(top = 12.dp))
        }

        // Fotografia de la capilla en arco
        Image(
            painterResource(R.drawable.foto_capilla),
            "Capilla de la Residencia Guadalupe",
            Modifier
                .padding(top = 26.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(topStart = 160.dp, topEnd = 160.dp, bottomStart = 28.dp, bottomEnd = 28.dp)),
            contentScale = ContentScale.Crop
        )

        when {
            modo == Modo.LISTO -> TarjetaEspera(
                "Cuenta creada",
                "Recepción confirmará su cuenta",
                "Guardamos su nombre y su correo. Podrá entrar al mural en cuanto recepción confirme su habitación.",
                "Volver a iniciar sesión"
            ) { cambiarModo(Modo.LOGIN) }

            estado.pendiente -> TarjetaEspera(
                "Cuenta pendiente",
                "Su cuenta aún no está confirmada",
                "Recepción debe confirmar su habitación antes de que pueda entrar. Si ya pasó un día, pregunte en recepción.",
                "Entendido"
            ) { vm.limpiarAcceso() }

            else -> {
                val registro = modo == Modo.REGISTRO
                val esPin = metodo == "pin"
                Titulo(if (registro) "Cree su cuenta" else Fechas.saludo(), 38, modifier = Modifier.padding(top = 30.dp))
                Cuerpo(
                    if (registro) "Con su nombre y su correo le abrimos el mural. Recepción confirma su habitación."
                    else "Entre con el correo que dio en recepción para ver los avisos de la residencia.",
                    19, modifier = Modifier.padding(top = 10.dp)
                )

                estado.aviso?.let { Consejo(it, Modifier.padding(top = 20.dp)) }

                Spacer(Modifier.height(26.dp))
                if (registro) {
                    CampoTexto("Nombre completo", nombre, { nombre = it.take(150) }, placeholder = "Sebastián Ruiz Mendoza", habilitado = editable)
                    Spacer(Modifier.height(16.dp))
                }
                CampoTexto("Correo electrónico", correo, { correo = it.trim() },
                    placeholder = "sebastian.ruiz@correo.com", teclado = KeyboardType.Email, habilitado = editable)

                Spacer(Modifier.height(18.dp))
                Rotulo("Entro con", modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                SelectorPildoras(listOf("password" to "Contraseña", "pin" to "PIN"), metodo,
                    { metodo = it; pass = "" }, habilitado = editable)

                Spacer(Modifier.height(16.dp))
                CampoTexto(
                    if (esPin) "PIN" else "Contraseña", pass,
                    { pass = if (esPin) it.filter(Char::isDigit).take(6) else it.take(128) },
                    placeholder = if (esPin) "4 a 6 números" else if (registro) "Al menos 8 caracteres" else "••••••••",
                    oculto = !verPass,
                    teclado = if (esPin) KeyboardType.NumberPassword else KeyboardType.Password,
                    alTerminar = {
                        if (registro) vm.registrar(nombre, correo, metodo, pass) else vm.entrar(correo, metodo, pass)
                    }
                , habilitado = editable)
                BotonEnlace(if (verPass) "Ocultar ${if (esPin) "PIN" else "contraseña"}" else "Mostrar ${if (esPin) "PIN" else "contraseña"}",
                    { verPass = !verPass }, Laton, habilitado = editable)

                Consejo(
                    estado.error
                        ?: if (olvido) "Pida en recepción que le repongan su contraseña o PIN. Por seguridad no se envía por correo."
                        else "Revise su correo y su ${if (esPin) "PIN" else "contraseña"}. Si no la recuerda, recepción puede reponerla.",
                    Modifier.padding(top = 20.dp)
                )

                Spacer(Modifier.height(26.dp))
                Linea()
                Spacer(Modifier.height(24.dp))
                if (estado.cargando) {
                    Cargando(if (registro) "Creando su cuenta…" else "Entrando…", Modifier.padding(vertical = 0.dp))
                } else {
                    BotonPrincipal(if (registro) "Crear cuenta" else "Iniciar sesión", {
                        if (registro) vm.registrar(nombre, correo, metodo, pass) else vm.entrar(correo, metodo, pass)
                    })
                }
                Spacer(Modifier.height(12.dp))
                BotonSecundario(if (registro) "Ya tengo cuenta" else "Crear cuenta", {
                    cambiarModo(if (registro) Modo.LOGIN else Modo.REGISTRO)
                }, habilitado = !estado.cargando)
                if (!registro) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BotonEnlace("¿Olvidó su contraseña?", { vm.limpiarAcceso(); olvido = true }, habilitado = editable)
                    }
                }
            }
        }

        Cuerpo("Fundación sin fines de lucro · Cuernavaca", 16, Gris, alineacion = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    }
}

/** Tarjeta verde de espera: registro recibido o cuenta pendiente. */
@Composable
private fun TarjetaEspera(pildora: String, titulo: String, texto: String, boton: String, onBoton: () -> Unit) {
    Column(
        Modifier
            .padding(top = 30.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(SalviaPapel)
            .padding(24.dp)
    ) {
        Pildora(pildora, Verde, colorBorde = Salvia)
        Titulo(titulo, 32, modifier = Modifier.padding(top = 16.dp))
        Cuerpo(texto, color = VerdeTinta, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp))
        BotonPrincipal(boton, onBoton)
    }
}
