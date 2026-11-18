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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.verisonder.sonderweb.data.Bookmark
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
    site: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    // The site's own icon when there is one (same as the start page and tabs).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!site.isNullOrEmpty()) androidx.compose.runtime.LaunchedEffect(site) { com.verisonder.sonderweb.data.SiteIcons.load(ctx, site) }
    val bmp = site?.let { com.verisonder.sonderweb.data.SiteIcons.icons[it] }
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
            } else if (bmp != null) {
                androidx.compose.foundation.Image(
                    remember(bmp) { bmp.asImageBitmap() },
                    null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                )
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
        trailing?.invoke()
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
                        site = UrlText.host(h.url),
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
    // The folders opened, top level first (empty: the top level).
    val path = remember { androidx.compose.runtime.mutableStateListOf<Bookmark>() }
    val here = path.lastOrNull()
    val items = remember(q, version, here?.id, com.verisonder.sonderweb.sync.FxSync.bookmarksVersion) {
        if (q.isNotEmpty()) Browser.db.bookmarks(q) else Browser.db.bookmarksIn(here?.id ?: 0L)
    }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Bookmark?>(null) }
    var deleting by remember { mutableStateOf<Bookmark?>(null) }
    val back: () -> Unit = {
        if (path.isNotEmpty()) {
            path.removeAt(path.lastIndex)
        } else {
            ui.panel = Panel.None
        }
    }
    androidx.activity.compose.BackHandler(enabled = path.isNotEmpty()) { back() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            here?.title?.ifBlank { "Folder" } ?: "Bookmarks",
            back,
            q,
            { q = it },
            Icons.Outlined.CreateNewFolder,
            "New folder",
        ) { newFolder = true }
        if (items.isEmpty()) {
            Empty(
                when {
                    q.isNotEmpty() -> "Nothing matches"
                    here != null -> "This folder is empty"
                    else -> "Save pages from the menu (⋮ › Bookmark)"
                },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { b ->
                    BookmarkRow(
                        b,
                        Modifier.animateItem(),
                        onClick = {
                            if (b.folder) {
                                path += b
                                q = ""
                            } else {
                                open(ui, b.url)
                            }
                        },
                        onEdit = { editing = b },
                        onMove = { moving = b },
                        onDelete = {
                            if (b.folder) {
                                deleting = b
                            } else {
                                Browser.db.deleteBookmark(b.id)
                                version++
                            }
                        },
                    )
                }
            }
        }
    }
    editing?.let { b ->
        BookmarkEditDialog(
            if (b.folder) "Rename folder" else "Edit bookmark",
            b.title,
            if (b.folder) null else b.url,
            { editing = null },
        ) { title, url ->
            Browser.db.editBookmark(b.id, title, url)
            editing = null
            version++
        }
    }
    if (newFolder) {
        BookmarkEditDialog("New folder", "", null, { newFolder = false }) { title, _ ->
            Browser.db.addFolder(title, here?.id ?: 0L)
            newFolder = false
            version++
        }
    }
    moving?.let { b ->
        MoveBookmarkDialog(b, { moving = null }) { to ->
            Browser.db.moveBookmark(b.id, to)
            moving = null
            version++
        }
    }
    deleting?.let { b ->
        ConfirmDialog("Delete \"${b.title.ifBlank { "Folder" }}\"?", "The folder and everything in it.", "Delete", { deleting = null }) {
            Browser.db.deleteBookmark(b.id)
            version++
        }
    }
}

/** A bookmark or folder, with a ⋮ menu (rename or edit, move, delete). */
@Composable
private fun BookmarkRow(
    b: Bookmark,
    modifier: Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val host = UrlText.host(b.url)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        EntryRow(
            Modifier,
            host.firstOrNull()?.uppercase() ?: "•",
            b.title.ifBlank { if (b.folder) "Folder" else host },
            if (b.folder) "Folder" else UrlText.editable(b.url),
            onClick = onClick,
            onRemove = null,
            icon = if (b.folder) Icons.Outlined.Folder else null,
            iconTint = if (b.folder) p.accent else null,
            site = if (b.folder) null else host,
            trailing = if (b.fixed) null else {
                {
                    Box {
                        IconButtonSmall(Icons.Outlined.MoreVert, "More", tint = p.faint) { menu = true }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            containerColor = p.card,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            MoreItem(if (b.folder) "Rename" else "Edit", Icons.Outlined.Edit) { menu = false; onEdit() }
                            MoreItem("Move to…", Icons.Outlined.FolderOpen) { menu = false; onMove() }
                            MoreItem("Delete", Icons.Outlined.Delete, danger = true) { menu = false; onDelete() }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun BookmarkEditDialog(
    title: String,
    name: String,
    address: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    val p = LocalPalette.current
    var n by remember { mutableStateOf(name) }
    var a by remember { mutableStateOf(address?.let { UrlText.editable(it) } ?: "") }
    val url = remember(a) {
        val t = a.trim()
        when {
            address == null -> null
            t.isEmpty() -> null
            UrlText.isWeb(t) -> t
            else -> com.verisonder.sonderweb.core.AddressInput.hostTarget(t)
        }
    }
    val ok = n.isNotBlank() && (address == null || url != null)
    val colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = p.text,
        unfocusedTextColor = p.text,
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.line,
        cursorColor = p.accent,
        focusedLabelColor = p.accent,
        unfocusedLabelColor = p.mute,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true, colors = colors)
                if (address != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(a, { a = it }, label = { Text("Address") }, singleLine = true, colors = colors)
                }
            }
        },
        confirmButton = {
            TextButton({ if (ok) onSave(n.trim(), url) }, enabled = ok) { Text("Save", color = if (ok) p.accent else p.faint) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}

@Composable
private fun MoveBookmarkDialog(b: Bookmark, onDismiss: () -> Unit, onMove: (Long) -> Unit) {
    val p = LocalPalette.current
    val folders = remember(b.id) {
        // Not into itself, nor into a folder inside it.
        val all = Browser.db.folders()
        val inside = HashSet<Long>().apply { add(b.id) }
        var grew = true
        while (grew) {
            grew = false
            all.forEach { (f, _) -> if (f.parent in inside && inside.add(f.id)) grew = true }
        }
        all.filter { (f, _) -> f.id !in inside }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("Move to", color = p.text, fontSize = 18.sp) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    MoveTarget("Bookmarks (top level)", b.parent == 0L) { onMove(0L) }
                }
                items(folders, key = { it.first.id }) { (f, name) ->
                    MoveTarget(name, b.parent == f.id) { onMove(f.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}

@Composable
private fun MoveTarget(name: String, current: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !current, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Folder, null, tint = if (current) p.faint else p.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = if (current) p.faint else p.text,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DownloadsScreen(ui: UiState) {
    val items = remember(Downloads.changes) { Browser.db.downloads() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Downloads",
            { ui.panel = Panel.None },
            action = Icons.Outlined.DeleteSweep,
            actionLabel = "Clear list",
            onAction = { Downloads.clear() },
        )
        if (items.isEmpty()) {
            Empty("Files you download show up here")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { d ->
                    DownloadRow(d, Downloads.live[d.id], Modifier.animateItem()) { url -> open(ui, url) }
                }
            }
        }
    }
}

/** One download: its kind, name and what's going on (progress, speed and time left while it
 *  runs; size, site and when once done), with a ⋮ menu for everything you can do with it. */
@Composable
private fun DownloadRow(d: DownloadItem, live: Downloads.Live?, modifier: Modifier, openUrl: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val running = live != null
    val failed = d.state == DownloadItem.FAILED || (d.state == DownloadItem.RUNNING && !running)
    val host = UrlText.host(d.url)
    val fmt = { b: Long -> Formatter.formatShortFileSize(ctx, b) }
    val status = when {
        running -> Downloads.progressText(ctx, live!!.received, live.total, live.speed)
        d.state == DownloadItem.DONE -> listOf(
            fmt(d.size),
            host,
            DateUtils.getRelativeTimeSpanString(d.added, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        d.state == DownloadItem.CANCELLED -> listOf("Cancelled", host).filter { it.isNotEmpty() }.joinToString(" · ")
        else -> listOf("Failed", host).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = d.state == DownloadItem.DONE) { Downloads.open(d) }
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(p.field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (failed) Icons.Outlined.ErrorOutline else kindIcon(d.name, d.mime),
                null,
                tint = if (failed) p.danger else p.mute,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(status, color = p.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (running) {
                Spacer(Modifier.height(7.dp))
                if (live!!.total > 0) {
                    val shown by animateFloatAsState(
                        (live.received.toFloat() / live.total).coerceIn(0f, 1f),
                        tween(380),
                        label = "download",
                    )
                    LinearProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = p.accent,
                        trackColor = p.line,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = p.accent,
                        trackColor = p.line,
                    )
                }
            }
        }
        Box {
            IconButtonSmall(Icons.Outlined.MoreVert, "More", tint = p.mute) { menu = true }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = p.card,
                shape = RoundedCornerShape(14.dp),
            ) {
                val close = { menu = false }
                if (running) {
                    MoreItem("Cancel", Icons.Outlined.Close) { close(); Downloads.cancel(d.id) }
                }
                if (d.state == DownloadItem.DONE) {
                    MoreItem("Open", Icons.AutoMirrored.Outlined.OpenInNew) { close(); Downloads.open(d) }
                    MoreItem("Share", Icons.Outlined.Share) { close(); Downloads.share(d) }
                }
                if (!running && d.state != DownloadItem.DONE) {
                    MoreItem("Try again", Icons.Outlined.Refresh) { close(); Downloads.retry(d) }
                }
                MoreItem("Copy download link", Icons.Outlined.Link) { close(); Downloads.copyLink(d) }
                if (host.isNotEmpty()) {
                    MoreItem("Go to $host", Icons.Outlined.Public) { close(); openUrl("https://$host") }
                }
                if (!running) {
                    MoreItem("Remove from list", Icons.Outlined.RemoveCircleOutline) { close(); Downloads.remove(d) }
                }
                if (d.state == DownloadItem.DONE) {
                    MoreItem("Delete file", Icons.Outlined.Delete, danger = true) { close(); Downloads.deleteFile(d) }
                }
            }
        }
    }
}

@Composable
private fun MoreItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val tint = if (danger) p.danger else p.text
    DropdownMenuItem(
        text = { Text(label, color = tint, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, null, tint = if (danger) p.danger else p.mute, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

/** An icon for the kind of file. */
private fun kindIcon(name: String, mime: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") -> Icons.Outlined.Image
        mime.startsWith("video/") -> Icons.Outlined.Movie
        mime.startsWith("audio/") -> Icons.Outlined.MusicNote
        mime == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        mime == "application/vnd.android.package-archive" || ext == "apk" -> Icons.Outlined.Android
        ext in setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2") -> Icons.Outlined.FolderZip
        mime.startsWith("text/") || ext in setOf("txt", "md", "csv", "doc", "docx", "odt") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
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
