package com.aira.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AiraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AiraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen events yahan observe kiye ja sakte hain
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun clickNodeByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (clickSelfOrParent(node)) return true
        }
        return false
    }

    private fun clickSelfOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun lockScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false

    fun takeScreenshotAction(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || sb.length > 1500 || depth > 25) return
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()
            val label = if (t.isNotEmpty()) t else d
            if (label.isNotEmpty()) sb.append(label).append(". ")
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString().take(1500)
    }

    fun scrollDown() {
        val displayMetrics = resources.displayMetrics
        val x = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.75f
        val endY = displayMetrics.heightPixels * 0.25f
        performSwipe(x, startY, x, endY)
    }

    fun scrollUp() {
        val displayMetrics = resources.displayMetrics
        val x = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.25f
        val endY = displayMetrics.heightPixels * 0.75f
        performSwipe(x, startY, x, endY)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
