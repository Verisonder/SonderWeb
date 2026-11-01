package com.verisonder.sonderweb.browser

import android.util.Base64
import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.WebRequestError

/** A plain page shown when a site can't be loaded. */
object ErrorPage {

    fun html(uri: String?, error: WebRequestError): String {
        val host = UrlText.host(uri).ifEmpty { uri ?: "" }
        val (title, body) = when (error.code) {
            WebRequestError.ERROR_UNKNOWN_HOST ->
                "Can't find $host" to "Check the address for typos, or your connection."
            WebRequestError.ERROR_OFFLINE ->
                "You're offline" to "Connect to the internet and try again."
            WebRequestError.ERROR_CONNECTION_REFUSED ->
                "$host refused to connect" to "The site may be down. Try again later."
            WebRequestError.ERROR_NET_TIMEOUT ->
                "$host took too long" to "The site didn't answer in time."
            WebRequestError.ERROR_NET_RESET, WebRequestError.ERROR_NET_INTERRUPT ->
                "The connection was cut" to "Try again."
            WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_BAD_HSTS_CERT,
            WebRequestError.ERROR_SECURITY_SSL ->
                "This connection isn't safe" to
                    "$host's certificate can't be trusted. Someone could be trying to intercept you."
            WebRequestError.ERROR_HTTPS_ONLY ->
                "$host has no secure version" to
                    "HTTPS-only mode is on (Settings). This site only offers an unencrypted connection."
            WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI,
            WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI ->
                "Dangerous site blocked" to "$host is known to spread harmful software."
            WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI ->
                "Deceptive site blocked" to "$host is known to steal passwords or payment details."
            WebRequestError.ERROR_REDIRECT_LOOP ->
                "Too many redirects" to "$host keeps redirecting. Clearing its cookies may help."
            WebRequestError.ERROR_MALFORMED_URI, WebRequestError.ERROR_UNKNOWN_PROTOCOL ->
                "That address isn't valid" to (uri ?: "")
            WebRequestError.ERROR_PORT_BLOCKED ->
                "Port blocked" to "This address uses a port browsers block for safety."
            WebRequestError.ERROR_FILE_NOT_FOUND ->
                "File not found" to (uri ?: "")
            else -> "Can't open this page" to "Something went wrong loading $host."
        }
        val retry = error.category != WebRequestError.ERROR_CATEGORY_SAFEBROWSING &&
            UrlText.isWeb(uri)
        val page = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${esc(title)}</title>
            <style>
              :root{color-scheme:light dark;--bg:#fff;--fg:#111;--mute:#666;--acc:#3B82FF}
              @media (prefers-color-scheme:dark){:root{--bg:#0B0B0C;--fg:#EDEDED;--mute:#9a9a9a}}
              body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,sans-serif;
                display:flex;min-height:100vh;align-items:center;justify-content:center}
              main{max-width:420px;padding:32px 24px}
              .dot{width:14px;height:14px;border-radius:50%;background:var(--acc);margin-bottom:24px}
              h1{font-size:24px;line-height:1.25;margin:0 0 12px;word-break:break-word}
              p{color:var(--mute);margin:0 0 28px;word-break:break-word}
              a.btn{display:inline-block;font-weight:600;border-radius:999px;padding:12px 24px;
                background:var(--fg);color:var(--bg);text-decoration:none}
            </style></head><body><main>
            <div class="dot"></div>
            <h1>${esc(title)}</h1><p>${esc(body)}</p>
            ${if (retry && uri != null) "<a class=\"btn\" href=\"${esc(uri)}\">Try again</a>" else ""}
            </main></body></html>
        """.trimIndent()
        return "data:text/html;base64," +
            Base64.encodeToString(page.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
