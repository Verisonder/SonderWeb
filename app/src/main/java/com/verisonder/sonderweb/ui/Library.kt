package com.verisonder.sonderweb.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Downloads
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.DownloadItem

/** A full-screen list page: back arrow, title, optional search and a trailing action. */
@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    query: String? = null,
    onQuery: ((String) -> Unit)? = null,
    action: ImageVector? = null,
    actionLabel: String = "",
    onAction: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonSmall(Icons.AutoMirrored.Outlined.ArrowBack, "Back", size = 44, onClick = onBack)
            Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) IconButtonSmall(action, actionLabel, size = 44, onClick = onAction)
        }
        if (query != null && onQuery != null) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(p.field)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = p.faint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = p.faint, fontSize = 15.sp)
                    BasicTextField(
                        query,
                        onQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) IconButtonSmall(Icons.Outlined.Close, "Clear", size = 30) { onQuery("") }
            }
        }
    }
}

@Composable
private fun EntryRow(
    modifier: Modifier,
    letter: String,
    title: String,
    sub: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(p.field),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = iconTint ?: p.mute, modifier = Modifier.size(18.dp))
            } else {
                Text(letter, color = p.mute, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) IconButtonSmall(Icons.Outlined.Close, "Remove", tint = p.faint, onClick = onRemove)
    }
}

@Composable
private fun Empty(text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = p.faint, fontSize = 14.sp)
    }
}

private fun open(ui: UiState, url: String) {
    ui.panel = Panel.None
    val cur = Browser.current
    if (cur != null && cur.isBlank) Browser.load(cur, url) else Browser.newTab(url, isPrivate = cur?.isPrivate ?: false)
}

@Composable
fun HistoryScreen(ui: UiState) {
    var q by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val items = remember(q, version) { Browser.db.history(q) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("History", { ui.panel = Panel.None }, q, { q = it }, Icons.Outlined.DeleteSweep, "Clear history") {
            confirm = true
        }
        if (items.isEmpty()) {
            Empty(if (q.isEmpty()) "Pages you visit show up here" else "Nothing matches")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { h ->
                    EntryRow(
                        modifier = Modifier.animateItem(),
                        UrlText.host(h.url).firstOrNull()?.uppercase() ?: "•",
                        h.title.ifBlank { UrlText.host(h.url) },
                        UrlText.host(h.url) + " · " +
                            DateUtils.getRelativeTimeSpanString(h.visited, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        onClick = { open(ui, h.url) },
                        onRemove = {
                            Browser.db.deleteHistory(h.id)
                            version++
                        },
                    )
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog("Clear all history?", "Bookmarks and open tabs stay.", "Clear", { confirm = false }) {
            Browser.db.clearHistory()
            version++
        }
    }
}

@Composable
fun BookmarksScreen(ui: UiState) {
    var q by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    val items = remember(q, version) { Browser.db.bookmarks(q) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Bookmarks", { ui.panel = Panel.None }, q, { q = it })
        if (items.isEmpty()) {
            Empty(if (q.isEmpty()) "Save pages from the menu (⋮ › Bookmark)" else "Nothing matches")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { b ->
                    EntryRow(
                        modifier = Modifier.animateItem(),
                        UrlText.host(b.url).firstOrNull()?.uppercase() ?: "•",
                        b.title.ifBlank { UrlText.host(b.url) },
                        UrlText.editable(b.url),
                        onClick = { open(ui, b.url) },
                        onRemove = {
                            Browser.db.removeBookmark(b.url)
                            version++
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(ui: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { Browser.db.downloads() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Downloads",
            { ui.panel = Panel.None },
            action = Icons.Outlined.DeleteSweep,
            actionLabel = "Clear list",
            onAction = {
                Browser.db.clearDownloads()
                version++
            },
        )
        if (items.isEmpty()) {
            Empty("Files you download show up here")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { d ->
                    val status = when (d.state) {
                        DownloadItem.RUNNING -> "Downloading…"
                        DownloadItem.FAILED -> "Failed"
                        else -> Formatter.formatShortFileSize(ctx, d.size) + " · " + UrlText.host(d.url)
                    }
                    EntryRow(
                        modifier = Modifier.animateItem(),
                        "",
                        d.name,
                        status,
                        onClick = { if (d.state == DownloadItem.DONE) Downloads.open(d) },
                        onRemove = {
                            Browser.db.deleteDownload(d.id)
                            version++
                        },
                        icon = if (d.state == DownloadItem.FAILED) Icons.Outlined.ErrorOutline else Icons.Outlined.InsertDriveFile,
                        iconTint = if (d.state == DownloadItem.FAILED) p.danger else null,
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String?, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = text?.let { { Text(it, color = p.mute) } },
        confirmButton = {
            TextButton({
                onConfirm()
                onDismiss()
            }) { Text(confirm, color = p.danger) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}
