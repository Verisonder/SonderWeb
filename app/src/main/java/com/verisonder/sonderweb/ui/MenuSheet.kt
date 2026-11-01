package com.verisonder.sonderweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.browser.Ublock
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.Shortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(ui: UiState, tab: Tab) {
    val p = LocalPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val web = UrlText.isWeb(tab.url)
    var bookmarked by remember(tab.url) { mutableStateOf(web && Browser.db.isBookmarked(tab.url)) }
    val close = { ui.menu = false }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = state,
        containerColor = p.bar,
        contentColor = p.text,
        dragHandle = null,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (web) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
                    Text(tab.displayTitle, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(UrlText.host(tab.url), color = p.faint, fontSize = 12.sp, maxLines = 1)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuickAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tab.canGoBack) {
                    tab.session.goBack()
                    close()
                }
                QuickAction(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", tab.canGoForward) {
                    tab.session.goForward()
                    close()
                }
                QuickAction(
                    if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                    if (tab.loading) "Stop" else "Reload",
                    !tab.isBlank,
                ) {
                    if (tab.loading) tab.session.stop() else tab.session.reload()
                    close()
                }
                QuickAction(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (bookmarked) "Saved" else "Bookmark",
                    web && !tab.isPrivate,
                    tint = if (bookmarked) p.accent else null,
                ) {
                    if (bookmarked) Browser.db.removeBookmark(tab.url) else Browser.db.addBookmark(tab.url, tab.title)
                    bookmarked = !bookmarked
                }
                QuickAction(Icons.Outlined.Share, "Share", web) {
                    Browser.host?.share(tab.url, tab.title)
                    close()
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(p.line))
            Spacer(Modifier.height(4.dp))

            MenuRow(Icons.Outlined.Add, "New tab") {
                Browser.newTab(null, isPrivate = false)
                close()
            }
            MenuRow(Icons.Outlined.VisibilityOff, "New private tab") {
                Browser.newTab(null, isPrivate = true)
                close()
            }
            if (web && !tab.isPrivate) {
                MenuRow(Icons.Outlined.AddCircleOutline, "Add to start page") {
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
            MenuRow(Icons.Outlined.Collections, "Bookmarks") {
                ui.panel = Panel.Bookmarks
                close()
            }
            MenuRow(Icons.Outlined.History, "History") {
                ui.panel = Panel.History
                close()
            }
            MenuRow(Icons.Outlined.Download, "Downloads") {
                ui.panel = Panel.Downloads
                close()
            }
            if (web) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Browser.setDesktop(tab, !tab.desktop) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Computer, null, tint = p.mute, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text("Desktop site", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = tab.desktop,
                        onCheckedChange = { Browser.setDesktop(tab, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.accent),
                    )
                }
            }
            if (Ublock.extension != null) {
                MenuRow(Icons.Outlined.Shield, "uBlock Origin settings") {
                    Ublock.openDashboard()
                    close()
                }
            }
            MenuRow(Icons.Outlined.Settings, "Settings") {
                ui.panel = Panel.Settings
                close()
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint ?: p.text, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = p.mute, fontSize = 11.sp, maxLines = 1)
    }
}

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
