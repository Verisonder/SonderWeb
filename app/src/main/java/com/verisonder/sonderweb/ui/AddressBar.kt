package com.verisonder.sonderweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import org.mozilla.geckoview.GeckoSession
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Bar
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Delegates
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.browser.Ublock
import com.verisonder.sonderweb.core.AddressInput
import com.verisonder.sonderweb.core.Bangs
import com.verisonder.sonderweb.core.Resolved
import com.verisonder.sonderweb.core.UrlText

fun startEditing(ui: UiState, tab: Tab?) {
    val text = UrlText.editable(tab?.url)
    ui.query = TextFieldValue(text, TextRange(0, text.length))
    ui.editing = true
}

fun submit(ui: UiState, text: String) {
    if (text.isBlank()) return
    ui.editing = false
    Bar.collapse()
    Browser.go(text)
}

@Composable
fun AddressBar(ui: UiState, tab: Tab?, top: Boolean) {
    val p = LocalPalette.current
    val floating = remember(ui.themeVersion) { Browser.prefs.barFloating }
    val insets = if (top) {
        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
    } else {
        Modifier.navigationBarsPadding()
    }
    val shape = RoundedCornerShape(28.dp)
    // Docked: the bar's colour reaches the screen edge and its contents keep clear of the camera
    // hole (top) or the gesture bar (bottom). Floating: a rounded pill with the page around it.
    Column(
        if (floating) {
            Modifier
                .fillMaxWidth()
                .then(insets)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(p.card)
        } else {
            Modifier
                .fillMaxWidth()
                .background(p.bar)
                .then(insets)
        },
    ) {
        val showLoad = tab != null && tab.loading && !tab.isBlank && !ui.editing
        // Floating: keep the line off the rounded ends so it never shows as a stray corner.
        val lineInset = if (floating) Modifier.padding(horizontal = 24.dp) else Modifier
        if (!top) {
            if (showLoad) Box(lineInset) { LoadLine(tab!!.progress, p.accent) } else Spacer(Modifier.height(2.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.finding && tab != null && !ui.editing) {
                FindBar(ui, tab)
            } else if (ui.editing) {
                IconButtonSmall(Icons.AutoMirrored.Outlined.ArrowBack, "Cancel", size = 44) { ui.editing = false }
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.field)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditField(ui, Modifier.weight(1f))
                    if (ui.query.text.isNotEmpty()) {
                        IconButtonSmall(Icons.Outlined.Close, "Clear", size = 32, tint = p.faint) { ui.query = TextFieldValue("") }
                    }
                }
                Spacer(Modifier.width(8.dp))
            } else {
                if (tab == null || tab.isBlank) {
                    // The start page has its own search box: here only tabs and the menu.
                    Spacer(Modifier.weight(1f))
                } else {
                    IconButtonSmall(Icons.Outlined.Home, "Home", size = 44, tint = p.mute) { Browser.home(tab) }
                    val pop = remember { NewTabPop() }
                    Box(Modifier.weight(1f)) {
                        Omnibox(ui, tab, Modifier.fillMaxWidth(), pop, top)
                        NewTabBubble(pop, top)
                    }
                }
                TabCountButton(
                    Browser.tabs.count { it.isPrivate == (tab?.isPrivate ?: false) },
                    top = top,
                ) {
                    tab?.let { Delegates.capture(it) }
                    ui.panel = Panel.Tabs
                }
                Box {
                    IconButtonSmall(Icons.Outlined.MoreVert, "Menu", size = 44, tint = p.mute) { ui.menu = true }
                    if (com.verisonder.sonderweb.data.AppUpdate.available != null) UpdateDot(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
                    if (tab != null) BrowserMenu(ui, tab, top)
                }
            }
        }
        if (top) {
            if (showLoad) Box(lineInset) { LoadLine(tab!!.progress, p.accent) } else Spacer(Modifier.height(2.dp))
        }
    }
}

/** The start page has its own search box: only the tabs button and the menu, bottom right. */
@Composable
fun StartControls(ui: UiState, tab: Tab, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(p.field)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCountButton(
            Browser.tabs.count { it.isPrivate == tab.isPrivate },
            top = false,
        ) { ui.panel = Panel.Tabs }
        Box {
            IconButtonSmall(Icons.Outlined.MoreVert, "Menu", size = 44, tint = p.mute) { ui.menu = true }
                    if (com.verisonder.sonderweb.data.AppUpdate.available != null) UpdateDot(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
            BrowserMenu(ui, tab, top = false)
        }
    }
}

/** The start page's (+) at the bottom centre, in the same style as the tabs and menu pill:
 *  tap it to search or type an address. */
@Composable
fun StartSearchButton(ui: UiState, tab: Tab, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(p.field)
            .clickable { startEditing(ui, tab) }
            .size(width = 68.dp, height = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Add, "Search", tint = p.text, modifier = Modifier.size(26.dp))
    }
}

/** Find in page: type, see "3/12", step up and down. */
@Composable
private fun RowScope.FindBar(ui: UiState, tab: Tab) {
    val p = LocalPalette.current
    val finder = tab.session.finder
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun seek(backwards: Boolean, fresh: Boolean) {
        val q = if (fresh) ui.findQuery.ifEmpty { null } else null
        if (fresh && q == null) {
            finder.clear()
            ui.findResult = ""
            return
        }
        finder.find(q, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0).accept({ r ->
            Browser.main.post {
                ui.findResult = when {
                    r == null || !r.found -> if (ui.findQuery.isEmpty()) "" else "0/0"
                    else -> "${r.current}/${r.total}"
                }
            }
        }, { _ -> })
    }
    LaunchedEffect(ui.findQuery) { seek(backwards = false, fresh = true) }

    Row(
        Modifier
            .weight(1f)
            .height(40.dp)
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (ui.findQuery.isEmpty()) Text("Find in page", color = p.faint, fontSize = 15.sp)
            IncognitoKeyboard(Browser.current?.isPrivate == true) {
                BasicTextField(
                    value = ui.findQuery,
                    onValueChange = { ui.findQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { seek(backwards = false, fresh = false) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (ui.findResult.isNotEmpty()) Text(ui.findResult, color = p.faint, fontSize = 13.sp)
    }
    IconButtonSmall(Icons.Outlined.KeyboardArrowUp, "Previous", size = 40, tint = p.mute) { seek(backwards = true, fresh = false) }
    IconButtonSmall(Icons.Outlined.KeyboardArrowDown, "Next", size = 40, tint = p.mute) { seek(backwards = false, fresh = false) }
    IconButtonSmall(Icons.Outlined.Close, "Close", size = 40, tint = p.mute) {
        finder.clear()
        ui.finding = false
    }
}

/** The rounded address field when not typing: site lock, the site's name, uBlock Origin's count. */
@Composable
private fun Omnibox(ui: UiState, tab: Tab?, modifier: Modifier, pop: NewTabPop? = null, top: Boolean = false) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .then(if (pop != null) Modifier.holdForNewTab(pop, top, tab?.isPrivate ?: false) { startEditing(ui, tab) } else Modifier.clickable { startEditing(ui, tab) })
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            tab == null || tab.isBlank -> if (tab?.isPrivate == true) Icons.Outlined.VisibilityOff else Icons.Outlined.Search
            tab.isPrivate -> Icons.Outlined.VisibilityOff
            tab.secure -> Icons.Outlined.Lock
            else -> Icons.Outlined.Info
        }
        Icon(icon, null, tint = p.faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        val host = UrlText.host(tab?.url)
        Text(
            host.ifEmpty { "Search or type address" },
            color = if (host.isEmpty()) p.faint else p.text,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (tab != null && !tab.isBlank && Ublock.extension != null) ShieldButton(tab)
    }
}

@Composable
private fun EditField(ui: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (ui.query.text.isEmpty()) Text("Search or type address", color = p.faint, fontSize = 15.sp, maxLines = 1)
        IncognitoKeyboard(Browser.current?.isPrivate == true) {
            BasicTextField(
                value = ui.query,
                onValueChange = { ui.query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 15.sp),
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
}

/** uBlock Origin's button: a small shield and the number it blocked on this page. */
@Composable
private fun ShieldButton(tab: Tab) {
    val p = LocalPalette.current
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { Ublock.showPopup(tab) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Shield, "uBlock Origin", tint = p.mute, modifier = Modifier.size(16.dp))
        if (tab.blockedBadge.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                tab.blockedBadge,
                color = p.mute,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(lineHeight = 12.sp, lineHeightStyle = Centered),
            )
        }
    }
}

/**
 * Chromium's tab button: a rounded square with the number of tabs.
 * Tap: the tab switcher. Hold: a list of your open tabs pops out; slide onto one and let go to
 * switch to it, all in one movement.
 */
@Composable
fun TabCountButton(count: Int, top: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onClick)
    val picker = rememberTabPicker(null, top)

    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .onSizeChanged { picker.anchorH = it.height.toFloat() }
            .pointerInput(picker) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    // Tap or hold?
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
                        if (!moved) tap()
                        return@awaitEachGesture
                    }
                    if (moved) return@awaitEachGesture
                    pickTab(picker, haptics)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.8.dp, p.mute, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) ":D" else count.toString(),
                color = p.mute,
                fontSize = if (count > 9) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = TextStyle(lineHeight = 11.sp, lineHeightStyle = Centered),
            )
        }
        TabPickerPopup(picker, end = true)
    }
}

/** Holding the address: a "+" pops out; slide onto it and let go for a new tab. */
class NewTabPop {
    var holding by mutableStateOf(false)
    var armed by mutableStateOf(false)
}

/** The held part of the gesture: slide up (down when the bar is on top) onto the "+"; elsewhere cancels. */
private suspend fun AwaitPointerEventScope.slideToNewTab(
    pop: NewTabPop,
    top: Boolean,
    reach: Float,
    startY: Float,
    haptics: HapticFeedback,
    onNewTab: () -> Unit,
) {
    pop.holding = true
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    while (true) {
        val c = awaitPointerEvent().changes.first()
        val dy = c.position.y - startY
        val over = if (top) dy > reach * 0.6f else dy < -reach * 0.6f
        if (over != pop.armed) {
            pop.armed = over
            if (over) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        c.consume()
        if (!c.pressed) break
    }
    if (pop.armed) onNewTab()
    pop.armed = false
    pop.holding = false
}

/** The "+" that pops out above the address (below it when the bar is on top). */
@Composable
fun NewTabBubble(pop: NewTabPop, top: Boolean) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val shown by animateFloatAsState(if (pop.holding) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "pop")
    val lit by animateFloatAsState(if (pop.armed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "lit")
    if (shown < 0.01f) return
    val offset = with(density) { (if (top) 60.dp else (-60).dp).roundToPx() }
    Popup(
        alignment = Alignment.Center,
        offset = IntOffset(0, offset),
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val k = (0.4f + 0.6f * shown) * (1f + 0.15f * lit)
                    scaleX = k
                    scaleY = k
                    alpha = shown.coerceIn(0f, 1f)
                }
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(lerp(p.field, p.accent, lit)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "New tab", tint = lerp(p.text, p.onAccent, lit), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun IconButtonSmall(icon: ImageVector, label: String, size: Int = 36, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint ?: p.text, modifier = Modifier.size(20.dp))
    }
}

/** What typing in the bar offers: the search itself, !bangs, bookmarks and history. */
@Composable
fun Suggestions(ui: UiState, tab: Tab, top: Boolean) {
    val p = LocalPalette.current
    val text = ui.query.text
    val resolved = remember(text) { AddressInput.resolve(text, Browser.prefs.engine()) }
    val rows = remember(text, tab.isPrivate) {
        // Private tabs are kept apart from your normal history and bookmarks.
        if (text.isBlank() || tab.isPrivate) emptyList() else Browser.db.suggest(text)
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

    LazyColumn(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .clickable(interactionSource = null, indication = null) { },
            reverseLayout = !top,
            // Bar at the bottom: the list grows up from just above the bar, not from the top of the screen.
            verticalArrangement = if (top) Arrangement.Top else Arrangement.Bottom,
        ) {
            if (resolved != null) {
                item {
                    val (title, sub, icon) = when (resolved) {
                        is Resolved.Address -> Triple(UrlText.editable(resolved.url), "Go to address", Icons.Outlined.Language)
                        is Resolved.BangSearch -> Triple(
                            resolved.query.ifEmpty { Bangs.find(resolved.bang)?.name ?: "!" + resolved.bang },
                            "!" + resolved.bang + " · " + (Bangs.find(resolved.bang)?.name ?: "via DuckDuckGo"),
                            Icons.Outlined.Search,
                        )
                        is Resolved.Search -> Triple(resolved.query, "Search with " + Browser.prefs.engine().name, Icons.Outlined.Search)
                    }
                    SuggestionRow(icon, title, sub, accent = true) { submit(ui, text) }
                }
            }
            items(bangHints, key = { "b" + it.trigger }) { b ->
                SuggestionRow(Icons.Outlined.Search, "!" + b.trigger, b.name, fill = true) {
                    val t = "!" + b.trigger + " "
                    ui.query = TextFieldValue(t, TextRange(t.length))
                }
            }
            items(rows, key = { "r" + it.first }) { (url, title) ->
                val bookmarked = remember(url) { Browser.db.isBookmarked(url) }
                SuggestionRow(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.History,
                    title.ifBlank { UrlText.host(url) },
                    UrlText.editable(url),
                    fill = true,
                    onFill = {
                        val t = UrlText.editable(url)
                        ui.query = TextFieldValue(t, TextRange(t.length))
                    },
                ) {
                    ui.editing = false
                    Browser.load(tab, url)
                }
            }
    }
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    title: String,
    sub: String,
    accent: Boolean = false,
    fill: Boolean = false,
    onFill: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (accent) p.accent else p.field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (accent) p.onAccent else p.mute, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fill && onFill != null) {
            IconButtonSmall(Icons.Outlined.NorthWest, "Edit", tint = p.faint) { onFill() }
        }
    }
}

/**
 * Hold the address and a list of your open tabs pops out; slide onto one and let go to switch
 * to it, all in one movement (like holding the tabs button for a new tab).
 */
class TabPicker(val top: Boolean, val rowPx: Float, val gapPx: Float, val padPx: Float) {
    var open by mutableStateOf(false)
    var tabs by mutableStateOf<List<Tab>>(emptyList())
    var hover by mutableStateOf(-1)
    var anchorH by mutableStateOf(0f)
    val listH get() = tabs.size * rowPx + 2 * padPx

    /** Which row is under the finger ([y] relative to the address field), or -1. */
    fun rowAt(y: Float): Int {
        val listTop = if (top) anchorH + gapPx else -(gapPx + listH)
        val i = kotlin.math.floor((y - listTop - padPx) / rowPx).toInt()
        return if (i in tabs.indices) i else -1
    }
}

private const val PICKER_MAX = 7

@Composable
fun rememberTabPicker(tab: Tab?, top: Boolean): TabPicker {
    val d = LocalDensity.current
    return remember(top, d) { with(d) { TabPicker(top, 52.dp.toPx(), 10.dp.toPx(), 6.dp.toPx()) } }
}

/** The address: tap to type; hold, slide onto the "+" that pops out and let go for a new tab. */
private fun Modifier.holdForNewTab(pop: NewTabPop, top: Boolean, isPrivate: Boolean, onTap: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val reach = with(LocalDensity.current) { 44.dp.toPx() }
    this
        .pointerInput(pop, top, isPrivate) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown()
                var moved = false
                var released = false
                var consumedUp = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val c = awaitPointerEvent().changes.first()
                        if ((c.position - down.position).getDistance() > slop) moved = true
                        if (!c.pressed) {
                            released = true
                            consumedUp = c.isConsumed
                            break
                        }
                    }
                }
                if (released) {
                    // A tap (not one the uBlock shield inside already took) opens the address for typing.
                    if (!moved && !consumedUp) tap()
                    return@awaitEachGesture
                }
                if (moved) return@awaitEachGesture
                slideToNewTab(pop, top, reach, down.position.y, haptics) { Browser.newTab(null, isPrivate = isPrivate) }
            }
        }
}

/** The held part of the gesture: the list pops out, slide onto a tab and let go to switch to it. */
private suspend fun AwaitPointerEventScope.pickTab(picker: TabPicker, haptics: HapticFeedback) {
    val cur = Browser.current
    val same = Browser.tabs.filter { it.isPrivate == (cur?.isPrivate ?: false) }
    val at = same.indexOf(cur).coerceAtLeast(0)
    val from = (at - PICKER_MAX / 2).coerceIn(0, (same.size - PICKER_MAX).coerceAtLeast(0))
    picker.tabs = same.drop(from).take(PICKER_MAX)
    picker.hover = -1
    picker.open = true
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    while (true) {
        val c = awaitPointerEvent().changes.first()
        val i = picker.rowAt(c.position.y)
        if (i != picker.hover) {
            picker.hover = i
            if (i >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        c.consume()
        if (!c.pressed) break
    }
    picker.tabs.getOrNull(picker.hover)?.let { if (it !== Browser.current) Browser.select(it) }
    picker.open = false
    picker.hover = -1
}

@Composable
fun TabPickerPopup(picker: TabPicker, end: Boolean = false) {
    val p = LocalPalette.current
    val show by animateFloatAsState(if (picker.open) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 520f), label = "picker")
    if (show < 0.01f || picker.tabs.isEmpty()) return
    val y = if (picker.top) picker.anchorH + picker.gapPx else -(picker.gapPx + picker.listH)
    val edge = with(LocalDensity.current) { 12.dp.roundToPx() }
    // Room around the list inside its popup window, so the shadow is never cut off.
    val room = with(LocalDensity.current) { 24.dp.roundToPx() }
    // Opened from the tabs button at the right: the list sits at the screen's right edge, under
    // your thumb; from the address it lines up with the address's left edge.
    val place = remember(end, y, edge, room) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ): IntOffset {
                val x = if (end) windowSize.width - popupContentSize.width - edge + room else anchorBounds.left - room
                return IntOffset(x, anchorBounds.top + y.roundToInt() - room)
            }
        }
    }
    Popup(
        popupPositionProvider = place,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .padding(with(LocalDensity.current) { room.toDp() })
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (end) 0.85f else 0.5f, if (picker.top) 0f else 1f)
                    val k = 0.85f + 0.15f * show
                    scaleX = k
                    scaleY = k
                    alpha = show.coerceIn(0f, 1f)
                }
                .width(300.dp)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(p.card)
                .padding(vertical = 6.dp),
        ) {
            picker.tabs.forEachIndexed { i, t ->
                val lit = i == picker.hover
                val bg by androidx.compose.animation.animateColorAsState(if (lit) p.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent, tween(90), label = "row")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val host = UrlText.host(t.url)
                    PickerIcon(t, host)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (t.isBlank) "New tab" else t.displayTitle,
                            color = if (lit) p.accent else p.text,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!t.isBlank) Text(host, color = p.faint, fontSize = 12.sp, maxLines = 1)
                    }
                    if (t === Browser.current) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent))
                    }
                }
            }
        }
    }
}

/** A tab's icon in the picker: the site's own icon, or a plain one for new and private tabs. */
@Composable
private fun PickerIcon(t: Tab, host: String) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val showSite = !t.isBlank && !t.isPrivate && host.isNotEmpty()
    if (showSite) LaunchedEffect(host) { com.verisonder.sonderweb.data.SiteIcons.load(ctx, host) }
    val bmp = if (showSite) com.verisonder.sonderweb.data.SiteIcons.icons[host] else null
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                remember(bmp) { bmp.asImageBitmap() },
                null,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)),
            )
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(p.field),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (t.isBlank) Icons.Outlined.Add else Icons.Outlined.Public,
                    null,
                    tint = p.mute,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
