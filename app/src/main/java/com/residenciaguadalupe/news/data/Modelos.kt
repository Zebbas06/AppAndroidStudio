package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.data.red.*
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Las cuatro categorias que se pueden publicar. [api] es la etiqueta que
 * devuelve la API. Salud, Capilla y Comida son alias antiguos de Aviso:
 * no son filtros distintos.
 */
enum class Categoria(val etiqueta: String, val esVerde: Boolean) {
    AVISO("Aviso", false),
    ACTIVIDAD("Actividad", true),
    EVENTO("Evento", true),
    NOTICIA("Noticia", false);

    val api: String get() = etiqueta

    companion object {
        fun desdeApi(valor: String): Categoria =
            entries.firstOrNull { it.etiqueta.equals(valor, ignoreCase = true) } ?: AVISO
    }
}

/** type de la API, independiente de la categoria. Inmutable tras crear. */
enum class TipoPublicacion(val api: String, val etiqueta: String) {
    AVISO("notice", "Aviso"),
    MENU("menu", "Menú"),
    EVENTO("event", "Evento");

    companion object {
        fun desdeApi(valor: String) = entries.firstOrNull { it.api == valor } ?: AVISO
    }
}

/** mealType de los menus. */
enum class Comida(val api: String, val etiqueta: String) {
    DESAYUNO("breakfast", "Desayuno"),
    COMIDA("lunch", "Comida"),
    CENA("dinner", "Cena"),
    DIARIO("daily", "Todo el día");

    companion object {
        fun desdeApi(valor: String?) = entries.firstOrNull { it.api == valor }
    }
}

enum class Rol(val api: String, val etiqueta: String) {
    RESIDENTE("resident", "Residente"),
    EMPLEADO("employee", "Personal"),
    ADMIN("admin", "Administración");

    companion object {
        fun desdeApi(valor: String) = entries.firstOrNull { it.api == valor } ?: RESIDENTE
    }
}

enum class EstadoCuenta(val api: String, val etiqueta: String) {
    PENDIENTE("pending", "Pendiente"),
    ACTIVA("active", "Activa"),
    DESHABILITADA("disabled", "Deshabilitada");

    companion object {
        fun desdeApi(valor: String) = entries.firstOrNull { it.api == valor } ?: PENDIENTE
    }
}

val UserDto.rol get() = Rol.desdeApi(role)
val UserDto.esPersonal get() = rol != Rol.RESIDENTE
val UserDto.nombrePila get() = fullName.trim().substringBefore(' ')
val UserDto.iniciales get() = fullName.trim().split(Regex("\\s+")).take(2)
    .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")

/** "7772148830" -> "777 214 88 30". */
fun telefono(t: String?): String? =
    t?.takeIf { it.length == 10 }?.let { "${it.take(3)} ${it.substring(3, 6)} ${it.substring(6, 8)} ${it.takeLast(2)}" } ?: t

/**
 * Estado visible en administracion. Fijado es un booleano aparte del
 * status; aqui se combinan solo para la etiqueta.
 */
enum class EstadoAviso(val etiqueta: String) {
    FIJADO("Fijado · Importante"),
    PUBLICADO("Publicado"),
    ARCHIVADO("Archivado"),
    BORRADOR("Borrador")
}

/** Archivo privado de una publicacion. [url] ya es absoluta y requiere Bearer. */
data class Adjunto(
    val id: String,
    val url: String,
    val mime: String,
    val nombre: String,
    val textoAlternativo: String,
    val tamano: Long
) {
    val esImagen get() = mime == "image/jpeg" || mime == "image/png"
    val esPdf get() = mime == "application/pdf"
}

data class Reacciones(
    val meGusta: Int = 0,
    val meEncanta: Int = 0,
    val meAlegra: Int = 0,
    val total: Int = 0,
    /** like | love | happy | null */
    val mia: String? = null
)

/** Una publicacion del muro, del detalle y de administracion. */
data class Publicacion(
    val id: String,
    val tipo: TipoPublicacion,
    val categoria: Categoria,
    val titulo: String,
    val texto: String,
    val estado: String,
    val fijada: Boolean,
    val leida: Boolean,
    val autorId: String,
    val autor: String,
    val autorArea: String?,
    val publicadaEn: ZonedDateTime?,
    val eventoInicio: ZonedDateTime?,
    val eventoFin: ZonedDateTime?,
    val eventoLugar: String?,
    val fechaMenu: LocalDate?,
    val comida: Comida?,
    val venceEn: ZonedDateTime?,
    val version: Int,
    val adjunto: Adjunto?,
    val reacciones: Reacciones
) {
    /** "Hoy a las 8:00 a. m. · Administración". */
    val meta: String
        get() = listOfNotNull(publicadaEn?.let(Fechas::momento), autor).joinToString(" · ")

    val estadoAdmin: EstadoAviso
        get() = when {
            estado == "archived" -> EstadoAviso.ARCHIVADO
            estado == "draft" -> EstadoAviso.BORRADOR
            fijada -> EstadoAviso.FIJADO
            else -> EstadoAviso.PUBLICADO
        }

    /** Cuando ocurre, segun el tipo; null en un aviso comun. */
    val cuando: String?
        get() = when (tipo) {
            TipoPublicacion.EVENTO -> eventoInicio?.let { ini ->
                val fin = eventoFin?.takeIf { it.isAfter(ini) }
                val dia = Fechas.diaRelativo(ini.toLocalDate())
                val horas = if (fin != null && fin.toLocalDate() == ini.toLocalDate())
                    "de ${Fechas.hora(ini)} a ${Fechas.hora(fin)}" else Fechas.hora(ini)
                "$dia, $horas"
            }
            TipoPublicacion.MENU -> fechaMenu?.let { d ->
                listOfNotNull(Fechas.diaRelativo(d), comida?.etiqueta).joinToString(" · ")
            }
            TipoPublicacion.AVISO -> null
        }
}

fun ReaccionesDto.aReacciones() = Reacciones(counts.like, counts.love, counts.happy, total, myReaction)

fun MediaDto.aAdjunto(resolver: (String) -> String) =
    Adjunto(id, resolver(contentUrl), mimeType, originalName, altText, sizeBytes)

fun PostDto.aPublicacion(resolver: (String) -> String) = Publicacion(
    id = id,
    tipo = TipoPublicacion.desdeApi(type),
    categoria = Categoria.desdeApi(category),
    titulo = title,
    texto = body,
    estado = status,
    fijada = isPinned,
    leida = isRead,
    autorId = author.id,
    autor = author.fullName,
    autorArea = author.area,
    publicadaEn = Fechas.local(publishedAt),
    eventoInicio = Fechas.local(eventStartAt),
    eventoFin = Fechas.local(eventEndAt),
    eventoLugar = eventLocation,
    fechaMenu = Fechas.fecha(menuDate),
    comida = Comida.desdeApi(mealType),
    venceEn = Fechas.local(expiresAt),
    version = version,
    adjunto = attachments.firstOrNull()?.aAdjunto(resolver),
    reacciones = reactions.aReacciones()
)

/** Una fila de la agenda (pantalla Hoy y banda del muro). */
data class Actividad(
    val postId: String,
    val hora: String,
    val lugar: String,
    val titulo: String,
    /** Extracto local del cuerpo. */
    val nota: String
)

fun Publicacion.aActividad(conDia: Boolean): Actividad {
    val ini = eventoInicio
    val hora = when {
        ini == null -> ""
        conDia -> "${Fechas.diaCorto(ini.toLocalDate())}, ${Fechas.hora(ini)}"
        else -> Fechas.hora(ini)
    }
    return Actividad(id, hora, eventoLugar.orEmpty(), titulo, extracto(texto, 110))
}

/** Primeras palabras hasta [max] caracteres, sin cortar una palabra. */
fun extracto(texto: String, max: Int): String {
    val limpio = texto.replace(Regex("\\s+"), " ").trim()
    if (limpio.length <= max) return limpio
    return limpio.take(max).substringBeforeLast(' ').trimEnd(',', '.', ';', ':') + "…"
}

/** Una notificacion del buzon. */
data class Notificacion(
    val id: String,
    val tipo: String,        // "Nuevo aviso" / "Recordatorio"
    val titulo: String,
    val cuerpo: String,
    val cuando: String,
    val leida: Boolean,
    val postId: String?
)

fun NotificacionDto.aNotificacion() = Notificacion(
    id = id,
    tipo = if (type == "reminder") "Recordatorio" else "Nuevo aviso",
    titulo = title,
    cuerpo = body,
    cuando = Fechas.local(createdAt)?.let(Fechas::hace).orEmpty(),
    leida = isRead,
    postId = postId
)

/** Un mes del archivo, con sus entradas. */
data class MesArchivo(val mes: String, val entradas: List<EntradaArchivo>)
data class EntradaArchivo(val postId: String, val meta: String, val titulo: String)

/** Las tres pestanas de la agenda. El rango de fechas se calcula en Fechas. */
enum class PestanaAgenda(val etiqueta: String) {
    HOY("Hoy"),
    MANANA("Mañana"),
    SEMANA("Esta semana");

    fun rango(): Pair<LocalDate, LocalDate> = when (this) {
        HOY -> Fechas.rangoHoy()
        MANANA -> Fechas.rangoManana()
        SEMANA -> Fechas.rangoSemana()
    }

    /** "Hoy · Miércoles 16", "Del lunes 14 al domingo 20". */
    fun rotulo(): String {
        val (desde, hasta) = rango()
        return when (this) {
            HOY -> "Hoy · ${Fechas.diaCorto(desde)}"
            MANANA -> "Mañana · ${Fechas.diaCorto(desde)}"
            SEMANA -> {
                val fin = hasta.minusDays(1)
                "Del ${Fechas.diaSemana(desde)} ${desde.dayOfMonth} al ${Fechas.diaSemana(fin)} ${fin.dayOfMonth}"
            }
        }
    }
}

/** Los tres tamanos de letra del perfil. */
enum class Escala(val etiqueta: String, val factor: Float) {
    CHICA("A−", 0.9f),
    NORMAL("A", 1.0f),
    GRANDE("A+", 1.15f)
}
