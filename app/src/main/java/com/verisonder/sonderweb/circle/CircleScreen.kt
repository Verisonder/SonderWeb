package com.verisonder.sonderweb.circle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.ImageSearch
import com.verisonder.sonderweb.data.Profiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Bg = Color(0xFF101114)
private val Panel = Color(0xFF1B1D22)
private val Field = Color(0xFF262930)
private val PopupBg = Color(0xFF2C2F36)
private val Text1 = Color(0xFFECEDF0)
private val Text2 = Color(0xFF9DA1AA)
private val Pick = Color(0xFF6F8DFF)
private val Glow = listOf(Color(0xFF4F8CFF), Color(0xFF9B6BFF), Color(0xFFFF6FA8), Color(0xFF4F8CFF))

/**
 * The circle: the screen glows and settles into a card; draw around anything (or tap it) to
 * search it with Google Lens; tap a word to pick text (drag its handles for more), copy it or
 * translate it, and the results follow what's picked. Results slide up in a sheet, with your own
 * SonderWeb sign-ins; the card shrinks to stay in view above it.
 */
@Composable
fun CircleScreen(shot: Bitmap, image: ImageBitmap, textEngine: String, imageEngine: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ---- What's going on ----
    var words by remember { mutableStateOf<List<Ocr.Word>>(emptyList()) }
    // The circled part, in screenshot pixels (image search).
    var selection by remember { mutableStateOf<android.graphics.Rect?>(null) }
    // Picked text: a run of words, from [pickA] to [pickB] in reading order (-1: none).
    var textMode by remember { mutableStateOf(false) }
    var pickA by remember { mutableIntStateOf(-1) }
    var pickB by remember { mutableIntStateOf(-1) }
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var sheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    // The sheet's top takes the colour of the page under it (Google's own background).
    var pageColor by remember { mutableStateOf(Color(0xFF1F1F1F)) }
    var pageLoaded by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    // Translate the screen: the translations painted over the text (null: showing the original).
    var translatedLines by remember { mutableStateOf<List<ScreenTranslate.Line>?>(null) }
    var translating by remember { mutableStateOf(false) }
    // Pinch zoom on the screenshot: how much, and where the zoomed picture sits in its card
    // (as a share of the card's size, so it stays right while the card resizes with the sheet).
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    fun picked(): List<Ocr.Word> {
        if (pickA < 0 || pickB < 0) return emptyList()
        val a = min(pickA, pickB)
        val b = max(pickA, pickB)
        return words.filter { it.order in a..b }
    }

    // Reading the words can't take the circle down with it: without them, circling still works.
    LaunchedEffect(shot) { words = runCatching { Ocr.read(shot) }.getOrDefault(emptyList()) }

    // ---- Animations ----
    val settle = remember { Animatable(0f) } // 0: full screen, 1: the card
    val glow = remember { Animatable(0f) } // the light that runs up the screen at the start
    LaunchedEffect(Unit) {
        launch { glow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        delay(260)
        settle.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "turn")

    // ---- The results: a page in SonderWeb's engine, with your sign-ins ----
    val session = remember {
        GeckoSession(
            GeckoSessionSettings.Builder()
                .contextId(Profiles.contextId(Profiles.current))
                .build(),
        ).apply {
            open(Browser.runtime)
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    pageLoaded++
                }
            }
            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean,
                ) {
                    pageUrl = url
                }

                override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                    val scheme = Uri.parse(request.uri).scheme?.lowercase()
                    if (scheme == "http" || scheme == "https" || scheme == "data" || scheme == "about" || scheme == "blob") {
                        // Links that would open a new window stay in the sheet.
                        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                            session.loadUri(request.uri)
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    // App links (intent:, market:…) go to their apps.
                    runCatching {
                        val i = if (scheme == "intent") Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(request.uri))
                        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                    session.loadUri(uri)
                    return null
                }
            }
        }
    }
    val pageView = remember {
        GeckoView(ctx).apply {
            // A texture, so the page can slide with the sheet.
            setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
            setSession(session)
        }
    }
    // Reads the page's top edge once it has drawn (and again a moment later, as Google's page
    // settles), so the sheet's top matches it exactly.
    LaunchedEffect(pageLoaded) {
        if (pageLoaded == 0) return@LaunchedEffect
        repeat(2) {
            delay(if (it == 0) 250 else 900)
            runCatching {
                pageView.capturePixels().accept({ bmp ->
                    if (bmp != null && bmp.width > 4 && bmp.height > 4) {
                        pageColor = Color(bmp.getPixel(4, 2)).copy(alpha = 1f)
                        bmp.recycle()
                    }
                }, { _ -> })
            }
        }
    }
    val pageTone by androidx.compose.animation.animateColorAsState(pageColor, tween(220), label = "pageTone")
    DisposableEffect(Unit) {
        onDispose {
            runCatching { pageView.releaseSession() }
            runCatching { session.close() }
        }
    }

    fun searchImage(r: android.graphics.Rect) {
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) { crop(shot, r) }
            session.loadUri(if (imageEngine == "google") ImageSearch.page(jpeg) else PictureSearch.page(imageEngine, jpeg))
            sheetOpen = true
        }
    }

    fun searchText(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        // SonderWeb's own engine (or the one picked in SonderCircle's settings); !bangs work too.
        val engine = if (textEngine.isEmpty()) Browser.prefs.engine() else com.verisonder.sonderweb.core.SearchEngines.resolve(textEngine, null)
        val target = com.verisonder.sonderweb.core.AddressInput.resolve(q, engine) ?: return
        session.loadUri(target.url)
        sheetOpen = true
    }

    fun translate(text: String) {
        if (text.isBlank()) {
            Toast.makeText(ctx, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }
        session.loadUri(
            "https://translate.google.com/?sl=auto&tl=" + Uri.encode(Locale.getDefault().language) +
                "&text=" + Uri.encode(text.take(4500)) + "&op=translate",
        )
        sheetOpen = true
    }

    /** The translate button: paints the screen's text over in the phone's language, or back. */
    fun translateScreen() {
        if (translatedLines != null) {
            translatedLines = null
            return
        }
        if (translating) return
        translating = true
        scope.launch {
            // The words may still be being read: give it a moment.
            var waited = 0
            while (words.isEmpty() && waited < 3000) {
                delay(150)
                waited += 150
            }
            val result = if (words.isEmpty()) emptyList() else ScreenTranslate.translate(shot, words)
            translating = false
            when {
                result == null -> Toast.makeText(ctx, "Couldn't translate. Check the connection.", Toast.LENGTH_SHORT).show()
                result.isEmpty() -> Toast.makeText(ctx, "No text on the screen", Toast.LENGTH_SHORT).show()
                else -> translatedLines = result
            }
        }
    }

    fun copy(text: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SonderWeb", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun clearAll() {
        sheetOpen = false
        selection = null
        textMode = false
        pickA = -1
        pickB = -1
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) searchText(said)
    }
    val speak = {
        runCatching {
            voice.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH),
            )
        }.onFailure { Toast.makeText(ctx, "No speech recognition on this phone", Toast.LENGTH_SHORT).show() }
        Unit
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
        val top = statusTop + with(density) { 60.dp.toPx() }
        val bottomRoom = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() + 92.dp.toPx() }
        val side = with(density) { 14.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val minCard = with(density) { 120.dp.toPx() }
        val corner = with(density) { 22.dp.toPx() }
        val sw = shot.width.toFloat()
        val sh = shot.height.toFloat()
        val sheetPeek = h * 0.5f
        val sheetFull = h * 0.9f
        // Where the sheet's top is (h: put away). The card above it shrinks to fit.
        val sheetY = remember(h) { Animatable(h) }
        val snap = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
        LaunchedEffect(sheetOpen) {
            if (sheetOpen && sheetY.value > h - sheetPeek) sheetY.animateTo(h - sheetPeek, snap)
            if (!sheetOpen) sheetY.animateTo(h, snap)
        }

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val fullScale = min(w / sw, h / sh)
        val fullLeft = (w - sw * fullScale) / 2f
        fun cardBottom(): Float {
            val base = h - bottomRoom
            val s = sheetY.value
            return if (s >= h - 1f) base else min(base, s - gap).coerceAtLeast(top + minCard)
        }
        fun cardScale() = min((w - 2 * side) / sw, (cardBottom() - top) / sh)
        fun scaleNow() = lerp(fullScale, cardScale(), settle.value)
        fun leftNow() = lerp(fullLeft, (w - sw * cardScale()) / 2f, settle.value)
        fun topNow() = lerp(0f, top, settle.value)
        // The card's frame stays put; when zoomed, the picture inside it is bigger and moved.
        fun toScreen(x: Float, y: Float) = Offset(
            leftNow() + panX * sw * scaleNow() + x * scaleNow() * zoom,
            topNow() + panY * sh * scaleNow() + y * scaleNow() * zoom,
        )
        fun toShot(p: Offset) = Offset(
            (p.x - leftNow() - panX * sw * scaleNow()) / (scaleNow() * zoom),
            (p.y - topNow() - panY * sh * scaleNow()) / (scaleNow() * zoom),
        )

        // The picture moves freely (over the bars too, like the original), but a good part of it
        // always stays on screen.
        fun clampPan() {
            val fw = sw * scaleNow()
            val fh = sh * scaleNow()
            val iw = fw * zoom
            val ih = fh * zoom
            val minX = (w * 0.25f - leftNow() - iw) / fw
            val maxX = (w * 0.75f - leftNow()) / fw
            val minY = (h * 0.25f - topNow() - ih) / fh
            val maxY = (h * 0.75f - topNow()) / fh
            panX = panX.coerceIn(minX, maxX)
            panY = panY.coerceIn(minY, maxY)
        }

        // Two fingers: zoom in and move around; the spot between your fingers stays under them.
        suspend fun AwaitPointerEventScope.pinch(first: PointerEvent) {
            fun centroid(e: PointerEvent): Offset {
                val ps = e.changes.filter { it.pressed }
                return Offset(ps.map { it.position.x }.average().toFloat(), ps.map { it.position.y }.average().toFloat())
            }
            fun span(e: PointerEvent): Float {
                val ps = e.changes.filter { it.pressed }
                val c = centroid(e)
                return ps.map { (it.position - c).getDistance() }.average().toFloat().coerceAtLeast(1f)
            }
            stroke = emptyList()
            var c0 = centroid(first)
            var s0 = span(first)
            while (true) {
                val ev = awaitPointerEvent()
                ev.changes.forEach { it.consume() }
                if (ev.changes.count { it.pressed } < 2) break
                val c1 = centroid(ev)
                val s1 = span(ev)
                val q = toShot(c0)
                zoom = (zoom * s1 / s0).coerceIn(0.5f, 6f)
                val base = scaleNow() * zoom
                panX = (c1.x - leftNow() - q.x * base) / (sw * scaleNow())
                panY = (c1.y - topNow() - q.y * base) / (sh * scaleNow())
                clampPan()
                c0 = c1
                s0 = s1
            }
            // Nearly the card's own size: snaps back into the card.
            if (zoom in 0.92f..1.08f) {
                zoom = 1f
                panX = 0f
                panY = 0f
            }
            // The rest of the gesture (a finger still down) does nothing.
            while (true) {
                val ev = awaitPointerEvent()
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
        fun screenRect(r: android.graphics.Rect) = Rect(toScreen(r.left.toFloat(), r.top.toFloat()), toScreen(r.right.toFloat(), r.bottom.toFloat()))

        fun wordAt(p: Offset, reachDp: Float = 10f): Ocr.Word? {
            val s = toShot(p)
            val reach = reachDp * density.density / scaleNow()
            return words.firstOrNull { wd ->
                s.x >= wd.box.left - reach && s.x <= wd.box.right + reach && s.y >= wd.box.top - reach && s.y <= wd.box.bottom + reach
            }
        }

        // The word nearest to a finger dragging a handle.
        fun wordNear(p: Offset): Ocr.Word? {
            val s = toShot(p)
            return words.minByOrNull { wd ->
                val dx = max(0f, max(wd.box.left - s.x, s.x - wd.box.right))
                val dy = max(0f, max(wd.box.top - s.y, s.y - wd.box.bottom))
                dx * dx + dy * dy * 4f
            }
        }

        fun wordsIn(r: android.graphics.Rect) = words.filter { android.graphics.Rect.intersects(it.box, r) }

        fun handles(): Pair<Offset, Offset>? {
            val p = picked()
            if (p.isEmpty()) return null
            val first = p.minBy { it.order }
            val last = p.maxBy { it.order }
            val r = with(density) { 9.dp.toPx() }
            val a = toScreen(first.box.left.toFloat(), first.box.bottom.toFloat())
            val b = toScreen(last.box.right.toFloat(), last.box.bottom.toFloat())
            return Offset(a.x, a.y + r) to Offset(b.x, b.y + r)
        }

        fun selectRegion(r: android.graphics.Rect) {
            selection = r
            textMode = false
            pickA = -1
            pickB = -1
            searchImage(r)
        }

        fun pickText(a: Int, b: Int, search: Boolean) {
            textMode = true
            selection = null
            pickA = a
            pickB = b
            val text = Ocr.join(picked())
            query = text
            if (search) searchText(text)
        }

        fun close() {
            if (closing) return
            closing = true
            scope.launch {
                sheetOpen = false
                launch { sheetY.animateTo(h, tween(200)) }
                settle.animateTo(0f, tween(230, easing = FastOutSlowInEasing))
                onClose()
            }
        }

        BackHandler {
            when {
                textMode || sheetOpen || selection != null -> clearAll()
                else -> close()
            }
        }

        // The background darkens as the screenshot settles into its card.
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Bg.copy(alpha = settle.value.coerceIn(0f, 1f))) })

        // ---- The screenshot, the light, the circle, the picked text ----
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(words) {
                    val slop = 12.dp.toPx()
                    val handleReach = 30.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (closing || settle.value < 0.9f) return@awaitEachGesture
                        val start = down.position
                        // Anywhere on the picture (also where it's been moved over the bars).
                        if (!Rect(toScreen(0f, 0f), toScreen(sw, sh)).contains(start)) return@awaitEachGesture

                        // Dragging a handle of the picked text.
                        if (textMode) {
                            val hs = handles()
                            val which = when {
                                hs == null -> 0
                                (hs.first - start).getDistance() < handleReach -> 1
                                (hs.second - start).getDistance() < handleReach -> 2
                                else -> 0
                            }
                            if (which != 0) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull() ?: break
                                    if (!c.pressed) break
                                    c.consume()
                                    val near = wordNear(c.position) ?: continue
                                    if (which == 1) pickA = near.order else pickB = near.order
                                    query = Ocr.join(picked())
                                }
                                searchText(Ocr.join(picked()))
                                return@awaitEachGesture
                            }
                        }

                        // Resizing the circled box by a corner.
                        val sel = selection
                        if (sel != null && !textMode) {
                            val r = screenRect(sel)
                            val hit = listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)
                                .indexOfFirst { (it - start).getDistance() < handleReach }
                            if (hit >= 0) {
                                var cur = android.graphics.Rect(sel)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull() ?: break
                                    if (!c.pressed) break
                                    c.consume()
                                    val s = toShot(c.position)
                                    val x = s.x.roundToInt().coerceIn(0, shot.width)
                                    val y = s.y.roundToInt().coerceIn(0, shot.height)
                                    val minSide = 40
                                    // "box." spelled out: a plain "top" here is the screen's top room.
                                    val box = android.graphics.Rect(cur)
                                    when (hit) {
                                        0 -> { box.left = min(x, box.right - minSide); box.top = min(y, box.bottom - minSide) }
                                        1 -> { box.right = max(x, box.left + minSide); box.top = min(y, box.bottom - minSide) }
                                        2 -> { box.left = min(x, box.right - minSide); box.bottom = max(y, box.top + minSide) }
                                        else -> { box.right = max(x, box.left + minSide); box.bottom = max(y, box.top + minSide) }
                                    }
                                    cur = box
                                    selection = cur
                                }
                                selectRegion(cur)
                                return@awaitEachGesture
                            }
                        }

                        // Drawing, sliding across words, or tapping.
                        val firstWord = wordAt(start)
                        val points = arrayListOf(start)
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } >= 2) {
                                pinch(ev)
                                return@awaitEachGesture
                            }
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            if (!moved && (c.position - start).getDistance() > slop) moved = true
                            if (moved) {
                                c.consume()
                                if (textMode && firstWord != null) {
                                    // Already picking text: a slide from a word picks a run of words.
                                    val near = wordNear(c.position)
                                    if (near != null) pickText(firstWord.order, near.order, search = false)
                                } else {
                                    points += c.position
                                    stroke = points.toList()
                                }
                            }
                        }
                        stroke = emptyList()
                        when {
                            !moved && firstWord != null -> pickText(firstWord.order, firstWord.order, search = true)
                            !moved && textMode -> clearAll()
                            !moved -> {
                                // A tap away from text searches the area around it.
                                val s = toShot(start)
                                val half = (shot.width * 0.16f).roundToInt()
                                val r = android.graphics.Rect(s.x.roundToInt() - half, s.y.roundToInt() - half, s.x.roundToInt() + half, s.y.roundToInt() + half)
                                r.intersect(0, 0, shot.width, shot.height)
                                selectRegion(r)
                            }
                            textMode && firstWord != null -> searchText(Ocr.join(picked()))
                            else -> {
                                val inShot = points.map { toShot(it) }
                                val pad = 16f
                                val l = (inShot.minOf { it.x } - pad).coerceIn(0f, sw)
                                val t = (inShot.minOf { it.y } - pad).coerceIn(0f, sh)
                                val rr = (inShot.maxOf { it.x } + pad).coerceIn(0f, sw)
                                val b = (inShot.maxOf { it.y } + pad).coerceIn(0f, sh)
                                if (rr - l >= 24f && b - t >= 24f) {
                                    selectRegion(android.graphics.Rect(l.roundToInt(), t.roundToInt(), rr.roundToInt(), b.roundToInt()))
                                }
                            }
                        }
                    }
                },
        ) {
            val s = scaleNow()
            // The card's frame, and the (maybe zoomed) picture inside it.
            val tl = Offset(leftNow(), topNow())
            val cw = sw * s
            val ch = sh * s
            val itl = toScreen(0f, 0f)
            val radius = corner * settle.value.coerceIn(0f, 1f) * min(zoom, 1f)
            // The picture itself, wherever it's been moved and however big it is.
            val pic = Rect(itl, Size(cw * zoom, ch * zoom))
            val card = Path().apply { addRoundRect(RoundRect(pic, CornerRadius(if (zoom > 1f) 0f else radius))) }
            clipPath(card) {
                drawImage(
                    image,
                    dstOffset = IntOffset(itl.x.roundToInt(), itl.y.roundToInt()),
                    dstSize = IntSize((cw * zoom).roundToInt(), (ch * zoom).roundToInt()),
                    filterQuality = if (zoom > 1f) FilterQuality.High else FilterQuality.Medium,
                )
                // The light that runs up the screen when it opens.
                val g = glow.value
                if (g < 1f) {
                    val band = ch * 0.55f
                    val centre = tl.y + ch + band / 2 - g * (ch + band)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Glow[0].copy(alpha = 0.40f),
                            0.55f to Glow[1].copy(alpha = 0.45f),
                            0.75f to Glow[2].copy(alpha = 0.35f),
                            1f to Color.Transparent,
                            startY = centre - band / 2,
                            endY = centre + band / 2,
                        ),
                        topLeft = tl,
                        size = Size(cw, ch),
                    )
                    drawRect(Pick.copy(alpha = 0.10f * (1f - g)), topLeft = tl, size = Size(cw, ch))
                }
                val sel = selection
                if (sel != null) {
                    val r = screenRect(sel)
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(pic)
                        addRoundRect(RoundRect(r, CornerRadius(16.dp.toPx())))
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.45f))
                }
                // The translation, painted over each line of the original.
                translatedLines?.let { lines ->
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        for (line in lines) {
                            val r = screenRect(line.box)
                            val pad = 2.dp.toPx()
                            drawRoundRect(
                                Color(line.background),
                                Offset(r.left - pad, r.top - pad),
                                Size(r.width + 2 * pad, r.height + 2 * pad),
                                CornerRadius(4.dp.toPx()),
                            )
                            paint.color = line.ink
                            paint.textScaleX = 1f
                            paint.textSize = r.height * 0.8f
                            val width = paint.measureText(line.translated).coerceAtLeast(1f)
                            if (width > r.width) {
                                // Longer than the original: narrower letters first, then smaller ones.
                                paint.textScaleX = (r.width / width).coerceAtLeast(0.7f)
                                val still = paint.measureText(line.translated)
                                if (still > r.width) paint.textSize *= r.width / still
                            }
                            val fm = paint.fontMetrics
                            val baseline = r.center.y - (fm.ascent + fm.descent) / 2f
                            canvas.nativeCanvas.drawText(line.translated, r.left, baseline, paint)
                        }
                    }
                }
                // Picked words, highlighted like selected text.
                for (wd in picked()) {
                    val r = screenRect(wd.box)
                    drawRoundRect(Pick.copy(alpha = 0.50f), r.topLeft, r.size, CornerRadius(3.dp.toPx()))
                }
            }
            // The text handles.
            handles()?.let { (a, b) ->
                val r = 9.dp.toPx()
                for (c in listOf(a, b)) {
                    drawLine(Pick, Offset(c.x, c.y - r - 2.dp.toPx()), Offset(c.x, c.y), strokeWidth = 2.dp.toPx())
                    drawCircle(Pick, radius = r, center = c)
                }
            }
            // The circled part: a soft turning glow and a white frame with corner handles.
            val sel = selection
            if (sel != null) {
                val r = screenRect(sel)
                val frame = Path().apply { addRoundRect(RoundRect(r, CornerRadius(16.dp.toPx()))) }
                // The frame stays still; only its glow's colours run around it.
                val glowBrush = object : androidx.compose.ui.graphics.ShaderBrush() {
                    override fun createShader(size: Size): androidx.compose.ui.graphics.Shader =
                        androidx.compose.ui.graphics.SweepGradientShader(r.center, Glow).apply {
                            setLocalMatrix(android.graphics.Matrix().apply { setRotate(turn, r.center.x, r.center.y) })
                        }
                }
                drawPath(frame, glowBrush, alpha = 0.6f, style = Stroke(width = 9.dp.toPx()))
                drawPath(frame, Color.White, style = Stroke(width = 3.dp.toPx()))
                for (c in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)) drawCircle(Color.White, radius = 5.dp.toPx(), center = c)
            }
            // The line being drawn, glowing.
            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(
                    path,
                    Brush.linearGradient(Glow.dropLast(1), start = stroke.first(), end = stroke.last()),
                    alpha = 0.55f,
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(path, Color.White, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // ---- Top bar ----
        AnimatedVisibility(
            visible = settle.value > 0.6f && !closing,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = with(density) { statusTop.toDp() })
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Outlined.Close, "Close") { close() }
                Spacer(Modifier.weight(1f))
                Text("SonderCircle", color = Text1, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                // ⋮: SonderCircle's settings.
                RoundButton(Icons.Outlined.MoreVert, "Settings") {
                    runCatching { com.verisonder.sonderweb.data.CircleApp.open(ctx) }
                        .onFailure { Toast.makeText(ctx, "Couldn't open SonderCircle's settings", Toast.LENGTH_SHORT).show() }
                    close()
                }
            }
        }

        // ---- Over the picked text: Copy | Translate. Over a circled part: Select text ----
        var popupW by remember { mutableIntStateOf(0) }
        val showPopup = textMode && pickA >= 0
        val sel = selection
        val inside = remember(sel, words) { sel?.let { wordsIn(it) } ?: emptyList() }
        if ((showPopup || (sel != null && inside.isNotEmpty())) && settle.value > 0.9f) {
            Row(
                Modifier
                    .offset {
                        val anchor = if (showPopup) {
                            val p = picked()
                            if (p.isEmpty()) {
                                Rect(Offset.Zero, Size.Zero)
                            } else {
                                val l = p.minOf { it.box.left }
                                val t = p.minOf { it.box.top }
                                val rr = p.maxOf { it.box.right }
                                val b = p.maxOf { it.box.bottom }
                                screenRect(android.graphics.Rect(l, t, rr, b))
                            }
                        } else {
                            screenRect(sel!!)
                        }
                        val x = (anchor.center.x - popupW / 2f).coerceIn(8.dp.toPx(), (w - popupW - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
                        val y = (anchor.top - 52.dp.toPx()).coerceAtLeast(top - 8.dp.toPx())
                        IntOffset(x.roundToInt(), y.roundToInt())
                    }
                    .onSizeChanged { popupW = it.width }
                    .clip(RoundedCornerShape(22.dp))
                    .background(PopupBg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showPopup) {
                    PopupItem("Copy") { copy(Ocr.join(picked())) }
                    Box(Modifier.width(1.dp).height(20.dp).background(Text2.copy(alpha = 0.4f)))
                    PopupItem("Translate") { translate(Ocr.join(picked())) }
                } else {
                    PopupItem("Select text") {
                        val orders = inside.map { it.order }
                        pickText(orders.min(), orders.max(), search = true)
                    }
                }
            }
        }

        // ---- Translated: say so, and a tap shows the original again ----
        AnimatedVisibility(
            visible = (translatedLines != null || translating) && !closing,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { statusTop.toDp() } + 60.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PopupBg)
                    .clickable(enabled = !translating) { translatedLines = null }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Translate, null, tint = Text1, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (translating) "Translating…" else "Translated · Show original",
                    color = Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ---- Search field at the bottom (before anything is searched) ----
        AnimatedVisibility(
            visible = settle.value > 0.6f && !sheetOpen && !closing,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .padding(bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + 14.dp)
                    .padding(horizontal = 14.dp),
            ) {
                WidgetBar(query, onQuery = { query = it }, onSearch = { searchText(query) }, onMic = speak) {
                    translateScreen()
                }
            }
        }

        // ---- Results ----
        if (sheetOpen || sheetY.value < h - 1f) {
            Box(
                Modifier
                    .offset { IntOffset(0, sheetY.value.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { sheetFull.toDp() })
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(pageTone),
            ) {
                // The top of the sheet: a handle to drag it, and "open in a tab". The page's own
                // search box is the only search field (no second one above it).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(h) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val v = sheetY.value
                                    scope.launch {
                                        when {
                                            v > h - sheetPeek + 110.dp.toPx() -> clearAll()
                                            v < h - (sheetPeek + sheetFull) / 2 -> sheetY.animateTo(h - sheetFull, snap)
                                            else -> sheetY.animateTo(h - sheetPeek, snap)
                                        }
                                    }
                                },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { sheetY.snapTo((sheetY.value + amount).coerceIn(h - sheetFull, h)) }
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Text2.copy(alpha = 0.6f)),
                    )
                    Row(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(Icons.AutoMirrored.Outlined.OpenInNew, "Open in a tab", tint = if (pageTone.luminance() > 0.5f) Color(0xFF202124) else Text1) {
                            val url = pageUrl
                            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .setClass(ctx, com.verisonder.sonderweb.ui.MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                                close()
                            }
                        }
                    }
                }
                AndroidView(
                    factory = { pageView.also { v -> (v.parent as? android.view.ViewGroup)?.removeView(v) } },
                    modifier = Modifier
                        .padding(top = 44.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The search field at the bottom, drawn like your home-screen search widget (its colours, its
 * logo, the bold "Search"), with the microphone and translate as its two buttons.
 */
@Composable
private fun WidgetBar(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onMic: () -> Unit,
    onTranslate: () -> Unit,
) {
    val ctx = LocalContext.current
    val (look, colors) = remember { com.verisonder.sonderweb.ui.SearchWidget.currentLook(ctx) }
    val bg = Color(colors[0] or 0xFF000000.toInt())
    val bgAlpha = ((colors[0] ushr 24) / 255f).let { if (it == 0f) 1f else it }
    val text = Color(colors[1])
    val icon = Color(if (look.mono) colors[2] else colors[3])
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(bg.copy(alpha = bgAlpha))
            .padding(start = 13.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look.logo) {
            Box(Modifier.size(24.dp)) {
                when {
                    look.arcs -> Icon(
                        androidx.compose.ui.res.painterResource(com.verisonder.sonderweb.R.drawable.widget_logo_arcs),
                        null,
                        tint = icon,
                        modifier = Modifier.fillMaxSize(),
                    )
                    look.mono || look.tint != null -> {
                        Icon(androidx.compose.ui.res.painterResource(com.verisonder.sonderweb.R.drawable.widget_logo_disc), null, tint = Color(colors[2]), modifier = Modifier.fillMaxSize())
                        Icon(androidx.compose.ui.res.painterResource(com.verisonder.sonderweb.R.drawable.widget_logo_marks), null, tint = bg, modifier = Modifier.fillMaxSize())
                    }
                    else -> androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.verisonder.sonderweb.R.drawable.widget_logo_color),
                        null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty() && look.label) Text("Search", color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(width = 50.dp, height = 40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.ui.res.painterResource(com.verisonder.sonderweb.R.drawable.widget_mic), "Speak", tint = icon, modifier = Modifier.size(width = 26.dp, height = 26.dp))
        }
        Box(
            Modifier.size(width = 50.dp, height = 40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onTranslate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Translate, "Translate", tint = icon, modifier = Modifier.size(24.dp))
        }
    }
}

/** The search field at the top of the results: what's searched (editable), a search icon and the microphone. */
@Composable
private fun QueryField(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onMic: () -> Unit,
    compact: Boolean = false,
    fieldColor: Color = Field,
    trailing: @Composable () -> Unit = {},
) {
    // Readable on a light page too.
    val light = compact && fieldColor.luminance() > 0.5f
    val main = if (light) Color(0xFF202124) else Text1
    val faint = if (light) Color(0xFF5F6368) else Text2
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 50.dp else 58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(if (compact) fieldColor else Panel)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search or ask about your screen", color = faint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = main, fontSize = 15.sp),
                cursorBrush = SolidColor(Pick),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RoundButton(Icons.Outlined.Mic, "Speak", tint = main, onClick = onMic)
        trailing()
    }
}

@Composable
private fun PopupItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Text1,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, tint: Color = Text1, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The circled part of [shot], as a JPEG small enough to send quickly. */
private fun crop(shot: Bitmap, r: android.graphics.Rect): ByteArray {
    val box = android.graphics.Rect(r)
    box.intersect(0, 0, shot.width, shot.height)
    var bmp = Bitmap.createBitmap(shot, box.left, box.top, box.width().coerceAtLeast(1), box.height().coerceAtLeast(1))
    val side = max(bmp.width, bmp.height)
    if (side > 1280) {
        val k = 1280f / side
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
    }
    var quality = 88
    while (true) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (out.size() <= 300_000 || quality <= 40) return out.toByteArray()
        quality -= 12
    }
}

/** A field on [c]: a little lighter on a dark page, a little darker on a light one. */
private fun lift(c: Color): Color {
    val dark = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f < 0.5f
    return if (dark) {
        Color(c.red + (1f - c.red) * 0.09f, c.green + (1f - c.green) * 0.09f, c.blue + (1f - c.blue) * 0.09f)
    } else {
        Color(c.red * 0.93f, c.green * 0.93f, c.blue * 0.93f)
    }
}
