package com.residenciaguadalupe.news.profile

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.profile.data.RepositorioPerfil
import com.residenciaguadalupe.news.profile.data.RepositorioPerfilApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class RepositorioPerfilApiTest {
    private lateinit var servidor: MockWebServer
    private lateinit var gestor: GestorSesion
    private lateinit var almacen: SesionEnMemoria
    private lateinit var cliente: ClienteApi
    private lateinit var repo: RepositorioPerfil
    private val usuario = UserDto("6", "QA", "qa@example.invalid", "resident", "active", "password")

    @Before fun preparar() {
        servidor = MockWebServer().apply { start() }
        almacen = SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario))
        gestor = GestorSesion(almacen)
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        repo = RepositorioPerfilApi(cliente.api, gestor)
    }
    @After fun cerrar() { servidor.shutdown() }
    private fun respuesta(cuerpo: String, codigo: Int = 200) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)
    private fun peticion() = checkNotNull(servidor.takeRequest(5, TimeUnit.SECONDS))
    private fun <T> cuerpo(serializer: kotlinx.serialization.KSerializer<T>, data: T) =
        ClienteApi.json.encodeToString(ApiData.serializer(serializer), ApiData(data))

    @Test fun `guardar preferencias envia snapshot completo y persiste usuario actual`() = runTest {
        val p = PreferenciasDto(notifyNotices = false, notifyActivities = true, notifyPhotos = false)
        servidor.enqueue(respuesta(cuerpo(PreferenciasDto.serializer(), p)))
        assertEquals(p, repo.guardarPreferencias(p))
        assertEquals(p, almacen.leer()!!.usuario.preferences)
        assertEquals("QA", almacen.leer()!!.usuario.fullName)
        val r = peticion()
        assertEquals("PUT", r.method)
        assertEquals("/api/v1/auth/me/preferences", r.requestUrl!!.encodedPath)
        assertEquals(Json.parseToJsonElement(ClienteApi.json.encodeToString(p)), Json.parseToJsonElement(r.body.readUtf8()))
    }

    @Test fun `consultar preferencias conserva perfil y actualiza persistencia`() = runTest {
        val p = PreferenciasDto(notifyPhotos = false)
        servidor.enqueue(respuesta(cuerpo(PreferenciasDto.serializer(), p)))
        assertEquals(p, repo.preferencias())
        assertEquals(usuario.copy(preferences = p), gestor.estado.value.sesion!!.usuario)
        assertEquals(p, almacen.leer()!!.usuario.preferences)
        assertEquals("GET", peticion().method)
    }

    @Test fun `nombre se recorta y propaga a la sesion compartida`() = runTest {
        val nuevo = usuario.copy(fullName = "Nombre nuevo")
        servidor.enqueue(respuesta(cuerpo(UserDto.serializer(), nuevo)))
        assertEquals(nuevo, repo.cambiarNombre("  Nombre nuevo  "))
        assertEquals(nuevo, gestor.estado.value.sesion!!.usuario)
        val r = peticion()
        assertEquals("Nombre nuevo", Json.parseToJsonElement(r.body.readUtf8()).jsonObject.getValue("fullName").jsonPrimitive.content)
    }

    @Test fun `204 de credencial confirma metodo aunque refresco falle`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))
        servidor.enqueue(respuesta("""{"error":{"code":"INTERNAL_ERROR","message":"Temporal."}}""", 500))
        val u = repo.cambiarCredencial("actual", "pin", "1234")
        assertEquals("pin", u.authMethod)
        assertEquals("pin", almacen.leer()!!.usuario.authMethod)
        val r = peticion()
        val cuerpo = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("actual", cuerpo.getValue("currentSecret").jsonPrimitive.content)
        assertEquals("pin", cuerpo.getValue("authMethod").jsonPrimitive.content)
        assertEquals("/api/v1/auth/me", peticion().requestUrl!!.encodedPath)
        assertEquals("token", gestor.estado.value.sesion!!.token)
    }

    @Test fun `respuesta de nombre anterior no modifica una cuenta nueva`() = runTest {
        val nueva = Sesion("nuevo", Instant.now().plusSeconds(3600), usuario.copy(id = "8", fullName = "Otra cuenta"))
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor, listOf(okhttp3.Interceptor { cadena ->
            cadena.proceed(cadena.request()).also { gestor.iniciar(nueva) }
        }))
        repo = RepositorioPerfilApi(cliente.api, gestor)
        servidor.enqueue(respuesta(cuerpo(UserDto.serializer(), usuario.copy(fullName = "Nombre viejo"))))
        val error = runCatching { repo.cambiarNombre("Nombre viejo") }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(nueva, gestor.estado.value.sesion)
        assertEquals("Bearer token", peticion().getHeader("Authorization"))
    }
}
