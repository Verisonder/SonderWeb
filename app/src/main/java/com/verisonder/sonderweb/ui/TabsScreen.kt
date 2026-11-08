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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.UrlText
import kotlinx.coroutines.flow.first

@Composable
fun TabsScreen(ui: UiState) {
    val p = LocalPalette.current
    var privateMode by remember { mutableStateOf(Browser.current?.isPrivate ?: false) }
    // Screenshots are only blocked while the Private list itself is on screen.
    androidx.compose.runtime.LaunchedEffect(privateMode) { ui.tabsPrivate = privateMode }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ui.tabsPrivate = false } }
    var showInactive by remember { mutableStateOf(false) }
    BackHandler(enabled = showInactive) { showInactive = false }
    // Inactive tabs (Brave's idea) sit behind one row at the top instead of in the grid.
    val inactive = if (privateMode) emptyList() else Browser.inactiveTabs()
    val list = Browser.tabs.filter { it.isPrivate == privateMode && it !in inactive }
    val accent = if (privateMode) PrivatePalette.accent else p.accent

    // Opens at the bottom, where the newest tabs are (and again on switching normal / private).
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(
                listOf("Tabs · " + Browser.tabs.count { !it.isPrivate }, "Private · " + Browser.tabs.count { it.isPrivate }),
                if (privateMode) 1 else 0,
                accent,
            ) { privateMode = it == 1 }
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                color = p.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { ui.panel = Panel.None }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                Text(
                    if (privateMode) "No private tabs" else "No tabs",
                    color = p.faint,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (inactive.isNotEmpty()) {
                    item(key = "inactive", span = { GridItemSpan(2) }) {
                        InactiveRow(inactive.size) { showInactive = true }
                    }
                }
                items(list, key = { it.id }) { t ->
                    TabCard(ui, t, selected = t === Browser.current, accent = accent, modifier = Modifier.animateItem()) {
                        Browser.select(t)
                        ui.panel = Panel.None
                    }
                }
            }
            // Locked: the private tabs' titles stay hidden too.
            if (privateMode && Browser.privateLocked && list.isNotEmpty()) PrivateLock(onLeave = null)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Close all",
                color = if (list.isEmpty()) p.faint else p.danger,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = list.isNotEmpty()) { Browser.closeAll(privateMode) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(accent)
                    .clickable {
                        Browser.newTab(null, isPrivate = privateMode)
                        ui.panel = Panel.None
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = p.onAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (privateMode) "Private tab" else "New tab", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InactiveRow(count: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FilterNone, null, tint = p.mute, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("($count) inactive tabs", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Unused or duplicate tabs", color = p.faint, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.mute, modifier = Modifier.size(22.dp))
    }
}

/** The inactive tabs as a list: tap one to open it, × to close it, or close them all. */
@Composable
private fun InactiveTabs(ui: UiState, tabs: List<Tab>, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.text, modifier = Modifier.size(22.dp))
            }
            Text("Inactive tabs", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Not used for ${Browser.INACTIVE_DAYS} days, or open in another tab.",
            color = p.faint,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(tabs.size, key = { tabs[it].id }) { i ->
                val t = tabs[i]
                val url = t.pendingUrl ?: t.url
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Browser.select(t)
                            ui.panel = Panel.None
                        }
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabIcon(t)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title.ifBlank { UrlText.host(url) },
                            color = p.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(UrlText.host(url), color = p.faint, fontSize = 12.sp, maxLines = 1)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { Browser.close(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Close tab", tint = p.mute, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Close all inactive",
                color = if (tabs.isEmpty()) p.faint else p.danger,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = tabs.isNotEmpty()) {
                        tabs.forEach { Browser.close(it) }
                        onBack()
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TabCard(ui: UiState, tab: Tab, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.card)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier.border(1.dp, p.line, shape))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcon(tab)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    tab.isBlank -> "New tab"
                    tab.title.isNotBlank() -> tab.title
                    else -> UrlText.host(tab.url).ifEmpty { tab.displayTitle }
                },
                color = p.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { Browser.close(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Close tab", tint = p.mute, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(11.dp))
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
