package com.residenciaguadalupe.news.media.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.residenciaguadalupe.news.data.red.ErrorApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

/** Adaptador del selector de documentos, galeria y camara; se invoca desde el repositorio en IO. */
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
            val mime = resolver.getType(uri)
            // Fotos de camara o galeria: HEIC/WebP o de mas de 5 MB se convierten a JPG.
            if (mime != null && mime.startsWith("image/") && necesitaConversion(mime, tamano)) {
                return comoJpeg(uri, nombre)
            }
            return ArchivoLocal(nombre, mime, tamano) {
                resolver.openInputStream(uri)
                    ?: throw ErrorApi(-1, "LOCAL_FILE", "No se pudo leer el archivo elegido.")
            }
        } catch (e: SecurityException) {
            throw ErrorApi(-1, "LOCAL_FILE", "Ya no hay permiso para leer el archivo. Elija el archivo otra vez.")
        }
    }

    private fun necesitaConversion(mime: String, tamano: Long?): Boolean {
        val limite = RepositorioMediosApi.LIMITES_ADJUNTO[mime] ?: return true
        return tamano != null && tamano > limite
    }

    /** Reduce el lado mayor a [LADO_MAXIMO] px, aplica la rotacion EXIF y comprime a JPG. */
    private fun comoJpeg(uri: Uri, nombre: String): ArchivoLocal {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir(uri).use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) throw fotoIlegible()

        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
        val original = abrir(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: throw fotoIlegible()

        val rotacion = runCatching { abrir(uri).use { ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        val escala = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(original.width, original.height))
        val matriz = Matrix().apply { postScale(escala, escala); postRotate(rotacion.toFloat()) }
        val final = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)

        val bytes = ByteArrayOutputStream().use { salida ->
            final.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            salida.toByteArray()
        }
        if (final !== original) final.recycle()
        original.recycle()
        return ArchivoLocal(nombre.substringBeforeLast('.') + ".jpg", "image/jpeg", bytes.size.toLong()) {
            ByteArrayInputStream(bytes)
        }
    }

    private fun abrir(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw fotoIlegible()

    private fun fotoIlegible() =
        ErrorApi(-1, "LOCAL_FILE", "No se pudo leer la fotografía elegida. Elija otra o tómela de nuevo.")

    private companion object {
        /** Suficiente para verse nitida en el muro y quedar muy por debajo de 5 MB. */
        const val LADO_MAXIMO = 2048
        const val CALIDAD_JPEG = 85
    }
}
