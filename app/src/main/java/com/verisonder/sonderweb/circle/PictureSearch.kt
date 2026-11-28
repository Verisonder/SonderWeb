package com.verisonder.sonderweb.circle

import android.util.Base64

/**
 * Search by picture with Bing or Yandex (Google Lens is [com.verisonder.sonderweb.browser.ImageSearch]).
 * A little page posts the picture the way each site's own upload form does.
 */
object PictureSearch {
    fun page(engine: String, jpeg: ByteArray): String {
        val image = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val form = when (engine) {
            // Bing takes the picture as base64 text in a form field.
            "bing" -> """
                <form id="f" method="post" enctype="multipart/form-data"
                  action="https://www.bing.com/images/search?view=detailv2&amp;iss=sbiupload&amp;FORM=SBIIDP">
                <input type="hidden" name="cbir" value="sbi">
                <input type="hidden" name="imageBin" value="$image"></form>
                <script>document.getElementById("f").submit();</script>
            """
            // Yandex takes it as a file upload.
            else -> """
                <form id="f" method="post" enctype="multipart/form-data"
                  action="https://yandex.com/images/search?rpt=imageview">
                <input type="file" name="upfile" id="i" hidden></form>
                <script>
                  const b = atob("$image"), a = new Uint8Array(b.length);
                  for (let k = 0; k < b.length; k++) a[k] = b.charCodeAt(k);
                  const d = new DataTransfer();
                  d.items.add(new File([a], "image.jpg", { type: "image/jpeg" }));
                  document.getElementById("i").files = d.files;
                  document.getElementById("f").submit();
                </script>
            """
        }
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark light"><title>Search with a picture</title>
            <style>body{margin:0;background:#1f1f1f}@media (prefers-color-scheme: light){body{background:#fff}}</style>
            </head><body>$form</body></html>
        """.trimIndent()
        return "data:text/html;base64," + Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
