package com.residenciaguadalupe.news.media

import com.residenciaguadalupe.news.media.data.leerLimitado
import com.residenciaguadalupe.news.data.red.ErrorApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class LecturaLimitadaTest {
    @Test fun `archivo de tamano exacto se acepta`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        assertArrayEquals(bytes, leerLimitado(ByteArrayInputStream(bytes), 3, "image/png"))
    }
    @Test fun `excedente se rechaza leyendo solo limite mas uno`() = runTest {
        val entrada = ByteArrayInputStream(ByteArray(20))
        val error = runCatching { leerLimitado(entrada, 10, "application/pdf") }.exceptionOrNull()
        assertTrue(error is ErrorApi)
        assertEquals(9, entrada.available())
    }
    @Test fun `cancelacion interrumpe lectura sin consumir resto`() = runTest {
        val trabajo = Job()
        var lecturas = 0
        val entrada = object : InputStream() {
            override fun read(): Int = error("Se lee en bloques")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                lecturas++; trabajo.cancel(); b[off] = 1; return 1
            }
        }
        val error = runCatching { withContext(trabajo) { leerLimitado(entrada, 10, "image/png") } }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, lecturas)
    }
}
