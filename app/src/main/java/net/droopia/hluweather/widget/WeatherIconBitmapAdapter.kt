package net.droopia.hluweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.DisplayMetrics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.glance.ImageProvider
import java.util.LinkedHashMap
import kotlin.math.roundToInt

/** Draws shared Compose vectors into small, reusable Glance-compatible bitmaps. */
object WeatherIconBitmapAdapter {
    internal const val MAX_CACHE_ENTRIES = 32
    internal const val MAX_ICON_DIMENSION_PX = 192

    private data class CacheKey(
        val imageVector: ImageVector,
        val tint: Color,
        val densityBits: Int,
        val widthPx: Int,
        val heightPx: Int
    )

    private val bitmapCache =
        object : LinkedHashMap<CacheKey, Bitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<CacheKey, Bitmap>?
            ): Boolean = size > MAX_CACHE_ENTRIES
        }

    /** Returns an [ImageProvider] directly usable by Glance's Image element. */
    fun imageProvider(
        imageVector: ImageVector,
        tint: Color,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): ImageProvider = ImageProvider(bitmap(imageVector, tint, widthPx, heightPx, density))

    internal fun bitmap(
        imageVector: ImageVector,
        tint: Color,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        require(widthPx in 1..MAX_ICON_DIMENSION_PX) {
            "Icon width must be between 1 and $MAX_ICON_DIMENSION_PX pixels"
        }
        require(heightPx in 1..MAX_ICON_DIMENSION_PX) {
            "Icon height must be between 1 and $MAX_ICON_DIMENSION_PX pixels"
        }
        require(density.isFinite() && density > 0f) { "Density must be a finite positive value" }
        require(tint != Color.Unspecified) { "Icon tint must be specified" }
        require(imageVector.viewportWidth.isFinite() && imageVector.viewportWidth > 0f) {
            "ImageVector viewport width must be positive"
        }
        require(imageVector.viewportHeight.isFinite() && imageVector.viewportHeight > 0f) {
            "ImageVector viewport height must be positive"
        }

        val key = CacheKey(imageVector, tint, density.toBits(), widthPx, heightPx)
        return synchronized(bitmapCache) {
            bitmapCache.getOrPut(key) {
                renderBitmap(imageVector, tint, widthPx, heightPx, density)
            }
        }
    }

    internal val cachedBitmapCount: Int
        get() = synchronized(bitmapCache) { bitmapCache.size }

    private fun renderBitmap(
        imageVector: ImageVector,
        tint: Color,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        val sourceBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val drawScope = CanvasDrawScope()

        drawScope.draw(
            density = Density(density = density, fontScale = 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = ComposeCanvas(AndroidCanvas(sourceBitmap)),
            size = Size(widthPx.toFloat(), heightPx.toFloat())
        ) {
            scale(
                scaleX = widthPx / imageVector.viewportWidth,
                scaleY = heightPx / imageVector.viewportHeight,
                pivot = Offset.Zero
            ) {
                drawVectorGroup(imageVector.root)
            }
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            this.density =
                (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt().coerceAtLeast(1)
        }
        drawScope.draw(
            density = Density(density = density, fontScale = 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = ComposeCanvas(AndroidCanvas(bitmap)),
            size = Size(widthPx.toFloat(), heightPx.toFloat())
        ) {
            drawImage(
                image = sourceBitmap.asImageBitmap(),
                topLeft = Offset.Zero,
                colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
            )
        }

        return bitmap
    }

    private fun DrawScope.drawVectorGroup(group: VectorGroup) {
        val groupMatrix = Matrix().apply {
            translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
            rotateZ(group.rotation)
            scale(group.scaleX, group.scaleY, 1f)
            translate(-group.pivotX, -group.pivotY)
        }
        val groupClipPath =
            group.clipPathData.takeIf { it.isNotEmpty() }?.let(::parsePath)

        withTransform({
            transform(groupMatrix)
            groupClipPath?.let { clipPath(it) }
        }) {
            for (node in group) {
                when (node) {
                    is VectorGroup -> drawVectorGroup(node)
                    is VectorPath -> drawVectorPath(node)
                    else -> error("Unsupported ImageVector node: ${node::class.java.name}")
                }
            }
        }
    }

    private fun DrawScope.drawVectorPath(vectorPath: VectorPath) {
        val path = parsePath(vectorPath.pathData).apply {
            fillType = vectorPath.pathFillType
        }
        val drawablePath =
            trimPath(
                path = path,
                start = vectorPath.trimPathStart,
                end = vectorPath.trimPathEnd,
                offset = vectorPath.trimPathOffset
            )

        vectorPath.fill?.let { brush ->
            drawPath(
                path = drawablePath,
                brush = brush,
                alpha = vectorPath.fillAlpha
            )
        }
        vectorPath.stroke?.let { brush ->
            drawPath(
                path = drawablePath,
                brush = brush,
                alpha = vectorPath.strokeAlpha,
                style = Stroke(
                    width = vectorPath.strokeLineWidth,
                    miter = vectorPath.strokeLineMiter,
                    cap = vectorPath.strokeLineCap,
                    join = vectorPath.strokeLineJoin
                )
            )
        }
    }

    private fun parsePath(nodes: List<androidx.compose.ui.graphics.vector.PathNode>): Path =
        PathParser().addPathNodes(nodes).toPath()

    private fun trimPath(path: Path, start: Float, end: Float, offset: Float): Path {
        if (start == 0f && end == 1f) return path

        val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
        val length = measure.length
        val startDistance = ((start + offset) % 1f) * length
        val endDistance = ((end + offset) % 1f) * length
        return Path().also { trimmed ->
            if (startDistance > endDistance) {
                measure.getSegment(startDistance, length, trimmed, startWithMoveTo = true)
                measure.getSegment(0f, endDistance, trimmed, startWithMoveTo = true)
            } else {
                measure.getSegment(startDistance, endDistance, trimmed, startWithMoveTo = true)
            }
        }
    }
}
