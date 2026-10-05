package com.residenciaguadalupe.news.notifications

import com.residenciaguadalupe.news.core.session.GestorSesion
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.notifications.data.RepositorioNotificaciones
import com.residenciaguadalupe.news.notifications.data.RepositorioNotificacionesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class RepositorioNotificacionesApiTest {
    private lateinit var servidor: MockWebServer
    private lateinit var repo: RepositorioNotificaciones
    @Before fun preparar() {
        servidor = MockWebServer().apply { start() }
        val usuario = UserDto("6", "QA", "qa@example.invalid", "resident", "active", "password")
        val gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        val cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        repo = RepositorioNotificacionesApi(cliente.api)
    }
    @After fun cerrar() { servidor.shutdown() }
    private fun respuesta(cuerpo: String, codigo: Int = 200) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)
    private fun peticion() = checkNotNull(servidor.takeRequest(5, TimeUnit.SECONDS))

    @Test fun `lista traduce paginacion notificacion y contador independiente`() = runTest {
        servidor.enqueue(respuesta("""{"data":[$NOTIFICACION],"meta":{"page":2,"limit":30,"total":31,"unreadCount":8}}"""))
        val r = repo.notificaciones(2)
        assertEquals("9", r.pagina.elementos.single().id)
        assertEquals("7", r.pagina.elementos.single().postId)
        assertTrue(r.pagina.elementos.single().leida)
        assertEquals(8, r.sinLeer)
        assertEquals(2, r.pagina.pagina)
        assertFalse(r.pagina.hayMas)
        val p = peticion()
        assertEquals("Bearer token", p.getHeader("Authorization"))
        assertEquals("2", p.requestUrl!!.queryParameter("page"))
        assertEquals("30", p.requestUrl!!.queryParameter("limit"))
    }

    @Test fun `marcar devuelve fila confirmada por servidor`() = runTest {
        servidor.enqueue(respuesta("""{"data":$NOTIFICACION}"""))
        val n = repo.marcarLeida("9")
        assertTrue(n.leida)
        val p = peticion()
        assertEquals("PUT", p.method)
        assertEquals("/api/v1/notifications/9/read", p.requestUrl!!.encodedPath)
    }

    @Test fun `contador leer todas y archivar mantienen rutas y resultados`() = runTest {
        servidor.enqueue(respuesta("""{"data":{"unreadCount":8}}"""))
        servidor.enqueue(respuesta("""{"data":{"updated":8,"unreadCount":0}}"""))
        servidor.enqueue(respuesta("""{"data":{"archived":8}}"""))
        assertEquals(8, repo.noLeidas())
        assertEquals("/api/v1/notifications/unread-count", peticion().requestUrl!!.encodedPath)
        assertEquals(0, repo.leerTodas())
        assertEquals("/api/v1/notifications/read-all", peticion().requestUrl!!.encodedPath)
        assertEquals(8, repo.archivarLeidas())
        val p = peticion()
        assertEquals("POST", p.method)
        assertEquals("/api/v1/notifications/archive-read", p.requestUrl!!.encodedPath)
    }

    @Test fun `fallo de marcado conserva ErrorApi para decidir en ViewModel`() = runTest {
        servidor.enqueue(respuesta("""{"error":{"code":"NOT_FOUND","message":"No existe."}}""", 404))
        val error = runCatching { repo.marcarLeida("9") }.exceptionOrNull() as ErrorApi
        assertEquals(404, error.estado)
        assertEquals("NOT_FOUND", error.codigo)
    }

    companion object {
        const val NOTIFICACION = """{"id":"9","type":"new_post","title":"Aviso QA","body":"Texto","createdAt":"2026-10-05T12:00:00Z","isRead":true,"postId":"7"}"""
    }
}
