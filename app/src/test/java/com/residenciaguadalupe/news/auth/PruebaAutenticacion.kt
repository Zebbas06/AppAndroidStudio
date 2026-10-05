package com.residenciaguadalupe.news.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.residenciaguadalupe.news.auth.data.RepositorioAutenticacion
import com.residenciaguadalupe.news.core.session.*
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.UserDto
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PruebaAutenticacion {
    private val modelos = ViewModelStore()
    protected lateinit var gestor: GestorSesion
    protected lateinit var auth: AutenticacionFalsa
    protected val usuario = UserDto("1", "QA", "qa@example.invalid", "resident", "active", "password")
    @Before fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
        gestor = GestorSesion(SesionEnMemoria())
        auth = AutenticacionFalsa(usuario)
    }
    @After fun terminar() { modelos.clear(); Dispatchers.resetMain() }
    protected fun <T : ViewModel> conservar(vm: T) = vm.also { modelos.put(it.javaClass.name, it) }
}

class AutenticacionFalsa(private val usuario: UserDto) : RepositorioAutenticacion {
    var entradas = 0
    var registros = 0
    var consultas = 0
    var salidas = 0
    var login: suspend () -> UserDto = { usuario }
    var registro: suspend () -> UserDto = { usuario.copy(status = "pending") }
    var consultar: suspend () -> UserDto = { usuario }
    var salir: () -> Unit = {}
    override suspend fun entrar(correo: String, metodo: String, secreto: String): UserDto { entradas++; return login() }
    override suspend fun registrar(nombre: String, correo: String, metodo: String, secreto: String): UserDto { registros++; return registro() }
    override suspend fun yo(): UserDto { consultas++; return consultar() }
    override fun salir() { salidas++; salir.invoke() }
}
