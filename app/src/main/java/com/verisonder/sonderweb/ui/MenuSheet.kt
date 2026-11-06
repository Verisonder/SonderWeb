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
    var bookmarked by remember(tab.url, ui.menu) { mutableStateOf(web && Browser.db.isBookmarked(tab.url)) }
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

            Item("New tab", Icons.Outlined.Add) {
                Browser.newTab(null, isPrivate = false)
                close()
            }
            Item("New private tab", Icons.Outlined.VisibilityOff) {
                Browser.newTab(null, isPrivate = true)
                close()
            }
            // Private tabs stay apart: no way into your normal history, downloads or bookmarks from here.
            if (!tab.isPrivate) {
                Divider()
                Item("History", Icons.Outlined.History) {
                    ui.panel = Panel.History
                    close()
                }
                Item("Downloads", Icons.Outlined.Download) {
                    ui.panel = Panel.Downloads
                    close()
                }
                Item("Bookmarks", Icons.Outlined.Collections) {
                    ui.panel = Panel.Bookmarks
                    close()
                }
            }
            if (web) {
                Divider()
                Item("Find in page", Icons.Outlined.FindInPage) {
                    ui.findQuery = ""
                    ui.findResult = ""
                    ui.finding = true
                    close()
                }
                // A video is playing: put it in the small window right away.
                if (tab.videoPlaying || tab.mediaPlaying) {
                    Item("Picture-in-picture", Icons.Outlined.PictureInPictureAlt) {
                        close()
                        if (Browser.host?.enterPip() != true) {
                            Browser.host?.toast("Picture-in-picture is off for SonderWeb in the phone's settings")
                        }
                    }
                }
                Item(if (tab.translatedTo != null) "Show original" else "Translate page", Icons.Outlined.Translate) {
                    Translate.toggle(tab)
                    close()
                }
                if (!tab.isPrivate) {
                    Item(
                        if (bookmarked) "Remove bookmark" else "Add bookmark",
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    ) {
                        if (bookmarked) {
                            Browser.db.removeBookmark(tab.url)
                            Browser.host?.toast("Bookmark removed")
                        } else {
                            Browser.db.addBookmark(tab.url, tab.title)
                            Browser.host?.toast("Bookmarked")
                        }
                        bookmarked = !bookmarked
                        close()
                    }
                }
                DropdownMenuItem(
                    text = { Text("Desktop site", fontSize = 16.sp, color = p.text) },
                    onClick = { Browser.setDesktop(tab, !tab.desktop) },
                    leadingIcon = { Icon(Icons.Outlined.Computer, null, tint = p.mute, modifier = Modifier.size(22.dp)) },
                    trailingIcon = {
                        Checkbox(
                            tab.desktop,
                            { Browser.setDesktop(tab, it) },
                            colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.mute),
                        )
                    },
                    contentPadding = PaddingValues(start = 20.dp, end = 8.dp),
                    modifier = Modifier.height(54.dp),
                )
                // Like Brave's "Shred site data" (private tabs forget everything anyway).
                if (!tab.isPrivate) {
                    Item("Shred site data", ShredIcon) {
                        ui.shred = Browser.siteOf(tab.url)
                        close()
                    }
                }
            }
            Divider()
            Item("Extensions", Icons.Outlined.Extension) {
                ui.panel = Panel.Extensions
                close()
            }
            Item("Settings", Icons.Outlined.Settings, dot = com.verisonder.sonderweb.data.AppUpdate.available != null) {
                ui.panel = Panel.Settings
                close()
            }
            Item("Exit", Icons.AutoMirrored.Outlined.Logout) {
                close()
                Browser.saveTabs()
                (ctx as? android.app.Activity)?.finishAndRemoveTask()
            }
            if (!top) actions()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = LocalPalette.current.line, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
}

@Composable
private fun Item(label: String, icon: ImageVector, dot: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    DropdownMenuItem(
        text = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, color = p.text)
                if (dot) UpdateDot(Modifier.padding(start = 8.dp))
            }
        },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = p.mute, modifier = Modifier.size(22.dp)) },
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.height(54.dp),
    )
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
