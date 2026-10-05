package com.residenciaguadalupe.news.publications

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.*
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.publications.data.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class RepositorioPublicacionesApiTest {
    private lateinit var servidor: MockWebServer
    private lateinit var repo: RepositorioPublicaciones
    @Before fun preparar() {
        servidor = MockWebServer().apply { start() }
        val usuario = UserDto("6", "QA", "qa@example.invalid", "resident", "active", "password")
        val gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        repo = RepositorioPublicacionesApi(cliente.api, cliente::urlAdjunto)
    }
    @After fun terminar() { servidor.shutdown() }
    private fun respuesta(cuerpo: String, codigo: Int = 200) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)
    private fun peticion() = checkNotNull(servidor.takeRequest(5, TimeUnit.SECONDS))

    @Test fun `consulta traduce filtros pagina y modelo del contrato`() = runTest {
        val post = Json.parseToJsonElement(ClienteApiTest.POST).jsonObject.getValue("data")
        val cuerpo = buildJsonObject {
            put("data", JsonArray(listOf(post)))
            putJsonObject("meta") { put("page", 2); put("limit", 1); put("total", 3) }
        }
        servidor.enqueue(respuesta(cuerpo.toString()))
        val p = repo.publicaciones(FiltroPublicaciones(conImagen = true, categoria = Categoria.AVISO), 2, 1)
        assertEquals("7", p.elementos.single().id)
        assertEquals(2, p.pagina)
        assertTrue(p.hayMas)
        val url = peticion().requestUrl!!
        assertEquals("2", url.queryParameter("page"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals("true", url.queryParameter("hasImage"))
        assertEquals("Aviso", url.queryParameter("category"))
    }

    @Test fun `edicion conserva If Match omite type y avisa al muro`() = runTest {
        servidor.enqueue(respuesta(ClienteApiTest.POST))
        repo.editar("7", PostEscrituraRequest(type = "event", category = "Aviso", title = "Nuevo", body = "Texto"), 4)
        val r = peticion()
        assertEquals("PUT", r.method)
        assertEquals("\"4\"", r.getHeader("If-Match"))
        assertFalse(Json.parseToJsonElement(r.body.readUtf8()).jsonObject.containsKey("type"))
        assertEquals(1, repo.cambios.value)
    }

    @Test fun `escritura fallida no anuncia un cambio exitoso`() = runTest {
        servidor.enqueue(respuesta("""{"error":{"code":"VERSION_CONFLICT","message":"Cambio previo."}}""", 409))
        val error = runCatching { repo.editar("7", PostEscrituraRequest(category = "Aviso", title = "Nuevo", body = "Texto"), 4) }.exceptionOrNull()
        assertTrue(error is ErrorApi)
        assertEquals(0, repo.cambios.value)
    }

    @Test fun `lectura exitosa emite evento para muro y notificaciones`() = runTest {
        servidor.enqueue(respuesta("""{"data":{"read":true,"readAt":"2026-10-05T12:00:00Z"}}"""))
        val evento = async(start = CoroutineStart.UNDISPATCHED) { repo.actualizaciones.first() }
        repo.marcarLeida("7")
        assertEquals(CambioPublicacion.Leida("7"), evento.await())
        assertEquals("/api/v1/posts/7/read", peticion().requestUrl!!.encodedPath)
        assertEquals(0, repo.cambios.value)
    }

    @Test fun `quitar reaccion emite conteos sin invalidar todo el muro`() = runTest {
        servidor.enqueue(respuesta("""{"data":{"counts":{"like":0,"love":0,"happy":0},"total":0,"myReaction":null}}"""))
        val evento = async(start = CoroutineStart.UNDISPATCHED) { repo.actualizaciones.first() }
        val reaccion = repo.reaccionar("7", null)
        assertEquals(CambioPublicacion.Reaccion("7", reaccion), evento.await())
        assertEquals("DELETE", peticion().method)
        assertNull(reaccion.mia)
        assertEquals(0, repo.cambios.value)
    }
}
