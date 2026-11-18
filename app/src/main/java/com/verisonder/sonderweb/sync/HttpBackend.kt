package com.verisonder.sonderweb.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.appservices.viaduct.Backend
import mozilla.appservices.viaduct.ClientSettings
import mozilla.appservices.viaduct.Method
import mozilla.appservices.viaduct.Request
import mozilla.appservices.viaduct.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * How Mozilla's account and sync code reaches the internet: plain HTTPS requests from the app,
 * without cookies or caches (it only talks to Mozilla's account and sync servers).
 */
internal class HttpBackend : Backend {

    override suspend fun sendRequest(request: Request, settings: ClientSettings): Response = withContext(Dispatchers.IO) {
        val conn = URL(request.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = when (request.method) {
                Method.GET -> "GET"
                Method.HEAD -> "HEAD"
                Method.POST -> "POST"
                Method.PUT -> "PUT"
                Method.DELETE -> "DELETE"
                Method.OPTIONS -> "OPTIONS"
                Method.TRACE -> "TRACE"
                else -> throw java.io.IOException("Unsupported method: ${request.method}")
            }
            val timeout = settings.timeout.toLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.instanceFollowRedirects = settings.redirectLimit.toInt() > 0
            conn.useCaches = false
            settings.userAgent?.let { conn.setRequestProperty("User-Agent", it) }
            request.headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            val body = request.body
            if (body != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else runCatching { conn.inputStream }.getOrNull()
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = conn.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.joinToString(", ") }
            Response(
                requestMethod = request.method,
                url = conn.url.toString(),
                status = status.toUShort(),
                headers = headers,
                body = bytes,
            )
        } finally {
            conn.disconnect()
        }
    }
}
