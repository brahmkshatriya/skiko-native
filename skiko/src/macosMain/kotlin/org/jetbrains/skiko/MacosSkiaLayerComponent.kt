@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.jetbrains.skiko

import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.impl.NativePointer

@InternalSkikoApi
interface MacosSkiaLayerComponent {
    val windowHandle: Any
    val drawableWidth: Int
    val drawableHeight: Int
    val contentScale: Float
    val pixelGeometry: PixelGeometry
        get() = PixelGeometry.UNKNOWN
    var fullscreen: Boolean

    fun createOpenGlContext(): NativePointer
    fun makeOpenGlContextCurrent(context: NativePointer)
    fun setOpenGlSwapInterval(interval: Int): Boolean
    fun swapOpenGlBuffers()
    fun deleteOpenGlContext(context: NativePointer)
    fun openGlRendererName(): String? = null
    fun requestRender()
}
