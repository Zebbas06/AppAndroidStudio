package com.residenciaguadalupe.news.core.session

/** Persistencia solamente: el ciclo de vida pertenece a GestorSesion. */
interface AlmacenSesion {
    fun leer(): Sesion?
    fun guardar(sesion: Sesion)
    fun borrar()
}
