package com.verisonder.sonderweb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
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

/** Settings › Firefox Sync: the option, your Mozilla account, and the saved passwords. */
@Composable
internal fun SyncPage(ui: UiState) {
    var signingOut by remember { mutableStateOf(false) }
    var showPasswords by remember { mutableStateOf(false) }
    fun signIn() = FxSync.signIn { url ->
        ui.panel = Panel.None
        Browser.newTab(url, isPrivate = false)
    }

    GroupTitle("Firefox Sync")
    Group {
        Toggle(
            "Firefox Sync",
            "Sign in with your Mozilla account: SonderWeb saves passwords and syncs them with Firefox",
            FxSync.enabled,
        ) { FxSync.turn(it) }
    }

    if (FxSync.enabled) {
        GroupTitle("Account")
        Group {
            when {
                !FxSync.signedIn -> ActionRow("Sign in", "Opens Mozilla's sign-in page in a tab") { signIn() }
                FxSync.needsSignIn -> {
                    ActionRow(FxSync.email ?: "Your account", "Signed in")
                    ActionRow("Sign in again", "Your account asks you to sign in again to keep syncing") { signIn() }
                }
                else -> {
                    ActionRow(FxSync.email ?: "Your account", "Signed in")
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
                }
            }
            if (FxSync.signedIn) ActionRow("Sign out", "Your saved passwords stay on this phone", danger = true) { signingOut = true }
        }
        FxSync.problem?.let { Hint(it) }

        GroupTitle("Passwords")
        Group {
            ActionRow(
                "Saved passwords",
                when (FxSync.passwordCount) {
                    0 -> "None yet: SonderWeb offers to save them when you sign in to a site"
                    1 -> "1 saved"
                    else -> "${FxSync.passwordCount} saved"
                },
            ) { showPasswords = true }
        }
        Hint(
            "Passwords are encrypted on your phone before they're synced, so Mozilla can't read them. " +
                "Only passwords sync for now. SonderWeb signs in to your account as Firefox for Android.",
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
                FxSync.turn(true)
                FxSync.signIn { url ->
                    ui.panel = Panel.None
                    Browser.newTab(url, isPrivate = false)
                }
            }) { Text("Sign in", color = p.accent) }
        },
        dismissButton = {
            TextButton({ done() }) { Text("Not now", color = p.text) }
        },
    )
}
