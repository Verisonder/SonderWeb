package com.verisonder.sonderweb.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
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
    val close = { ui.menu = false }

    DropdownMenu(
        expanded = ui.menu,
        onDismissRequest = close,
        shape = RoundedCornerShape(26.dp),
        containerColor = p.card,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.width(290.dp),
    ) {
        val actions: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundAction(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", tab.canGoForward) {
                    tab.session.goForward()
                    close()
                }
                RoundAction(
                    if (bookmarked) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    if (bookmarked) "Remove bookmark" else "Bookmark",
                    web && !tab.isPrivate,
                    tint = if (bookmarked) p.accent else null,
                ) {
                    if (bookmarked) Browser.db.removeBookmark(tab.url) else Browser.db.addBookmark(tab.url, tab.title)
                    bookmarked = !bookmarked
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
                    if (tab.loading) tab.session.stop() else tab.session.reload()
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
        if (web) {
            Divider()
            Item("Find in page", Icons.Outlined.FindInPage) {
                ui.findQuery = ""
                ui.findResult = ""
                ui.finding = true
                close()
            }
            if (!tab.isPrivate) {
                Item("Add to start page", Icons.Outlined.AddCircleOutline) {
                    val list = Browser.prefs.shortcuts
                    if (list.none { UrlText.host(it.url) == UrlText.host(tab.url) }) {
                        Browser.prefs.shortcuts = list + Shortcut(tab.url, tab.title.take(24).ifBlank { UrlText.host(tab.url) })
                        Browser.host?.toast("Added to the start page")
                    } else {
                        Browser.host?.toast("Already on the start page")
                    }
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
        }
        Divider()
        if (Ublock.extension != null) {
            Item("uBlock Origin", Icons.Outlined.Shield) {
                Ublock.openDashboard()
                close()
            }
        }
        Item("Settings", Icons.Outlined.Settings) {
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

@Composable
private fun Divider() {
    HorizontalDivider(color = LocalPalette.current.line, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
}

@Composable
private fun Item(label: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    DropdownMenuItem(
        text = { Text(label, fontSize = 16.sp, color = p.text) },
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
