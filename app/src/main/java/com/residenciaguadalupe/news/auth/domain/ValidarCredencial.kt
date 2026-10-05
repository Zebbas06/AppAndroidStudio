package com.residenciaguadalupe.news.auth.domain

/** Misma validacion para registro y cambio de credencial; login no exige longitud nueva. */
fun validarCredencial(metodo: String, secreto: String, nueva: Boolean): String? {
    if (metodo !in setOf("pin", "password")) return "Elija contraseña o PIN."
    if (secreto.isEmpty()) return if (metodo == "pin") "Escriba su PIN." else "Escriba su contraseña."
    if (metodo == "pin" && !secreto.all { it.isDigit() }) return "El PIN solo lleva números."
    if (nueva && metodo == "pin" && secreto.length !in 4..6) return "El PIN debe tener de 4 a 6 números."
    if (nueva && metodo == "password" && secreto.length !in 8..128) return "La contraseña debe tener al menos 8 caracteres."
    return null
}
