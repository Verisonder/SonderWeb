package com.verisonder.sonderweb.core

object UrlText {
    /** The short form shown in the bar: "en.wikipedia.org" for https://en.wikipedia.org/wiki/X. */
    fun host(url: String?): String {
        if (url.isNullOrBlank() || url == "about:blank") return ""
        val rest = url.substringAfter("://", "")
        if (rest.isEmpty()) return url
        return rest.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').removePrefix("www.")
    }

    /** What the bar shows while you edit: the full address without "https://". */
    fun editable(url: String?): String {
        if (url.isNullOrBlank() || url == "about:blank") return ""
        return url.removePrefix("https://")
    }

    fun isWeb(url: String?): Boolean =
        url != null && (url.startsWith("https://") || url.startsWith("http://"))

    /** Schemes the engine loads itself; everything else is handed to another app. */
    fun isInternal(url: String): Boolean {
        val l = url.lowercase()
        return l.startsWith("http://") || l.startsWith("https://") || l.startsWith("about:") ||
            l.startsWith("data:") || l.startsWith("blob:") || l.startsWith("file:") ||
            l.startsWith("moz-extension:") || l.startsWith("resource:") || l.startsWith("view-source:") ||
            l.startsWith("javascript:") || l.startsWith("chrome:")
    }

    /** A file name for a download, from Content-Disposition, then the URL. */
    fun fileName(contentDisposition: String?, url: String, mime: String?): String {
        var name: String? = null
        if (contentDisposition != null) {
            val star = Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""").find(contentDisposition)
            name = star?.groupValues?.get(1)?.let { percentDecode(it.trim()) }
            if (name.isNullOrBlank()) {
                val plain = Regex("""filename\s*=\s*"?([^";]+)"?""").find(contentDisposition)
                name = plain?.groupValues?.get(1)?.trim()
            }
        }
        if (name.isNullOrBlank()) {
            name = percentDecode(url.substringBefore('?').substringBefore('#').substringAfterLast('/'))
        }
        var clean = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trimStart('.')
        if (clean.isEmpty()) clean = "download"
        if (!clean.contains('.')) {
            val ext = extensionFor(mime)
            if (ext != null) clean += ".$ext"
        }
        return clean.take(120)
    }

    private fun extensionFor(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "audio/mpeg" -> "mp3"
        "application/zip" -> "zip"
        "application/vnd.android.package-archive" -> "apk"
        "text/plain" -> "txt"
        "text/html" -> "html"
        else -> null
    }

    fun percentDecode(s: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.write(hex); i += 3; continue
                }
            }
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toString("UTF-8")
    }
}
