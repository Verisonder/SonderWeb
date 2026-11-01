package com.verisonder.sonderweb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.UrlText

@Composable
fun TabsScreen(ui: UiState) {
    val p = LocalPalette.current
    var privateMode by remember { mutableStateOf(Browser.current?.isPrivate ?: false) }
    val list = Browser.tabs.filter { it.isPrivate == privateMode }
    val accent = if (privateMode) PrivatePalette.accent else p.accent

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(
                listOf("Tabs · " + Browser.tabs.count { !it.isPrivate }, "Private · " + Browser.tabs.count { it.isPrivate }),
                if (privateMode) 1 else 0,
                accent,
            ) { privateMode = it == 1 }
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                color = p.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { ui.panel = Panel.None }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                Text(
                    if (privateMode) "No private tabs" else "No tabs",
                    color = p.faint,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { t ->
                    TabCard(t, selected = t === Browser.current, accent = accent, modifier = Modifier.animateItem()) {
                        Browser.select(t)
                        ui.panel = Panel.None
                    }
                }
            }
            // Locked: the private tabs' titles stay hidden too.
            if (privateMode && Browser.privateLocked && list.isNotEmpty()) PrivateLock(onLeave = null)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Close all",
                color = if (list.isEmpty()) p.faint else p.danger,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = list.isNotEmpty()) { Browser.closeAll(privateMode) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(accent)
                    .clickable {
                        Browser.newTab(null, isPrivate = privateMode)
                        ui.panel = Panel.None
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = p.onAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (privateMode) "Private tab" else "New tab", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.card)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier.border(1.dp, p.line, shape))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (tab.isBlank) "New tab" else tab.displayTitle,
                color = p.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { Browser.close(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Close tab", tint = p.mute, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(p.bg),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = tab.thumbnail
            if (thumb != null && !tab.isBlank) {
                Image(
                    thumb,
                    null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tab.isBlank) {
                        Mark(40, p.faint, accent)
                    } else {
                        Text(
                            UrlText.host(tab.url).firstOrNull()?.uppercase() ?: "•",
                            color = p.mute,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(UrlText.host(tab.url), color = p.faint, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) p.onAccent else p.mute,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
