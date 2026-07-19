package com.example.overdex.battle.debug.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.overdex.battle.debug.observatory.AccessibilityProbeNode
import com.example.overdex.battle.debug.observatory.RectData

/**
 * An AccessibilityService that observes system events and captures node trees.
 * Only processes events when AccessibilityProbeManager is active.
 */
class AccessibilityProbeService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!AccessibilityProbeManager.isActive()) return

        val timestamp = System.currentTimeMillis()
        val sequenceNumber = AccessibilityProbeManager.nextSequenceNumber()
        val relativeTimestamp = AccessibilityProbeManager.getRelativeTimestamp(timestamp)

        // Capture the node tree of the active window
        val nodeTree = rootInActiveWindow?.let { dumpNode(it) }

        // Future-proofing: capture raw serializable fields
        val rawData = mutableMapOf<String, String>()
        rawData["eventTime"] = event.eventTime.toString()
        rawData["eventTypeInt"] = event.eventType.toString()
        rawData["windowId"] = event.windowId.toString()
        event.beforeText?.let { rawData["beforeText"] = it.toString() }

        val probeEvent = AccessibilityProbeEvent(
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            relativeTimestamp = relativeTimestamp,
            eventType = AccessibilityEvent.eventTypeToString(event.eventType),
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            text = event.text.map { it.toString() },
            contentDescription = event.contentDescription?.toString(),
            bounds = event.source?.let { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                RectData(rect.left, rect.top, rect.right, rect.bottom)
            },
            viewIdResourceName = event.source?.viewIdResourceName,
            nodeTree = nodeTree,
            rawEventData = rawData,
        )

        AccessibilityProbeManager.onEventReceived(probeEvent)
    }

    private fun dumpNode(node: AccessibilityNodeInfo): AccessibilityProbeNode {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        val children = mutableListOf<AccessibilityProbeNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.add(dumpNode(child))
                // Note: In a production service, we'd need to call child.recycle() on older APIs,
                // but for modern Android development and this probe, we'll keep it simple.
            }
        }

        return AccessibilityProbeNode(
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            clickable = node.isClickable,
            focusable = node.isFocusable,
            visible = node.isVisibleToUser,
            enabled = node.isEnabled,
            bounds = RectData(rect.left, rect.top, rect.right, rect.bottom),
            children = children
        )
    }

    override fun onInterrupt() {
        // No-op for the probe tool
    }
}
