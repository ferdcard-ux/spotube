/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.spotube.spotube.core.webview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import compose.icons.FeatherIcons
import compose.icons.feathericons.ExternalLink
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.web.LocalWebViewFactory
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.web.defaultWebViewFactory
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import org.jetbrains.compose.resources.stringResource
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.millennium.composables.MillenniumIcon
import org.spotube.spotube.PlatformType
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.getPlatform
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.webview_external_window_cancel
import spotube.composeapp.generated.resources.webview_external_window_desc
import spotube.composeapp.generated.resources.webview_external_window_show
import spotube.composeapp.generated.resources.webview_external_window_title
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
actual fun PlatformWebViewHost(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onClose: () -> Unit,
) {
    if (getPlatform().type == PlatformType.Linux) {
        LinuxPlatformWebViewHost(
            state = state,
            modifier = modifier,
            navigator = navigator,
            webViewJsBridge = webViewJsBridge,
            onCreated = onCreated,
            onClose = onClose,
        )
    } else {
        WebView(
            state = state,
            modifier = modifier,
            navigator = navigator,
            webViewJsBridge = webViewJsBridge,
            onCreated = onCreated,
        )
    }
}

private interface GtkDeleteEventCallback : Callback {
    fun invoke(widget: Pointer, event: Pointer, data: Pointer?): Boolean
}

private object GtkWindowBridge {
    private val gtkLib: NativeLibrary? by lazy {
        runCatching { NativeLibrary.getInstance("gtk-3") }.getOrNull()
    }
    private val gobjectLib: NativeLibrary? by lazy {
        runCatching { NativeLibrary.getInstance("gobject-2.0") }.getOrNull()
    }
    private val webkitLib: NativeLibrary? by lazy {
        listOf(
            "libwebkit2gtk-4.1.so.0",
            "libwebkit2gtk-4.1.so",
            "libwebkit2gtk-4.0.so.37",
            "libwebkit2gtk-4.0.so",
            "webkit2gtk-4.1",
            "webkit2gtk-4.0",
        ).firstNotNullOfOrNull { name ->
            runCatching { NativeLibrary.getInstance(name) }.getOrNull()
        }
    }

    val isAvailable: Boolean
        get() = gtkLib != null && gobjectLib != null

    fun createWindow(title: String, width: Int = 900, height: Int = 750): Pointer? {
        val gtk = gtkLib ?: return null
        return try {
            val win = gtk.getFunction("gtk_window_new")
                .invoke(Pointer::class.java, arrayOf(0)) as? Pointer ?: return null

            gtk.getFunction("gtk_window_set_title").invoke(arrayOf(win, title))
            gtk.getFunction("gtk_window_set_default_size").invoke(arrayOf(win, width, height))
            gtk.getFunction("gtk_window_set_position").invoke(arrayOf(win, 1)) // GTK_WIN_POS_CENTER
            win
        } catch (_: Throwable) {
            null
        }
    }

    fun setTitle(window: Pointer, title: String) {
        runCatching {
            gtkLib?.getFunction("gtk_window_set_title")?.invoke(arrayOf(window, title))
        }
    }

    fun addWidget(window: Pointer, widgetHandle: Long) {
        runCatching {
            val widget = Pointer(widgetHandle)
            val parent = gtkLib?.getFunction("gtk_widget_get_parent")
                ?.invoke(Pointer::class.java, arrayOf(widget)) as? Pointer
            if (parent != null) {
                gtkLib?.getFunction("gtk_container_remove")?.invoke(arrayOf(parent, widget))
            }
            gtkLib?.getFunction("gtk_widget_set_hexpand")?.invoke(arrayOf(widget, true))
            gtkLib?.getFunction("gtk_widget_set_vexpand")?.invoke(arrayOf(widget, true))
            gtkLib?.getFunction("gtk_container_add")?.invoke(arrayOf(window, widget))
        }
    }

    fun setWindowDarkTheme(window: Pointer) {
        runCatching {
            val provider = gtkLib?.getFunction("gtk_css_provider_new")
                ?.invoke(Pointer::class.java, emptyArray()) as? Pointer ?: return
            val css = "window, .background { background-color: #121212; }"
            gtkLib?.getFunction("gtk_css_provider_load_from_data")
                ?.invoke(arrayOf(provider, css, -1, null))
            val styleCtx = gtkLib?.getFunction("gtk_widget_get_style_context")
                ?.invoke(Pointer::class.java, arrayOf(window)) as? Pointer ?: return
            gtkLib?.getFunction("gtk_style_context_add_provider")
                ?.invoke(arrayOf(styleCtx, provider, 600)) // GTK_STYLE_PROVIDER_PRIORITY_APPLICATION = 600
        }
    }

    fun setWebViewDarkBackground(widgetHandle: Long) {
        runCatching {
            val lib = webkitLib ?: return
            // GdkRGBA structure in GTK3 uses 4 gdouble (8 bytes each): red, green, blue, alpha
            // #121212 dark color: 18.0 / 255.0 = 0.07058823529411765
            val rgba = com.sun.jna.Memory(32)
            rgba.setDouble(0, 0.07058823529411765)
            rgba.setDouble(8, 0.07058823529411765)
            rgba.setDouble(16, 0.07058823529411765)
            rgba.setDouble(24, 1.0)
            lib.getFunction("webkit_web_view_set_background_color")
                .invoke(arrayOf(Pointer(widgetHandle), rgba))
        }
    }

    fun removeWidget(window: Pointer, widgetHandle: Long) {
        runCatching {
            gtkLib?.getFunction("gtk_container_remove")?.invoke(arrayOf(window, Pointer(widgetHandle)))
        }
    }

    fun hasParent(widgetHandle: Long): Boolean {
        return runCatching {
            val parent = gtkLib?.getFunction("gtk_widget_get_parent")
                ?.invoke(Pointer::class.java, arrayOf(Pointer(widgetHandle))) as? Pointer
            parent != null
        }.getOrDefault(false)
    }

    fun showAll(window: Pointer) {
        runCatching {
            gtkLib?.getFunction("gtk_widget_show_all")?.invoke(arrayOf(window))
        }
    }

    fun present(window: Pointer) {
        runCatching {
            gtkLib?.getFunction("gtk_window_present")?.invoke(arrayOf(window))
        }
    }

    fun destroyWindow(window: Pointer) {
        runCatching {
            gtkLib?.getFunction("gtk_widget_destroy")?.invoke(arrayOf(window))
        }
    }

    fun connectDeleteEvent(window: Pointer, callback: GtkDeleteEventCallback): Long {
        return runCatching {
            val fn = gobjectLib?.getFunction("g_signal_connect_data") ?: return 0L
            (fn.invoke(Long::class.java, arrayOf(window, "delete-event", callback, null, null, 0)) as? Long) ?: 0L
        }.getOrDefault(0L)
    }
}

@Composable
private fun LinuxPlatformWebViewHost(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onClose: () -> Unit,
) {
    val currentOnClose = rememberUpdatedState(onClose)
    val windowRef = remember { AtomicReference<Pointer?>(null) }
    val widgetHandleRef = remember { AtomicReference<Long?>(null) }
    val deleteCallbackRef = remember { AtomicReference<GtkDeleteEventCallback?>(null) }
    val isClosed = remember { AtomicBoolean(false) }

    LaunchedEffect(state.pageTitle) {
        val win = windowRef.get()
        if (win != null) {
            val title = state.pageTitle?.takeIf { it.isNotBlank() } ?: "Spotube - Authentication"
            GtkWindowBridge.setTitle(win, title)
        }
    }

    val closeHandler = remember {
        {
            if (isClosed.compareAndSet(false, true)) {
                val win = windowRef.getAndSet(null)
                val handle = widgetHandleRef.getAndSet(null)
                if (win != null) {
                    if (handle != null && GtkWindowBridge.hasParent(handle)) {
                        GtkWindowBridge.removeWidget(win, handle)
                    }
                    GtkWindowBridge.destroyWindow(win)
                }
                currentOnClose.value()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isClosed.set(true)
            val win = windowRef.getAndSet(null)
            val handle = widgetHandleRef.getAndSet(null)
            if (win != null) {
                if (handle != null && GtkWindowBridge.hasParent(handle)) {
                    GtkWindowBridge.removeWidget(win, handle)
                }
                GtkWindowBridge.destroyWindow(win)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(AppTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).widthIn(max = 480.dp),
        ) {
            MillenniumIcon(
                FeatherIcons.ExternalLink,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = AppTheme.colors.primary,
            )
            Text(
                text = stringResource(Res.string.webview_external_window_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.webview_external_window_desc),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MillenniumButton(
                    onClick = {
                        windowRef.get()?.let { GtkWindowBridge.present(it) }
                    },
                    style = MillenniumButtonStyle.Secondary,
                ) {
                    Text(stringResource(Res.string.webview_external_window_show))
                }
                MillenniumButton(
                    onClick = closeHandler,
                    style = MillenniumButtonStyle.Quiet,
                ) {
                    Text(stringResource(Res.string.webview_external_window_cancel))
                }
            }
        }

        // Hidden WebView driving the background session, cookies, and navigation without rendering NativeView
        CompositionLocalProvider(
            LocalWebViewFactory provides { param -> defaultWebViewFactory(param) }
        ) {
            WebView(
                state = state,
                modifier = Modifier.size(0.dp),
                navigator = navigator,
                webViewJsBridge = webViewJsBridge,
                onCreated = { native ->
                    onCreated(native)
                    val gtkHandle = (native as? LinuxWebKitNativeWebView)?.asPlatformView()?.gtkWidgetHandle
                    if (gtkHandle != null && gtkHandle != 0L && !isClosed.get()) {
                        widgetHandleRef.set(gtkHandle)
                        val win = GtkWindowBridge.createWindow(
                            title = state.pageTitle?.takeIf { it.isNotBlank() } ?: "Spotube - Authentication"
                        )
                        if (win != null) {
                            windowRef.set(win)
                            val callback = object : GtkDeleteEventCallback {
                                override fun invoke(widget: Pointer, event: Pointer, data: Pointer?): Boolean {
                                    closeHandler()
                                    return false
                                }
                            }
                            deleteCallbackRef.set(callback)
                            GtkWindowBridge.connectDeleteEvent(win, callback)
                            GtkWindowBridge.setWindowDarkTheme(win)
                            GtkWindowBridge.setWebViewDarkBackground(gtkHandle)
                            GtkWindowBridge.addWidget(win, gtkHandle)
                            GtkWindowBridge.showAll(win)
                            GtkWindowBridge.present(win)
                        }
                    }
                }
            )
        }
    }
}
