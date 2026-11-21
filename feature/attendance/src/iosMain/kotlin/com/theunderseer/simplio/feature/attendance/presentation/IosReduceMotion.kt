package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
internal fun rememberIosReduceMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { reduced = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { center.removeObserver(observer) }
    }
    return reduced
}
