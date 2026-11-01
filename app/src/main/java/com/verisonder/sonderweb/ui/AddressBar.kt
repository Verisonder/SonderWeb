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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
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
        val showLoad = tab != null && tab.loading && !ui.editing
        if (!top) {
            if (showLoad) LoadLine(tab!!.progress, p.accent) else Spacer(Modifier.height(2.dp))
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
                    Omnibox(ui, tab, Modifier.weight(1f))
                }
                TabCountButton(
                    Browser.tabs.count { it.isPrivate == (tab?.isPrivate ?: false) },
                    top = top,
                    onNewTab = { Browser.newTab(null, isPrivate = tab?.isPrivate ?: false) },
                ) {
                    tab?.let { Delegates.capture(it) }
                    ui.panel = Panel.Tabs
                }
                Box {
                    IconButtonSmall(Icons.Outlined.MoreVert, "Menu", size = 44, tint = p.mute) { ui.menu = true }
                    if (tab != null) BrowserMenu(ui, tab, top)
                }
            }
        }
        if (top) {
            if (showLoad) LoadLine(tab!!.progress, p.accent) else Spacer(Modifier.height(2.dp))
        }
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
private fun Omnibox(ui: UiState, tab: Tab?, modifier: Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .clickable { startEditing(ui, tab) }
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

/** Chromium's tab button: a rounded square with the number of tabs, centred. */
/**
 * Chromium's tab button: a rounded square with the number of tabs.
 * Tap: the tab switcher. Hold, then slide up (down when the bar is on top) onto the "+" that
 * pops out, and let go: a new tab. One continuous gesture; letting go elsewhere cancels.
 */
@Composable
private fun TabCountButton(count: Int, top: Boolean, onNewTab: () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reach = with(density) { 44.dp.toPx() }
    val tap by rememberUpdatedState(onClick)
    val newTab by rememberUpdatedState(onNewTab)
    var holding by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(if (holding) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "pop")
    val lit by animateFloatAsState(if (armed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "lit")

    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pointerInput(top) {
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
                    holding = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.first()
                        val dy = c.position.y - down.position.y
                        val over = if (top) dy > reach * 0.6f else dy < -reach * 0.6f
                        if (over != armed) {
                            armed = over
                            if (over) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        c.consume()
                        if (!c.pressed) break
                    }
                    if (armed) newTab()
                    armed = false
                    holding = false
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
        if (pop > 0.01f) {
            // The "+" pops out above the button (below it when the bar is on top).
            val offset = with(density) { (if (top) 60.dp else (-60).dp).roundToPx() }
            Popup(
                alignment = Alignment.Center,
                offset = IntOffset(0, offset),
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val k = (0.4f + 0.6f * pop) * (1f + 0.15f * lit)
                            scaleX = k
                            scaleY = k
                            alpha = pop.coerceIn(0f, 1f)
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
        if (text.isBlank()) emptyList() else Browser.db.suggest(text)
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
            verticalArrangement = Arrangement.Top,
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
