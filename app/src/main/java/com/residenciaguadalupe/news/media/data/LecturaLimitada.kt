package com.residenciaguadalupe.news.media.data

import com.residenciaguadalupe.news.data.red.ErrorApi
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Lee a lo sumo limite + 1 bytes, incluso si el proveedor omite o falsea SIZE. */
internal suspend fun leerLimitado(entrada: InputStream, limite: Long, mime: String): ByteArray {
    val salida = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val cantidad = minOf(buffer.size.toLong(), limite - total + 1).toInt()
        val leidos = entrada.read(buffer, 0, cantidad)
        if (leidos < 0) return salida.toByteArray()
        total += leidos
        if (total > limite) throw ErrorApi(413, "PAYLOAD_TOO_LARGE", RepositorioMediosApi.mensajeTamano(mime))
        salida.write(buffer, 0, leidos)
    }
}
