package com.verisonder.sonderweb.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.sync.FxSync
import com.verisonder.sonderweb.sync.SavedAddress
import com.verisonder.sonderweb.sync.SavedCard

@Composable
private fun formFieldColors() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = p.text,
        unfocusedTextColor = p.text,
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.line,
        cursorColor = p.accent,
        focusedLabelColor = p.accent,
        unfocusedLabelColor = p.mute,
    )
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, single: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = single,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = formFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
}

/** A list of saved things: tap one to edit it, the bin deletes it, "Add" makes a new one. */
@Composable
private fun <T> SavedList(
    title: String,
    empty: String,
    list: List<T>?,
    key: (T) -> String,
    line1: (T) -> String,
    line2: (T) -> String,
    onOpen: (T?) -> Unit,
    onDelete: (T) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text(title, color = p.text, fontSize = 18.sp) },
        text = {
            when {
                list == null -> Text("Loading…", color = p.faint)
                list.isEmpty() -> Text(empty, color = p.faint)
                else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(list, key = key) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpen(item) }
                                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line1(item), color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = line2(item)
                                if (sub.isNotBlank()) {
                                    Text(sub, color = p.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.width(2.dp))
                            IconButtonSmall(Icons.Outlined.DeleteOutline, "Delete", size = 40, tint = Color(0xFFE57373)) { onDelete(item) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = p.accent) } },
        dismissButton = { TextButton(onClick = { onOpen(null) }) { Text("Add", color = p.text) } },
    )
}

/** The saved addresses (for filling in forms). */
@Composable
internal fun AddressesDialog(onClose: () -> Unit) {
    var list by remember { mutableStateOf<List<SavedAddress>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<SavedAddress?>(null) }
    LaunchedEffect(version) { FxSync.listAddresses { list = it } }
    val e = editing
    if (e != null) {
        AddressEditor(e, { editing = null }) { a ->
            FxSync.saveAddress(a) { version++ }
            editing = null
        }
        return
    }
    SavedList(
        "Saved addresses",
        "No saved addresses yet. SonderWeb offers to save the ones you type into forms.",
        list,
        { it.guid ?: "" },
        { it.name.ifBlank { it.email.ifBlank { "Address" } } },
        { listOf(it.street.lineSequence().firstOrNull().orEmpty(), it.city, it.country).filter { s -> s.isNotBlank() }.joinToString(", ") },
        { editing = it ?: SavedAddress() },
        { a -> a.guid?.let { FxSync.deleteAddress(it) { version++ } } },
        onClose,
    )
}

@Composable
private fun AddressEditor(start: SavedAddress, onDismiss: () -> Unit, onSave: (SavedAddress) -> Unit) {
    val p = LocalPalette.current
    var a by remember { mutableStateOf(start) }
    val ok = listOf(a.name, a.street, a.email, a.tel).any { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(if (start.guid == null) "Add address" else "Edit address", color = p.text, fontSize = 18.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Field("Name", a.name) { a = a.copy(name = it) }
                Field("Organization", a.organization) { a = a.copy(organization = it) }
                Field("Street address", a.street, single = false) { a = a.copy(street = it) }
                Field("City", a.city) { a = a.copy(city = it) }
                Field("State or region", a.region) { a = a.copy(region = it) }
                Field("Postal code", a.postalCode) { a = a.copy(postalCode = it) }
                Field("Country (code, e.g. MA)", a.country) { a = a.copy(country = it.uppercase().take(2)) }
                Field("Phone", a.tel, KeyboardType.Phone) { a = a.copy(tel = it) }
                Field("Email", a.email, KeyboardType.Email) { a = a.copy(email = it.trim()) }
            }
        },
        confirmButton = {
            TextButton({ if (ok) onSave(a) }, enabled = ok) { Text("Save", color = if (ok) p.accent else p.faint) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}

/** The saved payment cards (for filling in forms). Numbers are shown by their last four digits only. */
@Composable
internal fun CardsDialog(onClose: () -> Unit) {
    var list by remember { mutableStateOf<List<SavedCard>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<SavedCard?>(null) }
    LaunchedEffect(version) { FxSync.listCards { list = it } }
    val e = editing
    if (e != null) {
        CardEditor(e, { editing = null }) { c, number ->
            FxSync.saveCard(c, number) { version++ }
            editing = null
        }
        return
    }
    SavedList(
        "Saved cards",
        "No saved cards yet. SonderWeb offers to save the ones you type into forms.",
        list,
        { it.guid ?: "" },
        { it.summary },
        { it.name },
        { editing = it ?: SavedCard() },
        { c -> c.guid?.let { FxSync.deleteCard(it) { version++ } } },
        onClose,
    )
}

@Composable
private fun CardEditor(start: SavedCard, onDismiss: () -> Unit, onSave: (SavedCard, String) -> Unit) {
    val p = LocalPalette.current
    val editing = start.guid != null
    var name by remember { mutableStateOf(start.name) }
    var number by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(if (start.month > 0) start.month.toString() else "") }
    var year by remember { mutableStateOf(if (start.year > 0) start.year.toString() else "") }
    val digits = number.filter { it.isDigit() }
    val m = month.toIntOrNull()
    val y = year.toIntOrNull()
    val ok = (digits.length in 12..19 || (editing && digits.isEmpty())) && m != null && m in 1..12 && y != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(if (editing) "Edit card" else "Add card", color = p.text, fontSize = 18.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Field(if (editing) "Card number (•••• ${start.last4}; leave empty to keep)" else "Card number", number, KeyboardType.Number) {
                    number = it.filter { c -> c.isDigit() || c == ' ' }.take(23)
                }
                Field("Name on card", name) { name = it }
                Row {
                    Column(Modifier.weight(1f)) {
                        Field("Month (MM)", month, KeyboardType.Number) { month = it.filter { c -> c.isDigit() }.take(2) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Field("Year (YYYY)", year, KeyboardType.Number) { year = it.filter { c -> c.isDigit() }.take(4) }
                    }
                }
                Text("Encrypted on this phone before it's saved or synced.", color = p.faint, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton({ if (ok) onSave(start.copy(name = name.trim(), month = m!!, year = y!!), digits) }, enabled = ok) {
                Text("Save", color = if (ok) p.accent else p.faint)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.text) } },
    )
}

/** "Send to device": your devices that take tabs; tap one to send the page there. */
@Composable
internal fun SendTabDialog(title: String, url: String, onClose: () -> Unit) {
    val p = LocalPalette.current
    var targets by remember { mutableStateOf<List<com.verisonder.sonderweb.sync.SendTarget>?>(null) }
    LaunchedEffect(Unit) { FxSync.loadSendTargets { targets = it } }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text("Send to device", color = p.text, fontSize = 18.sp) },
        text = {
            val l = targets
            when {
                l == null -> Text("Looking for your devices…", color = p.faint)
                l.isEmpty() -> Text(
                    "No other devices that take tabs. Sign in to Firefox Sync on another device (Firefox or SonderWeb Sync).",
                    color = p.faint,
                )
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(l, key = { it.id }) { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onClose()
                                    FxSync.sendTab(d, title, url) { ok ->
                                        com.verisonder.sonderweb.browser.Browser.host?.toast(
                                            if (ok) "Sent to ${d.name}" else "Couldn't send to ${d.name}",
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                if (d.kind == "desktop") Icons.Outlined.Computer else Icons.Outlined.PhoneAndroid,
                                null,
                                tint = p.accent,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(d.name, color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClose) { Text("Cancel", color = p.text) } },
    )
}
