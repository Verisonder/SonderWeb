package com.verisonder.sonderweb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.data.Prefs
import com.verisonder.sonderweb.data.Transfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The other SonderWeb app handing over everything (Settings › Copy to …): asks first, then copies
 * it in and starts the app afresh. Only the other SonderWeb can open this (signature permission).
 */
class TransferActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Transfer.Incoming?>(null)
    private var working by mutableStateOf(false)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        // Only from the other SonderWeb, signed with the same key.
        if (intent?.action != Transfer.ACTION || uri == null || !Transfer.sameSigner(this)) {
            finish()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val inc = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { Transfer.read(it) } }.getOrNull()
            }
            if (inc == null) failed = true else incoming = inc
        }
        val theme = Prefs(this).theme
        setContent {
            SonderTheme(paletteFor(theme)) {
                val p = LocalPalette.current
                val inc = incoming
                when {
                    failed -> AlertDialog(
                        onDismissRequest = { finish() },
                        containerColor = p.card,
                        title = { Text("Couldn't copy", color = p.text, fontSize = 18.sp) },
                        text = { Text("What ${Transfer.otherName} sent couldn't be read or copied in. Try again from ${Transfer.otherName}.", color = p.mute) },
                        confirmButton = { TextButton({ finish() }) { Text("OK", color = p.accent) } },
                    )
                    inc != null -> AlertDialog(
                        onDismissRequest = { if (!working) finish() },
                        containerColor = p.card,
                        title = { Text("Copy everything from ${Transfer.otherName}?", color = p.text, fontSize = 18.sp) },
                        text = {
                            Column {
                                Text(
                                    "Its settings replace this app's (look, search, privacy, start page and wallpaper). " +
                                        "Its history, bookmarks (${inc.bookmarkCount}) and open tabs (${inc.tabCount}) are added " +
                                        "to the ones here: nothing here is lost.",
                                    color = p.mute,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Not copied: sign-ins to sites (cookies), extensions, site permissions, " +
                                        "and the Firefox Sync account with its passwords, addresses and cards.",
                                    color = p.faint,
                                    fontSize = 13.sp,
                                )
                                if (working) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Copying…", color = p.accent)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                {
                                    if (working) return@TextButton
                                    working = true
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching { Transfer.apply(this@TransferActivity, inc) }.isSuccess
                                        }
                                        if (ok) {
                                            Transfer.restart(this@TransferActivity)
                                        } else {
                                            working = false
                                            failed = true
                                        }
                                    }
                                },
                                enabled = !working,
                            ) { Text("Copy", color = p.accent) }
                        },
                        dismissButton = {
                            TextButton({ finish() }, enabled = !working) { Text("Cancel", color = p.text) }
                        },
                    )
                }
            }
        }
    }
}
