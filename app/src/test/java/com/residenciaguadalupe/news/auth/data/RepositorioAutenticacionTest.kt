package com.residenciaguadalupe.news.auth.data

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.ClienteApiTest
import com.residenciaguadalupe.news.data.red.ClienteApi
import com.residenciaguadalupe.news.data.red.ErrorApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import com.residenciaguadalupe.news.data.red.ApiData
import com.residenciaguadalupe.news.data.red.UserDto

@OptIn(ExperimentalCoroutinesApi::class)
class RepositorioAutenticacionTest {
    private lateinit var servidor: MockWebServer
    @Before fun preparar() { servidor = MockWebServer().apply { start() } }
    @After fun cerrar() { servidor.shutdown() }

    private fun respuesta(cuerpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `login conserva token usuario y vencimiento declarados`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria())
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        val repo = RepositorioAutenticacionApi(cliente.api, gestor, this)
        servidor.enqueue(respuesta(ClienteApiTest.LOGIN))
        val usuario = repo.entrar(" qa@example.invalid ", "password", "secreto")
        assertEquals(usuario, gestor.estado.value.sesion!!.usuario)
        assertEquals("t", gestor.estado.value.sesion!!.token)
        assertEquals(Instant.parse("2026-09-24T00:39:06Z"), gestor.estado.value.sesion!!.venceEn)
    }

    @Test
    fun `vencimiento ilegible no inventa ocho horas de sesion`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria())
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        val repo = RepositorioAutenticacionApi(cliente.api, gestor, this)
        servidor.enqueue(respuesta(ClienteApiTest.LOGIN.replace("2026-09-24T00:39:06.000Z", "fecha-invalida")))
        val error = runCatching { repo.entrar("qa@example.invalid", "password", "secreto") }.exceptionOrNull() as ErrorApi
        assertEquals("BAD_RESPONSE", error.codigo)
        assertNull(gestor.estado.value.sesion)
    }

    @Test
    fun `logout limpia inmediatamente y revoca el token capturado aunque haya otra cuenta`() = runTest {
        val usuario = ClienteApi.json.decodeFromString(com.residenciaguadalupe.news.data.red.ApiData.serializer(
            com.residenciaguadalupe.news.data.red.LoginDto.serializer()), ClienteApiTest.LOGIN).data.user
        val gestor = GestorSesion(SesionEnMemoria(Sesion("anterior", Instant.now().plusSeconds(3600), usuario)))
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        val repo = RepositorioAutenticacionApi(cliente.api, gestor, this)
        servidor.enqueue(MockResponse().setResponseCode(401))
        repo.salir()
        assertNull(gestor.estado.value.sesion)
        gestor.iniciar(Sesion("nuevo", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
        runCurrent()
        val peticion = servidor.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer anterior", peticion.getHeader("Authorization"))
        assertEquals("/api/v1/auth/logout", peticion.requestUrl!!.encodedPath)
        assertEquals("nuevo", gestor.estado.value.sesion!!.token)
    }

    @Test fun `registro pendiente recorta datos y no instala sesion`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria())
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        val repo = RepositorioAutenticacionApi(cliente.api, gestor, this)
        val pendiente = UserDto("8", "QA", "qa@example.invalid", "resident", "pending", "pin")
        servidor.enqueue(respuesta(ClienteApi.json.encodeToString(ApiData(pendiente))).setResponseCode(201))
        assertEquals(pendiente, repo.registrar(" QA ", " qa@example.invalid ", "pin", "1234"))
        assertNull(gestor.estado.value.sesion)
        val r = servidor.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/register", r.path)
        val enviado = ClienteApi.json.decodeFromString(com.residenciaguadalupe.news.data.red.RegistroRequest.serializer(), r.body.readUtf8())
        assertEquals("QA", enviado.fullName)
        assertEquals("qa@example.invalid", enviado.email)
    }

    @Test fun `login anterior no sustituye otra sesion instalada antes de respuesta`() = runTest {
        val gestor = GestorSesion(SesionEnMemoria())
        val nuevo = UserDto("8", "Otra cuenta", "otra@example.invalid", "resident", "active", "password")
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor, listOf(okhttp3.Interceptor { cadena ->
            cadena.proceed(cadena.request()).also {
                gestor.iniciar(Sesion("nuevo", Instant.now().plusSeconds(3600), nuevo))
            }
        }))
        val repo = RepositorioAutenticacionApi(cliente.api, gestor, this)
        servidor.enqueue(respuesta(ClienteApiTest.LOGIN))
        val error = runCatching { repo.entrar("qa@example.invalid", "password", "secreto") }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals("nuevo", gestor.estado.value.sesion!!.token)
        assertEquals(nuevo, gestor.estado.value.sesion!!.usuario)
    }
}
