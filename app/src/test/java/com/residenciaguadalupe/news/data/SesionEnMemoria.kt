package com.residenciaguadalupe.news.data

import com.residenciaguadalupe.news.core.session.AlmacenSesion
import com.residenciaguadalupe.news.core.session.Sesion

/** AlmacenSesion sin Android para las pruebas. */
class SesionEnMemoria(inicial: Sesion? = null) : AlmacenSesion {
    private var sesion = inicial
    override fun leer(): Sesion? = sesion
    override fun guardar(sesion: Sesion) { this.sesion = sesion }
    override fun borrar() { sesion = null }
}
