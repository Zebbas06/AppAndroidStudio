package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class ModelosTest {

    @After
    fun restaurarReloj() { Fechas.reloj = Clock.system(Fechas.ZONA) }

    /** Fija "ahora" en la zona de la residencia. */
    private fun ahora(iso: String) { Fechas.reloj = Clock.fixed(Instant.parse(iso), Fechas.ZONA) }

    @Test
    fun `categorias antiguas son alias de Aviso`() {
        assertEquals(Categoria.AVISO, Categoria.desdeApi("Salud"))
        assertEquals(Categoria.AVISO, Categoria.desdeApi("CAPILLA"))
        assertEquals(Categoria.ACTIVIDAD, Categoria.desdeApi("ACTIVIDAD"))
        assertEquals(Categoria.NOTICIA, Categoria.desdeApi("Noticia"))
    }

    @Test
    fun `convierte la publicacion de la evidencia`() {
        val dto = ClienteApi.json.decodeFromString(ApiData.serializer(PostDto.serializer()), ClienteApiTest.POST).data
        val p = dto.aPublicacion { "https://h$it" }
        assertEquals("7", p.id)
        assertEquals(TipoPublicacion.AVISO, p.tipo)
        assertEquals(Categoria.AVISO, p.categoria)
        assertEquals(EstadoAviso.PUBLICADO, p.estadoAdmin)
        // 16:39Z = 10:39 en Ciudad de Mexico (UTC-6, sin horario de verano).
        assertEquals(10, p.publicadaEn!!.hour)
        assertNull(p.adjunto)
    }

    @Test
    fun `un menu puede tener categoria Aviso y un evento conserva su hora local`() {
        val evento = PostDto(
            id = "9", type = "event", category = "Actividad", title = "Taller", body = "b", status = "published",
            author = AutorDto("1", "Gabriela"), eventStartAt = "2026-09-25T17:00:00.000Z",
            eventEndAt = "2026-09-25T18:30:00.000Z", eventLocation = "Patio",
            attachments = listOf(MediaDto("3", contentUrl = "/api/v1/media/3/content", mimeType = "image/png"))
        ).aPublicacion { "https://h$it" }
        assertEquals(11, evento.eventoInicio!!.hour)
        assertEquals("https://h/api/v1/media/3/content", evento.adjunto!!.url)
        assertTrue(evento.adjunto!!.esImagen)
        ahora("2026-09-25T15:00:00Z")
        assertEquals("Hoy, de 11:00 a. m. a 12:30 p. m.", evento.cuando)
    }

    @Test
    fun `rangos de la agenda en Ciudad de Mexico`() {
        // Miercoles 23 de septiembre de 2026, 23:30 en CDMX = jueves 05:30Z.
        ahora("2026-09-24T05:30:00Z")
        assertEquals(LocalDate.of(2026, 9, 23), Fechas.hoy())
        assertEquals("2026-09-23T06:00:00Z", Fechas.inicioDelDia(Fechas.rangoHoy().first))
        assertEquals("2026-09-24T06:00:00Z", Fechas.inicioDelDia(Fechas.rangoHoy().second))
        val (lunes, siguiente) = Fechas.rangoSemana()
        assertEquals(LocalDate.of(2026, 9, 21), lunes)
        assertEquals(LocalDate.of(2026, 9, 28), siguiente)
        assertEquals("Del lunes 21 al domingo 27", PestanaAgenda.SEMANA.rotulo())
    }

    @Test
    fun `tzdata vieja con horario de verano cae a UTC-6 fijo`() {
        // Chihuahua (Ojinaga) conserva horario de verano: simula reglas de antes de 2022.
        val conVerano = java.time.ZoneId.of("America/Ojinaga")
        assertEquals(java.time.ZoneOffset.ofHours(-6), Fechas.zonaResidencia(conVerano))
        val actual = java.time.ZoneId.of("America/Mexico_City")
        assertEquals(actual, Fechas.zonaResidencia(actual))
    }

    @Test
    fun `hora con el formato del mockup`() {
        val z = Instant.parse("2026-09-23T16:30:00Z").atZone(Fechas.ZONA)
        assertEquals("10:30 a. m.", Fechas.hora(z))
        assertEquals("12:00 p. m.", Fechas.hora(z.withHour(12).withMinute(0)))
    }

    @Test
    fun `extracto no corta palabras`() {
        assertEquals("Uno dos…", extracto("Uno dos tres", 9))
        assertEquals("Corto", extracto("Corto", 50))
    }

    @Test
    fun `lista de notificaciones lee unreadCount`() {
        val r = ClienteApi.json.decodeFromString(PaginaNotificacionesDto.serializer(),
            """{"data":[{"id":"7","type":"new_post","title":"T","body":"B","createdAt":"2026-09-23T16:39:11.858Z","isRead":false,"readAt":null,"postId":"7"}],"meta":{"page":1,"limit":100,"total":1,"unreadCount":1}}""")
        assertEquals(1, r.meta.unreadCount)
        val n = r.data.first().aNotificacion()
        assertEquals("Nuevo aviso", n.tipo)
        assertEquals("7", n.postId)
    }

    @Test
    fun `perfil de residente con familiar y telefono legible`() {
        val u = ClienteApi.json.decodeFromString(UserDto.serializer(),
            """{"id":"6","fullName":"Sebastián Ruiz Mendoza","email":"s@x.mx","role":"resident","status":"active","authMethod":"pin","emailVerifiedAt":null,"approvedBy":"1","approvedAt":null,"createdAt":null,"updatedAt":null,"residentProfile":{"phone":"7772148830","department":"112","roomNumber":"112","admissionDate":"2021-01-01","roomLocation":"Ala sur","familyContact":{"fullName":"Mariana Ruiz","relationship":"Hija","phone":"7772148830"}},"staffProfile":null,"preferences":{"notifyNotices":true,"notifyActivities":true,"notifyPhotos":false}}""")
        assertEquals("Sebastián", u.nombrePila)
        assertEquals("SR", u.iniciales)
        assertEquals("777 214 88 30", telefono(u.residentProfile!!.familyContact!!.phone))
        assertFalse(u.esPersonal)
    }
}
