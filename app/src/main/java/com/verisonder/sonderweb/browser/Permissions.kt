package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource

/** Sites asking for location, camera, microphone, DRM and the like. */
object Permissions {

    fun delegate(tab: Tab) = object : PermissionDelegate {

        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<out String>?,
            callback: PermissionDelegate.Callback,
        ) {
            val host = Browser.host
            if (permissions.isNullOrEmpty() || host == null) {
                callback.reject()
                return
            }
            host.requestAndroidPermissions(Array(permissions.size) { permissions[it] }) { ok ->
                if (ok) callback.grant() else callback.reject()
            }
        }

        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: ContentPermission,
        ): GeckoResult<Int>? {
            val site = UrlText.host(perm.uri).ifEmpty { "This site" }
            val what = when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
                PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected (DRM) content"
                PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "use its cookies on ${UrlText.host(perm.thirdPartyOrigin)}"
                PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS,
                PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> "connect to devices on your network"
                PermissionDelegate.PERMISSION_XR -> "use virtual reality"
                // Kept quiet: storage is harmless, autoplay without sound is fine.
                PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                    return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
                // No notifications from websites, and no sound until you press play.
                PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION,
                PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                    return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
                else -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
            }
            val result = GeckoResult<Int>()
            Prompts.ask(
                tab,
                title = "$site wants to $what",
                positive = "Allow",
                negative = "Block",
            ) { allow ->
                result.complete(if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
            }
            return result
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<out MediaSource>?,
            audio: Array<out MediaSource>?,
            callback: PermissionDelegate.MediaCallback,
        ) {
            val cam = video?.firstOrNull { it.source == MediaSource.SOURCE_CAMERA } ?: video?.firstOrNull()
            val mic = audio?.firstOrNull { it.source == MediaSource.SOURCE_MICROPHONE } ?: audio?.firstOrNull()
            if (cam == null && mic == null) {
                callback.reject()
                return
            }
            val what = when {
                cam != null && mic != null -> "use your camera and microphone"
                cam != null -> "use your camera"
                else -> "use your microphone"
            }
            val site = UrlText.host(uri).ifEmpty { "This site" }
            Prompts.ask(tab, title = "$site wants to $what", positive = "Allow", negative = "Block") { allow ->
                if (allow) callback.grant(cam, mic) else callback.reject()
            }
        }
    }
}
