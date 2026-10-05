package com.residenciaguadalupe.news.media

import com.residenciaguadalupe.news.core.session.*
import com.residenciaguadalupe.news.data.Adjunto
import com.residenciaguadalupe.news.data.SesionEnMemoria
import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.media.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.concurrent.TimeUnit

class RepositorioMediosApiTest {
    @get:Rule val carpeta = TemporaryFolder()
    private lateinit var servidor: MockWebServer
    private lateinit var gestor: GestorSesion
    private lateinit var cliente: ClienteApi
    private lateinit var repo: RepositorioMediosApi
    private lateinit var archivo: ArchivoLocal
    private val usuario = UserDto("1", "QA", "qa@example.invalid", "admin", "active", "password")
    @Before fun preparar() {
        servidor = MockWebServer().apply { start() }
        gestor = GestorSesion(SesionEnMemoria(Sesion("token", Instant.now().plusSeconds(3600), usuario)))
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor)
        archivo = ArchivoLocal("foto.png", "image/png", null) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        repo = RepositorioMediosApi(cliente, gestor, FuenteArchivos { archivo }, carpeta.root)
    }
    @After fun cerrar() { servidor.shutdown() }
    private fun peticion() = checkNotNull(servidor.takeRequest(5, TimeUnit.SECONDS))
    private fun adjunto() = Adjunto("45", servidor.url("/api/v1/media/45/content").toString(),
        "application/pdf", "documento.pdf", "", 3)

    @Test fun `tipo desconocido rechaza antes de leer y enviar`() = runTest {
        archivo = ArchivoLocal("video.mp4", "video/mp4", null) { error("No debe abrir") }
        val error = runCatching { repo.subir("content://archivo", null) }.exceptionOrNull()
        assertEquals(415, (error as ErrorApi).estado)
        assertEquals(0, servidor.requestCount)
    }
    @Test fun `tamano informado excedido rechaza antes de abrir`() = runTest {
        archivo = ArchivoLocal("foto.png", "image/png", 5_000_001) { error("No debe abrir") }
        val error = runCatching { repo.subir("content://archivo", null) }.exceptionOrNull()
        assertEquals(413, (error as ErrorApi).estado)
        assertEquals(0, servidor.requestCount)
    }
    @Test fun `tamano falso no permite lectura ilimitada`() = runTest {
        val entrada = ByteArrayInputStream(ByteArray(5_000_020))
        archivo = ArchivoLocal("foto.png", "image/png", 1) { entrada }
        val error = runCatching { repo.subir("content://archivo", null) }.exceptionOrNull()
        assertEquals(413, (error as ErrorApi).estado)
        assertEquals(19, entrada.available())
        assertEquals(0, servidor.requestCount)
    }
    @Test fun `permiso perdido devuelve error local`() = runTest {
        archivo = ArchivoLocal("foto.png", "image/png", null) { throw SecurityException("Revocado") }
        val error = runCatching { repo.subir("content://archivo", null) }.exceptionOrNull()
        assertEquals("LOCAL_FILE", (error as ErrorApi).codigo)
        assertEquals(0, servidor.requestCount)
    }
    @Test fun `subida envia multipart con bearer y altText recortado`() = runTest {
        val media = MediaDto("45", originalName = "foto.png", mimeType = "image/png",
            contentUrl = "/api/v1/media/45/content", sizeBytes = 3)
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody(ClienteApi.json.encodeToString(ApiData(media))))
        val a = repo.subir("content://archivo", "  Foto de prueba  ")
        assertEquals("45", a.id)
        assertEquals(servidor.url("/api/v1/media/45/content").toString(), a.url)
        val r = peticion()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/media", r.path)
        assertEquals("Bearer token", r.getHeader("Authorization"))
        val cuerpo = r.body.readUtf8()
        assertTrue(cuerpo.contains("filename=\"foto.png\""))
        assertTrue(cuerpo.contains("name=\"altText\""))
        assertTrue(cuerpo.contains("Foto de prueba"))
        assertTrue(RepositorioMediosApi.cuerpoSubida("foto.png", "image/png", byteArrayOf(1), null).isOneShot())
    }
    @Test fun `eliminar upload admite respuesta 204`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))
        repo.eliminarArchivo("45")
        assertEquals("DELETE", peticion().method)
    }
    @Test fun `pdf se publica completo y no deja temporal`() = runTest {
        servidor.enqueue(MockResponse().setBody("PDF").setHeader("Content-Type", "application/pdf"))
        val f = repo.descargar(adjunto())
        assertEquals("PDF", f.readText())
        assertEquals("adjunto-45.pdf", f.name)
        assertEquals(listOf(f.name), carpeta.root.listFiles()!!.map { it.name })
        assertEquals("Bearer token", peticion().getHeader("Authorization"))
    }
    @Test fun `cambio de cuenta impide publicar pdf y limpia temporal`() = runTest {
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor, listOf(okhttp3.Interceptor { cadena ->
            cadena.proceed(cadena.request()).also {
                gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
            }
        }))
        repo = RepositorioMediosApi(cliente, gestor, FuenteArchivos { archivo }, carpeta.root)
        servidor.enqueue(MockResponse().setBody("PDF"))
        val error = runCatching { repo.descargar(adjunto()) }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(carpeta.root.listFiles()!!.isEmpty())
        assertEquals("Bearer token", peticion().getHeader("Authorization"))
    }

    @Test fun `fallo de almacenamiento se muestra como error local`() = runTest {
        val archivoEnVezDeCarpeta = carpeta.newFile("no-es-carpeta")
        repo = RepositorioMediosApi(cliente, gestor, FuenteArchivos { archivo }, archivoEnVezDeCarpeta)
        servidor.enqueue(MockResponse().setBody("PDF"))
        val error = runCatching { repo.descargar(adjunto()) }.exceptionOrNull()
        assertEquals("LOCAL_FILE", (error as ErrorApi).codigo)
        assertEquals(0, archivoEnVezDeCarpeta.length())
    }

    @Test fun `upload de cuenta anterior no se entrega a cuenta nueva`() = runTest {
        cliente = ClienteApi(servidor.url("/api/v1/").toString(), gestor, listOf(okhttp3.Interceptor { cadena ->
            cadena.proceed(cadena.request()).also {
                gestor.iniciar(Sesion("otro", Instant.now().plusSeconds(3600), usuario.copy(id = "8")))
            }
        }))
        repo = RepositorioMediosApi(cliente, gestor, FuenteArchivos { archivo }, carpeta.root)
        servidor.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody(ClienteApi.json.encodeToString(ApiData(MediaDto("45", mimeType = "image/png", contentUrl = "/api/v1/media/45/content")))))
        val error = runCatching { repo.subir("content://archivo", null) }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals("Bearer token", peticion().getHeader("Authorization"))
    }
}
