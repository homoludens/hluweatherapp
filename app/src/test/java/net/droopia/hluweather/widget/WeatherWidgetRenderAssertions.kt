package net.droopia.hluweather.widget

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Bounds and overlap checks against the translated, inflated production Glance RemoteViews. */
internal fun assertRenderedContentFitsWithoutOverlap(root: View) {
    val content = findRenderedContent(root)
    content.forEach { view ->
        val bounds = view.boundsIn(root)
        assertTrue("$view remains inside the rendered card on the left", bounds.left >= 0)
        assertTrue("$view remains inside the rendered card on the right", bounds.right <= root.width)
        assertTrue("$view remains inside the rendered card at the top", bounds.top >= 0)
        assertTrue("$view remains inside the rendered card at the bottom", bounds.bottom <= root.height)
        if (view is TextView) {
            val textWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
            assertTrue("${view.text} has a positive rendered width", textWidth > 0)
            assertTrue(
                "${view.text} is not clipped inside its translated TextView",
                view.paint.measureText(view.text.toString()) <= textWidth + 1f
            )
            assertTrue("${view.text} stays on one rendered line", view.lineCount == 1)
        }
    }

    content.forEachIndexed { index, first ->
        content.drop(index + 1).forEach { second ->
            val firstBounds = first.boundsIn(root)
            val secondBounds = second.boundsIn(root)
            val overlapsHorizontally = firstBounds.left < secondBounds.right &&
                secondBounds.left < firstBounds.right
            val overlapsVertically = firstBounds.top < secondBounds.bottom &&
                secondBounds.top < firstBounds.bottom
            assertFalse(
                "${first.renderDescription()} overlaps ${second.renderDescription()}",
                overlapsHorizontally && overlapsVertically
            )
        }
    }
}

private fun findRenderedContent(root: View): List<View> = buildList {
    when (root) {
        is TextView -> if (root.visibility == View.VISIBLE && root.text.isNotBlank()) add(root)
        is ImageView -> if (root.visibility == View.VISIBLE && root.drawable is BitmapDrawable) add(root)
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) addAll(findRenderedContent(root.getChildAt(index)))
    }
}

private fun View.boundsIn(root: View): Bounds {
    var left = left
    var top = top
    var parent = parent
    while (parent is View && parent !== root) {
        left += parent.left
        top += parent.top
        parent = parent.parent
    }
    return Bounds(left, top, left + width, top + height)
}

private fun View.renderDescription(): String = when (this) {
    is TextView -> "TextView '$text'"
    is ImageView -> "ImageView '${contentDescription ?: "weather/control icon"}'"
    else -> javaClass.simpleName
}

private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
