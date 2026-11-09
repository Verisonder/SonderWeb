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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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

/** The three highlights (top-left, middle, bottom-right) in the icon's 108-unit space. */
private val HIGHLIGHTS = listOf(
    "M38,50 A17,17 0,0 1,52 37",
    "M47.25,47.25 A17,17 0,0 1,60.75 60.75",
    "M70,58 A17,17 0,0 1,56 71",
)

/** The SonderWeb mark: a blue glass sphere with three highlights that suggest an S (same as the app icon). */
@Composable
fun Mark(size: Int, @Suppress("UNUSED_PARAMETER") ring: Color = Color.White, @Suppress("UNUSED_PARAMETER") dot: Color = Color.White) {
    val pieces = remember { HIGHLIGHTS.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension * 0.46f
        val k = r / 25f
        val c = center
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF8AB4F8), Color(0xFF1A4FD6)),
                center = Offset(c.x - 6f * k, c.y - 8f * k),
                radius = 35f * k,
            ),
            radius = r,
            center = c,
        )
        withTransform({
            translate(c.x - 54f * k, c.y - 54f * k)
            scale(k, k, pivot = Offset.Zero)
        }) {
            pieces.forEach {
                drawPath(it, Color.White.copy(alpha = 0.85f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
    }
}

/** Just the mark's three highlights in one colour, for the small bar button. */
@Composable
fun MarkLines(size: Int, color: Color) {
    val pieces = remember { HIGHLIGHTS.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size.dp)) {
        // The highlights span 31..77 of the 108-unit icon.
        val k = this.size.minDimension / 46f
        withTransform({
            scale(k, k, pivot = Offset.Zero)
            translate(-31f, -31f)
        }) {
            pieces.forEach { drawPath(it, color, style = Stroke(width = 6f, cap = StrokeCap.Round)) }
        }
    }
}

/** A private tab's start page: no clock, name, weather, place or shortcuts, just a search box. */
@Composable
private fun PrivateStart(ui: UiState, tab: com.verisonder.sonderweb.browser.Tab) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(64.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(p.field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = p.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Private tab", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Text(
            "Nothing you do here is kept: no history, cookies or site data once you close it. " +
                "It doesn't see your history, bookmarks or shortcuts, and stays locked behind your fingerprint.",
            color = p.faint,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
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
    }
}

/** The start page's own look: its colours, and the wallpaper if you chose one. */
private class StartLook(val palette: Palette, val color: Color, val wallpaper: androidx.compose.ui.graphics.ImageBitmap?, val dim: Float)

@Composable
private fun startLook(ui: UiState): StartLook {
    val base = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = Browser.prefs
    val version = ui.themeVersion
    val wallOn = prefs.startWallpaper
    LaunchedEffect(version, wallOn) { if (wallOn) com.verisonder.sonderweb.data.StartBackground.load(ctx) }
    val wall = if (wallOn) com.verisonder.sonderweb.data.StartBackground.image else null
    val custom = prefs.startBgColor
    val dim = prefs.startDim
    return remember(version, base, wall, custom, dim) {
        when {
            // On a picture: light text, see-through search box and tiles.
            wall != null -> StartLook(
                DarkPalette.copy(accent = base.accent, field = Color.White.copy(alpha = 0.16f)),
                Color(0xFF101114),
                wall,
                dim.coerceIn(0, 80) / 100f,
            )
            custom != 0 -> {
                val c = Color(custom)
                val light = c.luminance() > 0.5f
                val b = if (light) LightPalette else DarkPalette
                StartLook(
                    b.copy(bg = c, accent = base.accent, field = if (light) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.1f)),
                    c,
                    null,
                    0f,
                )
            }
            else -> StartLook(base, base.bg, null, 0f)
        }
    }
}

/** What a new tab shows: the time, a greeting, the weather, a search box and your shortcuts,
 *  on the theme's colour, a colour you pick, or your wallpaper (Settings › Start page). */
@Composable
fun StartPage(ui: UiState, preview: com.verisonder.sonderweb.browser.Tab? = null) {
    val tab = preview ?: Browser.current ?: return
    if (tab.isPrivate) {
        StartPageBody(ui, preview)
        return
    }
    val look = startLook(ui)
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides look.palette) {
        Box(Modifier.fillMaxSize().background(look.color)) {
            look.wallpaper?.let { w ->
                Image(w, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (look.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.dim)))
            }
            StartPageBody(ui, preview, ownBackground = false)
        }
    }
}

@Composable
private fun StartPageBody(ui: UiState, preview: com.verisonder.sonderweb.browser.Tab?, ownBackground: Boolean = true) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    // [preview]: drawn small on a card in the tab switcher.
    val tab = preview ?: Browser.current ?: return
    if (tab.isPrivate) {
        PrivateStart(ui, tab)
        return
    }
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

    // Everything sits in the middle of the screen, a little below centre; it scrolls if it doesn't fit.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(if (ownBackground) p.bg else Color.Transparent)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The time and date can sit higher or lower (Settings › Start page › Layout), without
            // moving anything else.
            val lift = if (preview == null) prefs.clockLift.coerceIn(-120, 200) else 0
            if (preview == null) Spacer(Modifier.height(88.dp))
            if (prefs.showClock) {
                Column(
                    Modifier.offset { androidx.compose.ui.unit.IntOffset(0, -lift.dp.roundToPx()) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        time,
                        color = p.text,
                        fontSize = 88.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-2).sp,
                        maxLines = 1,
                    )
                    Text(date, color = p.text, fontSize = 16.sp)
                }
                Spacer(Modifier.height(34.dp))
            }
            // The greeting, weather, search box and shortcuts move together (Settings › Start page ›
            // Layout); the time and date have their own setting.
            val restLift = if (preview == null) prefs.restLift.coerceIn(-120, 200) else 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, -restLift.dp.roundToPx()) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (prefs.showGreeting) {
                    Text(
                        WeatherCodes.greeting(hour, prefs.userName),
                        color = p.text,
                        fontSize = 27.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                // Until the first reading comes in its room is kept (invisible), so nothing jumps.
                val shown = weather ?: Weather.Now(0, 0, true, "C", "", 0L).takeIf { prefs.weatherOn && prefs.place != null }
                if (shown != null) {
                    Column(
                        Modifier.graphicsLayer { alpha = if (weather != null) 1f else 0f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            WeatherCodes.sentence(shown.code, shown.temp, shown.unit),
                            color = p.text,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkyIcon(WeatherCodes.sky(shown.code, shown.day), 34)
                            Spacer(Modifier.width(8.dp))
                            Text("${shown.temp}°${shown.unit}", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        }
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
                    onRemove = { i ->
                        prefs.shortcuts = shortcuts.filterIndexed { j, _ -> j != i }
                        version++
                    },
                    onAdd = { editing = -1 },
                    onReorder = { list ->
                        prefs.shortcuts = list
                        version++
                    },
                )
                Spacer(Modifier.height(32.dp))
            }
        }
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
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Shortcut>) -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Hold an icon: its menu (Edit, Remove) opens above it at once. Drag on from there to move it;
    // the menu closes as soon as it moves.
    val order = remember(shortcuts) { androidx.compose.runtime.mutableStateListOf<Shortcut>().apply { addAll(shortcuts) } }
    var dragged by remember { mutableStateOf<Shortcut?>(null) }
    var menuFor by remember { mutableStateOf<Shortcut?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var dragX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var dragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var rowPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val lift by androidx.compose.animation.core.animateFloatAsState(
        if (dragged != null) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "lift",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 560.dp) 6 else 4
        val cell = maxWidth / columns
        val cellPx = with(androidx.compose.ui.platform.LocalDensity.current) { cell.toPx() }
        val names = Browser.prefs.showShortcutNames
        val showAdd = Browser.prefs.showAddShortcut && shortcuts.size < 24
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = columns,
        ) {
            order.forEach { s ->
                androidx.compose.runtime.key(s) {
                    val host = UrlText.host(s.url)
                    LaunchedEffect(host) { SiteIcons.load(ctx, host) }
                    val isDragged = dragged == s
                    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
                    val edit by androidx.compose.runtime.rememberUpdatedState(onEdit)
                    val remove by androidx.compose.runtime.rememberUpdatedState(onRemove)
                    val save by androidx.compose.runtime.rememberUpdatedState(onReorder)
                    Column(
                        Modifier
                            .width(cell)
                            .onSizeChanged { rowPx = it.height.toFloat().coerceAtLeast(1f) }
                            .zIndex(if (isDragged) 1f else 0f)
                            .pointerInput(s, showAdd) {
                                val slop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var moved = false
                                    var released = false
                                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val c = awaitPointerEvent().changes.first()
                                            if ((c.position - down.position).getDistance() > slop) moved = true
                                            if (!c.pressed) {
                                                released = true
                                                break
                                            }
                                        }
                                    }
                                    if (released) {
                                        if (!moved) open(s)
                                        return@awaitEachGesture
                                    }
                                    if (moved) return@awaitEachGesture
                                    // Picked up.
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    menuFor = s
                                    menuOpen = true
                                    dragged = s
                                    dragX = 0f
                                    dragY = 0f
                                    var travelled = 0f
                                    while (true) {
                                        val c = awaitPointerEvent().changes.first()
                                        val d = c.positionChange()
                                        c.consume()
                                        travelled += d.getDistance()
                                        if (menuOpen && travelled > slop) menuOpen = false
                                        dragX += d.x
                                        dragY += d.y
                                        // Over another spot: move there; the icon stays under the finger.
                                        // Rows are centred, so a short last row starts further in.
                                        val total = order.size + if (showAdd) 1 else 0
                                        fun spotX(i: Int): Float {
                                            val inRow = minOf(columns, total - (i / columns) * columns)
                                            return ((columns - inRow) / 2f + i % columns) * cellPx
                                        }
                                        val from = order.indexOf(s)
                                        val atX = spotX(from) + dragX
                                        val atY = (from / columns) * rowPx + dragY
                                        val to = order.indices.minBy { i ->
                                            val ddx = spotX(i) - atX
                                            val ddy = (i / columns) * rowPx - atY
                                            ddx * ddx + ddy * ddy
                                        }
                                        if (to != from) {
                                            order.removeAt(from)
                                            order.add(to, s)
                                            dragX -= spotX(to) - spotX(from)
                                            dragY -= (to / columns - from / columns) * rowPx
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        }
                                        if (!c.pressed) break
                                    }
                                    dragged = null
                                    dragX = 0f
                                    dragY = 0f
                                    if (order.toList() != shortcuts) save(order.toList())
                                }
                            }
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragX
                                    translationY = dragY
                                    val k = 1f + 0.12f * lift
                                    scaleX = k
                                    scaleY = k
                                }
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SiteTile(host, s.title)
                        if (menuFor == s) {
                            ShortcutMenu(
                                open = menuOpen,
                                onDismiss = { menuOpen = false },
                                onClosed = { if (!menuOpen && menuFor == s) menuFor = null },
                                onEdit = {
                                    menuOpen = false
                                    edit(shortcuts.indexOf(s))
                                },
                                onRemove = {
                                    menuOpen = false
                                    remove(shortcuts.indexOf(s))
                                },
                            )
                        }
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
            }
            if (showAdd) {
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

/**
 * A site's icon, like an app on a home screen: the icon itself with no grey tile behind it
 * (square ones get rounded corners, shaped ones keep their shape). Sites with only a tiny favicon
 * get a soft tile; sites with none get their first letter on a tile in a colour of their own.
 */
@Composable
private fun SiteTile(host: String, title: String) {
    val p = LocalPalette.current
    val bmp = SiteIcons.icons[host]
    val shape = RoundedCornerShape(14.dp)
    // A logo rather than an app-style icon (YouTube's play button, Gmail's M: wider than tall):
    // drawn a little smaller and uncut, so it fits and stays sharp.
    val logo = bmp != null && bmp.width >= 64 && kotlin.math.abs(bmp.width - bmp.height) > minOf(bmp.width, bmp.height) / 8
    when {
        logo -> Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Image(
                remember(bmp) { bmp!!.asImageBitmap() },
                title,
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.size(44.dp),
            )
        }
        bmp != null && bmp.width >= 64 -> Image(
            remember(bmp) { bmp.asImageBitmap() },
            title,
            contentScale = ContentScale.Fit,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.size(52.dp).clip(shape),
        )
        // Only a small favicon: the icon itself, a little smaller, still without a tile.
        bmp != null -> Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Image(
                remember(bmp) { bmp.asImageBitmap() },
                title,
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.size(40.dp),
            )
        }
        else -> {
            val hue = remember(host) { ((host.hashCode() % 360) + 360) % 360 }.toFloat()
            Box(
                Modifier.size(52.dp).clip(shape).background(Color.hsl(hue, 0.42f, 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (title.ifBlank { host }).firstOrNull()?.uppercase() ?: "•",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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

/** The menu above a held start page icon. */
@Composable
private fun ShortcutMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalPalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val show by androidx.compose.animation.core.animateFloatAsState(
        if (open) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "shortcutMenu",
        finishedListener = { if (it == 0f) onClosed() },
    )
    // Room around the menu inside its popup window, so the shadow is never cut off.
    val room = with(density) { 24.dp.roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    var above by remember { mutableStateOf(true) }
    val place = remember(room, gap) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ): androidx.compose.ui.unit.IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(-room, (windowSize.width - popupContentSize.width + room).coerceAtLeast(-room))
                val top = anchorBounds.top - gap - popupContentSize.height + room
                val fits = top + room >= 0
                above = fits
                val y = if (fits) top else anchorBounds.bottom + gap - room
                return androidx.compose.ui.unit.IntOffset(x, y)
            }
        }
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = place,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(with(density) { room.toDp() })
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (above) 1f else 0f)
                    val k = 0.85f + 0.15f * show
                    scaleX = k
                    scaleY = k
                    alpha = show.coerceIn(0f, 1f)
                }
                .width(190.dp)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(p.card)
                .padding(vertical = 6.dp),
        ) {
            ShortcutMenuRow(Icons.Outlined.Edit, "Edit", p.text, onEdit)
            ShortcutMenuRow(Icons.Outlined.Delete, "Remove", p.text, onRemove)
        }
    }
}

@Composable
private fun ShortcutMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.mute, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontSize = 15.sp)
    }
}
