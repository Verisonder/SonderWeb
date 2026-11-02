package com.verisonder.sonderweb.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Four claw scratches: the "Shred site data" icon. */
val ShredIcon: ImageVector by lazy {
    val b = ImageVector.Builder("Shred", 24.dp, 24.dp, 24f, 24f)
    listOf(
        "M2.8,2.8 L2.89,4.18 L3.43,5.46 L4,6.73 L4.32,8.05 L4.64,9.38 L5.21,10.65 L5.62,11.95 L5.7,13.34 L5.92,14.69 L6.32,16 L6.48,17.36 L6.51,18.76 L6.68,20.12 L7.2,21.4 L7.83,19.85 L7.96,18.41 L8.13,16.97 L8.11,15.57 L7.8,14.24 L7.63,12.88 L7.57,11.49 L7.14,10.19 L6.52,8.93 L6.11,7.63 L5.65,6.34 L4.88,5.11 L4.04,3.91 Z",
        "M6.9,2 L7.07,3.38 L7.65,4.64 L8.07,5.95 L8.49,7.27 L9.12,8.52 L9.59,9.82 L9.74,11.2 L10.05,12.54 L10.55,13.83 L10.78,15.19 L10.83,16.59 L11.05,17.96 L11.29,19.31 L11.8,20.6 L12.44,19.01 L12.5,17.58 L12.47,16.16 L12.55,14.72 L12.41,13.33 L11.96,12.03 L11.68,10.69 L11.51,9.31 L10.98,8.03 L10.26,6.8 L9.71,5.52 L9.1,4.26 L8.21,3.07 Z",
        "M11,1.7 L11.28,2.93 L11.82,4.07 L12.36,5.21 L13.06,6.29 L13.64,7.41 L13.93,8.64 L14.36,9.82 L14.99,10.93 L15.33,12.14 L15.48,13.42 L15.82,14.63 L16.19,15.83 L16.4,17.08 L17,18.2 L17.51,16.68 L17.59,15.32 L17.41,14.05 L17.2,12.79 L17.14,11.48 L16.85,10.25 L16.24,9.14 L15.79,7.96 L15.45,6.75 L14.79,5.66 L13.95,4.63 L13.22,3.56 L12.39,2.53 Z",
    ).forEach { b.addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    b.build()
}
