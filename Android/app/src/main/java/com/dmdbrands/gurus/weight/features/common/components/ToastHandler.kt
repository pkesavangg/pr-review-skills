package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.exposeTestTagsAsResourceId
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import android.view.Gravity
import android.view.WindowManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToastHandler(
    toast: Toast? = null,
    hostState: SnackbarHostState,
    clearToast: () -> Unit,
) {
    val screenWith = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    LocalConfiguration.current
    val screenWidthPx = with(density) { screenWith.dp.toPx() }
    val toastWidthPx = screenWidthPx * 0.8f
    val dragState =
        remember {
            AnchoredDraggableState(
                initialValue = DragAnchors.Center,
                positionalThreshold = { toastWidthPx * 0.25f },
                velocityThreshold = { 100f },
                snapAnimationSpec = spring(),
                decayAnimationSpec = exponentialDecay(),
            ).apply {
                updateAnchors(
                    DraggableAnchors {
                        DragAnchors.Center at 0f
                        DragAnchors.Left at toastWidthPx
                        DragAnchors.Right at -toastWidthPx
                    },
                )
            }
        }
    val isDismissed = remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast != null) {
            // Indefinite so Material's own Short timer (~4s) doesn't dismiss the snackbar early and
            // cancel our timeout LaunchedEffect below — that timer owns dismissal + onTimeout
            // (auto-save/assign). Without this the reading auto-dismissed before onTimeout ran, so
            // the entry was never saved.
            hostState.showSnackbar(message = toast.message, duration = SnackbarDuration.Indefinite)
        }
    }
    if (toast != null) {
        SnackbarHost(hostState = hostState, modifier = Modifier.statusBarsPadding()) { snackbarData ->
            ToastSnackbarContent(
                toast = toast,
                dragState = dragState,
                isDismissed = isDismissed,
                snackbarData = snackbarData,
                clearToast = clearToast,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToastSnackbarContent(
    toast: Toast,
    dragState: AnchoredDraggableState<DragAnchors>,
    isDismissed: MutableState<Boolean>,
    snackbarData: SnackbarData,
    clearToast: () -> Unit,
) {
    if (!isDismissed.value) {
        // Auto-dismiss after a delay. Reading arrival cards stay up longer so the user has
        // time to Save/Assign/Add-a-baby (and multi-baby cards fire their onTimeout
        // auto-assign before clearing); simple toasts keep the short window. (MOB-598)
        LaunchedEffect(snackbarData) {
            val readingToast = (toast as? Toast.Custom)?.content as? ReadingToast
            val timeoutAction = readingToast?.onTimeout
            delay(if (readingToast != null) READING_TOAST_MS else AUTO_DISMISS_MS)
            if (dragState.currentValue == DragAnchors.Center) {
                clearToast()
                snackbarData.dismiss()
                timeoutAction?.invoke()
            }
        }
        // Dismiss on swipe
        LaunchedEffect(dragState.currentValue) {
            if (dragState.currentValue != DragAnchors.Center) {
                isDismissed.value = true
                clearToast()
                snackbarData.dismiss()
            }
        }
        ToastDialog(
            toast = toast,
            dragState = dragState,
            isDismissed = isDismissed,
            snackbarData = snackbarData,
            clearToast = clearToast,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToastDialog(
    toast: Toast,
    dragState: AnchoredDraggableState<DragAnchors>,
    isDismissed: MutableState<Boolean>,
    snackbarData: SnackbarData,
    clearToast: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Override Dialog window behavior to allow touch-through
        (LocalView.current.parent as DialogWindowProvider).window.apply {
            setGravity(Gravity.TOP)
            setDimAmount(0f)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        val cardModifier = Modifier
            // Toast renders in its own Dialog window; opt its tree into
            // resource-id exposure so Appium can select the toast (MOB-1099).
            .exposeTestTagsAsResourceId()
            .offset { IntOffset(-dragState.requireOffset().roundToInt(), 0) }
            .anchoredDraggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                reverseDirection = true,
                enabled = true,
            )
        val dismiss = {
            isDismissed.value = true
            clearToast()
            snackbarData.dismiss()
        }
        when (toast) {
            is Toast.Simple -> ToastCard(
                modifier = cardModifier,
                toast = toast,
                clearToast = dismiss,
            )
            is Toast.Custom -> when (toast.content) {
                is ReadingToast -> ReadingArrivalCard(
                    modifier = cardModifier,
                    readingToast = toast.content,
                    clearToast = dismiss,
                )
            }
        }
    }
}

/** Default auto-dismiss window for a simple toast. */
private const val AUTO_DISMISS_MS = 3900L

/** Window for reading arrival cards, giving the user time to Save/Assign/Add-a-baby. */
private const val READING_TOAST_MS = 30_000L

enum class DragAnchors {
    Center,
    Left,
    Right,
    End,
}
