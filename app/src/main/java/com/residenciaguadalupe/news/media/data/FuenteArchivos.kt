package com.residenciaguadalupe.news.media.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.residenciaguadalupe.news.data.red.ErrorApi
import java.io.InputStream

data class ArchivoLocal(
    val nombre: String,
    val mime: String?,
    val tamano: Long?,
    val abrir: () -> InputStream
)

fun interface FuenteArchivos {
    fun archivo(referencia: String): ArchivoLocal
}

/** Adaptador del selector de documentos; se invoca desde el repositorio en IO. */
class FuenteArchivosAndroid(private val resolver: ContentResolver) : FuenteArchivos {
    override fun archivo(referencia: String): ArchivoLocal {
        try {
            val uri = Uri.parse(referencia)
            var nombre = "adjunto"
            var tamano: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { nombre = it }
                    if (!c.isNull(1)) tamano = c.getLong(1)
                }
            }
            return ArchivoLocal(nombre, resolver.getType(uri), tamano) {
                resolver.openInputStream(uri)
                    ?: throw ErrorApi(-1, "LOCAL_FILE", "No se pudo leer el archivo elegido.")
            }
        } catch (e: SecurityException) {
            throw ErrorApi(-1, "LOCAL_FILE", "Ya no hay permiso para leer el archivo. Elija el archivo otra vez.")
        }
    }
}
