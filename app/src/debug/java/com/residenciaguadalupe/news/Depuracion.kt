package com.residenciaguadalupe.news

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Solo en debug y nunca a nivel BODY: el login lleva la contrasena y la
 * respuesta el token. La cabecera Authorization se oculta.
 */
fun interceptoresDepuracion(): List<Interceptor> = listOf(
    HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }
)
