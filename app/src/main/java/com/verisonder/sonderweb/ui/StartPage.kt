package com.verisonder.sonderweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.core.UrlText

/** The ring-and-dot mark (same as the app icon). */
@Composable
fun Mark(size: Int, ring: androidx.compose.ui.graphics.Color, dot: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension * 0.30f
        val c = center
        drawCircle(ring, radius = r, center = c, style = Stroke(width = this.size.minDimension * 0.085f))
        drawCircle(dot, radius = this.size.minDimension * 0.09f, center = Offset(c.x + r * 0.72f, c.y - r * 1.05f))
    }
}

/** What a new tab shows before you type anything: the mark and your bookmarks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPage(ui: UiState) {
    val p = LocalPalette.current
    val tab = Browser.current ?: return
    val marks = remember(ui.panel) { Browser.db.bookmarks().take(8) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .clickable(interactionSource = null, indication = null) { startEditing(ui, tab) }
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Mark(72, p.text, p.accent)
        Spacer(Modifier.height(18.dp))
        Text(
            if (tab.isPrivate) "Private tab" else "SonderWeb",
            color = p.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (tab.isPrivate) {
                "History, cookies and site data from this tab are forgotten when you close it."
            } else {
                "uBlock Origin is on. Search with !bangs, like !w or !yt."
            },
            color = p.mute,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (!tab.isPrivate && marks.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(if (marks.size > 4) 200.dp else 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                items(marks, key = { it.id }) { b ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { Browser.load(tab, b.url) })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.field),
                            contentAlignment = Alignment.Center,
                        ) {
                            val host = UrlText.host(b.url)
                            Text(
                                host.firstOrNull()?.uppercase() ?: "•",
                                color = p.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            b.title.ifBlank { UrlText.host(b.url) },
                            color = p.mute,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1.3f))
    }
}
