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
    Browser.go(text)
}

@Composable
fun AddressBar(ui: UiState, tab: Tab?) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.bar)) {
        if (tab != null && tab.loading && !ui.editing) LoadLine(tab.progress, p.accent) else Spacer(Modifier.height(2.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.field)
                    .then(
                        if (ui.editing) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(22.dp)) else Modifier,
                    )
                    .clickable(enabled = !ui.editing) { startEditing(ui, tab) }
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.editing) {
                    Icon(Icons.Outlined.Search, null, tint = p.mute, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    EditField(ui, Modifier.weight(1f))
                    if (ui.query.text.isNotEmpty()) {
                        IconButtonSmall(Icons.Outlined.Close, "Clear") { ui.query = TextFieldValue("") }
                    }
                } else {
                    val icon = when {
                        tab == null || tab.isBlank -> if (tab?.isPrivate == true) Icons.Outlined.VisibilityOff else Icons.Outlined.Search
                        tab.isPrivate -> Icons.Outlined.VisibilityOff
                        tab.secure -> Icons.Outlined.Lock
                        else -> Icons.Outlined.Language
                    }
                    Icon(icon, null, tint = if (tab?.isPrivate == true) p.accent else p.mute, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                    val host = UrlText.host(tab?.url)
                    Text(
                        host.ifEmpty { if (tab?.isPrivate == true) "Private search or address" else "Search or type address" },
                        color = if (host.isEmpty()) p.faint else p.text,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (tab != null && !tab.isBlank && Ublock.extension != null) {
                        ShieldButton(tab)
                    }
                }
            }
            if (ui.editing) {
                Text(
                    "Cancel",
                    color = p.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { ui.editing = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                TabCountButton(Browser.tabs.count { it.isPrivate == (tab?.isPrivate ?: false) }) {
                    tab?.let { Delegates.capture(it) }
                    ui.panel = Panel.Tabs
                }
                IconButtonSmall(Icons.Outlined.MoreVert, "Menu", size = 44) { ui.menu = true }
            }
        }
    }
}

@Composable
private fun EditField(ui: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = ui.query,
        onValueChange = { ui.query = it },
        singleLine = true,
        textStyle = TextStyle(color = p.text, fontSize = 15.sp),
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { submit(ui, ui.query.text) }),
        modifier = modifier.focusRequester(focus),
    )
}

/** uBlock Origin's button: a shield with the number of things it blocked on this page. */
@Composable
private fun ShieldButton(tab: Tab) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { Ublock.showPopup(tab) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Shield, "uBlock Origin", tint = p.accent, modifier = Modifier.size(18.dp))
        if (tab.blockedBadge.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(tab.blockedBadge, color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.8.dp, p.text, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) ":D" else count.toString(),
                color = p.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
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
fun Suggestions(ui: UiState, tab: Tab) {
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
            reverseLayout = true,
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
            Icon(icon, null, tint = if (accent) androidx.compose.ui.graphics.Color.White else p.mute, modifier = Modifier.size(16.dp))
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
