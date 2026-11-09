package com.verisonder.sonderweb.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.verisonder.sonderweb.R
import org.json.JSONObject

/** How one search widget looks (set on its settings screen). A colour of null follows the theme. */
data class WidgetLook(
    val theme: Int = DARK,
    val mono: Boolean = true,
    val logo: Boolean = true,
    val arcs: Boolean = false,
    val voice: Boolean = true,
    val image: Boolean = true,
    val label: Boolean = true,
    val shadow: Boolean = false,
    val bg: Int? = null,
    val text: Int? = null,
    val tint: Int? = null,
) {
    companion object {
        const val DARK = 0
        const val LIGHT = 1
        const val FOLLOW = 2
        const val ADAPTIVE = 3

        private fun sp(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun load(context: Context, id: Int): WidgetLook {
            val raw = sp(context).getString("look_$id", null) ?: return WidgetLook()
            return runCatching {
                val j = JSONObject(raw)
                fun color(k: String): Int? = if (j.has(k)) j.getLong(k).toInt() else null
                WidgetLook(
                    theme = j.optInt("theme", DARK).let { if (it == ADAPTIVE && Build.VERSION.SDK_INT < 31) DARK else it },
                    mono = j.optBoolean("mono", true),
                    logo = j.optBoolean("logo", true),
                    arcs = j.optBoolean("arcs", false),
                    voice = j.optBoolean("voice", true),
                    image = j.optBoolean("image", true),
                    label = j.optBoolean("label", true),
                    shadow = j.optBoolean("shadow", false),
                    bg = color("bg"),
                    text = color("text"),
                    tint = color("tint"),
                )
            }.getOrDefault(WidgetLook())
        }

        fun save(context: Context, id: Int, look: WidgetLook) {
            val j = JSONObject()
                .put("theme", look.theme)
                .put("mono", look.mono)
                .put("logo", look.logo)
                .put("arcs", look.arcs)
                .put("voice", look.voice)
                .put("image", look.image)
                .put("label", look.label)
                .put("shadow", look.shadow)
            look.bg?.let { j.put("bg", it.toLong() and 0xFFFFFFFFL) }
            look.text?.let { j.put("text", it.toLong() and 0xFFFFFFFFL) }
            look.tint?.let { j.put("tint", it.toLong() and 0xFFFFFFFFL) }
            sp(context).edit().putString("look_$id", j.toString()).apply()
        }

        fun forget(context: Context, id: Int) {
            sp(context).edit().remove("look_$id").apply()
        }
    }
}

/** The home screen search widget: tap to search, the mic for voice search, the frame to search with a picture. */
class SearchWidget : AppWidgetProvider() {

    companion object {
        const val SEARCH = "com.verisonder.sonderweb.WIDGET_SEARCH"
        const val VOICE = "com.verisonder.sonderweb.WIDGET_VOICE"
        const val IMAGE = "com.verisonder.sonderweb.WIDGET_IMAGE"

        // The fixed themes: background, text, icons, coloured icons.
        private val DARK_COLORS = intArrayOf(0xFF1C1C1C.toInt(), 0xFFF4F4F4.toInt(), 0xFFDEDEDE.toInt(), 0xFF8AB4F8.toInt())
        private val LIGHT_COLORS = intArrayOf(0xFFF1F3F4.toInt(), 0xFF202124.toInt(), 0xFF3C4043.toInt(), 0xFF1A73E8.toInt())
        private val FOLLOW_RES = intArrayOf(R.color.widget_follow_bg, R.color.widget_follow_text, R.color.widget_follow_icon, R.color.widget_follow_accent)
        private val ADAPTIVE_RES = intArrayOf(R.color.widget_adaptive_bg, R.color.widget_adaptive_text, R.color.widget_adaptive_icon, R.color.widget_adaptive_accent)

        private const val BG = 0
        private const val TEXT = 1
        private const val ICON = 2
        private const val ACCENT = 3

        /** The search widgets on the home screen. */
        fun placed(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchWidget::class.java))

        /** From Settings: the widgets' settings screen, or, with none placed, asks the launcher to add one. */
        fun openSettings(context: Context) {
            val ids = placed(context)
            if (ids.isNotEmpty()) {
                context.startActivity(
                    Intent(context, WidgetSettingsActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids.last())
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
                return
            }
            val manager = AppWidgetManager.getInstance(context)
            val asked = manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(ComponentName(context, SearchWidget::class.java), null, null)
            if (!asked) {
                Toast.makeText(context, "Hold your home screen, then Widgets › SonderWeb", Toast.LENGTH_LONG).show()
            }
        }

        /** Redraws one widget with its saved look. */
        fun update(context: Context, id: Int) {
            AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, WidgetLook.load(context, id)))
        }

        /** The widget drawn with [look]. [clickable] is false for the preview on the settings screen. */
        fun views(context: Context, look: WidgetLook, clickable: Boolean = true): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_search)

            // A theme colour: a fixed one, or (Follow phone, Material adaptive) a resource the home
            // screen looks up itself, so it follows dark mode and the wallpaper without asking us.
            fun paint(view: Int, method: String, role: Int, custom: Int?) {
                when {
                    custom != null -> v.setInt(view, method, custom)
                    look.theme == WidgetLook.FOLLOW || look.theme == WidgetLook.ADAPTIVE -> {
                        val res = (if (look.theme == WidgetLook.FOLLOW) FOLLOW_RES else ADAPTIVE_RES)[role]
                        if (Build.VERSION.SDK_INT >= 31) v.setColor(view, method, res) else v.setInt(view, method, context.getColor(res))
                    }
                    else -> v.setInt(view, method, (if (look.theme == WidgetLook.LIGHT) LIGHT_COLORS else DARK_COLORS)[role])
                }
            }

            // Background: the colour (opaque) on the pill, and its transparency as the pill's own.
            val bg = look.bg
            paint(R.id.widget_bg, "setColorFilter", BG, bg?.let { it or 0xFF000000.toInt() })
            v.setInt(R.id.widget_bg, "setImageAlpha", bg?.let { it ushr 24 } ?: 255)
            v.setViewVisibility(R.id.widget_shadow, if (look.shadow) View.VISIBLE else View.GONE)
            // The pill fills the widget's width; with the shadow it leaves room for it.
            if (Build.VERSION.SDK_INT >= 31) {
                for (id in intArrayOf(R.id.widget_bg, R.id.widget_pill)) {
                    v.setViewLayoutMargin(id, RemoteViews.MARGIN_START, if (look.shadow) 3f else 0f, TypedValue.COMPLEX_UNIT_DIP)
                    v.setViewLayoutMargin(id, RemoteViews.MARGIN_END, if (look.shadow) 3f else 0f, TypedValue.COMPLEX_UNIT_DIP)
                    v.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, if (look.shadow) 1f else 3f, TypedValue.COMPLEX_UNIT_DIP)
                    v.setViewLayoutMargin(id, RemoteViews.MARGIN_BOTTOM, if (look.shadow) 5f else 3f, TypedValue.COMPLEX_UNIT_DIP)
                }
            }

            // The logo: one colour with its highlights cut out, the blue sphere, or just the three arcs.
            v.setViewVisibility(R.id.widget_logo, if (look.logo) View.VISIBLE else View.GONE)
            if (look.arcs) {
                v.setImageViewResource(R.id.widget_logo_disc, R.drawable.widget_logo_arcs)
                paint(R.id.widget_logo_disc, "setColorFilter", if (look.mono) ICON else ACCENT, look.tint)
                v.setViewVisibility(R.id.widget_logo_marks, View.GONE)
            } else if (look.mono || look.tint != null) {
                v.setImageViewResource(R.id.widget_logo_disc, R.drawable.widget_logo_disc)
                paint(R.id.widget_logo_disc, "setColorFilter", ICON, look.tint)
                v.setViewVisibility(R.id.widget_logo_marks, View.VISIBLE)
                paint(R.id.widget_logo_marks, "setColorFilter", BG, bg?.let { it or 0xFF000000.toInt() })
            } else {
                v.setImageViewResource(R.id.widget_logo_disc, R.drawable.widget_logo_color)
                // A see-through filter: the sphere's own colours show.
                v.setInt(R.id.widget_logo_disc, "setColorFilter", 0)
                v.setViewVisibility(R.id.widget_logo_marks, View.GONE)
            }

            v.setViewVisibility(R.id.widget_label, if (look.label) View.VISIBLE else View.GONE)
            paint(R.id.widget_label, "setTextColor", TEXT, look.text)

            val iconRole = if (look.mono) ICON else ACCENT
            v.setViewVisibility(R.id.widget_voice, if (look.voice) View.VISIBLE else View.GONE)
            paint(R.id.widget_voice, "setColorFilter", iconRole, look.tint)
            v.setViewVisibility(R.id.widget_image, if (look.image) View.VISIBLE else View.GONE)
            paint(R.id.widget_image, "setColorFilter", iconRole, look.tint)
            // Without the picture button, the mic takes its place at the end.
            if (Build.VERSION.SDK_INT >= 31) {
                v.setViewLayoutMargin(R.id.widget_voice, RemoteViews.MARGIN_END, if (look.image) 10f else 0f, TypedValue.COMPLEX_UNIT_DIP)
            }

            if (clickable) {
                v.setOnClickPendingIntent(R.id.widget_pill, open(context, SEARCH, 1))
                v.setOnClickPendingIntent(R.id.widget_voice, open(context, VOICE, 2))
                v.setOnClickPendingIntent(R.id.widget_image, open(context, IMAGE, 3))
            }
            return v
        }

        private fun open(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                code,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, WidgetLook.load(context, it))) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetLook.forget(context, it) }
    }
}
