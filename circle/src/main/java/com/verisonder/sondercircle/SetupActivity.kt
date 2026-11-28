package com.verisonder.sondercircle

import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SonderCircle's settings: the one switch it needs (screenshots), and the ways to open it
 * (Quick Settings tile, app icon, the phone's assistant).
 */
class SetupActivity : ComponentActivity() {
    // Bumped on resume so the statuses are read again after a trip to Android's settings.
    private var refresh by androidx.compose.runtime.mutableIntStateOf(0)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            @Suppress("UNUSED_EXPRESSION") refresh
            Setup(
                screenshots = CircleAccessibility.enabled(this),
                running = CircleAccessibility.instance != null,
                target = Handoff.target(this),
                both = Handoff.installed(this, Handoff.SONDERWEB) && Handoff.installed(this, Handoff.SONDERWEB_SYNC),
                textEngine = Handoff.textEngine(this),
                imageEngine = Handoff.imageEngine(this),
                onTextEngine = {
                    Handoff.setTextEngine(this, it)
                    refresh++
                },
                onImageEngine = {
                    Handoff.setImageEngine(this, it)
                    refresh++
                },
                delay = Handoff.delay(this),
                onDelay = {
                    Handoff.changeDelay(this, it)
                    refresh++
                },
                onTarget = {
                    val now = Handoff.target(this)
                    Handoff.choose(this, if (now == Handoff.SONDERWEB) Handoff.SONDERWEB_SYNC else Handoff.SONDERWEB)
                    refresh++
                },
                assistant = isAssistant(),
                needsScreenshot = intent?.getBooleanExtra(EXTRA_NEEDS_SCREENSHOT, false) == true,
                onScreenshots = ::openAccessibility,
                onAppInfo = ::openAppInfo,
                onTile = ::addTile,
                onAssistant = ::openAssistant,
                onTry = {
                    startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                version = Updater.current(this),
                onCheck = ::checkForUpdate,
                onUpdate = ::installUpdate,
            )
        }
        // A newer SonderCircle shows here as soon as the page opens.
        scope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) { Updater.cleanUp(this@SetupActivity) }
            runCatching { Updater.check(this@SetupActivity) }
        }
    }

    private fun checkForUpdate() {
        if (Updater.checking) return
        scope.launch {
            val r = runCatching { Updater.check(this@SetupActivity) }
            val text = when {
                r.isFailure -> "Couldn't check: " + (r.exceptionOrNull()?.message ?: "no connection")
                r.getOrNull() == null -> "SonderCircle is up to date"
                else -> null
            }
            if (text != null) Toast.makeText(this@SetupActivity, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun installUpdate() {
        scope.launch {
            runCatching { Updater.install(this@SetupActivity) }.onFailure {
                Toast.makeText(this@SetupActivity, it.message ?: "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun isAssistant(): Boolean = runCatching {
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }.getOrDefault(false)

    private fun openAccessibility() {
        val me = ComponentName(this, CircleAccessibility::class.java).flattenToString()
        // Straight to SonderCircle's own switch where the phone supports it, else the list.
        val direct = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, me)
        runCatching { startActivity(direct) }.onFailure {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openAssistant() {
        runCatching { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val bar = getSystemService(StatusBarManager::class.java)
            bar.requestAddTileService(
                ComponentName(this, CircleTile::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor,
            ) { result ->
                val text = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "The tile is already there"
                    else -> null
                }
                if (text != null) Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Edit your Quick Settings and drag SonderCircle in", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_NEEDS_SCREENSHOT = "needs_screenshot"
    }
}

private val Bg = Color(0xFF101114)
private val Card = Color(0xFF1B1D22)
private val Text1 = Color(0xFFECEDF0)
private val Text2 = Color(0xFF9DA1AA)
private val Accent = Color(0xFF6F8DFF)
val CircleGlow = listOf(Color(0xFF4F8CFF), Color(0xFF9B6BFF), Color(0xFFFF6FA8), Color(0xFF4F8CFF))

@Composable
private fun Setup(
    screenshots: Boolean,
    running: Boolean,
    target: String?,
    both: Boolean,
    textEngine: String,
    imageEngine: String,
    onTextEngine: (String) -> Unit,
    onImageEngine: (String) -> Unit,
    delay: Int,
    onDelay: (Int) -> Unit,
    onTarget: () -> Unit,
    assistant: Boolean,
    needsScreenshot: Boolean,
    onScreenshots: () -> Unit,
    onAppInfo: () -> Unit,
    onTile: () -> Unit,
    onAssistant: () -> Unit,
    onTry: () -> Unit,
    version: String,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(CircleGlow)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Bg))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
        }
        Spacer(Modifier.height(16.dp))
        Text("SonderCircle", color = Text1, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Circle anything on your screen to search it. Tap a word to pick text, copy it or translate it.",
            color = Text2,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))

        Section("Screenshots")
        Item(
            title = when {
                screenshots && running -> "On"
                screenshots -> "On, but stopped"
                else -> "Turn on"
            },
            text = when {
                screenshots && !running ->
                    "Android stopped it (this happens after a crash). Turn SonderCircle's switch off and on again."
                needsScreenshot && !screenshots ->
                    "SonderCircle needs this one switch before it can open. It only takes a screenshot when you open SonderCircle."
                else ->
                    "SonderCircle's switch under Accessibility. It only takes a screenshot when you open SonderCircle; it can't read your screen or your typing."
            },
            done = screenshots && running,
            onClick = onScreenshots,
        )
        if (!screenshots) {
            Item(
                title = "Switch greyed out?",
                text = "Android limits apps installed from outside a store. Open App info, tap ⋮ and choose \"Allow restricted settings\", then try again.",
                onClick = onAppInfo,
            )
        }

        Section("Opens in")
        Item(
            title = when (target) {
                null -> "Install SonderWeb"
                Handoff.SONDERWEB_SYNC -> "SonderWeb Sync"
                else -> "SonderWeb"
            },
            text = when {
                target == null -> "The circle and its results open in SonderWeb, with your own sign-ins. Install SonderWeb (or SonderWeb Sync) first."
                both -> "Both are installed: tap to switch. The results come with that app's sign-ins."
                else -> "The circle and its results open in it, with your own sign-ins."
            },
            done = target != null,
            onClick = if (both) onTarget else null,
        )

        Section("Search with")
        Picker(
            title = "Text",
            hint = "Picked words and typed searches. !bangs work too.",
            options = Handoff.textEngines,
            selected = textEngine,
            onPick = onTextEngine,
        )
        Picker(
            title = "Pictures",
            hint = "What you circle. DuckDuckGo can't search by picture.",
            options = Handoff.imageEngines,
            selected = imageEngine,
            onPick = onImageEngine,
        )

        Section("Ways to open it")
        Item(
            title = "Quick Settings tile",
            text = "Add SonderCircle to the panel you pull down from the top.",
            onClick = onTile,
        )
        Item(
            title = if (assistant) "Your assistant: on" else "As your assistant",
            text = "Where your phone lets you pick the digital assistant, choose SonderCircle: holding home or power opens it.",
            done = assistant,
            onClick = onAssistant,
        )
        Item(
            title = "App icon",
            text = "Tap SonderCircle's icon (from the home screen or a sidebar) to circle what's under it.",
            onClick = null,
        )

        Section("Advanced")
        DelayRow(delay, onDelay)

        Section("Updates")
        UpdateRow(version, onCheck, onUpdate)

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(if (screenshots && running) Accent else Card)
                .clickable(enabled = screenshots && running, onClick = onTry)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Try it", color = if (screenshots && running) Color.White else Text2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
private fun Item(title: String, text: String, done: Boolean = false, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(text, color = Text2, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (done) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

/** A setting with a few choices: shows the current one; tap to see them all and pick. */
@Composable
private fun Picker(title: String, hint: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val name = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(hint, color = Text2, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(name, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (expanded) {
            for ((id, label) in options) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(id)
                            expanded = false
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = Text1, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(if (id == selected) Accent else Color(0xFF33363D)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id == selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Settings › Advanced: a wait before the screenshot, 0 to 3 seconds in tenths (slide, or − and + for 0.1). */
@Composable
private fun DelayRow(tenths: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Delay before the screenshot", color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Time to open a menu or scroll first. SonderCircle gets out of the way, then takes the screenshot.",
                    color = Text2,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            StepButton("−", enabled = tenths > 0) { onChange(tenths - 1) }
            Text(
                if (tenths == 0) "Off" else "%.1f s".format(java.util.Locale.US, tenths / 10f),
                color = if (tenths == 0) Text2 else Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(52.dp),
            )
            StepButton("+", enabled = tenths < Handoff.MAX_DELAY) { onChange(tenths + 1) }
        }
        androidx.compose.material3.Slider(
            value = tenths.toFloat(),
            onValueChange = { onChange(kotlin.math.round(it).toInt()) },
            valueRange = 0f..Handoff.MAX_DELAY.toFloat(),
            steps = Handoff.MAX_DELAY - 1,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF33363D),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF33363D))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Text1 else Text2.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/** Like SonderWeb's: the version you have, a newer one when there is, one tap to install it. */
@Composable
private fun UpdateRow(version: String, onCheck: () -> Unit, onUpdate: () -> Unit) {
    val newer = Updater.available
    val progress = Updater.progress
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (newer != null) "Version $newer is ready" else "SonderCircle $version", color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    progress != null -> "Downloading… ${(progress * 100).toInt()}%"
                    Updater.checking -> "Looking for a new version…"
                    newer != null -> "You have $version"
                    else -> "Checks the SonderWeb releases on GitHub"
                },
                color = if (newer != null && progress == null) Accent else Text2,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        val busy = progress != null || Updater.checking
        Box(
            Modifier
                .clip(RoundedCornerShape(19.dp))
                .background(if (newer != null) Accent else Color(0xFF33363D))
                .clickable(enabled = !busy) { if (newer != null) onUpdate() else onCheck() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (newer != null) "Update" else "Check",
                color = if (busy) Text2 else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
