package com.verisonder.sonderweb.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The home screen icon: the round sphere (default) or the squircle glass. */
object AppIcon {
    private const val ROUND = "com.verisonder.sonderweb.IconRound"
    private const val SQUIRCLE = "com.verisonder.sonderweb.IconSquircle"

    fun isSquircle(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx, SQUIRCLE)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Turns one icon on before the other off, so the app never disappears from the home screen. */
    fun set(ctx: Context, squircle: Boolean) {
        val pm = ctx.packageManager
        val on = ComponentName(ctx, if (squircle) SQUIRCLE else ROUND)
        val off = ComponentName(ctx, if (squircle) ROUND else SQUIRCLE)
        pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
