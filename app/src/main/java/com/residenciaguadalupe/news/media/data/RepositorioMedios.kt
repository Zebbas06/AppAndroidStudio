package com.residenciaguadalupe.news.media.data

import com.residenciaguadalupe.news.data.Adjunto
import java.io.File

/** La referencia es la URI del selector; solo el adaptador Android la interpreta. */
interface RepositorioMedios {
    suspend fun subir(referencia: String, textoAlternativo: String?): Adjunto
    suspend fun eliminarArchivo(id: String)
    suspend fun descargar(adjunto: Adjunto): File
}
