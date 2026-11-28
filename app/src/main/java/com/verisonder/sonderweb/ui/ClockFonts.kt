package com.verisonder.sonderweb.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.R

/** A look for the start page's time. */
class ClockFont(
    val id: String,
    val name: String,
    val family: FontFamily,
    val weight: FontWeight,
    val size: TextUnit,
    val spacing: TextUnit,
)

/**
 * The start page clock's fonts. The bundled ones hold only the digits and the colon
 * (SIL Open Font License 1.1); the phone's is whatever font the phone uses (its theme's too).
 */
object ClockFonts {
    private val phone: FontFamily by lazy {
        val light = if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.DEFAULT, 300, false)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        FontFamily(light)
    }

    val all: List<ClockFont> by lazy {
        listOf(
            ClockFont("", "SonderWeb", Inter, FontWeight.ExtraLight, 88.sp, (-2).sp),
            ClockFont("phone", "Your phone's font", phone, FontWeight.Normal, 88.sp, (-2).sp),
            ClockFont("unbounded", "Unbounded", bundled(R.font.clock_unbounded), FontWeight.Normal, 76.sp, (-1).sp),
            ClockFont("philosopher", "Philosopher", bundled(R.font.clock_philosopher), FontWeight.Normal, 100.sp, 0.sp),
            ClockFont("dot", "Dot", bundled(R.font.clock_dot), FontWeight.Normal, 84.sp, 2.sp),
            // More looks, after the ones above.
            ClockFont("spacegrotesk", "Space Grotesk", bundled(R.font.clock_spacegrotesk), FontWeight.Normal, 88.sp, (-2).sp),
            ClockFont("fraunces", "Fraunces", bundled(R.font.clock_fraunces), FontWeight.Normal, 104.sp, (-1).sp),
            ClockFont("cinzel", "Cinzel", bundled(R.font.clock_cinzel), FontWeight.Normal, 100.sp, 0.sp),
            ClockFont("michroma", "Michroma", bundled(R.font.clock_michroma), FontWeight.Normal, 58.sp, 0.sp),
            ClockFont("majormono", "Major Mono", bundled(R.font.clock_majormono), FontWeight.Normal, 64.sp, (-4).sp),
            ClockFont("zendots", "Zen Dots", bundled(R.font.clock_zendots), FontWeight.Normal, 68.sp, 0.sp),
            ClockFont("bungee", "Bungee", bundled(R.font.clock_bungee), FontWeight.Normal, 76.sp, 0.sp),
            ClockFont("pixel", "Pixel", bundled(R.font.clock_silkscreen), FontWeight.Normal, 72.sp, 0.sp),
        )
    }

    private fun bundled(res: Int) = FontFamily(Font(res))

    fun of(id: String): ClockFont = all.firstOrNull { it.id == id } ?: all.first()
}

/** Hidden: hold the start page's time for 10 seconds. Each font shows the time as it is now. */
@Composable
fun ClockFontPicker(time: String, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("Clock font", color = p.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClockFonts.all.forEach { f ->
                    val on = f.id == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) p.field else Color.Transparent)
                            .clickable { onPick(f.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                time,
                                color = p.text,
                                fontFamily = f.family,
                                fontWeight = f.weight,
                                fontSize = f.size * 0.45f,
                                letterSpacing = f.spacing * 0.45f,
                                maxLines = 1,
                            )
                            Text(f.name, color = p.mute, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (on) p.accent else p.line),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(p.onAccent))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", color = p.accent) } },
    )
}
