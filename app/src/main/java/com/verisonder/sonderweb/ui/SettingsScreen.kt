package com.verisonder.sonderweb.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.BuildConfig
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Ublock
import com.verisonder.sonderweb.core.SearchEngines
import com.verisonder.sonderweb.data.Place
import com.verisonder.sonderweb.data.Weather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.StorageController

@Composable
fun SettingsScreen(ui: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = Browser.prefs
    var engine by remember { mutableStateOf(prefs.searchEngineId) }
    var custom by remember { mutableStateOf(prefs.customSearch) }
    var strict by remember { mutableStateOf(prefs.strictTracking) }
    var httpsOnly by remember { mutableStateOf(prefs.httpsOnly) }
    var history by remember { mutableStateOf(prefs.saveHistory) }
    var restore by remember { mutableStateOf(prefs.restoreTabs) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var barTop by remember { mutableStateOf(prefs.barOnTop) }
    var clearing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", { ui.panel = Panel.None })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Section("Search")
            SearchEngines.builtIn.forEach { e ->
                Choice(e.name, engine == e.id) {
                    engine = e.id
                    prefs.searchEngineId = e.id
                }
            }
            Choice("Custom", engine == SearchEngines.CUSTOM_ID) {
                engine = SearchEngines.CUSTOM_ID
                prefs.searchEngineId = SearchEngines.CUSTOM_ID
            }
            if (engine == SearchEngines.CUSTOM_ID) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.field)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (custom.isEmpty()) Text("https://example.com/search?q={q}", color = p.faint, fontSize = 14.sp)
                        BasicTextField(
                            custom,
                            {
                                custom = it
                                prefs.customSearch = it
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(p.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        if (SearchEngines.isValidTemplate(custom)) "Your search goes where {q} is." else "Needs to start with https:// and contain {q}. Until then DuckDuckGo is used.",
                        color = if (SearchEngines.isValidTemplate(custom)) p.faint else p.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Hint("Type !w, !yt, !gh … before or after your words to search a site directly. Unknown bangs go through DuckDuckGo.")

            StartPageSettings(ui)

            Section("Privacy")
            Toggle("Strict tracking protection", "Firefox's own blocking on top of uBlock Origin. Turn off if a site breaks.", strict) {
                strict = it
                prefs.strictTracking = it
                Browser.applySettings()
            }
            Toggle("HTTPS only", "Never load a site over an unencrypted connection.", httpsOnly) {
                httpsOnly = it
                prefs.httpsOnly = it
                Browser.applySettings()
            }
            Toggle("Keep history", "Off: pages you visit aren't remembered (private tabs never are).", history) {
                history = it
                prefs.saveHistory = it
            }
            Toggle("Reopen tabs", "Bring back your normal tabs when the app starts.", restore) {
                restore = it
                prefs.restoreTabs = it
            }
            Action("Clear browsing data", "History, cookies, site data, cache") { clearing = true }

            Section("uBlock Origin")
            Action(
                "Filters and settings",
                if (Ublock.extension != null) "Version " + BuildConfig.UBLOCK_VERSION + " · built in" else "Starting…",
            ) { if (Ublock.extension != null) {
                ui.panel = Panel.None
                Ublock.openDashboard()
            } }
            Hint("Tap the shield in the address bar to see what a page blocked, or to turn blocking off for that site.")

            Section("Look")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Address bar", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Segmented(listOf("Top", "Bottom"), if (barTop) 0 else 1, p.accent) {
                    barTop = it == 0
                    prefs.barOnTop = barTop
                    ui.themeVersion++
                }
            }
            Choice("Follow the system", theme == 0) { theme = 0; prefs.theme = 0; Browser.applySettings(); ui.themeVersion++ }
            Choice("Light", theme == 1) { theme = 1; prefs.theme = 1; Browser.applySettings(); ui.themeVersion++ }
            Choice("Dark", theme == 2) { theme = 2; prefs.theme = 2; Browser.applySettings(); ui.themeVersion++ }

            Section("Default browser")
            Action("Make SonderWeb the default", "Links from other apps open here") {
                val i = if (Build.VERSION.SDK_INT >= 29) {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    } else {
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    }
                } else {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
                runCatching { ctx.startActivity(i) }
            }

            Section("About")
            Hint(
                "SonderWeb " + BuildConfig.VERSION_NAME + " by Verisonder · Apache License 2.0\n" +
                    "Engine: GeckoView (Mozilla Public License 2.0)\n" +
                    "uBlock Origin by Raymond Hill (GPL-3.0), included unmodified\n" +
                    "No accounts, no telemetry, no tracking.",
            )
            Action("Source code", "github.com/Verisonder/SonderWeb") {
                ui.panel = Panel.None
                Browser.newTab("https://github.com/Verisonder/SonderWeb", isPrivate = false)
            }
        }
    }

    if (clearing) ClearDataDialog { clearing = false }
}

@Composable
private fun ClearDataDialog(onDone: () -> Unit) {
    val p = LocalPalette.current
    var hist by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = p.card,
        title = { Text("Clear browsing data", color = p.text) },
        text = {
            Column {
                Check("History", hist) { hist = it }
                Check("Cookies and site data (signs you out)", cookies) { cookies = it }
                Check("Cached images and files", cache) { cache = it }
                Check("Downloads list (files stay)", downloads) { downloads = it }
            }
        },
        confirmButton = {
            TextButton({
                if (hist) Browser.db.clearHistory()
                if (downloads) Browser.db.clearDownloads()
                var flags = 0L
                if (cookies) {
                    flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or
                        StorageController.ClearFlags.AUTH_SESSIONS
                }
                if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
                if (flags != 0L) {
                    Browser.clearData(flags) { Browser.host?.toast("Cleared") }
                } else {
                    Browser.host?.toast("Cleared")
                }
                onDone()
            }) { Text("Clear", color = p.danger) }
        },
        dismissButton = { TextButton(onDone) { Text("Cancel", color = p.text) } },
    )
}

@Composable
private fun Check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(on, onChange, colors = CheckboxDefaults.colors(checkedColor = p.accent))
        Text(label, color = p.text, fontSize = 14.sp)
    }
}

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Text(
        title.uppercase(),
        color = p.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) p.accent else p.field),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(p.onAccent))
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = p.faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun Action(title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = p.text, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = p.faint, fontSize = 12.sp)
    }
}


@Composable
private fun StartPageSettings(ui: UiState) {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(prefs.userName) }
    var weatherOn by remember { mutableStateOf(prefs.weatherOn) }
    var fahrenheit by remember { mutableStateOf(prefs.fahrenheit) }
    var place by remember { mutableStateOf(prefs.place) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    Section("Start page")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Your name", color = p.text, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        SettingsField(name, "Shown in the greeting") {
            name = it
            prefs.userName = it
            ui.themeVersion++
        }
    }
    Toggle("Weather", "From Open-Meteo. Only the place you pick below is sent.", weatherOn) {
        weatherOn = it
        prefs.weatherOn = it
        if (!it) Weather.forget()
        ui.themeVersion++
    }
    if (weatherOn) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(place?.name ?: "No place yet", color = if (place == null) p.faint else p.text, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            SettingsField(query, "Search a city") { query = it }
            LaunchedEffect(query) {
                if (query.trim().length < 2) {
                    results = emptyList()
                    return@LaunchedEffect
                }
                delay(350)
                searching = true
                results = Weather.search(query)
                searching = false
            }
            if (searching) Text("Searching…", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            results.forEach { r ->
                Text(
                    r.name,
                    color = p.text,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            place = r
                            prefs.place = r
                            query = ""
                            results = emptyList()
                            scope.launch { Weather.refresh(prefs, force = true) }
                            ui.themeVersion++
                        }
                        .padding(vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("°C", "°F"), if (fahrenheit) 1 else 0, p.accent) {
                fahrenheit = it == 1
                prefs.fahrenheit = fahrenheit
                Weather.refresh(prefs, force = true)
                ui.themeVersion++
            }
        }
    }
    Hint("Long-press a shortcut on the start page to edit or remove it.")
}

@Composable
private fun SettingsField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.field)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 14.sp)
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
