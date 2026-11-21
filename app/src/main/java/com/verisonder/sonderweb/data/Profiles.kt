package com.verisonder.sonderweb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One profile: its own sign-ins and site data, open tabs, history, bookmarks and start page
 * shortcuts. The first profile ([id] "") is what SonderWeb always had.
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int,
    /** Its Google account picture's address. */
    val google: String = "",
    /** The Google account it's for (the first one signed in on it). */
    val account: String = "",
    /** Other Google accounts you chose to keep in it (not asked about again). */
    val kept: List<String> = emptyList(),
)

/** The profiles, which one is in use, and their pictures. */
object Profiles {
    private lateinit var app: Context
    private lateinit var prefs: Prefs

    val list = mutableStateListOf<Profile>()

    /** Profiles are turned on (Settings › Profiles). Off: only the first profile, no profile button. */
    var enabled by mutableStateOf(false)
        private set

    /** The profile in use. */
    var current by mutableStateOf("")
        private set

    /** Goes up when a picture changes, so it's drawn again. */
    var pictures by mutableIntStateOf(0)
        private set

    /** Goes up when what profiles keep separate changes (lists read again). */
    var changes by mutableIntStateOf(0)
        private set

    fun changed() {
        changes++
    }

    private val cache = HashMap<String, Bitmap?>()

    val colors = listOf(
        0xFF5B8DEF.toInt(), 0xFF2EB67D.toInt(), 0xFFE5534B.toInt(), 0xFFF0A202.toInt(),
        0xFF9B6BDF.toInt(), 0xFF00A3BF.toInt(), 0xFFE0648B.toInt(), 0xFF8D9399.toInt(),
    )

    fun load(context: Context, p: Prefs) {
        app = context.applicationContext
        prefs = p
        list.clear()
        runCatching {
            val a = JSONArray(p.profiles ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val kept = o.optJSONArray("k")?.let { k -> List(k.length()) { k.optString(it) } } ?: emptyList()
                list += Profile(o.getString("id"), o.optString("name"), o.optInt("color", colors[0]), o.optString("g"), o.optString("a"), kept)
            }
        }
        if (list.none { it.id.isEmpty() }) list.add(0, Profile("", "Personal", colors[0]))
        enabled = p.profilesOn
        current = if (enabled) p.profile.takeIf { id -> list.any { it.id == id } } ?: "" else ""
        p.scope = current
    }

    private fun save() {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("g", it.google)
                    .put("a", it.account).put("k", JSONArray(it.kept)),
            )
        }
        prefs.profiles = a.toString()
    }

    fun get(id: String): Profile = list.firstOrNull { it.id == id } ?: list.first()

    val active: Profile get() = get(current)

    /** The engine's storage for a profile: none for the first (its sign-ins stay where they are). */
    fun contextId(id: String): String? = if (id.isEmpty()) null else "sw-profile-$id"

    /** The database file holding a profile's history and bookmarks. */
    fun dbName(id: String): String = if (id.isEmpty()) "sonderweb.db" else "sonderweb-$id.db"

    internal fun turn(on: Boolean) {
        enabled = on
        prefs.profilesOn = on
    }

    /** Becomes the profile in use (the browser swaps the tabs; see Browser.switchProfile). */
    internal fun use(id: String) {
        current = id
        prefs.profile = id
        prefs.scope = id
    }

    fun add(name: String, color: Int): Profile {
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        val p = Profile(id, name.trim().ifEmpty { "Profile ${list.size + 1}" }, color)
        list += p
        save()
        return p
    }

    fun edit(id: String, name: String, color: Int) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(name = name.trim().ifEmpty { list[i].name }, color = color)
        save()
    }

    // ---- Google accounts ----

    /** This profile is for this Google account (its first). */
    fun setAccount(id: String, email: String) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0 || list[i].account == email) return
        list[i] = list[i].copy(account = email)
        save()
    }

    /** Another Google account stays in this profile (you said so; not asked again). */
    fun keep(id: String, email: String) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0 || email in list[i].kept) return
        list[i] = list[i].copy(kept = list[i].kept + email)
        save()
    }

    /** The profile that's for this Google account, if any. */
    fun withAccount(email: String): Profile? = list.firstOrNull { it.account == email }

    /** A colour not used yet (or the next one round). */
    fun nextColor(): Int = colors.firstOrNull { c -> list.none { it.color == c } } ?: colors[list.size % colors.size]

    /** Removes a profile from the list (its data is cleared by Browser.deleteProfile). */
    internal fun remove(id: String) {
        if (id.isEmpty()) return
        list.removeAll { it.id == id }
        save()
        removePicture(id)
        googleFile(id).delete()
    }

    // ---- Pictures ----

    private fun pictureFile(id: String) = File(File(app.filesDir, "profiles"), (id.ifEmpty { "default" }) + ".png")
    private fun googleFile(id: String) = File(File(app.filesDir, "profiles"), (id.ifEmpty { "default" }) + ".google.png")

    /** The picture you chose, else (if that's on) the Google account's, else none. */
    fun picture(id: String): Bitmap? = cache.getOrPut(id) {
        val own = pictureFile(id).takeIf { it.exists() }
        val file = own ?: googleFile(id).takeIf { it.exists() && prefs.profileGooglePictures }
        file?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }

    /** You chose a picture for this profile (not the Google account's). */
    fun hasOwnPicture(id: String): Boolean = pictureFile(id).exists()

    /** Settings › Profiles › Google account pictures. */
    fun changeGooglePictures(on: Boolean) {
        prefs.profileGooglePictures = on
        cache.clear()
        pictures++
    }

    /**
     * The picture of the Google account signed in on a profile (from its Google pages). Downloaded
     * at 256 px, once per address. Ignored while profiles or Google pictures are off.
     */
    fun googlePicture(id: String, url: String) {
        if (!enabled || !prefs.profileGooglePictures) return
        val p = list.firstOrNull { it.id == id } ?: return
        if (!url.startsWith("https://") || !Regex("^https://lh\\d\\.googleusercontent\\.com/").containsMatchIn(url)) return
        // Ask for it at 256 px (the address ends in its size, like "=s32-c").
        val big = if ('=' in url.substringAfterLast('/')) url.substringBeforeLast('=') + "=s256-c" else "$url=s256-c"
        if (p.google == big && googleFile(id).exists()) return
        Thread {
            val ok = runCatching {
                val c = java.net.URL(big).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 15_000
                val bytes = c.inputStream.use { it.readBytes() }
                c.disconnect()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
                val side = minOf(bmp.width, bmp.height)
                val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                val small = Bitmap.createScaledBitmap(square, 256, 256, true)
                val f = googleFile(id)
                f.parentFile?.mkdirs()
                f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            com.verisonder.sonderweb.browser.Browser.main.post {
                if (!ok) return@post
                val i = list.indexOfFirst { it.id == id }
                if (i >= 0) {
                    list[i] = list[i].copy(google = big)
                    save()
                }
                cache.remove(id)
                pictures++
            }
        }.start()
    }

    /** A picked image, cut to a square from its middle and kept small. Off the main thread. */
    fun setPicture(id: String, uri: Uri, done: (Boolean) -> Unit) {
        Thread {
            val ok = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
                val full = app.contentResolver.openInputStream(uri)!!.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }!!
                val side = minOf(full.width, full.height)
                val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
                val small = Bitmap.createScaledBitmap(square, 256, 256, true)
                val f = pictureFile(id)
                f.parentFile?.mkdirs()
                f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cache[id] = small
            }.isSuccess
            com.verisonder.sonderweb.browser.Browser.main.post {
                pictures++
                done(ok)
            }
        }.start()
    }

    fun removePicture(id: String) {
        pictureFile(id).delete()
        cache.remove(id)
        pictures++
    }
}
