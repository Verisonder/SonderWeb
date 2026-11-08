package com.verisonder.sonderweb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.UrlText
import kotlinx.coroutines.flow.first

/**
 * The tab switcher, laid out like Brave's: the normal / private switch on top, "Search your
 * tabs", a row for inactive tabs, big rounded cards, and a round + with a ⋮ menu at the bottom.
 */
@Composable
fun TabsScreen(ui: UiState) {
    val p = LocalPalette.current
    var privateMode by remember { mutableStateOf(Browser.current?.isPrivate ?: false) }
    var query by remember { mutableStateOf("") }
    var showInactive by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Screenshots are only blocked while the Private list itself is on screen.
    androidx.compose.runtime.LaunchedEffect(privateMode) { ui.tabsPrivate = privateMode }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ui.tabsPrivate = false } }
    BackHandler(enabled = showInactive) { showInactive = false }

    val inactive = if (privateMode) emptyList() else Browser.inactiveTabs()
    val q = query.trim().lowercase()
    val list = Browser.tabs.filter { t ->
        t.isPrivate == privateMode && when {
            // A search looks through every tab, inactive ones too.
            q.isNotEmpty() -> t.title.lowercase().contains(q) || (t.pendingUrl ?: t.url).lowercase().contains(q)
            else -> t !in inactive
        }
    }
    val accent = if (privateMode) PrivatePalette.accent else p.accent
    val locked = privateMode && Browser.privateLocked

    // Opens at the bottom, where the newest tabs are (and again when switching normal / private).
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    androidx.compose.runtime.LaunchedEffect(privateMode, showInactive) {
        if (showInactive) return@LaunchedEffect
        // The grid's own count is 0 until it has laid out once: wait for it.
        val count = androidx.compose.runtime.snapshotFlow { grid.layoutInfo.totalItemsCount }
            .first { it > 0 }
        grid.scrollToItem(count - 1)
    }

    if (showInactive) {
        InactiveTabs(ui, inactive) { showInactive = false }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Normal / private, as two icons in one pill.
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.field)
                    .padding(4.dp),
            ) {
                ModeButton(on = !privateMode, onClick = { privateMode = false }) {
                    CountBox(Browser.tabs.count { !it.isPrivate }, if (!privateMode) p.text else p.mute)
                }
                Spacer(Modifier.width(4.dp))
                ModeButton(on = privateMode, onClick = { privateMode = true }) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        "Private tabs",
                        tint = if (privateMode) PrivatePalette.accent else p.mute,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "search", span = { GridItemSpan(2) }) {
                    SearchTabs(query) { query = it }
                }
                if (q.isEmpty() && inactive.isNotEmpty()) {
                    item(key = "inactive", span = { GridItemSpan(2) }) {
                        InactiveRow(inactive.size) { showInactive = true }
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(2) }) {
                        Text(
                            when {
                                q.isNotEmpty() -> "No tabs match"
                                privateMode -> "No private tabs"
                                else -> "No tabs"
                            },
                            color = p.faint,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 40.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(list, key = { it.id }) { t ->
                    TabCard(ui, t, selected = t === Browser.current, modifier = Modifier.animateItem()) {
                        Browser.select(t)
                        ui.panel = Panel.None
                    }
                }
            }
            // Locked: the private tabs' titles stay hidden too.
            if (locked && Browser.tabs.any { it.isPrivate }) PrivateLock(onLeave = null)
        }

        // A round + in the middle, ⋮ on the right.
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (privateMode) accent else p.text)
                    .clickable {
                        Browser.newTab(null, isPrivate = privateMode)
                        ui.panel = Panel.None
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    if (privateMode) "New private tab" else "New tab",
                    tint = if (privateMode) p.onAccent else p.bg,
                    modifier = Modifier.size(28.dp),
                )
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MoreVert, "More", tint = p.text, modifier = Modifier.size(24.dp))
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = p.card,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    val all = Browser.tabs.any { it.isPrivate == privateMode }
                    if (inactive.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Close inactive tabs", color = p.text, fontSize = 15.sp) },
                            onClick = {
                                menu = false
                                inactive.forEach { Browser.close(it) }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (privateMode) "Close all private tabs" else "Close all tabs",
                                color = if (all) p.danger else p.faint,
                                fontSize = 15.sp,
                            )
                        },
                        enabled = all,
                        onClick = {
                            menu = false
                            Browser.closeAll(privateMode)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeButton(on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(width = 60.dp, height = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) p.line else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The tab count in a rounded square, like the one on the address bar. */
@Composable
private fun CountBox(count: Int, color: Color) {
    Box(
        Modifier
            .size(24.dp)
            .border(2.dp, color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) ":D" else count.toString(),
            color = color,
            fontSize = if (count > 9) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchTabs(query: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.field)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = p.faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search your tabs", color = p.faint, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 16.sp, fontFamily = Inter),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Clear", tint = p.mute, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InactiveRow(count: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FilterNone, null, tint = p.text, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("($count) inactive tabs", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Unused or duplicate tabs", color = p.mute, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.text, modifier = Modifier.size(26.dp))
    }
}

/** The inactive tabs, as a list: tap to open one, × to close it, or close them all. */
@Composable
private fun InactiveTabs(ui: UiState, tabs: List<Tab>, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.text, modifier = Modifier.size(24.dp))
            }
            Text("Inactive tabs", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Not used for ${Browser.INACTIVE_DAYS} days, or open in another tab.",
            color = p.mute,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(tabs.size, key = { tabs[it].id }) { i ->
                val t = tabs[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Browser.select(t)
                            ui.panel = Panel.None
                        }
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabIcon(t)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title.ifBlank { UrlText.host(t.pendingUrl ?: t.url) },
                            color = p.text,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            UrlText.host(t.pendingUrl ?: t.url),
                            color = p.faint,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { Browser.close(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Close tab", tint = p.mute, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "Close all inactive tabs",
                color = if (tabs.isEmpty()) p.faint else p.danger,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = tabs.isNotEmpty()) {
                        tabs.forEach { Browser.close(it) }
                        onBack()
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

/** A tab, Brave style: big rounded card, title row on top; the current tab's card is filled in. */
@Composable
private fun TabCard(ui: UiState, tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(26.dp)
    val bg = if (selected) p.text else p.card
    val fg = if (selected) p.bg else p.text
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcon(tab)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    tab.isBlank -> "New tab"
                    tab.title.isNotBlank() -> tab.title
                    else -> UrlText.host(tab.url).ifEmpty { tab.displayTitle }
                },
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { Browser.close(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Close tab", tint = fg, modifier = Modifier.size(20.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(p.bg),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = tab.thumbnail
            if (tab.isBlank) {
                // A new tab shows its start page, small: the real one, drawn at about 40%.
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalPalette provides if (tab.isPrivate) PrivatePalette else p,
                    ) {
                        Scaled(0.42f) { StartPage(ui, preview = tab) }
                    }
                    // Taps open the tab; nothing on the small page itself reacts.
                    Box(Modifier.matchParentSize().clickable(onClick = onClick))
                }
            } else if (thumb != null) {
                Image(
                    thumb,
                    null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        UrlText.host(tab.url).firstOrNull()?.uppercase() ?: "•",
                        color = p.mute,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(UrlText.host(tab.url), color = p.faint, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/** The small icon before a tab's title: the site's icon, or a plain one for new and private tabs. */
@Composable
private fun TabIcon(tab: Tab) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val host = UrlText.host(tab.url)
    val showSite = !tab.isBlank && !tab.isPrivate && host.isNotEmpty()
    if (showSite) androidx.compose.runtime.LaunchedEffect(host) { com.verisonder.sonderweb.data.SiteIcons.load(ctx, host) }
    val bmp = if (showSite) com.verisonder.sonderweb.data.SiteIcons.icons[host] else null
    if (bmp != null) {
        Image(
            remember(bmp) { bmp.asImageBitmap() },
            null,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.size(16.dp).iconCorners(bmp, 4.dp),
        )
    } else {
        Icon(
            if (tab.isBlank) Icons.Outlined.Add else Icons.Outlined.Public,
            null,
            tint = p.faint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) p.onAccent else p.mute,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
