package com.verisonder.sonderweb.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.core.AddressInput
import com.verisonder.sonderweb.core.Sky
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.core.WeatherCodes
import com.verisonder.sonderweb.data.Shortcut
import com.verisonder.sonderweb.data.SiteIcons
import com.verisonder.sonderweb.data.Weather
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The ring-and-dot mark (same as the app icon). */
@Composable
fun Mark(size: Int, ring: Color, dot: Color) {
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension * 0.30f
        val c = center
        drawCircle(ring, radius = r, center = c, style = Stroke(width = this.size.minDimension * 0.085f))
        drawCircle(dot, radius = this.size.minDimension * 0.09f, center = Offset(c.x + r * 0.72f, c.y - r * 1.05f))
    }
}

/** What a new tab shows: the time, a greeting, the weather, a search box and your shortcuts. */
@Composable
fun StartPage(ui: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val tab = Browser.current ?: return
    val prefs = Browser.prefs
    // Settings bump themeVersion; reading it here redraws the page with the new choices.
    if (ui.themeVersion < 0) return

    // The clock ticks on the minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50)
        }
    }
    LaunchedEffect(ui.themeVersion) { Weather.refresh(prefs) }

    val is24 = DateFormat.is24HourFormat(ctx)
    val time = DateFormat.format(if (is24) "HH:mm" else "h:mm", Date(now)).toString()
    val date = DateFormat.format("EEEE, MMMM d", Date(now)).toString()
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val weather = Weather.now.takeIf { prefs.weatherOn }

    var version by remember { mutableIntStateOf(0) }
    val shortcuts = remember(version, ui.themeVersion) { prefs.shortcuts }
    var editing by remember { mutableStateOf<Int?>(null) } // index, or -1 for a new one

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        if (prefs.showClock) {
            Text(
                time,
                color = p.text,
                fontSize = 88.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-2).sp,
                maxLines = 1,
            )
            Text(date, color = p.text, fontSize = 16.sp)
            Spacer(Modifier.height(34.dp))
        }
        if (tab.isPrivate) {
            Text("Private tab", color = p.text, fontSize = 27.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nothing from this tab is kept once you close it.",
                color = p.mute,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        } else if (prefs.showGreeting) {
            Text(
                WeatherCodes.greeting(hour, prefs.userName),
                color = p.text,
                fontSize = 27.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (weather != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                WeatherCodes.sentence(weather.code, weather.temp, weather.unit),
                color = p.text,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkyIcon(WeatherCodes.sky(weather.code, weather.day), 34)
                Spacer(Modifier.width(8.dp))
                Text("${weather.temp}°${weather.unit}", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Light)
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.field)
                .clickable { startEditing(ui, tab) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, "Search", tint = p.faint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(30.dp))
        ShortcutGrid(
            shortcuts,
            onOpen = { Browser.load(tab, it.url) },
            onEdit = { editing = it },
            onAdd = { editing = -1 },
        )
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { i ->
        ShortcutDialog(
            existing = shortcuts.getOrNull(i),
            onDismiss = { editing = null },
            onSave = { s ->
                val list = shortcuts.toMutableList()
                if (i in list.indices) list[i] = s else list += s
                prefs.shortcuts = list
                SiteIcons.forget(UrlText.host(s.url))
                version++
                editing = null
            },
            onRemove = if (i in shortcuts.indices) {
                {
                    prefs.shortcuts = shortcuts.filterIndexed { j, _ -> j != i }
                    version++
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ShortcutGrid(
    shortcuts: List<Shortcut>,
    onOpen: (Shortcut) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 560.dp) 6 else 4
        val cell = maxWidth / columns
        val names = Browser.prefs.showShortcutNames
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = columns,
        ) {
            shortcuts.forEachIndexed { i, s ->
                val host = UrlText.host(s.url)
                LaunchedEffect(host) { SiteIcons.load(ctx, host) }
                Column(
                    Modifier
                        .width(cell)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = { onOpen(s) }, onLongClick = { onEdit(i) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SiteTile(host, s.title)
                    if (names) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.title.ifBlank { host },
                            color = p.mute,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            if (shortcuts.size < 24) {
                Column(
                    Modifier
                        .width(cell)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAdd)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(p.field),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Add, "Add shortcut", tint = p.mute, modifier = Modifier.size(22.dp))
                    }
                    if (names) {
                        Spacer(Modifier.height(6.dp))
                        Text("Add", color = p.faint, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** A site's icon: big app icons fill the tile, small favicons sit in the middle of it. */
@Composable
private fun SiteTile(host: String, title: String) {
    val p = LocalPalette.current
    val bmp = SiteIcons.icons[host]
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier.size(52.dp).clip(shape).background(p.field),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null && bmp.width >= 96 -> Image(
                remember(bmp) { bmp.asImageBitmap() },
                title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            bmp != null -> Image(
                remember(bmp) { bmp.asImageBitmap() },
                title,
                modifier = Modifier.size(28.dp),
            )
            else -> Text(
                (title.ifBlank { host }).firstOrNull()?.uppercase() ?: "•",
                color = p.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ShortcutDialog(
    existing: Shortcut?,
    onDismiss: () -> Unit,
    onSave: (Shortcut) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(existing?.title ?: "") }
    var address by remember { mutableStateOf(existing?.url?.let { UrlText.editable(it) } ?: "") }
    val url = remember(address) {
        val t = address.trim()
        when {
            t.isEmpty() -> null
            UrlText.isWeb(t) -> t
            else -> AddressInput.hostTarget(t)
        }
    }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = p.text,
        unfocusedTextColor = p.text,
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.line,
        cursorColor = p.accent,
        focusedLabelColor = p.accent,
        unfocusedLabelColor = p.mute,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(if (existing == null) "Add shortcut" else "Edit shortcut", color = p.text) },
        text = {
            Column(Modifier.heightIn(min = 10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = colors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(address, { address = it }, label = { Text("Address") }, singleLine = true, colors = colors)
                if (onRemove != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Remove from start page",
                        color = p.danger,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRemove)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    val u = url ?: return@TextButton
                    onSave(Shortcut(u, name.trim().ifEmpty { UrlText.host(u) }))
                },
                enabled = url != null,
            ) { Text("Save", color = if (url != null) p.accent else p.faint) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}

/** Simple line-drawn weather pictures, coloured like the screenshot sun. */
@Composable
fun SkyIcon(sky: Sky, size: Int) {
    val p = LocalPalette.current
    val sun = Color(0xFFFFD233)
    val cloud = if (p.dark) Color(0xFFE6E6EA) else Color(0xFF9CA3AF)
    val rain = Color(0xFF60A5FA)
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension
        when (sky) {
            Sky.SUN -> drawSun(Offset(s / 2, s / 2), s * 0.2f, sun)
            Sky.MOON -> drawMoon(Offset(s / 2, s / 2), s * 0.26f, cloud, p.bg)
            Sky.PARTLY_DAY -> {
                drawSun(Offset(s * 0.38f, s * 0.36f), s * 0.15f, sun)
                drawCloud(Offset(s * 0.58f, s * 0.64f), s * 0.6f, cloud)
            }
            Sky.PARTLY_NIGHT -> {
                drawMoon(Offset(s * 0.38f, s * 0.36f), s * 0.18f, cloud, p.bg)
                drawCloud(Offset(s * 0.58f, s * 0.66f), s * 0.6f, cloud)
            }
            Sky.CLOUD -> drawCloud(Offset(s / 2, s * 0.55f), s * 0.8f, cloud)
            Sky.FOG -> {
                for (i in 0..2) {
                    val y = s * (0.35f + i * 0.17f)
                    drawLine(cloud, Offset(s * 0.15f, y), Offset(s * 0.85f, y), s * 0.07f, StrokeCap.Round)
                }
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                drawCloud(Offset(s / 2, s * 0.42f), s * 0.8f, cloud)
                val drops = if (sky == Sky.RAIN) 3 else 2
                for (i in 0 until drops) {
                    val x = s * (0.32f + i * 0.36f / (drops - 1).coerceAtLeast(1))
                    drawLine(rain, Offset(x, s * 0.7f), Offset(x - s * 0.06f, s * 0.88f), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.SNOW -> {
                drawCloud(Offset(s / 2, s * 0.42f), s * 0.8f, cloud)
                for (i in 0..2) drawCircle(cloud, s * 0.04f, Offset(s * (0.3f + i * 0.2f), s * 0.8f))
            }
            Sky.STORM -> {
                drawCloud(Offset(s / 2, s * 0.4f), s * 0.8f, cloud)
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.55f, s * 0.58f)
                    lineTo(s * 0.42f, s * 0.78f)
                    lineTo(s * 0.52f, s * 0.78f)
                    lineTo(s * 0.45f, s * 0.95f)
                    lineTo(s * 0.64f, s * 0.7f)
                    lineTo(s * 0.53f, s * 0.7f)
                    close()
                }
                drawPath(bolt, sun)
            }
        }
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color) {
    drawCircle(color, r, c)
    for (i in 0 until 8) {
        val a = i * PI / 4
        val from = Offset(c.x + cos(a).toFloat() * r * 1.5f, c.y + sin(a).toFloat() * r * 1.5f)
        val to = Offset(c.x + cos(a).toFloat() * r * 2.1f, c.y + sin(a).toFloat() * r * 2.1f)
        drawLine(color, from, to, r * 0.28f, StrokeCap.Round)
    }
}

private fun DrawScope.drawMoon(c: Offset, r: Float, color: Color, bg: Color) {
    drawCircle(color, r, c)
    drawCircle(bg, r * 0.85f, Offset(c.x + r * 0.55f, c.y - r * 0.35f))
}

private fun DrawScope.drawCloud(c: Offset, w: Float, color: Color) {
    val h = w * 0.36f
    drawRoundRect(
        color,
        topLeft = Offset(c.x - w / 2, c.y - h / 2),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
    )
    drawCircle(color, h * 0.62f, Offset(c.x - w * 0.12f, c.y - h * 0.45f))
    drawCircle(color, h * 0.48f, Offset(c.x + w * 0.16f, c.y - h * 0.32f))
}
