package com.example.weglow.feature.auth

import java.net.URI

internal object PasswordRecovery {
    fun isValidEmail(email: String): Boolean = email.length <= 254 &&
        email.substringBefore('@').let { it.length <= 64 && !it.startsWith('.') && !it.endsWith('.') && ".." !in it } &&
        Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$").matches(email)

    // Accept only the recovery template's exact shape, never OAuth codes or access tokens.
    fun tokenHash(link: String): String? = runCatching {
        require(link.length <= 4096)
        val uri = URI(link)
        require(uri.scheme == "weglow" && uri.rawAuthority == "password-reset")
        require(uri.rawPath.isNullOrEmpty() && uri.rawFragment == null)
        val pairs = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        require(pairs.size == 2 && pairs.all { it.size == 2 })
        require(pairs.map { it[0] }.toSet() == setOf("token_hash", "type"))
        val parameters = pairs.associate { it[0] to it[1] }
        require(parameters["type"] == "recovery")
        parameters["token_hash"]?.takeIf { Regex("[A-Za-z0-9_-]{32,512}").matches(it) }
    }.getOrNull()
}
