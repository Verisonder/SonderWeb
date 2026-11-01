package com.verisonder.sonderweb.ui

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser

/**
 * In private tabs the keyboard is asked not to learn what you type
 * (Gboard and most keyboards switch to incognito).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboard(on: Boolean, content: @Composable () -> Unit) {
    if (!on) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            val wrapped = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val ic = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    return ic
                }
            }
            next.startInputMethod(wrapped)
        },
        content = content,
    )
}

/** What covers the private tabs after you've left the app, until you unlock them. */
@Composable
fun PrivateLock(modifier: Modifier = Modifier, onLeave: (() -> Unit)?) {
    Box(
        modifier
            .fillMaxSize()
            .background(PrivatePalette.bg)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrivatePalette.field),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = PrivatePalette.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Private tabs are locked", color = PrivatePalette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Unlock with your fingerprint or screen lock.",
                color = PrivatePalette.faint,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Unlock",
                color = PrivatePalette.onAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PrivatePalette.accent)
                    .clickable { Browser.host?.unlockPrivate() }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            )
            if (onLeave != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Go to normal tabs",
                    color = PrivatePalette.text,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onLeave)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}
