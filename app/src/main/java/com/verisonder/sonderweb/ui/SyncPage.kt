package com.verisonder.sonderweb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.sync.FxSync
import com.verisonder.sonderweb.sync.SavedPassword

/** Starts signing in: turns Firefox Sync on and opens Mozilla's sign-in page in a tab. */
internal fun startSignIn(ui: UiState) {
    FxSync.turn(true)
    FxSync.signIn { url ->
        ui.panel = Panel.None
        Browser.newTab(url, isPrivate = false)
    }
}

/** Opens a page of Mozilla's (managing your account) in a tab. */
private fun openInTab(ui: UiState, url: String) {
    ui.panel = Panel.None
    Browser.newTab(url, isPrivate = false)
}

/**
 * The top of Settings in SonderWeb Sync: signed out, "Sign in"; signed in, your account's
 * picture, name and email, opening Account and sync.
 */
@Composable
internal fun AccountCard(ui: UiState, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val signedIn = FxSync.signedIn
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.field)
            .clickable { if (signedIn) onOpen() else startSignIn(ui) }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(if (signedIn) FxSync.avatar else null, 56)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            if (signedIn) {
                Text(
                    FxSync.displayName?.takeIf { it.isNotBlank() } ?: "Mozilla account",
                    color = p.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (FxSync.needsSignIn) "Sign in again to keep syncing" else FxSync.email.orEmpty(),
                    color = if (FxSync.needsSignIn) p.danger else p.faint,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("Sign in", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Sync your passwords with Firefox on your other devices.", color = p.faint, fontSize = 14.sp)
            }
        }
    }
}

/** Your account's picture, or a round person mark when there's none. */
@Composable
private fun Avatar(picture: android.graphics.Bitmap?, size: Int) {
    val image = remember(picture) { picture?.asImageBitmap() }
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF00B3F4), Color(0xFF9059FF)))),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, "Your account's picture", Modifier.size(size.dp), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.size((size * 0.58f).dp))
        }
    }
}

/** Settings › Account and sync: your account, syncing, this phone's name, what to sync, passwords. */
@Composable
internal fun SyncPage(ui: UiState) {
    var signingOut by remember { mutableStateOf(false) }
    var showPasswords by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    if (!FxSync.signedIn) {
        AccountCard(ui) {}
        Hint(
            "Your saved passwords come over from Firefox, and the ones you save here go to your other devices. " +
                "They're encrypted on this phone first: Mozilla can't read them. SonderWeb Sync signs in as Firefox for Android.",
        )
    } else {
        // Your account at the top, as on the main page.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(FxSync.avatar, 64)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                val p = LocalPalette.current
                Text(
                    FxSync.displayName?.takeIf { it.isNotBlank() } ?: "Mozilla account",
                    color = p.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(FxSync.email.orEmpty(), color = p.faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Group {
            if (FxSync.needsSignIn) {
                ActionRow("Sign in again", "Your account asks you to sign in again to keep syncing") { startSignIn(ui) }
            }
            ActionRow("Manage account", "Change your password, manage data collection, or delete your account") {
                FxSync.manageAccount { openInTab(ui, it) }
            }
            ActionRow(
                "Sync now",
                when {
                    FxSync.syncing -> "Syncing…"
                    FxSync.lastSynced > 0 -> "Last synced " + DateUtils.getRelativeTimeSpanString(
                        FxSync.lastSynced,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    )
                    else -> "Not synced yet"
                },
            ) { FxSync.sync() }
            ActionRow("Device name", FxSync.deviceName) { renaming = true }
            ActionRow("Sign out", "Your saved passwords stay on this phone", danger = true) { signingOut = true }
        }
        FxSync.problem?.let { Hint(it) }

        GroupTitle("Choose what to sync")
        Group {
            Toggle("Passwords", null, FxSync.syncPasswords) { FxSync.setSyncPasswords(it) }
        }
        Hint("Bookmarks, history and open tabs come in a later version.")
    }

    GroupTitle("Passwords")
    Group {
        Toggle(
            "Save and fill passwords",
            "Offers to save the passwords you sign in with, and fills them in",
            FxSync.enabled && FxSync.fillPasswords,
        ) { FxSync.setFillPasswords(it) }
        if (FxSync.enabled) {
            ActionRow(
                "Saved passwords",
                when (FxSync.passwordCount) {
                    0 -> "None yet"
                    1 -> "1 saved"
                    else -> "${FxSync.passwordCount} saved"
                },
            ) { showPasswords = true }
        }
    }

    if (renaming) {
        val p = LocalPalette.current
        var name by remember { mutableStateOf(FxSync.deviceName) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = p.card,
            title = { Text("Device name", color = p.text, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(64) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text,
                        unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent,
                        unfocusedBorderColor = p.line,
                        cursorColor = p.accent,
                    ),
                )
            },
            confirmButton = {
                TextButton({
                    renaming = false
                    FxSync.setDeviceName(name)
                }) { Text("Save", color = p.accent) }
            },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel", color = p.text) } },
        )
    }
    if (signingOut) {
        val p = LocalPalette.current
        AlertDialog(
            onDismissRequest = { signingOut = false },
            containerColor = p.card,
            title = { Text("Sign out?", color = p.text, fontSize = 18.sp) },
            text = { Text("Syncing stops. Your saved passwords stay on this phone.", color = p.mute) },
            confirmButton = {
                TextButton({
                    signingOut = false
                    FxSync.signOut()
                }) { Text("Sign out", color = p.danger) }
            },
            dismissButton = { TextButton({ signingOut = false }) { Text("Cancel", color = p.text) } },
        )
    }
    if (showPasswords) PasswordsDialog { showPasswords = false }
}

@Composable
private fun ActionRow(title: String, sub: String?, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = if (danger) p.danger else p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, color = p.faint, fontSize = 13.sp)
    }
}

/** The saved passwords: tap one to show its password, copy it, or delete it. */
@Composable
private fun PasswordsDialog(onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<SavedPassword>?>(null) }
    var shown by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(version) { FxSync.listPasswords { list = it } }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text("Saved passwords", color = p.text, fontSize = 18.sp) },
        text = {
            val l = list
            when {
                l == null -> Text("Loading…", color = p.faint)
                l.isEmpty() -> Text("No saved passwords yet.", color = p.faint)
                else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(l, key = { it.id }) { login ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { shown = if (shown == login.id) null else login.id }
                                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(login.site, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(login.username.ifBlank { "(no username)" }, color = p.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (shown == login.id) {
                                    Text(login.password, color = p.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            IconButtonSmall(Icons.Outlined.ContentCopy, "Copy password", size = 40) {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Password", login.password))
                            }
                            Spacer(Modifier.width(2.dp))
                            IconButtonSmall(Icons.Outlined.DeleteOutline, "Delete", size = 40, tint = Color(0xFFE57373)) {
                                FxSync.deletePassword(login.id) { version++ }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = p.accent) } },
    )
}

/**
 * SonderWeb Sync, the first time it opens: asks whether to sign in to Firefox Sync. Asked once;
 * "Not now" leaves it off (Settings › Firefox Sync turns it on any time).
 */
@Composable
internal fun FirstSyncAsk(ui: UiState) {
    var show by remember { mutableStateOf(FxSync.shouldAskFirst()) }
    if (!show) return
    val p = LocalPalette.current
    fun done() {
        show = false
        FxSync.askedFirst()
    }
    AlertDialog(
        onDismissRequest = { done() },
        containerColor = p.card,
        title = { Text("Sign in to Firefox Sync?", color = p.text, fontSize = 18.sp) },
        text = {
            Text(
                "Use your Mozilla account to bring your saved passwords from Firefox, and keep them in step " +
                    "on all your devices. They're encrypted on this phone first: Mozilla can't read them.",
                color = p.mute,
            )
        },
        confirmButton = {
            TextButton({
                done()
                startSignIn(ui)
            }) { Text("Sign in", color = p.accent) }
        },
        dismissButton = {
            TextButton({ done() }) { Text("Not now", color = p.text) }
        },
    )
}
