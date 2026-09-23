package com.residenciaguadalupe.news.data.red

import kotlinx.serialization.Serializable

/*
 * DTOs del contrato (documentacionAPI/openapi.yaml). Nombres en ingles y
 * camelCase, como los manda la API, aunque el resto de la aplicacion este
 * en espanol.
 *
 * Reglas que no se rompen:
 *  - Los IDs son String decimales, nunca Int ni Long (BIGINT de MySQL).
 *  - Fechas *At: ISO 8601 UTC con Z. menuDate y admissionDate: YYYY-MM-DD.
 *  - El Json de ClienteApi omite los null y codifica los valores por
 *    defecto: un campo nullable en null equivale a "no enviarlo", que en
 *    este contrato siempre significa su default (null).
 */

// --- Usuario y sesion ------------------------------------------------------

@Serializable
data class UserDto(
    val id: String,
    val fullName: String,
    val email: String,
    val role: String,                 // resident | employee | admin
    val status: String,               // pending | active | disabled
    val authMethod: String,           // password | pin
    val emailVerifiedAt: String? = null,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** null mientras recepcion no lo completa: no inventar habitacion ni fecha. */
    val residentProfile: ResidentProfileDto? = null,
    val staffProfile: StaffProfileDto? = null,
    val preferences: PreferenciasDto = PreferenciasDto()
)

@Serializable
data class ResidentProfileDto(
    val phone: String? = null,
    val department: String,
    /** Alias de department; solo viene en respuestas. */
    val roomNumber: String? = null,
    val admissionDate: String,        // fecha civil YYYY-MM-DD, sin hora
    val roomLocation: String? = null,
    val familyContact: FamiliarDto? = null
)

@Serializable
data class FamiliarDto(
    val fullName: String,
    val relationship: String,
    val phone: String                 // 10 digitos MX
)

@Serializable
data class StaffProfileDto(
    val area: String? = null,
    val createdAt: String? = null,
    val lastAccessAt: String? = null
)

@Serializable
data class PreferenciasDto(
    val notifyNotices: Boolean = true,
    val notifyActivities: Boolean = true,
    val notifyPhotos: Boolean = false
)

@Serializable
data class LoginDto(
    val user: UserDto,
    val expiresAt: String,
    val tokenType: String,
    val accessToken: String
)

// --- Peticiones de autenticacion -------------------------------------------

/** Registro: solo estos cuatro campos. Nada de role, status ni perfiles. */
@Serializable
data class RegistroRequest(
    val fullName: String,
    val email: String,
    val authMethod: String,
    val secret: String
)

/** clientType siempre "android". */
@Serializable
data class LoginRequest(
    val email: String,
    val authMethod: String,
    val secret: String,
    val clientType: String = "android"
)

@Serializable
data class CambioCredencialRequest(
    val currentSecret: String,
    val authMethod: String,
    val secret: String
)

/** Reposicion por recepcion/admin: revoca todas las sesiones del usuario. */
@Serializable
data class ReponerCredencialRequest(val authMethod: String, val secret: String)

@Serializable
data class NombreRequest(val fullName: String)

// --- Publicaciones ---------------------------------------------------------

@Serializable
data class PostDto(
    val id: String,
    val type: String,                 // notice | menu | event
    val category: String,             // Aviso | Actividad | Evento | Noticia
    val title: String,
    val body: String,
    val status: String,               // draft | published | archived
    val isPinned: Boolean = false,
    val isRead: Boolean = false,
    val author: AutorDto,
    val menuDate: String? = null,
    val mealType: String? = null,     // breakfast | lunch | dinner | daily
    val eventStartAt: String? = null,
    val eventEndAt: String? = null,
    val eventLocation: String? = null,
    val publishedAt: String? = null,
    val expiresAt: String? = null,
    /** Se manda como If-Match: "<version>" al editar. */
    val version: Int = 1,
    val attachments: List<MediaDto> = emptyList(),
    val reactions: ReaccionesDto = ReaccionesDto(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class AutorDto(val id: String, val fullName: String, val area: String? = null)

/**
 * Cuerpo de POST /posts y PUT /posts/{id} en una sola clase.
 *
 * - POST lleva type; PUT lo manda en null (y por tanto lo omite): la API
 *   responde 422 si un PUT trae type.
 * - Los campos de otro tipo tambien van en null: la API rechaza un aviso
 *   con menuDate o un menu con eventStartAt.
 * - isPinned en null se omite (lo usa el empleado, que no puede fijar).
 */
@Serializable
data class PostEscrituraRequest(
    val type: String? = null,
    val category: String,
    val title: String,
    val body: String,
    val isPinned: Boolean? = null,
    val attachmentIds: List<String> = emptyList(),
    val expiresAt: String? = null,
    val menuDate: String? = null,
    val mealType: String? = null,
    val eventStartAt: String? = null,
    val eventEndAt: String? = null,
    val eventLocation: String? = null
)

@Serializable
data class EstadoPostRequest(val status: String)   // published | archived

@Serializable
data class FijarPostRequest(val isPinned: Boolean)

// --- Archivos --------------------------------------------------------------

@Serializable
data class MediaDto(
    val id: String,
    val postId: String? = null,
    val originalName: String = "",
    val mimeType: String = "",        // image/jpeg | image/png | application/pdf
    val sizeBytes: Long = 0,
    val altText: String = "",
    val sortOrder: Int = 0,
    /** Relativa al ORIGEN, por ejemplo /api/v1/media/45/content. */
    val contentUrl: String = "",
    val createdAt: String? = null
)

// --- Reacciones y lecturas -------------------------------------------------

@Serializable
data class ReaccionesDto(
    val counts: ConteosDto = ConteosDto(),
    val total: Int = 0,
    val myReaction: String? = null    // like | love | happy | null
)

@Serializable
data class ConteosDto(val like: Int = 0, val love: Int = 0, val happy: Int = 0)

@Serializable
data class ReaccionRequest(val type: String)

@Serializable
data class LecturaDto(val read: Boolean = false, val readAt: String? = null)

// --- Notificaciones --------------------------------------------------------

@Serializable
data class NotificacionDto(
    val id: String,
    val type: String,                 // new_post | reminder
    val title: String,
    val body: String,
    val createdAt: String,
    val isRead: Boolean = false,
    val readAt: String? = null,
    val postId: String? = null
)

@Serializable
data class NoLeidasDto(val unreadCount: Int = 0)

@Serializable
data class LeerTodasDto(val updated: Int = 0, val unreadCount: Int = 0)

@Serializable
data class ArchivarLeidasDto(val archived: Int = 0)

// --- Usuarios y auditoria (administracion) ---------------------------------

/**
 * Perfil de residente de escritura. department y admissionDate son
 * obligatorios; los demas en null se omiten y quedan en null.
 */
@Serializable
data class PerfilResidenteRequest(
    val department: String,
    val admissionDate: String,
    val phone: String? = null,
    val roomLocation: String? = null,
    val familyContact: FamiliarDto? = null
)

@Serializable
data class PerfilPersonalRequest(val area: String? = null)

/** Alta administrativa: la cuenta nace active. */
@Serializable
data class CrearUsuarioRequest(
    val fullName: String,
    val email: String,
    val role: String,
    val authMethod: String,
    val secret: String,
    val residentProfile: PerfilResidenteRequest? = null,
    val staffProfile: PerfilPersonalRequest? = null
)

/**
 * fullName, role y status son obligatorios; ni email ni contrasena.
 * Si residentProfile va en null se omite y la API conserva el que haya
 * (un null explicito se rechazaria).
 */
@Serializable
data class EditarUsuarioRequest(
    val fullName: String,
    val role: String,
    val status: String,
    val residentProfile: PerfilResidenteRequest? = null,
    val staffProfile: PerfilPersonalRequest? = null
)

@Serializable
data class AuditLogDto(
    val id: String,
    val actorId: String? = null,
    val action: String = "",
    val entityType: String? = null,
    val entityId: String? = null,
    val requestId: String? = null,
    val createdAt: String? = null
)
