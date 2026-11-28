package com.verisonder.sonderweb.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.browser.Translate
import com.verisonder.sonderweb.browser.Ublock
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.Shortcut

/** The ⋮ menu, Brave-style: a big rounded card, a plain list with icons, page actions as round buttons. */
@Composable
fun BrowserMenu(ui: UiState, tab: Tab, top: Boolean) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val web = UrlText.isWeb(tab.url)
    var bookmarked by remember(tab.url, ui.menu) { mutableStateOf(web && Browser.marks.isBookmarked(tab.url)) }
    val host = UrlText.host(tab.url)
    var pinned by remember(tab.url, ui.menu) {
        mutableStateOf(web && Browser.prefs.shortcuts.any { UrlText.host(it.url) == host })
    }
    val close = { ui.menu = false }

    // The menu keeps its normal size however big the bar is.
    Unscaled {
        MenuPopup(expanded = ui.menu, top = top, onDismiss = close) {
            val actions: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundAction(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", tab.canGoForward) {
                        tab.session.goForward()
                        close()
                    }
                    // The star puts this site on the start page (or takes it off).
                    RoundAction(
                        if (pinned) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (pinned) "Remove from start page" else "Add to start page",
                        web && !tab.isPrivate,
                        tint = if (pinned) p.accent else null,
                    ) {
                        val list = Browser.prefs.shortcuts
                        if (pinned) {
                            Browser.prefs.shortcuts = list.filter { UrlText.host(it.url) != host }
                            Browser.host?.toast("Removed from the start page")
                        } else {
                            Browser.prefs.shortcuts = list + Shortcut(tab.url, tab.title.take(24).ifBlank { host })
                            Browser.host?.toast("Added to the start page")
                        }
                        pinned = !pinned
                    }
                    RoundAction(Icons.Outlined.Share, "Share", web) {
                        Browser.host?.share(tab.url, tab.title)
                        close()
                    }
                    RoundAction(
                        if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                        if (tab.loading) "Stop" else "Reload",
                        !tab.isBlank,
                    ) {
                        if (tab.loading) tab.session.stop() else Browser.refresh(tab)
                        close()
                    }
                }
            }
            if (top) {
                actions()
                Divider()
            } else {
                Spacer(Modifier.height(6.dp))
            }

            // What the menu offers, once; drawn as a list, or grouped like Firefox's menu.
            val nav = buildList {
                // Private tabs stay apart: no way into your normal history, downloads or bookmarks from here.
                if (!tab.isPrivate) {
                    add(MenuOption("History", Icons.Outlined.History) { ui.panel = Panel.History; close() })
                    add(MenuOption("Downloads", Icons.Outlined.Download) { ui.panel = Panel.Downloads; close() })
                    add(MenuOption("Bookmarks", Icons.Outlined.Collections) { ui.panel = Panel.Bookmarks; close() })
                }
            }
            val page = buildList {
                if (!web) return@buildList
                add(MenuOption("Find in page", Icons.Outlined.FindInPage) {
                    ui.findQuery = ""
                    ui.findResult = ""
                    ui.finding = true
                    close()
                })
                // A video is playing: put it in the small window right away.
                if (tab.videoPlaying || tab.mediaPlaying) {
                    add(MenuOption("Picture-in-picture", Icons.Outlined.PictureInPictureAlt) {
                        close()
                        if (Browser.host?.enterPip() != true) {
                            Browser.host?.toast("Picture-in-picture is off for SonderWeb in the phone's settings")
                        }
                    })
                }
                add(MenuOption(if (tab.translatedTo != null) "Show original" else "Translate page", Icons.Outlined.Translate) {
                    Translate.toggle(tab)
                    close()
                })
                if (!tab.isPrivate) {
                    add(MenuOption(
                        if (bookmarked) "Remove bookmark" else "Add bookmark",
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    ) {
                        if (bookmarked) {
                            Browser.marks.removeBookmark(tab.url)
                            Browser.host?.toast("Bookmark removed")
                        } else {
                            Browser.marks.addBookmark(tab.url, tab.title)
                            Browser.host?.toast("Bookmarked")
                        }
                        bookmarked = !bookmarked
                        close()
                    })
                }
                // Firefox Sync (SonderWeb Sync, signed in): open this page on another of your devices.
                if (!tab.isPrivate && com.verisonder.sonderweb.sync.FxSync.signedIn) {
                    add(MenuOption("Send to device", Icons.Outlined.Devices) {
                        ui.sendTab = tab.title to tab.url
                        close()
                    })
                }
                add(MenuOption("Desktop site", Icons.Outlined.Computer, toggle = tab.desktop) { Browser.setDesktop(tab, !tab.desktop) })
                // Like Brave's "Shred site data" (private tabs forget everything anyway).
                if (!tab.isPrivate) {
                    add(MenuOption("Shred site data", ShredIcon) {
                        ui.shred = Browser.siteOf(tab.url)
                        close()
                    })
                }
            }
            val extensions = MenuOption("Extensions", Icons.Outlined.Extension) { ui.panel = Panel.Extensions; close() }
            val app = buildList {
                add(MenuOption("Settings", Icons.Outlined.Settings, dot = com.verisonder.sonderweb.data.AppUpdate.available != null) {
                    ui.panel = Panel.Settings
                    close()
                })
                // Settings › Tabs › Exit in the menu: closes the app, your tabs stay saved.
                if (Browser.prefs.menuExit) {
                    add(MenuOption("Exit", Icons.AutoMirrored.Outlined.Logout) {
                        close()
                        Browser.saveTabs()
                        (ctx as? android.app.Activity)?.finishAndRemoveTask()
                    })
                }
            }

            if (remember(ui.menu) { Browser.prefs.menuGrid }) {
                // Settings › Tabs › Grouped menu, like Firefox's: the page's options in a rounded
                // group, then History · Downloads · Bookmarks · Extensions side by side, then Settings.
                if (page.isNotEmpty()) CardGroup(page)
                SideBySide(nav + extensions)
                CardGroup(app)
                Spacer(Modifier.height(4.dp))
            } else {
                nav.forEach { ListOption(it) }
                if (page.isNotEmpty()) {
                    if (nav.isNotEmpty()) Divider()
                    page.forEach { ListOption(it) }
                }
                Divider()
                ListOption(extensions)
                app.forEach { ListOption(it) }
            }
            if (!top) actions()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = LocalPalette.current.line, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
}

/** One option of the menu. [toggle]: an on/off option (Desktop site), null for a plain action. */
private class MenuOption(
    val label: String,
    val icon: ImageVector,
    val dot: Boolean = false,
    val toggle: Boolean? = null,
    val onClick: () -> Unit,
)

/** The list look: a row with its icon (a checkbox for on/off options). */
@Composable
private fun ListOption(o: MenuOption) {
    val p = LocalPalette.current
    DropdownMenuItem(
        text = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(o.label, fontSize = 16.sp, color = p.text)
                if (o.dot) UpdateDot(Modifier.padding(start = 8.dp))
            }
        },
        onClick = o.onClick,
        leadingIcon = { Icon(o.icon, null, tint = p.mute, modifier = Modifier.size(22.dp)) },
        trailingIcon = o.toggle?.let { on ->
            {
                Checkbox(
                    on,
                    { o.onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.mute),
                )
            }
        },
        contentPadding = if (o.toggle != null) PaddingValues(start = 20.dp, end = 8.dp) else PaddingValues(horizontal = 20.dp),
        modifier = Modifier.height(54.dp),
    )
}

/** Shapes for pieces of one rounded group: the outer corners round, the inner ones nearly square. */
private fun piece(first: Boolean, last: Boolean, vertical: Boolean): RoundedCornerShape {
    val big = 18.dp
    val small = 5.dp
    return if (vertical) {
        RoundedCornerShape(
            topStart = if (first) big else small,
            topEnd = if (first) big else small,
            bottomEnd = if (last) big else small,
            bottomStart = if (last) big else small,
        )
    } else {
        RoundedCornerShape(
            topStart = if (first) big else small,
            bottomStart = if (first) big else small,
            topEnd = if (last) big else small,
            bottomEnd = if (last) big else small,
        )
    }
}

/** The grouped look: options stacked in one rounded group, each its own row (On / Off for toggles). */
@Composable
private fun CardGroup(options: List<MenuOption>) {
    if (options.isEmpty()) return
    val p = LocalPalette.current
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(piece(i == 0, i == options.lastIndex, vertical = true))
                    .background(p.field)
                    .clickable(onClick = o.onClick)
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(o.icon, null, tint = p.mute, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(o.label, color = p.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if (o.dot) UpdateDot(Modifier.padding(end = 8.dp))
                o.toggle?.let { on ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) p.accent else p.card)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(if (on) "On" else "Off", color = if (on) p.onAccent else p.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** The grouped look: a few options side by side in one rounded row, icon over name. */
@Composable
private fun SideBySide(options: List<MenuOption>) {
    if (options.isEmpty()) return
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            androidx.compose.foundation.layout.Column(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(piece(i == 0, i == options.lastIndex, vertical = false))
                    .background(p.field)
                    .clickable(onClick = o.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(o.icon, null, tint = p.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(o.label, color = p.mute, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Round page-action buttons at the edge of the menu, like Brave's. */
@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(p.bg)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint ?: p.text, modifier = Modifier.size(22.dp))
    }
}

/** A plain menu row with an icon (used by the long-press sheet). */
@Composable
fun MenuRow(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: p.mute, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint ?: p.text, fontSize = 15.sp)
    }
}

/** The small blue dot that says a new version is ready (on the menu button and on Settings). */
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalPalette.current.accent),
    )
}

/**
 * The menu's card, in its own popup: the popup window has room around the card, so its shadow
 * is drawn whole (the standard dropdown cut it off at the edge). Opens from the ⋮ button's corner.
 */
@Composable
private fun MenuPopup(
    expanded: Boolean,
    top: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState && state.isIdle) return
    val p = LocalPalette.current
    val d = androidx.compose.ui.platform.LocalDensity.current
    val margin = 20.dp
    val marginPx = with(d) { margin.roundToPx() }
    val gapPx = with(d) { 4.dp.roundToPx() }
    val maxH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 96.dp
    val origin = androidx.compose.ui.graphics.TransformOrigin(0.9f, if (top) 0f else 1f)
    val shape = RoundedCornerShape(26.dp)
    androidx.compose.ui.window.Popup(
        popupPositionProvider = remember(top, marginPx, gapPx) {
            object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: androidx.compose.ui.unit.IntRect,
                    windowSize: androidx.compose.ui.unit.IntSize,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    popupContentSize: androidx.compose.ui.unit.IntSize,
                ): androidx.compose.ui.unit.IntOffset {
                    val x = (anchorBounds.right - popupContentSize.width + marginPx)
                        .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val y = if (top) {
                        anchorBounds.bottom + gapPx - marginPx
                    } else {
                        anchorBounds.top - gapPx - popupContentSize.height + marginPx
                    }
                    return androidx.compose.ui.unit.IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = state,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) +
                androidx.compose.animation.scaleIn(
                    androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 650f),
                    initialScale = 0.88f,
                    transformOrigin = origin,
                ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) +
                androidx.compose.animation.scaleOut(
                    androidx.compose.animation.core.tween(130),
                    targetScale = 0.94f,
                    transformOrigin = origin,
                ),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.padding(margin)) {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .width(290.dp)
                        .heightIn(max = maxH)
                        .shadow(12.dp, shape)
                        .clip(shape)
                        .background(p.card)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}
