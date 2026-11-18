package com.verisonder.sonderweb.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.UUID

/** An app on the search widget: what it opens, and the icon you picked for it (null: the app's own). */
data class WidgetApp(val pkg: String, val cls: String, val icon: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("pkg", pkg).put("cls", cls).also { j -> icon?.let { j.put("icon", it) } }

    companion object {
        /** At most this many apps on one widget. */
        const val MAX = 4

        fun fromJson(j: JSONObject): WidgetApp? {
            val pkg = j.optString("pkg")
            val cls = j.optString("cls")
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return WidgetApp(pkg, cls, j.optString("icon").ifEmpty { null })
        }
    }
}

/** An app with a home screen icon, that can go on the widget. */
class LaunchableApp(val pkg: String, val cls: String, val label: String)

/** An installed icon pack. */
class IconPack(val pkg: String, val label: String)

/** Icons for the widget's apps: the apps' own, from an icon pack, or a picture. */
object WidgetIcons {
    /** Icons are kept this small (px): plenty for the widget, and light for the home screen. */
    const val SIZE = 128

    private fun dir(context: Context) = File(context.filesDir, "widget_icons").apply { mkdirs() }

    /** The apps with a home screen icon, by name. */
    fun apps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { LaunchableApp(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg + "/" + it.cls }
            .sortedBy { it.label.lowercase() }
    }

    fun installed(context: Context, app: WidgetApp): Boolean = runCatching {
        context.packageManager.getActivityInfo(ComponentName(app.pkg, app.cls), 0)
        true
    }.getOrDefault(false)

    fun label(context: Context, app: WidgetApp): String = runCatching {
        context.packageManager.getActivityInfo(ComponentName(app.pkg, app.cls), 0).loadLabel(context.packageManager).toString()
    }.getOrDefault(app.pkg)

    /** The app's own icon. */
    fun appIcon(context: Context, pkg: String, cls: String): Drawable? = runCatching {
        context.packageManager.getActivityIcon(ComponentName(pkg, cls))
    }.getOrNull()

    /** The icon the widget shows for [app]: the one you picked, or the app's own. */
    fun bitmap(context: Context, app: WidgetApp): Bitmap? {
        app.icon?.let { path -> BitmapFactory.decodeFile(path)?.let { return it } }
        return appIcon(context, app.pkg, app.cls)?.let { toBitmap(it) }
    }

    fun toBitmap(d: Drawable, size: Int = SIZE): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(b))
        return b
    }

    /** Keeps a picked icon as a small PNG; returns its path. */
    fun keep(context: Context, bmp: Bitmap): String? = runCatching {
        val f = File(dir(context), UUID.randomUUID().toString() + ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.absolutePath
    }.getOrNull()

    /** Throws away kept icons no widget uses any more. */
    fun cleanUp(context: Context, used: Set<String>) {
        dir(context).listFiles()?.forEach { if (it.absolutePath !in used) it.delete() }
    }

    /** A picture from the phone, cut to a square from its middle and made small. */
    fun fromPicture(context: Context, uri: Uri): Bitmap? = runCatching {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val scale = (SIZE * 2f / minOf(s.width, s.height)).coerceAtMost(1f)
                decoder.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
    }.getOrNull()
}

/** Icon packs made for launchers (Nova, ADW, Apex, Go…): their list of icons and which app each is for. */
object IconPacks {
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
        "com.dlto.atom.launcher.THEME",
    )

    private val resources = HashMap<String, Resources>()

    fun list(context: Context): List<IconPack> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPack>()
        val intents = actions.map { Intent(it) } +
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME")
        intents.forEach { intent ->
            runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList()).forEach { r ->
                val pkg = r.activityInfo.packageName
                if (pkg !in found) found[pkg] = IconPack(pkg, r.activityInfo.applicationInfo.loadLabel(pm).toString())
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun res(context: Context, pack: String): Resources? =
        resources[pack] ?: runCatching { context.packageManager.getResourcesForApplication(pack) }.getOrNull()?.also { resources[pack] = it }

    /** The pack's icon names: the ones it made for [pkg]/[cls] first, then all the others by name. */
    fun icons(context: Context, pack: String, pkg: String, cls: String): Pair<List<String>, List<String>> {
        val res = res(context, pack) ?: return emptyList<String>() to emptyList()
        val exact = LinkedHashSet<String>()
        val samePkg = LinkedHashSet<String>()
        val all = HashSet<String>()
        parse(context, res, pack, "appfilter") { p ->
            if (p.name == "item") {
                val d = p.getAttributeValue(null, "drawable")
                if (!d.isNullOrEmpty()) {
                    all += d
                    val comp = p.getAttributeValue(null, "component").orEmpty()
                    if (comp == "ComponentInfo{$pkg/$cls}") exact += d else if (comp.startsWith("ComponentInfo{$pkg/")) samePkg += d
                }
            }
        }
        parse(context, res, pack, "drawable") { p ->
            if (p.name == "item") p.getAttributeValue(null, "drawable")?.takeIf { it.isNotEmpty() }?.let { all += it }
        }
        val mine = (exact + samePkg).toList()
        return mine to (all - mine.toSet()).sorted()
    }

    fun drawable(context: Context, pack: String, name: String): Drawable? = runCatching {
        val res = res(context, pack) ?: return null
        var id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) id = res.getIdentifier(name, "mipmap", pack)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    /** Reads one of the pack's lists, kept as an xml resource or as a file in its assets. */
    private fun parse(context: Context, res: Resources, pack: String, name: String, onTag: (XmlPullParser) -> Unit) {
        runCatching {
            val id = res.getIdentifier(name, "xml", pack)
            if (id != 0) {
                val parser = res.getXml(id)
                try {
                    walk(parser, onTag)
                } finally {
                    parser.close()
                }
                return
            }
            context.createPackageContext(pack, 0).assets.open("$name.xml").use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                walk(parser, onTag)
            }
        }
    }

    private fun walk(p: XmlPullParser, onTag: (XmlPullParser) -> Unit) {
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG) onTag(p)
            e = p.next()
        }
    }
}

// ---- Settings screen pieces ----

/** One app on the widget: its icon and name; tap to change the icon, × to take it off. */
@Composable
internal fun WidgetAppRow(app: WidgetApp, onIcon: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app) {
        value = withContext(Dispatchers.IO) { WidgetIcons.bitmap(ctx, app)?.asImageBitmap() }
    }
    val name = remember(app.pkg, app.cls) { WidgetIcons.label(ctx, app) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .clickable(onClick = onIcon)
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp)) { icon?.let { Image(it, null, Modifier.size(36.dp)) } }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (app.icon == null) "App's own icon · tap to change" else "Your icon · tap to change", color = p.faint, fontSize = 13.sp)
        }
        IconButtonSmall(Icons.Outlined.Close, "Remove", size = 44, onClick = onRemove)
    }
}

@Composable
internal fun AddWidgetAppRow(onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = p.accent) }
        Spacer(Modifier.width(16.dp))
        Text("Add an app", color = p.accent, fontSize = 16.sp)
    }
}

@Composable
private fun SearchField(text: String, hint: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint, color = p.faint) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text,
            unfocusedTextColor = p.text,
            focusedBorderColor = p.accent,
            unfocusedBorderColor = p.line,
            cursorColor = p.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Picks an app with a home screen icon. */
@Composable
internal fun AppPickerDialog(onDismiss: () -> Unit, onPick: (LaunchableApp) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) { value = withContext(Dispatchers.IO) { WidgetIcons.apps(ctx) } }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("Add an app", color = p.text) },
        text = {
            Column {
                SearchField(query, "Search apps") { query = it }
                Spacer(Modifier.height(8.dp))
                val shown = apps?.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                if (shown == null) {
                    Text("Loading…", color = p.faint, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it.pkg + "/" + it.cls }) { app ->
                            val icon by produceState<ImageBitmap?>(null, app.pkg, app.cls) {
                                value = withContext(Dispatchers.IO) {
                                    WidgetIcons.appIcon(ctx, app.pkg, app.cls)?.let { WidgetIcons.toBitmap(it, 96).asImageBitmap() }
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(app) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(32.dp)) { icon?.let { Image(it, null, Modifier.size(32.dp)) } }
                                Spacer(Modifier.width(14.dp))
                                Text(app.label, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.mute) } },
    )
}

/** Where an app's icon comes from: its own, an icon pack, or a picture. */
@Composable
internal fun IconSourceDialog(
    onDismiss: () -> Unit,
    onOwn: () -> Unit,
    onPack: (IconPack) -> Unit,
    onPicture: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val packs by produceState<List<IconPack>?>(null) { value = withContext(Dispatchers.IO) { IconPacks.list(ctx) } }
    @Composable
    fun Option(title: String, sub: String?, onClick: () -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = p.faint, fontSize = 13.sp)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("Icon", color = p.text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Option("App's own icon", null, onOwn)
                Option("A picture", "From your photos, cut to a square", onPicture)
                Text("Icon packs", color = p.mute, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
                when {
                    packs == null -> Text("Loading…", color = p.faint, modifier = Modifier.padding(8.dp))
                    packs!!.isEmpty() -> Text("No icon packs installed", color = p.faint, modifier = Modifier.padding(8.dp))
                    else -> LazyColumn {
                        items(packs!!, key = { it.pkg }) { pack -> Option(pack.label, null) { onPack(pack) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.mute) } },
    )
}

/** An icon pack's icons: the ones made for this app first, then all of them (searchable). */
@Composable
internal fun PackIconsDialog(pack: IconPack, app: WidgetApp, onDismiss: () -> Unit, onPick: (Drawable) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val names by produceState<Pair<List<String>, List<String>>?>(null, pack.pkg) {
        value = withContext(Dispatchers.IO) { IconPacks.icons(ctx, pack.pkg, app.pkg, app.cls) }
    }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(pack.label, color = p.text) },
        text = {
            Column {
                SearchField(query, "Search icons") { query = it.lowercase() }
                Spacer(Modifier.height(8.dp))
                val n = names
                if (n == null) {
                    Text("Loading…", color = p.faint, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    val q = query.trim().replace(' ', '_')
                    val shown = if (q.isEmpty()) n.first + n.second else (n.first + n.second).filter { it.contains(q) }
                    if (n.first.isEmpty() && q.isEmpty()) {
                        Text("This pack has no icon made for this app: pick any.", color = p.faint, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it }) { name ->
                            val d by produceState<Drawable?>(null, pack.pkg, name) {
                                value = withContext(Dispatchers.IO) { IconPacks.drawable(ctx, pack.pkg, name) }
                            }
                            val bmp = remember(d) { d?.let { WidgetIcons.toBitmap(it, 96).asImageBitmap() } }
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = d != null) { d?.let(onPick) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) { bmp?.let { Image(it, name, Modifier.size(40.dp)) } }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.mute) } },
    )
}
