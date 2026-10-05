package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.core.session.Sesion
import com.residenciaguadalupe.news.core.session.GestorSesion
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Contrato HTTP contra un servidor falso, con cuerpos copiados de
 * documentacionAPI/evidencia-pruebas-aws-2026-09-23.json.
 */
class ClienteApiTest {

    private lateinit var servidor: MockWebServer
    private lateinit var sesion: SesionEnMemoria
    private lateinit var cliente: ClienteApi
    private lateinit var gestor: GestorSesion

    private val usuario = UserDto("6", "Residente QA", "r@example.invalid", "resident", "active", "password")

    @Before
    fun preparar() {
        servidor = MockWebServer().apply { start() }
        sesion = SesionEnMemoria(Sesion("tok-123", Instant.now().plusSeconds(3600), usuario))
        gestor = GestorSesion(sesion)
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
    }

    @After
    fun cerrar() = servidor.shutdown()

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `manda el Bearer y usa rutas relativas a la base`() = runTest {
        servidor.enqueue(json(200, """{"data":[],"meta":{"page":1,"limit":20,"total":0}}"""))
        llamar { cliente.api.publicaciones(estado = "all") }
        val r = servidor.takeRequest()
        assertEquals("Bearer tok-123", r.getHeader("Authorization"))
        assertEquals("/api/v1/posts", r.requestUrl!!.encodedPath)
        assertEquals("all", r.requestUrl!!.queryParameter("status"))
        assertNull("los null no se envian", r.requestUrl!!.queryParameter("category"))
    }

    @Test
    fun `un 401 con token cierra la sesion local`() = runTest {
        servidor.enqueue(json(401, """{"error":{"code":"UNAUTHENTICATED","message":"Token Bearer requerido.","requestId":"x","details":null}}"""))
        val e = runCatching { llamar { cliente.api.yo() } }.exceptionOrNull() as ErrorApi
        assertTrue(e.sesionTerminada)
        assertNull(gestor.estado.value.sesion)
        assertEquals("Su sesión terminó. Vuelva a entrar.", gestor.estado.value.motivo)
    }

    @Test
    fun `un 401 del login no cierra nada`() = runTest {
        servidor.enqueue(json(401, """{"error":{"code":"INVALID_CREDENTIALS","message":"Correo o contraseña incorrectos.","requestId":"r1","details":null}}"""))
        val e = runCatching { llamar { cliente.api.entrar(LoginRequest("a@b.c", "password", "x")) } }
            .exceptionOrNull() as ErrorApi
        assertEquals("Correo o contraseña incorrectos.", e.mensaje)
        assertNotNull(gestor.estado.value.sesion)
    }

    @Test
    fun `un 401 activa la limpieza de datos privados`() = runTest {
        var limpiado = false
        gestor = GestorSesion(sesion) { limpiado = true }
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        servidor.enqueue(json(401, """{"error":{"code":"UNAUTHENTICATED","message":"Sesion vencida."}}"""))
        val error = runCatching { llamar { cliente.api.yo() } }.exceptionOrNull() as ErrorApi
        assertTrue(error.sesionTerminada)
        assertTrue(limpiado)
        assertNull(sesion.leer())
        assertNull(gestor.estado.value.sesion)
    }

    @Test
    fun `un 401 de una sesion anterior no cierra la nueva`() = runTest {
        val nueva = Sesion("tok-nuevo", Instant.now().plusSeconds(3600), usuario.copy(id = "8"))
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor, listOf(okhttp3.Interceptor { cadena ->
            val respuesta = cadena.proceed(cadena.request())
            // Simula un login nuevo mientras sigue en vuelo la peticion anterior.
            gestor.iniciar(nueva)
            respuesta
        }))
        servidor.enqueue(json(401, """{"error":{"code":"UNAUTHENTICATED","message":"Sesion anterior vencida."}}"""))

        val error = runCatching { llamar { cliente.api.yo() } }.exceptionOrNull() as ErrorApi

        assertTrue(error.sesionTerminada)
        assertEquals("Bearer tok-123", servidor.takeRequest().getHeader("Authorization"))
        assertEquals(nueva, gestor.estado.value.sesion)
        assertNull(gestor.estado.value.motivo)
    }

    @Test
    fun `un adjunto de otro host no recibe el token`() = runTest {
        cliente = ClienteApi("https://api.example.invalid/api/v1/", gestor)
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("PDF"))

        llamar { cliente.api.descargar(servidor.url("/documento.pdf").toString()) }.use {
            assertEquals("PDF", it.string())
        }

        assertNull(servidor.takeRequest().getHeader("Authorization"))
        assertNotNull(gestor.estado.value.sesion)
    }

    @Test
    fun `una respuesta exitosa incompatible produce un error comprensible`() = runTest {
        servidor.enqueue(json(200, """{"data":{"fullName":"Usuario incompleto"}}"""))

        val error = runCatching { llamar { cliente.api.yo() } }.exceptionOrNull() as ErrorApi

        assertEquals("BAD_RESPONSE", error.codigo)
        assertEquals(-1, error.estado)
        assertEquals("La respuesta del servidor no se pudo leer. Inténtelo de nuevo.", error.mensaje)
        assertNotNull(gestor.estado.value.sesion)
    }

    @Test
    fun `lee el error de la aplicacion con details`() = runTest {
        servidor.enqueue(json(422, """{"error":{"code":"VALIDATION_FAILED","message":"Revise los datos marcados.","requestId":"abc","details":[{"field":"title","message":"El título es obligatorio."}]}}"""))
        val e = runCatching { llamar { cliente.api.crearPublicacion(PostEscrituraRequest(type = "notice", category = "Aviso", title = "", body = "b"), "clave-12345") } }
            .exceptionOrNull() as ErrorApi
        assertEquals("VALIDATION_FAILED", e.codigo)
        assertEquals("abc", e.referencia)
        assertEquals("El título es obligatorio.", e.mensajeDe("title"))
        assertTrue(e.mensajeCompleto.contains("El título es obligatorio."))
    }

    @Test
    fun `tolera el 408 de AWS y el HTML de infraestructura`() = runTest {
        // OkHttp reintenta un 408 una vez por su cuenta (las subidas no: son de un solo envio).
        repeat(2) {
            servidor.enqueue(json(408, """{"message":"Request Timeout"}""").setHeader("apigw-requestid", "EKU1Pjt2oAMEVUg="))
        }
        val e408 = runCatching { llamar { cliente.api.salud() } }.exceptionOrNull() as ErrorApi
        assertEquals(408, e408.estado)
        assertEquals("EKU1Pjt2oAMEVUg=", e408.referencia)
        assertFalse(e408.mensaje.contains("Request Timeout"))

        servidor.enqueue(MockResponse().setResponseCode(502).setHeader("Content-Type", "text/html").setBody("<html><body>Bad Gateway</body></html>"))
        val e502 = runCatching { llamar { cliente.api.salud() } }.exceptionOrNull() as ErrorApi
        assertFalse(e502.mensaje.contains("<"))
    }

    @Test
    fun `429 usa Retry-After`() = runTest {
        servidor.enqueue(json(429, """{"error":{"code":"RATE_LIMITED","message":"Demasiadas solicitudes.","requestId":"q","details":null}}""").setHeader("Retry-After", "30"))
        val e = runCatching { llamar { cliente.api.salud() } }.exceptionOrNull() as ErrorApi
        assertEquals(30, e.reintentarEn)
        assertTrue(e.mensaje.contains("30 segundos"))
    }

    @Test
    fun `logout 204 sin cuerpo`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))
        llamarSinCuerpo { cliente.api.salir() }
        assertEquals("/api/v1/auth/logout", servidor.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `login manda clientType android`() = runTest {
        servidor.enqueue(json(200, LOGIN))
        val r = llamar { cliente.api.entrar(LoginRequest("a@b.c", "pin", "1234")) }.data
        assertEquals("Bearer", r.tokenType)
        val cuerpo = Json.parseToJsonElement(servidor.takeRequest().body.readUtf8()).jsonObject
        assertEquals("\"android\"", cuerpo["clientType"].toString())
        assertEquals("\"pin\"", cuerpo["authMethod"].toString())
    }

    @Test
    fun `POST lleva type e Idempotency-Key y PUT va sin type, sin nulls y con If-Match`() = runTest {
        val aviso = PostEscrituraRequest(type = "notice", category = "Aviso", title = "t", body = "b")
        servidor.enqueue(json(201, POST))
        llamar { cliente.api.crearPublicacion(aviso, "clave-12345") }
        val post = servidor.takeRequest()
        assertEquals("clave-12345", post.getHeader("Idempotency-Key"))
        val cuerpoPost = Json.parseToJsonElement(post.body.readUtf8()).jsonObject
        assertEquals("\"notice\"", cuerpoPost["type"].toString())
        assertEquals("[]", cuerpoPost["attachmentIds"].toString())
        assertFalse("un aviso no lleva campos de menu", cuerpoPost.containsKey("menuDate"))
        assertFalse(cuerpoPost.containsKey("isPinned"))

        servidor.enqueue(json(200, POST))
        llamar { cliente.api.editarPublicacion("7", aviso.copy(type = null), "\"1\"") }
        val put = servidor.takeRequest()
        assertEquals("\"1\"", put.getHeader("If-Match"))
        val cuerpoPut = Json.parseToJsonElement(put.body.readUtf8()).jsonObject
        assertFalse("PUT no lleva type", cuerpoPut.containsKey("type"))
        assertFalse(cuerpoPut.containsKey("expiresAt"))
    }

    @Test
    fun `editar usuario sin perfil lo omite en vez de mandar null`() {
        val cuerpo = ClienteApi.json.encodeToString(EditarUsuarioRequest.serializer(),
            EditarUsuarioRequest("Nombre", "resident", "active"))
        assertEquals("""{"fullName":"Nombre","role":"resident","status":"active"}""", cuerpo)
    }

    @Test
    fun `contentUrl se resuelve contra el origen sin duplicar api v1`() {
        val c = ClienteApi("https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1/", gestor)
        assertEquals("https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1/media/3/content",
            c.urlAdjunto("/api/v1/media/3/content"))
    }

    @Test
    fun `el Bearer tambien viaja al descargar adjuntos`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody("PNG"))
        llamar { cliente.api.descargar(cliente.urlAdjunto("/api/v1/media/3/content")) }
        val r = servidor.takeRequest()
        assertEquals("/api/v1/media/3/content", r.requestUrl!!.encodedPath)
        assertEquals("Bearer tok-123", r.getHeader("Authorization"))
    }

    companion object {
        const val LOGIN = """{"data":{"user":{"id":"1","fullName":"Administrador QA Guadalupe","email":"qa@example.invalid","role":"admin","status":"active","authMethod":"password","emailVerifiedAt":null,"approvedBy":null,"approvedAt":"2026-09-23T16:32:43.343Z","createdAt":"2026-09-23T16:32:43.343Z","updatedAt":"2026-09-23T16:32:43.343Z","residentProfile":null,"staffProfile":{"area":"Pruebas de integracion","createdAt":"2026-09-23T16:32:46.231Z","lastAccessAt":"2026-09-23T16:39:06.014Z"},"preferences":{"notifyNotices":true,"notifyActivities":true,"notifyPhotos":false}},"expiresAt":"2026-09-24T00:39:06.000Z","tokenType":"Bearer","accessToken":"t"}}"""

        const val POST = """{"data":{"id":"7","type":"notice","category":"Aviso","title":"QA-a0934d20 aviso ficticio","body":"Datos sinteticos de prueba; no corresponden a residentes reales.","status":"published","isPinned":false,"isRead":false,"author":{"id":"1","fullName":"Administrador QA Guadalupe","area":"Pruebas de integracion"},"menuDate":null,"mealType":null,"eventStartAt":null,"eventEndAt":null,"eventLocation":null,"publishedAt":"2026-09-23T16:39:11.852Z","expiresAt":null,"version":1,"attachments":[],"reactions":{"counts":{"like":0,"love":0,"happy":0},"total":0,"myReaction":null},"createdAt":"2026-09-23T16:39:11.852Z","updatedAt":"2026-09-23T16:39:11.852Z"}}"""
    }
}
