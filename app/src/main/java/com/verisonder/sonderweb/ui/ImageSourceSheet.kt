package com.verisonder.sonderweb.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Search with a picture (from the widget): take a photo or choose one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceSheet(activity: MainActivity) {
    val p = LocalPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Picking: the sheet slides away first, then the camera or the photos open.
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch { state.hide() }.invokeOnCompletion {
            activity.askImage = false
            then()
        }
    }
    ModalBottomSheet(
        onDismissRequest = { activity.askImage = false },
        sheetState = state,
        containerColor = p.bar,
        contentColor = p.text,
        dragHandle = null,
    ) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            Text(
                "Search with a picture",
                color = p.faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            MenuRow(Icons.Outlined.PhotoCamera, "Take a photo") { close { activity.takePhotoForSearch() } }
            MenuRow(Icons.Outlined.PhotoLibrary, "Choose a photo") { close { activity.choosePhotoForSearch() } }
        }
    }
}
