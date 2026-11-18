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
    val label: Boolean = true,
    val shadow: Boolean = false,
    val bg: Int? = null,
    val text: Int? = null,
    val tint: Int? = null,
    /**
     * Up to [WidgetApp.MAX] apps, and the microphone and picture button ([voice], [image]) among
     * them: each on its side, in this order.
     */
    val apps: List<WidgetApp> = listOf(WidgetApp.button(WidgetApp.VOICE), WidgetApp.button(WidgetApp.IMAGE)),
    /** The apps' icon size in dp (an app can have its own). */
    val appSize: Int = WidgetApp.SIZE_DEFAULT,
) {
    companion object {
        const val DARK = 0
        const val LIGHT = 1
        const val FOLLOW = 2
        const val ADAPTIVE = 3

        private fun sp(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        /**
         * Widgets saved before the buttons were in the list: the microphone and picture button
         * (if shown) go after the apps on the right, where they always were.
         */
        private fun withButtons(apps: List<WidgetApp>, voice: Boolean, image: Boolean): List<WidgetApp> {
            if (apps.any { it.isButton }) return apps
            return apps + listOfNotNull(
                if (voice) WidgetApp.button(WidgetApp.VOICE) else null,
                if (image) WidgetApp.button(WidgetApp.IMAGE) else null,
            )
        }

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
                    label = j.optBoolean("label", true),
                    shadow = j.optBoolean("shadow", false),
                    bg = color("bg"),
                    text = color("text"),
                    tint = color("tint"),
                    apps = j.optJSONArray("apps")?.let { a ->
                        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { WidgetApp.fromJson(it) } }
                    }.orEmpty().let { list ->
                        val apps = list.filter { !it.isButton }.take(WidgetApp.MAX)
                        val kept = list.filter { it.isButton }.distinctBy { it.pkg } + apps
                        val ordered = list.filter { it in kept }.distinctBy { it.pkg + "/" + it.cls }
                        withButtons(ordered, j.optBoolean("voice", true), j.optBoolean("image", true))
                    },
                    appSize = j.optInt("app_size", WidgetApp.SIZE_DEFAULT).coerceIn(WidgetApp.SIZE_MIN, WidgetApp.SIZE_MAX),
                )
            }.getOrDefault(WidgetLook())
        }

        fun save(context: Context, id: Int, look: WidgetLook) {
            val j = JSONObject()
                .put("theme", look.theme)
                .put("mono", look.mono)
                .put("logo", look.logo)
                .put("arcs", look.arcs)
                .put("voice", look.apps.any { it.pkg == WidgetApp.VOICE })
                .put("image", look.apps.any { it.pkg == WidgetApp.IMAGE })
                .put("label", look.label)
                .put("shadow", look.shadow)
            look.bg?.let { j.put("bg", it.toLong() and 0xFFFFFFFFL) }
            look.text?.let { j.put("text", it.toLong() and 0xFFFFFFFFL) }
            look.tint?.let { j.put("tint", it.toLong() and 0xFFFFFFFFL) }
            // Always written (an empty list too: both buttons hidden, no apps).
            j.put("apps", org.json.JSONArray(look.apps.map { it.toJson() }))
            if (look.appSize != WidgetApp.SIZE_DEFAULT) j.put("app_size", look.appSize)
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
        const val HOME = "com.verisonder.sonderweb.WIDGET_HOME"

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
            AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, WidgetLook.load(context, id), id = id))
        }

        /** The widget drawn with [look]. [clickable] is false for the preview on the settings screen. */
        fun views(context: Context, look: WidgetLook, clickable: Boolean = true, id: Int = 0): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_search)

            // A theme colour: a fixed one, or (Follow phone, Material adaptive) a resource the home
            // screen looks up itself, so it follows dark mode and the wallpaper without asking us.
            fun paint(view: Int, method: String, role: Int, custom: Int?, rv: RemoteViews = v) {
                when {
                    custom != null -> rv.setInt(view, method, custom)
                    look.theme == WidgetLook.FOLLOW || look.theme == WidgetLook.ADAPTIVE -> {
                        val res = (if (look.theme == WidgetLook.FOLLOW) FOLLOW_RES else ADAPTIVE_RES)[role]
                        if (Build.VERSION.SDK_INT >= 31) rv.setColor(view, method, res) else rv.setInt(view, method, context.getColor(res))
                    }
                    else -> rv.setInt(view, method, (if (look.theme == WidgetLook.LIGHT) LIGHT_COLORS else DARK_COLORS)[role])
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

            // Your apps, in their own colours (not tinted), and the microphone and picture button (in
            // the icon colour): on the left after the logo or on the right, in your order. An
            // animation plays as its frames, flipped by the home screen; all animations together
            // share a budget of distinct pictures (a frame shown several times is the same picture,
            // sent once) and of steps.
            val apps = look.apps.filter { WidgetIcons.installed(context, it) }
            val gifs = apps.count { !it.isButton && it.frames > 1 }
            val perGif = if (gifs == 0) 0 else (FRAME_BUDGET / gifs).coerceAtLeast(2)
            val perSteps = if (gifs == 0) 0 else STEP_BUDGET / gifs
            val sides = listOf(apps.filter { it.left } to LEFT_VIEWS, apps.filter { !it.left } to APP_VIEWS)
            for ((list, views) in sides) {
                views.forEachIndexed { i, view ->
                    val app = list.getOrNull(i)
                    v.removeAllViews(view)
                    if (app != null && app.isButton) {
                        val voice = app.pkg == WidgetApp.VOICE
                        val button = RemoteViews(context.packageName, R.layout.widget_app_frame)
                        button.setImageViewResource(R.id.widget_app_frame, if (voice) R.drawable.widget_mic else R.drawable.widget_image_search)
                        paint(R.id.widget_app_frame, "setColorFilter", iconRole, look.tint, button)
                        v.addView(view, button)
                        v.setInt(view, "setFlipInterval", 100_000)
                        v.setViewVisibility(view, View.VISIBLE)
                        v.setContentDescription(view, context.getString(if (voice) R.string.widget_voice else R.string.widget_image))
                        if (Build.VERSION.SDK_INT >= 31) {
                            v.setViewLayoutWidth(view, 50f, TypedValue.COMPLEX_UNIT_DIP)
                            v.setViewLayoutHeight(view, 26f, TypedValue.COMPLEX_UNIT_DIP)
                        }
                        if (clickable) {
                            v.setOnClickPendingIntent(view, if (voice) open(context, VOICE, 2) else open(context, IMAGE, 3))
                        }
                        return@forEachIndexed
                    }
                    val all = app?.let { WidgetIcons.frames(context, it) }.orEmpty()
                    if (app == null || all.isEmpty()) {
                        v.setViewVisibility(view, View.GONE)
                        return@forEachIndexed
                    }
                    // Over the budget: every step-th picture kept, the others shown as the one before.
                    val step = if (all.size > 1) (all.size + perGif - 1) / perGif else 1
                    // (Icons made before the play order was kept: their pictures in turn.)
                    var order = (app.seq?.filter { it in all.indices }?.map { it - it % step }
                        ?: all.indices.filter { it % step == 0 })
                        .ifEmpty { listOf(0) }
                    // Too many steps: every few kept, each shown that much longer.
                    val every = if (all.size > 1 && perSteps > 0 && order.size > perSteps) (order.size + perSteps - 1) / perSteps else 1
                    if (every > 1) order = order.filterIndexed { n, _ -> n % every == 0 }
                    order.forEach { n ->
                        val frame = RemoteViews(context.packageName, R.layout.widget_app_frame)
                        frame.setImageViewBitmap(R.id.widget_app_frame, all[n])
                        v.addView(view, frame)
                    }
                    val stepMs = if (app.seq != null) app.interval else app.interval * step
                    v.setInt(view, "setFlipInterval", (stepMs * every).coerceAtLeast(40))
                    v.setViewVisibility(view, View.VISIBLE)
                    v.setContentDescription(view, WidgetIcons.label(context, app))
                    if (Build.VERSION.SDK_INT >= 31) {
                        val size = (app.size ?: look.appSize).toFloat()
                        v.setViewLayoutWidth(view, size + 12f, TypedValue.COMPLEX_UNIT_DIP)
                        v.setViewLayoutHeight(view, size, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    val index = look.apps.indexOf(app)
                    if (clickable) v.setOnClickPendingIntent(view, launch(context, app, 100 + id * WidgetApp.MAX + index))
                }
            }

            if (clickable) {
                v.setOnClickPendingIntent(R.id.widget_pill, open(context, SEARCH, 1))
                // The logo: just the start page, without the search.
                v.setOnClickPendingIntent(R.id.widget_logo, open(context, HOME, 4))
            }
            return v
        }

        // Four apps and the two buttons can all be on one side.
        private val APP_VIEWS = intArrayOf(
            R.id.widget_app1, R.id.widget_app2, R.id.widget_app3, R.id.widget_app4, R.id.widget_app5, R.id.widget_app6,
        )
        private val LEFT_VIEWS = intArrayOf(
            R.id.widget_appl1, R.id.widget_appl2, R.id.widget_appl3, R.id.widget_appl4, R.id.widget_appl5, R.id.widget_appl6,
        )

        /** GIF frames on one widget, all GIFs together (each frame is a small picture sent to the home screen). */
        private const val FRAME_BUDGET = 24

        /** Steps (pictures flipped through) on one widget, all animations together. */
        private const val STEP_BUDGET = 240

        /** Opens one of your apps, as its home screen icon would. Each widget's buttons have their own code. */
        private fun launch(context: Context, app: WidgetApp, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                code,
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(app.pkg, app.cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

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
        ids.forEach { manager.updateAppWidget(it, views(context, WidgetLook.load(context, it), id = it)) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetLook.forget(context, it) }
    }
}
