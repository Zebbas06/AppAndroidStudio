package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.*
import com.residenciaguadalupe.news.data.repositorio.Repositorio
import com.residenciaguadalupe.news.data.sesion.Sesion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.util.Base64
import java.util.UUID

/**
 * Recorrido contra la API desplegada con el mismo cliente de la app.
 * Solo corre si existen RG_API_CORREO y RG_API_SECRETO (cuenta admin de
 * pruebas); nunca guarda la credencial. Crea datos con prefijo QA- y los
 * limpia al final: borra publicaciones y deshabilita las cuentas creadas.
 * Resultado paso a paso en app/build/integracion-api.txt.
 */
class IntegracionApiTest {

    private val url = System.getenv("RG_API_URL") ?: "https://jmuq4nqote.execute-api.us-east-1.amazonaws.com/api/v1/"
    private val bitacora = StringBuilder()
    private val fallas = mutableListOf<String>()

    private fun cliente(): Pair<ClienteApi, SesionEnMemoria> {
        val s = SesionEnMemoria()
        return ClienteApi(url, s) to s
    }

    private suspend fun ClienteApi.entrar(sesion: SesionEnMemoria, correo: String, metodo: String, secreto: String): UserDto {
        val r = llamar { api.entrar(LoginRequest(correo, metodo, secreto)) }.data
        sesion.guardar(Sesion(r.accessToken, Instant.parse(r.expiresAt), r.user))
        return r.user
    }

    private suspend fun paso(nombre: String, bloque: suspend () -> String) {
        try {
            val detalle = bloque()
            bitacora.append("OK    ").append(nombre).append(if (detalle.isNotEmpty()) " — $detalle" else "").append('\n')
        } catch (e: Throwable) {
            val msg = if (e is ErrorApi) "HTTP ${e.estado} ${e.codigo}: ${e.mensajeCompleto} (ref ${e.referencia})" else "${e::class.simpleName}: ${e.message}"
            bitacora.append("FALLA ").append(nombre).append(" — ").append(msg).append('\n')
            fallas += nombre
        }
    }

    /** Espera un ErrorApi con ese status. */
    private suspend fun esperaError(estado: Int, bloque: suspend () -> Any?): ErrorApi {
        try {
            bloque()
        } catch (e: ErrorApi) {
            if (e.estado != estado) throw AssertionError("se esperaba $estado y llegó ${e.estado} ${e.codigo}: ${e.mensaje}")
            return e
        }
        throw AssertionError("se esperaba $estado y la llamada tuvo éxito")
    }

    private fun check(condicion: Boolean, mensaje: String) { if (!condicion) throw AssertionError(mensaje) }

    @Test
    fun recorridoAdmin() = runBlocking {
        val correo = System.getenv("RG_API_CORREO")
        val secreto = System.getenv("RG_API_SECRETO")
        assumeTrue("Sin credenciales RG_API_*: se omite", !correo.isNullOrBlank() && !secreto.isNullOrBlank())

        val tag = "QA-" + UUID.randomUUID().toString().take(8)
        val (admin, sAdmin) = cliente()
        val api = admin.api
        val posts = mutableListOf<String>()
        val usuarios = mutableListOf<String>()
        val claveResidente = "Residente-${UUID.randomUUID().toString().take(12)}"
        val correoResidente = "qa.residente.${tag.lowercase()}@example.invalid"
        val correoEmpleado = "qa.empleado.${tag.lowercase()}@example.invalid"
        val pinEmpleado = "482915"
        var residenteId = ""
        var empleadoId = ""
        var avisoId = ""
        var avisoVersion = 0
        var eventoId = ""
        var mediaId = ""

        try {
            // --- Sesion y perfil admin ------------------------------------------
            paso("salud y ready") { llamar { api.salud() }.data.status }
            paso("login admin") {
                val u = admin.entrar(sAdmin, correo!!, "password", secreto!!)
                check(u.rol == Rol.ADMIN, "rol ${u.role}")
                "rol=${u.role}, vence=${sAdmin.sesion.value!!.venceEn}"
            }
            paso("GET auth/me") { llamar { api.yo() }.data.let { "${it.fullName}, staffProfile.area=${it.staffProfile?.area}" } }
            paso("preferencias GET/PUT") {
                val p = llamar { api.preferencias() }.data
                llamar { api.guardarPreferencias(p) }.data.let { check(it == p, "cambió"); "sin cambios" }
            }
            paso("buzón oculto para admin (403)") {
                esperaError(403) { llamar { api.noLeidas() } }.codigo
            }

            // --- Usuarios: registro publico, pendiente y aprobacion --------------
            paso("registro público queda resident/pending") {
                val u = llamar { api.registrar(RegistroRequest("$tag Residente", correoResidente, "password", claveResidente)) }.data
                residenteId = u.id; usuarios += u.id
                check(u.status == "pending" && u.role == "resident", "${u.role}/${u.status}")
                "id=${u.id}"
            }
            paso("login de pendiente = 403 ACCOUNT_PENDING") {
                val (c, s) = cliente()
                val e = esperaError(403) { c.entrar(s, correoResidente, "password", claveResidente) }
                check(e.cuentaPendiente, e.codigo); e.mensaje
            }
            paso("lista de pendientes lo incluye") {
                val p = llamar { api.usuarios(estado = "pending", busqueda = tag) }
                check(p.data.any { it.id == residenteId }, "no aparece"); "total=${p.meta.total}"
            }
            paso("(informativo) activar sin perfil") {
                // La API lo permite; la app exige habitacion y fecha de ingreso al confirmar.
                try {
                    val u = llamar { api.editarUsuario(residenteId, EditarUsuarioRequest("$tag Residente", "resident", "active")) }.data
                    "la API lo acepta: status=${u.status}, residentProfile=${u.residentProfile}"
                } catch (e: ErrorApi) { "la API lo rechaza: ${e.estado} ${e.codigo}" }
            }
            paso("aprobar con perfil (PUT users/{id})") {
                val u = llamar {
                    api.editarUsuario(residenteId, EditarUsuarioRequest("$tag Residente", "resident", "active",
                        PerfilResidenteRequest("QA-112", "2021-01-01", "7770000000", "Ala sur",
                            FamiliarDto("Familiar QA", "Hija", "7770000001"))))
                }.data
                check(u.status == "active" && u.residentProfile?.roomNumber == "QA-112", "${u.status} ${u.residentProfile}")
                "approvedBy=${u.approvedBy}, familiar=${u.residentProfile?.familyContact?.fullName}"
            }
            paso("PUT sin perfil conserva el perfil") {
                val u = llamar { api.editarUsuario(residenteId, EditarUsuarioRequest("$tag Residente", "resident", "active")) }.data
                check(u.residentProfile?.department == "QA-112", "perdió perfil"); "conservado"
            }
            paso("alta de empleado con PIN (POST users)") {
                val u = llamar {
                    api.crearUsuario(CrearUsuarioRequest("$tag Empleado", correoEmpleado, "employee", "pin", pinEmpleado,
                        staffProfile = PerfilPersonalRequest("QA Terapia")))
                }.data
                empleadoId = u.id; usuarios += u.id
                check(u.status == "active", u.status); "id=${u.id}, area=${u.staffProfile?.area}"
            }

            // --- Publicaciones -----------------------------------------------------
            val clave = UUID.randomUUID().toString()
            val aviso = PostEscrituraRequest(type = "notice", category = "AVISO", title = "$tag aviso",
                body = "Datos ficticios de prueba.\n\nSegundo párrafo.", isPinned = false)
            paso("crear aviso (POST + Idempotency-Key)") {
                val p = llamar { api.crearPublicacion(aviso, clave) }.data
                avisoId = p.id; avisoVersion = p.version; posts += p.id
                val m = p.aPublicacion(admin::urlAdjunto)
                check(m.categoria == Categoria.AVISO && m.estado == "published", "${p.category} ${p.status}")
                "id=${p.id}, category=${p.category}, version=${p.version}"
            }
            paso("repetir la misma clave no duplica") {
                val p = llamar { api.crearPublicacion(aviso, clave) }.data
                check(p.id == avisoId, "duplicó: ${p.id}"); "mismo id"
            }
            paso("misma clave con otro cuerpo = 409") {
                esperaError(409) { llamar { api.crearPublicacion(aviso.copy(title = "$tag otro"), clave) } }.codigo
            }
            paso("alias de categoría Salud -> Aviso") {
                val p = llamar {
                    api.crearPublicacion(aviso.copy(category = "Salud", title = "$tag salud"), UUID.randomUUID().toString())
                }.data
                posts += p.id
                check(p.category == "Aviso", p.category); "category=${p.category}"
            }
            paso("crear menú (menuDate civil, mealType)") {
                val p = llamar {
                    api.crearPublicacion(PostEscrituraRequest(type = "menu", category = "Aviso", title = "$tag menú",
                        body = "Sopa y guisado.", menuDate = Fechas.hoy().plusDays(1).toString(), mealType = "lunch"),
                        UUID.randomUUID().toString())
                }.data
                posts += p.id
                check(p.menuDate == Fechas.hoy().plusDays(1).toString(), "${p.menuDate}")
                "menuDate=${p.menuDate}, cuando=${p.aPublicacion(admin::urlAdjunto).cuando}"
            }
            paso("crear evento hoy 11:00-12:30 en CDMX") {
                val hoy = Fechas.hoy()
                val ini = Fechas.aIso(hoy.atTime(LocalTime.of(11, 0)).atZone(Fechas.ZONA))
                val fin = Fechas.aIso(hoy.atTime(LocalTime.of(12, 30)).atZone(Fechas.ZONA))
                val p = llamar {
                    api.crearPublicacion(PostEscrituraRequest(type = "event", category = "ACTIVIDAD", title = "$tag evento",
                        body = "Taller ficticio.", eventStartAt = ini, eventEndAt = fin, eventLocation = "Patio QA"),
                        UUID.randomUUID().toString())
                }.data
                eventoId = p.id; posts += p.id
                val m = p.aPublicacion(admin::urlAdjunto)
                check(m.eventoInicio!!.hour == 11 && m.eventoFin!!.minute == 30, "${p.eventStartAt}")
                "eventStartAt=${p.eventStartAt}, cuando=${m.cuando}"
            }
            paso("PUT con type = 422") {
                esperaError(422) {
                    // Se arma a mano para forzar type (Repositorio.editar lo quita).
                    llamar { api.editarPublicacion(avisoId, aviso, "\"$avisoVersion\"") }
                }.codigo
            }
            paso("editar con If-Match") {
                val p = llamar { api.editarPublicacion(avisoId, aviso.copy(type = null, title = "$tag aviso editado"), "\"$avisoVersion\"") }.data
                check(p.version == avisoVersion + 1, "version ${p.version}")
                val vieja = avisoVersion; avisoVersion = p.version
                "version $vieja -> ${p.version}"
            }
            paso("editar con versión vieja = 409 VERSION_CONFLICT") {
                val e = esperaError(409) {
                    llamar { api.editarPublicacion(avisoId, aviso.copy(type = null), "\"${avisoVersion - 1}\"") }
                }
                check(e.conflictoVersion, e.codigo); e.codigo
            }
            paso("fijar") {
                val p = llamar { api.fijar(avisoId, FijarPostRequest(true)) }.data
                avisoVersion = p.version
                check(p.isPinned, "no fijó"); "isPinned=${p.isPinned}"
            }
            paso("fijadas aparecen primero en el muro") {
                val p = llamar { api.publicaciones(limite = 5) }.data
                check(p.first().isPinned, "primero no fijado"); "primero=${p.first().title}"
            }
            paso("archivar desfija") {
                val p = llamar { api.cambiarEstado(avisoId, EstadoPostRequest("archived")) }.data
                check(p.status == "archived" && !p.isPinned, "${p.status} pinned=${p.isPinned}")
                "status=${p.status}, isPinned=${p.isPinned}"
            }
            paso("archivada no sale en el muro publicado") {
                val p = llamar { api.publicaciones(limite = 100) }.data
                check(p.none { it.id == avisoId }, "sigue visible"); "ok"
            }
            paso("status=all la incluye (admin)") {
                val p = llamar { api.publicaciones(estado = "all", limite = 100, orden = "-publishedAt") }.data
                check(p.any { it.id == avisoId }, "no aparece"); "total página=${p.size}"
            }
            paso("republicar (Deshacer de archivar) + volver a fijar") {
                llamar { api.cambiarEstado(avisoId, EstadoPostRequest("published")) }
                val p = llamar { api.fijar(avisoId, FijarPostRequest(true)) }.data
                avisoVersion = p.version
                check(p.status == "published" && p.isPinned, "${p.status}/${p.isPinned}"); "published + fijado"
            }

            // --- Adjuntos ------------------------------------------------------------
            val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")
            paso("subir PNG (multipart de un solo envío)") {
                val m = llamar { api.subirArchivo(Repositorio.cuerpoSubida("$tag.png", "image/png", png, "Imagen QA")) }.data
                mediaId = m.id
                check(m.postId == null && m.sizeBytes == png.size.toLong(), "${m.postId} ${m.sizeBytes}")
                "id=${m.id}, contentUrl=${m.contentUrl} -> ${admin.urlAdjunto(m.contentUrl)}"
            }
            paso("preview con Bearer, bytes idénticos") {
                val r = api.descargar(admin.urlAdjunto("/api/v1/media/$mediaId/content"))
                check(r.isSuccessful, "HTTP ${r.code()}")
                val bytes = r.body()!!.bytes()
                check(bytes.contentEquals(png), "bytes distintos")
                "Content-Type=${r.headers()["Content-Type"]}, Cache-Control=${r.headers()["Cache-Control"]}"
            }
            paso("sin token no se descarga (401)") {
                val (c, _) = cliente()
                esperaError(401) { llamar { c.api.descargar(c.urlAdjunto("/api/v1/media/$mediaId/content")) } }.codigo
            }
            paso("formato falso = 415") {
                esperaError(415) {
                    llamar { api.subirArchivo(Repositorio.cuerpoSubida("$tag.png", "image/png", "no soy png".toByteArray(), null)) }
                }.codigo
            }
            paso("asociar la imagen al evento (PUT attachmentIds)") {
                val e = llamar { api.publicacion(eventoId) }.data
                val p = llamar {
                    api.editarPublicacion(eventoId, PostEscrituraRequest(category = e.category, title = e.title, body = e.body,
                        isPinned = e.isPinned, attachmentIds = listOf(mediaId), eventStartAt = e.eventStartAt,
                        eventEndAt = e.eventEndAt, eventLocation = e.eventLocation), "\"${e.version}\"")
                }.data
                check(p.attachments.singleOrNull()?.id == mediaId, "sin adjunto")
                check(p.attachments.first().postId == eventoId, "postId")
                "attachments=${p.attachments.size}"
            }
            paso("filtro Fotografías (hasImage=true) lo encuentra") {
                val p = llamar { api.publicaciones(conImagen = true, limite = 50) }.data
                check(p.any { it.id == eventoId }, "no aparece"); "${p.size} con imagen"
            }
            paso("agenda de hoy (type=event, rango CDMX)") {
                val (d, h) = Fechas.rangoHoy()
                val p = llamar {
                    api.publicaciones(tipo = "event", eventoDesde = Fechas.inicioDelDia(d), eventoHasta = Fechas.inicioDelDia(h),
                        orden = "eventStartAt", limite = 100)
                }.data
                check(p.any { it.id == eventoId }, "no aparece"); "${p.size} evento(s) hoy"
            }
            paso("filtro categoría ACTIVIDAD") {
                val p = llamar { api.publicaciones(categoria = "Actividad", limite = 50) }.data
                check(p.all { it.category == "Actividad" } && p.any { it.id == eventoId }, "filtro"); "${p.size}"
            }
            paso("archivo (publishedBefore = mañana) incluye lo de hoy") {
                val p = llamar {
                    api.publicaciones(publicadasAntesDe = Fechas.inicioDelDia(Fechas.hoy().plusDays(1)), orden = "-publishedAt")
                }
                check(p.data.any { it.id == eventoId }, "no aparece"); "total=${p.meta.total}"
            }

            // --- Residente: muro, lectura, reaccion, buzon ---------------------------
            paso("residente aprobado: login, lee, reacciona y usa su buzón") {
                // Notificaciones solo al publicar despues de aprobar: se crea una nueva.
                val n = llamar {
                    api.crearPublicacion(PostEscrituraRequest(type = "notice", category = "Noticia", title = "$tag para residente",
                        body = "Notificación de prueba."), UUID.randomUUID().toString())
                }.data
                posts += n.id
                val (c, s) = cliente()
                c.entrar(s, correoResidente, "password", claveResidente)
                val no = llamar { c.api.noLeidas() }.data.unreadCount
                val buzon = llamar { c.api.notificaciones() }
                val notif = buzon.data.firstOrNull { it.postId == n.id } ?: throw AssertionError("sin notificación")
                check(!llamar { c.api.publicacion(n.id) }.data.isRead, "GET marcó lectura")
                llamar { c.api.marcarPublicacionLeida(n.id) }
                check(llamar { c.api.publicacion(n.id) }.data.isRead, "no marcó lectura")
                val despues = llamar { c.api.notificaciones() }.data.first { it.id == notif.id }
                check(despues.isRead, "la notificación no se marcó")
                val r1 = llamar { c.api.reaccionar(n.id, ReaccionRequest("love")) }.data
                val r2 = llamar { c.api.quitarReaccion(n.id) }.data
                check(r1.myReaction == "love" && r1.total == 1 && r2.myReaction == null && r2.total == 0, "$r1 / $r2")
                val todas = llamar { c.api.leerTodas() }.data
                val archivadas = llamar { c.api.archivarLeidas() }.data
                esperaError(403) { llamar { c.api.publicaciones(estado = "all") } }
                llamarSinCuerpo { c.api.salir() }
                esperaError(401) { llamar { c.api.yo() } }
                "unread=$no, buzón=${buzon.meta.total}, readAll.unread=${todas.unreadCount}, archivadas=${archivadas.archived}, status=all→403, logout→401"
            }

            // --- Empleado: permisos ----------------------------------------------------
            paso("empleado: crea y edita lo suyo, no fija ni elimina") {
                val (c, s) = cliente()
                c.entrar(s, correoEmpleado, "pin", pinEmpleado)
                val p = llamar {
                    c.api.crearPublicacion(PostEscrituraRequest(type = "notice", category = "Aviso", title = "$tag de empleado",
                        body = "Aviso del personal."), UUID.randomUUID().toString())
                }.data
                posts += p.id
                esperaError(403) { llamar { c.api.fijar(p.id, FijarPostRequest(true)) } }
                esperaError(403) { llamarSinCuerpo { c.api.eliminarPublicacion(p.id) } }
                val propias = llamar { c.api.publicaciones(estado = "all", limite = 100) }.data
                check(propias.all { it.author.id == empleadoId }, "ve ajenas en status=all")
                llamar { c.api.cambiarEstado(p.id, EstadoPostRequest("archived")) }
                llamar { c.api.cambiarEstado(p.id, EstadoPostRequest("published")) }
                // Lo que manda la app al editar: isPinned con el valor actual.
                val v = llamar { c.api.publicacion(p.id) }.data
                val ed = llamar {
                    c.api.editarPublicacion(p.id, PostEscrituraRequest(category = "Aviso", title = "$tag de empleado (editado)",
                        body = v.body, isPinned = v.isPinned), "\"${v.version}\"")
                }.data
                check(ed.title.endsWith("(editado)"), "no editó")
                val ajeno = esperaError(403) {
                    llamar { c.api.editarPublicacion(avisoId, PostEscrituraRequest(category = "Aviso", title = "x", body = "y"), "\"$avisoVersion\"") }
                }
                llamarSinCuerpo { c.api.salir() }
                "fijar→403, eliminar→403, status=all solo propias (${propias.size}), editar con isPinned actual→200, editar ajeno→${ajeno.estado}"
            }
            paso("empleado: editar con isPinned=false un aviso fijado por admin") {
                // Caso de la duda: si admin fijo algo del empleado, ¿que pasa al editar?
                val (c, s) = cliente()
                val mio = llamar { api.publicaciones(estado = "all", limite = 100) }.data
                    .first { it.title.startsWith("$tag de empleado") }
                llamar { api.fijar(mio.id, FijarPostRequest(true)) }
                c.entrar(s, correoEmpleado, "pin", pinEmpleado)
                val v = llamar { c.api.publicacion(mio.id) }.data
                val conValorActual = llamar {
                    c.api.editarPublicacion(mio.id, PostEscrituraRequest(category = "Aviso", title = v.title, body = v.body,
                        isPinned = true), "\"${v.version}\"")
                }.data
                val resultado = try {
                    val sinCampo = llamar {
                        c.api.editarPublicacion(mio.id, PostEscrituraRequest(category = "Aviso", title = v.title, body = v.body),
                            "\"${conValorActual.version}\"")
                    }.data
                    "omitido→200 isPinned=${sinCampo.isPinned}"
                } catch (e: ErrorApi) { "omitido→${e.estado} ${e.codigo}" }
                llamarSinCuerpo { c.api.salir() }
                "isPinned=true (valor actual)→200 isPinned=${conValorActual.isPinned}; $resultado"
            }

            // --- Usuarios: reponer credencial y deshabilitar -----------------------
            paso("reponer credencial del residente revoca sus sesiones") {
                val (c, s) = cliente()
                c.entrar(s, correoResidente, "password", claveResidente)
                llamarSinCuerpo { api.reponerCredencial(residenteId, ReponerCredencialRequest("pin", "2468")) }
                esperaError(401) { llamar { c.api.yo() } }
                val (c2, s2) = cliente()
                c2.entrar(s2, correoResidente, "pin", "2468").let { llamarSinCuerpo { c2.api.salir() } }
                "sesión anterior→401, entra con PIN nuevo"
            }
        } finally {
            // --- Limpieza --------------------------------------------------------------
            paso("limpieza: eliminar publicaciones") {
                var n = 0
                if (mediaId.isNotEmpty() && eventoId.isEmpty()) runCatching { llamarSinCuerpo { api.eliminarArchivo(mediaId) } }
                posts.distinct().forEach { id -> runCatching { llamarSinCuerpo { api.eliminarPublicacion(id) } }.onSuccess { n++ } }
                posts.distinct().forEach { id -> esperaError(404) { llamar { api.publicacion(id) } } }
                "$n eliminadas, GET→404"
            }
            paso("limpieza: el adjunto se borró con su publicación") {
                if (mediaId.isEmpty()) return@paso "sin adjunto"
                val r = api.descargar(admin.urlAdjunto("/api/v1/media/$mediaId/content"))
                check(!r.isSuccessful, "sigue disponible"); "HTTP ${r.code()}"
            }
            paso("limpieza: deshabilitar cuentas QA") {
                usuarios.forEach { id ->
                    val u = llamar { api.usuario(id) }.data
                    val r = llamar { api.editarUsuario(id, EditarUsuarioRequest(u.fullName, u.role, "disabled")) }.data
                    check(r.status == "disabled", r.status)
                }
                "${usuarios.size} deshabilitadas"
            }
            paso("logout admin (204) y token revocado") {
                llamarSinCuerpo { api.salir() }
                val tokenViejo = sAdmin.sesion.value
                check(tokenViejo != null, "sesión ya borrada")
                esperaError(401) { llamar { api.yo() } }
                check(sAdmin.sesion.value == null, "401 no cerró la sesión local")
                "401 cierra la sesión local"
            }
            File("build/integracion-api.txt").writeText("Corrida $tag contra $url\n\n$bitacora")
        }
        if (fallas.isNotEmpty()) fail("Fallaron ${fallas.size} pasos: $fallas\n\n$bitacora")
    }
}
