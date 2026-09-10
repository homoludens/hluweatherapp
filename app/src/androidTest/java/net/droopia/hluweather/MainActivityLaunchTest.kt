package net.droopia.hluweather

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun launching_main_activity_does_not_finish_immediately() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun initial_setup_action_opens_settings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assertNotNull(
                "First-launch setup screen was not displayed",
                waitForNode(instrumentation, text = "Add your first location", requireClickable = false)
            )
            val addLocationAction = waitForNode(
                instrumentation,
                text = "Add location"
            )
            assertNotNull("First-launch Add location action was not exposed", addLocationAction)
            assertTrue(addLocationAction!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            assertNotNull(
                "Settings screen did not appear after selecting Add location",
                waitForNode(instrumentation, text = "Settings", requireClickable = false)
            )
        }
    }

    private fun waitForNode(
        instrumentation: android.app.Instrumentation,
        text: String? = null,
        contentDescription: String? = null,
        requireClickable: Boolean = true
    ): AccessibilityNodeInfo? {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val match = root?.let { findNode(it, text, contentDescription, requireClickable) }
            if (match != null) return match
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        text: String?,
        contentDescription: String?,
        requireClickable: Boolean
    ): AccessibilityNodeInfo? {
        if ((text != null && node.text?.toString() == text) ||
            (contentDescription != null && node.contentDescription?.toString() == contentDescription)
        ) {
            if (!requireClickable) return node

            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }
            return clickableNode
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                findNode(child, text, contentDescription, requireClickable)?.let { return it }
            }
        }
        return null
    }
}
