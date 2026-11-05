package com.verisonder.sonderweb.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.AddressInput
import com.verisonder.sonderweb.core.Bangs
import com.verisonder.sonderweb.core.Resolved
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.SiteIcons
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Typing an address or a search: a sheet that sits on the keyboard (or under the top edge),
 * a big round search field, and under it what you might want: the search itself, !bangs,
 * then bookmarks and history, each with its site's icon. Nothing typed yet: your latest sites.
 */
@Composable
fun SearchSheet(ui: UiState, tab: Tab?, top: Boolean) {
    val p = LocalPalette.current
    // Swiping back (once the keyboard is down): the sheet shrinks towards its edge and follows
    // the finger, like the phone's own sheets. Let go to close it, swipe back the other way to stay.
    val back = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    PredictiveBackHandler { events ->
        try {
            events.collect { e ->
                fromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                back.snapTo(e.progress)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { back.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 600f)) }
            throw e
        }
        gone.animateTo(1f, tween(140))
        ui.editing = false
    }
    // Opening: the sheet rises out of the bar and settles, then its rows follow one after another.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 300f)) }
    val radius = 32.dp
    Column(
        Modifier
            .fillMaxWidth()
            // Animated values are only read here, frame by frame, never in composition.
            .graphicsLayer {
                val g = back.value
                val e = enter.value
                val s = (1f - 0.1f * g) * (0.9f + 0.1f * e)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, if (top) 0f else 1f)
                translationX = (if (fromLeft) 1f else -1f) * size.width * 0.04f * g
                translationY = (1f - e) * 48.dp.toPx() * (if (top) -1f else 1f)
                alpha = e.coerceIn(0f, 1f) * (1f - gone.value)
                // The edge on the keyboard side rounds off as the sheet lifts away from it.
                val full = radius.toPx()
                val far = CornerSize(full * g)
                val near = CornerSize(full)
                shape = if (top) {
                    RoundedCornerShape(far, far, near, near)
                } else {
                    RoundedCornerShape(near, near, far, far)
                }
                clip = true
                shadowElevation = 16.dp.toPx()
            }
            .background(p.card)
            // Keyboard down: keep clear of the camera hole (top) or the gesture bar (bottom).
            .then(
                if (top) {
                    Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                } else {
                    Modifier.navigationBarsPadding()
                },
            )
            // Taps on the sheet itself don't fall through to the page behind it.
            .clickable(interactionSource = null, indication = null) { }
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        SearchField(ui, tab)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                // Rows coming and going while you type: the sheet grows and shrinks smoothly.
                .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f))
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (tab != null) SheetSuggestions(ui, tab) { enter.value }
        }
    }
}

@Composable
private fun SearchField(ui: UiState, tab: Tab?) {
    val p = LocalPalette.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Voice search: the phone's own speech input; what it hears is searched right away.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) submit(ui, heard)
    }
    val fieldColor = if (p.dark) p.line else p.field
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(fieldColor)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (ui.query.text.isEmpty()) Text("Search…", color = p.faint, fontSize = 17.sp, maxLines = 1)
            IncognitoKeyboard(tab?.isPrivate == true) {
                BasicTextField(
                    value = ui.query,
                    onValueChange = { ui.query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit(ui, ui.query.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (ui.query.text.isNotEmpty()) {
            IconButtonSmall(Icons.Outlined.Close, "Clear", size = 44, tint = p.mute) { ui.query = TextFieldValue("") }
        }
        IconButtonSmall(Icons.Outlined.Mic, "Voice search", size = 44, tint = p.mute) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search")
            try {
                voice.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Browser.host?.toast("No voice input on this phone")
            }
        }
    }
}

@Composable
private fun SheetSuggestions(ui: UiState, tab: Tab, enter: () -> Float) {
    var row = 0
    val p = LocalPalette.current
    val text = ui.query.text
    val resolved = remember(text) { AddressInput.resolve(text, Browser.prefs.engine()) }
    // Private tabs are kept apart from your normal history and bookmarks.
    val rows = remember(text, tab.isPrivate) {
        when {
            tab.isPrivate -> emptyList()
            text.isBlank() -> Browser.db.history("", 40).distinctBy { it.url }.take(5).map { it.url to it.title }
            else -> Browser.db.suggest(text)
        }
    }
    val bangHints = remember(text) {
        val first = text.trimStart()
        if (first.startsWith("!") && !first.contains(' ')) {
            val t = first.drop(1).lowercase()
            Bangs.all.filter { it.trigger.startsWith(t) }.distinctBy { it.trigger }.take(6)
        } else {
            emptyList()
        }
    }

    if (resolved != null) {
        val (title, icon) = when (resolved) {
            is Resolved.Address -> UrlText.editable(resolved.url) to Icons.Outlined.Language
            is Resolved.BangSearch -> {
                val name = Bangs.find(resolved.bang)?.name ?: "!" + resolved.bang
                (if (resolved.query.isEmpty()) name else resolved.query + " · " + name) to Icons.Outlined.Search
            }
            is Resolved.Search -> resolved.query to Icons.Outlined.Search
        }
        SheetRow(title, row++, enter, { Glyph(icon, tint = p.accent) }) { submit(ui, text) }
    }
    bangHints.forEach { b ->
        SheetRow("!" + b.trigger + " · " + b.name, row++, enter, { Glyph(Icons.Outlined.Search) }) {
            val t = "!" + b.trigger + " "
            ui.query = TextFieldValue(t, TextRange(t.length))
        }
    }
    rows.forEach { (url, title) ->
        val bookmarked = remember(url) { Browser.db.isBookmarked(url) }
        SheetRow(
            title.ifBlank { UrlText.host(url) },
            row++,
            enter,
            { SiteGlyph(url, if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.History) },
        ) {
            ui.editing = false
            Browser.load(tab, url)
        }
    }
}

@Composable
private fun SheetRow(title: String, index: Int, enter: () -> Float, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            // Each row comes in a beat after the one before it.
            .graphicsLayer {
                val t = ((enter() - 0.2f - 0.07f * index) / 0.5f).coerceIn(0f, 1f)
                alpha = t
                translationY = (1f - t) * 14.dp.toPx()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(18.dp))
        Text(title, color = p.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: androidx.compose.ui.graphics.Color? = null) {
    Icon(icon, null, tint = tint ?: LocalPalette.current.mute, modifier = Modifier.size(22.dp))
}

/** The site's own icon in a small white circle (like the launcher's search), else a plain glyph. */
@Composable
private fun SiteGlyph(url: String, fallback: ImageVector) {
    val ctx = LocalContext.current
    val host = UrlText.host(url)
    LaunchedEffect(host) { SiteIcons.load(ctx, host) }
    val bmp = SiteIcons.icons[host]
    if (bmp == null) {
        Glyph(fallback)
        return
    }
    val image = remember(bmp) { bmp.asImageBitmap() }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(image, null, modifier = Modifier.size(18.dp))
    }
}
