package com.residenciaguadalupe.news.data.red

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Las operaciones de la API. Rutas relativas a la base (…/api/v1/), sin
 * "/" inicial y sin repetir api/v1.
 *
 * Todo devuelve Response<…> para poder leer el status y el cuerpo de error;
 * la conversion a excepcion la hace llamar() en ErrorApi.kt.
 */
interface ApiServicio {

    // --- Sesion --------------------------------------------------------------

    @POST("auth/register")
    suspend fun registrar(@Body cuerpo: RegistroRequest): Response<ApiData<UserDto>>

    @POST("auth/login")
    suspend fun entrar(@Body cuerpo: LoginRequest): Response<ApiData<LoginDto>>

    @GET("auth/me")
    suspend fun yo(): Response<ApiData<UserDto>>

    /** 204 sin cuerpo. */
    @POST("auth/logout")
    suspend fun salir(): Response<Unit>

    // --- Perfil propio -------------------------------------------------------

    @PATCH("auth/me")
    suspend fun cambiarNombre(@Body cuerpo: NombreRequest): Response<ApiData<UserDto>>

    /** 204. Revoca las otras sesiones y conserva la actual. */
    @PUT("auth/me/credentials")
    suspend fun cambiarCredencial(@Body cuerpo: CambioCredencialRequest): Response<Unit>

    @GET("auth/me/preferences")
    suspend fun preferencias(): Response<ApiData<PreferenciasDto>>

    @PUT("auth/me/preferences")
    suspend fun guardarPreferencias(@Body cuerpo: PreferenciasDto): Response<ApiData<PreferenciasDto>>

    // --- Publicaciones -------------------------------------------------------

    /** Los parametros en null no se envian. Fechas con Z u offset. */
    @GET("posts")
    suspend fun publicaciones(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 20,
        @Query("status") estado: String? = null,
        @Query("type") tipo: String? = null,
        @Query("category") categoria: String? = null,
        @Query("hasImage") conImagen: Boolean? = null,
        @Query("pinned") fijadas: Boolean? = null,
        @Query("eventFrom") eventoDesde: String? = null,
        @Query("eventTo") eventoHasta: String? = null,
        @Query("publishedBefore") publicadasAntesDe: String? = null,
        @Query("sort") orden: String? = null
    ): Response<PageDto<PostDto>>

    @GET("posts/{id}")
    suspend fun publicacion(@Path("id") id: String): Response<ApiData<PostDto>>

    @POST("posts")
    suspend fun crearPublicacion(
        @Body cuerpo: PostEscrituraRequest,
        @Header("Idempotency-Key") claveIdempotencia: String
    ): Response<ApiData<PostDto>>

    /** PUT sin type. ifMatch = "\"<version>\"" para recibir 409 si cambio. */
    @PUT("posts/{id}")
    suspend fun editarPublicacion(
        @Path("id") id: String,
        @Body cuerpo: PostEscrituraRequest,
        @Header("If-Match") ifMatch: String
    ): Response<ApiData<PostDto>>

    /** 204. Permanente: no hay restore. */
    @DELETE("posts/{id}")
    suspend fun eliminarPublicacion(@Path("id") id: String): Response<Unit>

    @PATCH("posts/{id}/status")
    suspend fun cambiarEstado(@Path("id") id: String, @Body cuerpo: EstadoPostRequest): Response<ApiData<PostDto>>

    @PATCH("posts/{id}/pin")
    suspend fun fijar(@Path("id") id: String, @Body cuerpo: FijarPostRequest): Response<ApiData<PostDto>>

    // --- Lectura y reaccion (residente) --------------------------------------

    @PUT("posts/{id}/read")
    suspend fun marcarPublicacionLeida(@Path("id") id: String): Response<ApiData<LecturaDto>>

    @PUT("posts/{id}/reaction")
    suspend fun reaccionar(@Path("id") id: String, @Body cuerpo: ReaccionRequest): Response<ApiData<ReaccionesDto>>

    /** 200 con el resumen, no 204. */
    @DELETE("posts/{id}/reaction")
    suspend fun quitarReaccion(@Path("id") id: String): Response<ApiData<ReaccionesDto>>

    // --- Archivos ------------------------------------------------------------

    /**
     * Multipart armado en Repositorio.subir: OkHttp genera el Content-Type
     * con su boundary. Va como @Body para poder marcarlo de un solo envio.
     */
    @POST("media")
    suspend fun subirArchivo(@Body cuerpo: RequestBody): Response<ApiData<MediaDto>>

    /** url absoluta: contentUrl ya resuelta contra el origen. */
    @Streaming
    @GET
    suspend fun descargar(@Url url: String): Response<ResponseBody>

    /** 204. Solo archivos propios sin asociar. */
    @DELETE("media/{id}")
    suspend fun eliminarArchivo(@Path("id") id: String): Response<Unit>

    // --- Notificaciones (solo residente) -------------------------------------

    @GET("notifications")
    suspend fun notificaciones(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 20
    ): Response<PaginaNotificacionesDto>

    @GET("notifications/unread-count")
    suspend fun noLeidas(): Response<ApiData<NoLeidasDto>>

    @PUT("notifications/{id}/read")
    suspend fun marcarNotificacionLeida(@Path("id") id: String): Response<ApiData<NotificacionDto>>

    @POST("notifications/read-all")
    suspend fun leerTodas(): Response<ApiData<LeerTodasDto>>

    @POST("notifications/archive-read")
    suspend fun archivarLeidas(): Response<ApiData<ArchivarLeidasDto>>

    // --- Usuarios (admin) ----------------------------------------------------

    @GET("users")
    suspend fun usuarios(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 20,
        @Query("role") rol: String? = null,
        @Query("status") estado: String? = null,
        @Query("q") busqueda: String? = null
    ): Response<PageDto<UserDto>>

    @GET("users/{id}")
    suspend fun usuario(@Path("id") id: String): Response<ApiData<UserDto>>

    @POST("users")
    suspend fun crearUsuario(@Body cuerpo: CrearUsuarioRequest): Response<ApiData<UserDto>>

    @PUT("users/{id}")
    suspend fun editarUsuario(@Path("id") id: String, @Body cuerpo: EditarUsuarioRequest): Response<ApiData<UserDto>>

    /** 204. Revoca todas las sesiones de esa cuenta. */
    @PUT("users/{id}/credentials")
    suspend fun reponerCredencial(@Path("id") id: String, @Body cuerpo: ReponerCredencialRequest): Response<Unit>

    // --- Auditoria y salud ---------------------------------------------------

    @GET("audit-logs")
    suspend fun auditoria(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 20,
        @Query("actorId") actorId: String? = null,
        @Query("entityType") tipoEntidad: String? = null,
        @Query("entityId") entidadId: String? = null
    ): Response<PageDto<AuditLogDto>>

    @GET("health")
    suspend fun salud(): Response<ApiData<EstadoDto>>
}
