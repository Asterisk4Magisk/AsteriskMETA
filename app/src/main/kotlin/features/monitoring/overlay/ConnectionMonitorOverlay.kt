// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.overlay

import android.app.AppOpsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.WindowSdkExtensions
import androidx.window.layout.WindowInfoTracker
import app.AppState
import app.R
import features.logs.AndroidAppLogger
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringRepository
import features.monitoring.observeWhileStarted
import features.settings.locale.ProvideAppLanguage
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import system.ScreenInteractiveObserver
import ui.AsteriskOverlayTheme

internal class ConnectionMonitorOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
    private val monitoring: MonitoringRepository,
    private val appState: StateFlow<AppState>,
    private val onRuntimeDemandChanged: (active: Boolean, connectionsVisible: Boolean) -> Unit,
) {
    private val mutableVisible = MutableStateFlow(false)
    val visible = mutableVisible.asStateFlow()
    private var removeWindow: (() -> Unit)? = null
    private val positionPreferences by lazy {
        context.getSharedPreferences("connection_monitor_overlay", Context.MODE_PRIVATE)
    }

    @MainThread
    fun show(activity: ComponentActivity?): Boolean {
        if (removeWindow != null) return true
        if (!appState.value.proxyRunning || !Settings.canDrawOverlays(context)) return false
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val displayContext = context.createDisplayContext(display)
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(type, null)
        } else {
            displayContext
        }
        val windows = windowContext.getSystemService(WindowManager::class.java) ?: return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val owner = OverlayViewLifecycle()
        val activityRef = WeakReference(activity)
        fun overlayView() = ComposeView(windowContext).apply {
            id = View.generateViewId()
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val view = overlayView()
        val buttonView = overlayView()
        val expansion = Animatable(0f)
        val geometry = ConnectionOverlayWindowGeometry(
            windowContext, windows, view, type,
            title = context.getString(R.string.monitor_overlay_title),
            buttonTitle = context.getString(R.string.monitor_overlay_restore),
            allowScreenshots = appState.value.allowScreenshots,
            savedButtonPosition = readButtonPosition(),
        )
        val layout = geometry.layout
        var savedButtonPosition = geometry.buttonPosition
        fun saveButtonPosition() {
            val position = geometry.buttonPosition
            if (position == savedButtonPosition) return
            savedButtonPosition = position
            positionPreferences.edit {
                putFloat(ButtonPositionX, position.x)
                putFloat(ButtonPositionY, position.y)
            }
        }
        var connectionsVisible = false
        var windowAttached = false

        fun updateRuntimeDemand() {
            val active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            onRuntimeDemandChanged(active, active && connectionsVisible)
        }
        fun updateScreenState(interactive: Boolean) {
            owner.setInteractive(interactive)
            if (windowAttached) updateRuntimeDemand()
        }
        val screenObserver = ScreenInteractiveObserver(context) { interactive ->
            if (windowAttached) updateScreenState(interactive)
        }
        updateScreenState(screenObserver.isInteractive)

        fun updateWindow() {
            if (!windowAttached) return
            try {
                windows.updateViewLayout(view, layout)
                windows.updateViewLayout(buttonView, geometry.buttonLayout)
            } catch (error: IllegalArgumentException) {
                logWindowFailure("update", error)
                dismiss()
            } catch (error: SecurityException) {
                logWindowFailure("update", error)
                dismiss()
            }
        }
        fun fitWindow() {
            if (geometry.fit(appState.value.allowScreenshots)) updateWindow()
        }
        val refitAfterInsets = Runnable { if (windowAttached) fitWindow() }
        fun toggleMinimized() {
            if (geometry.toggleMinimized(appState.value.allowScreenshots)) updateWindow()
        }
        fun drag(dx: Float, dy: Float) {
            if (geometry.drag(dx, dy)) fitWindow()
        }
        fun removeViews() {
            view.removeCallbacks(refitAfterInsets)
            owner.destroy()
            runCatching { windows.removeViewImmediate(buttonView) }
            runCatching { windows.removeViewImmediate(view) }
        }
        view.setOnApplyWindowInsetsListener { target, insets ->
            target.removeCallbacks(refitAfterInsets)
            target.post(refitAfterInsets)
            target.onApplyWindowInsets(insets)
        }
        view.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (geometry.updatePanelHeight(bottom - top)) fitWindow()
        }
        val initialAppearance = appState.value.let { Triple(it.languageMode, it.colorMode, it.seedIndex) }
        @Composable
        fun OverlayTheme(content: @Composable () -> Unit) {
            val locale = LocalConfiguration.current.locales[0]
            val appearance by remember(appState) {
                appState.map { Triple(it.languageMode, it.colorMode, it.seedIndex) }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialAppearance)
            val (languageMode, colorMode, seedIndex) = appearance
            ProvideAppLanguage(languageMode, locale) {
                AsteriskOverlayTheme(colorMode, seedIndex, content)
            }
        }
        view.setContent {
            LaunchedEffect(windowContext) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    WindowSdkExtensions.getInstance().extensionVersion >= 2
                ) {
                    WindowInfoTracker.getOrCreate(windowContext).windowLayoutInfo(windowContext).collect { info ->
                        geometry.windowLayoutInfo = info
                        fitWindow()
                    }
                } else {
                    val host = activityRef.get() ?: return@LaunchedEffect
                    host.lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        WindowInfoTracker.getOrCreate(host).windowLayoutInfo(host).collect { info ->
                            geometry.windowLayoutInfo = info.takeUnless { host.isInMultiWindowMode }
                            fitWindow()
                        }
                    }
                }
            }
            val configuration = LocalConfiguration.current
            val containerSize = LocalWindowInfo.current.containerSize
            LaunchedEffect(configuration, containerSize) {
                fitWindow()
            }
            OverlayTheme {
                val closeOnRight = LocalLayoutDirection.current == LayoutDirection.Ltr
                LaunchedEffect(closeOnRight) {
                    if (geometry.updateCloseSide(closeOnRight)) fitWindow()
                }
                ConnectionOverlayContent(
                    monitoring = monitoring,
                    appState = appState,
                    minimized = geometry.minimized,
                    panelVisible = geometry.panelVisible,
                    buttonOffset = geometry.buttonOffset,
                    expansion = expansion,
                    heightLimit = geometry.panelHeightLimit,
                    hasCustomSize = geometry.hasCustomSize,
                    onMinimize = ::toggleMinimized,
                    onMinimizeFinished = {
                        if (geometry.finishMinimizing(appState.value.allowScreenshots)) updateWindow()
                    },
                    onDrag = ::drag,
                    onDragFinished = ::saveButtonPosition,
                    onResize = { fromLeft, dx, dy ->
                        if (geometry.resize(fromLeft, dx, dy)) fitWindow()
                    },
                    onOverviewHeightChanged = { height ->
                        if (geometry.updateOverviewHeight(height)) view.post { fitWindow() }
                    },
                    onConnectionsVisibilityChanged = { visible ->
                        connectionsVisible = visible
                        if (windowAttached) updateRuntimeDemand()
                    },
                )
            }
        }
        buttonView.setContent {
            val configuration = LocalConfiguration.current
            val containerSize = LocalWindowInfo.current.containerSize
            LaunchedEffect(configuration, containerSize) {
                fitWindow()
            }
            OverlayTheme {
                ConnectionOverlayButton(
                    expansion = expansion,
                    interactive = !geometry.panelVisible,
                    onRestore = ::toggleMinimized,
                    onDrag = ::drag,
                    onDragFinished = ::saveButtonPosition,
                )
            }
        }
        try {
            windows.addView(view, layout)
            windows.addView(buttonView, geometry.buttonLayout)
        } catch (error: WindowManager.BadTokenException) {
            logWindowFailure("attach", error)
            removeViews()
            return false
        } catch (error: WindowManager.InvalidDisplayException) {
            logWindowFailure("attach", error)
            removeViews()
            return false
        } catch (error: SecurityException) {
            logWindowFailure("attach", error)
            removeViews()
            return false
        }

        val observation = monitoring.observeWhileStarted(MonitoringIntent.ConnectionOverlay, owner.lifecycle)
        val runtimePresence = scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
            appState.map { it.proxyRunning to it.allowScreenshots }.distinctUntilChanged().collect { (running, _) ->
                if (!running) {
                    dismiss()
                    return@collect
                }
                fitWindow()
            }
        }
        val permissionListener = AppOpsManager.OnOpChangedListener { _, _ ->
            scope.launch(Dispatchers.Main.immediate) {
                if (windowAttached && !Settings.canDrawOverlays(context)) dismiss()
            }
        }
        windowAttached = true
        removeWindow = {
            windowAttached = false
            saveButtonPosition()
            runtimePresence.cancel()
            screenObserver.unregister()
            onRuntimeDemandChanged(false, false)
            observation.close()
            appOps.stopWatchingMode(permissionListener)
            removeViews()
        }
        val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = dismiss()
        }
        view.addOnAttachStateChangeListener(detachListener)
        buttonView.addOnAttachStateChangeListener(detachListener)
        try {
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, permissionListener)
            screenObserver.register()
        } catch (_: SecurityException) {
            dismiss()
            return false
        }
        if (!Settings.canDrawOverlays(context) || !appState.value.proxyRunning) {
            dismiss()
            return false
        }
        updateScreenState(screenObserver.isInteractive)
        mutableVisible.value = true
        runtimePresence.start()
        return mutableVisible.value
    }

    private fun logWindowFailure(stage: String, error: RuntimeException) {
        AndroidAppLogger.warn("ConnectionMonitorOverlay", "Window $stage failed (${error.javaClass.simpleName})")
    }

    private fun readButtonPosition(): Offset? {
        val x = positionPreferences.getFloat(ButtonPositionX, Float.NaN)
        val y = positionPreferences.getFloat(ButtonPositionY, Float.NaN)
        return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null
    }

    @MainThread
    fun dismiss() {
        val remove = removeWindow ?: return
        removeWindow = null
        mutableVisible.value = false
        remove()
    }
}

private const val ButtonPositionX = "button_position_x"
private const val ButtonPositionY = "button_position_y"

private class OverlayViewLifecycle : SavedStateRegistryOwner {
    override val lifecycle = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    fun setInteractive(interactive: Boolean) {
        lifecycle.currentState = if (interactive) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    fun destroy() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
