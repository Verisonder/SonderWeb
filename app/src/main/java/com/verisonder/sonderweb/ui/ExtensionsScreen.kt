package com.verisonder.sonderweb.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Extensions
import com.verisonder.sonderweb.browser.Ublock
import org.mozilla.geckoview.WebExtension

/** Your extensions: uBlock Origin first, then the ones you added. */
@Composable
fun ExtensionsScreen(ui: UiState) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Extensions", { ui.panel = Panel.None })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            Ublock.error?.let { err ->
                item {
                    Text(
                        "uBlock Origin couldn't start: $err",
                        color = p.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(Extensions.list, key = { it.id }) { ext ->
                ExtensionCard(
                    ext,
                    expanded = open == ext.id,
                    onToggleOpen = { open = if (open == ext.id) null else ext.id },
                    ui = ui,
                    modifier = Modifier.animateItem(),
                )
            }
            if (Extensions.list.isEmpty() && Ublock.error == null) {
                item {
                    Text(
                        "Starting…",
                        color = p.faint,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(p.accent)
                .clickable {
                    ui.panel = Panel.None
                    Browser.newTab(Extensions.GET_MORE, isPrivate = false)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Get more extensions", color = p.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExtensionCard(
    ext: WebExtension,
    expanded: Boolean,
    onToggleOpen: () -> Unit,
    ui: UiState,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    val meta = ext.metaData
    val builtIn = ext.id == Ublock.ID
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.field)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 600f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleOpen)
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = Extensions.icons[ext.id]
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(remember(icon) { icon.asImageBitmap() }, null, modifier = Modifier.size(40.dp))
                } else {
                    Icon(Icons.Outlined.Extension, null, tint = p.mute, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(meta.name ?: ext.id, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        meta.version.takeIf { it.isNotBlank() },
                        if (builtIn) "built in" else meta.creatorName,
                    ).joinToString(" · "),
                    color = p.faint,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                meta.enabled,
                { Extensions.setEnabled(ext, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent),
            )
        }
        if (expanded) {
            meta.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = p.mute, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tab = Browser.current
                if (meta.enabled && tab != null && Extensions.hasButton(ext) && !tab.isBlank) {
                    Chip("Open") {
                        ui.panel = Panel.None
                        Extensions.showPopup(ext, tab)
                    }
                }
                if (meta.optionsPageUrl != null || builtIn) {
                    Chip("Settings") {
                        ui.panel = Panel.None
                        Extensions.openOptions(ext)
                    }
                }
                if (!builtIn) Chip("Remove", danger = true) { Extensions.remove(ext) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { Extensions.setPrivate(ext, !meta.allowedInPrivateBrowsing) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Run in private tabs", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    meta.allowedInPrivateBrowsing,
                    { Extensions.setPrivate(ext, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Chip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label,
        color = if (danger) p.danger else p.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** "Add <extension>?" when a site (addons.mozilla.org) wants to install one. */
@Composable
fun InstallDialog(req: Extensions.InstallRequest) {
    val p = LocalPalette.current
    var inPrivate by remember(req) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { Extensions.answer(false, false) },
        containerColor = p.card,
        title = { Text("Add ${req.ext.metaData.name ?: "this extension"}?", color = p.text) },
        text = {
            Column {
                if (req.permissions.isNotEmpty()) {
                    Text("It will be able to:", color = p.mute, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    req.permissions.take(8).forEach {
                        Text("• " + describe(it), color = p.text, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    if (req.permissions.size > 8) Text("• and ${req.permissions.size - 8} more", color = p.text, fontSize = 14.sp)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { inPrivate = !inPrivate }.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(inPrivate, { inPrivate = it }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                    Text("Also run in private tabs", color = p.text, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton({ Extensions.answer(true, inPrivate) }) { Text("Add", color = p.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton({ Extensions.answer(false, false) }) { Text("Cancel", color = p.text) } },
    )
}

private fun describe(permission: String): String = when (permission) {
    "<all_urls>", "*://*/*", "http://*/*", "https://*/*" -> "Read and change all your data on all websites"
    "tabs" -> "See your open tabs"
    "history" -> "See and change your history"
    "bookmarks" -> "See and change your bookmarks"
    "downloads" -> "Download files and see your downloads"
    "cookies" -> "See and change cookies"
    "storage", "unlimitedStorage" -> "Store data on your phone"
    "webRequest", "webRequestBlocking" -> "See and block network requests"
    "privacy" -> "Change privacy settings"
    "clipboardWrite" -> "Write to your clipboard"
    "clipboardRead" -> "Read your clipboard"
    "notifications" -> "Show notifications"
    "geolocation" -> "Know your location"
    else -> if (permission.contains("/") || permission.startsWith("*")) "Access $permission" else permission
}
