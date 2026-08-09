@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    org.jetbrains.skiko.ExperimentalSkikoApi::class,
    org.jetbrains.skiko.InternalSkikoApi::class,
)

package org.jetbrains.skiko

import kotlinx.cinterop.rawValue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.windows.CreateWindowExW
import platform.windows.CW_USEDEFAULT
import platform.windows.DestroyWindow
import platform.windows.GetModuleHandleW
import platform.windows.WS_OVERLAPPEDWINDOW

class SkiaLayerWindowsTest {
    @Test
    fun rendersAndSnapshotsWithSoftwareRenderer() = withWindow { window ->
        val layer =
            SkiaLayer(
                properties =
                    SkiaLayerProperties(
                        isVsyncEnabled = false,
                        renderApi = GraphicsApi.SOFTWARE_FAST,
                    ),
                pixelGeometry = PixelGeometry.BGR_H,
            )
        var renderCount = 0
        layer.renderDelegate = SkikoRenderDelegate { canvas, width, height, _ ->
            assertTrue(width > 0)
            assertTrue(height > 0)
            canvas.clear(Color.RED)
            renderCount += 1
        }

        layer.attachTo(window)

        assertTrue(layer.hasPendingRender)
        assertTrue(layer.render())
        assertEquals(1, renderCount)
        assertEquals(GraphicsApi.SOFTWARE_FAST, layer.renderApi)
        assertEquals(PixelGeometry.BGR_H, layer.pixelGeometry)
        assertEquals(1, layer.diagnostics.renderedFrameCount)
        assertEquals(1, layer.diagnostics.effectiveFrameBufferCount)
        assertFalse(layer.hasPendingRender)
        assertNotNull(layer.snapshot(32, 24)).close()

        layer.detach()
    }

    @Test
    fun fallsBackToSoftwareWhenOpenGlCannotBeCreated() = withWindow { window ->
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            if (api == GraphicsApi.OPENGL) throw RenderException("simulated WGL initialization failure")
            FakeWindowsRenderer(api).also(createdRenderers::add)
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> }

            layer.attachTo(window)

            assertEquals(GraphicsApi.SOFTWARE_FAST, layer.renderApi)
            assertEquals(1, layer.diagnostics.fallbackCount)
            assertTrue(layer.render())
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun recreatesLostOpenGlContextBeforeRendering() = withWindow { window ->
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            FakeWindowsRenderer(api).also(createdRenderers::add)
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            var delegateCalls = 0
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> delegateCalls += 1 }
            layer.attachTo(window)
            createdRenderers.single().contextLost = true

            assertTrue(layer.render())

            assertEquals(2, createdRenderers.size)
            assertTrue(createdRenderers.first().closedAfterContextLoss)
            assertEquals(1, createdRenderers.last().renderCount)
            assertEquals(1, delegateCalls)
            assertEquals(1, layer.diagnostics.contextRecoveryCount)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun recreatesLostDirect3DDeviceBeforeRendering() = withWindow { window ->
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            FakeWindowsRenderer(api).also(createdRenderers::add)
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.DIRECT3D)
                )
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> }
            layer.attachTo(window)
            createdRenderers.single().contextLost = true

            assertTrue(layer.render())

            assertEquals(2, createdRenderers.size)
            assertTrue(createdRenderers.first().closedAfterContextLoss)
            assertEquals(GraphicsApi.DIRECT3D, createdRenderers.last().renderApi)
            assertEquals(1, layer.diagnostics.contextRecoveryCount)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun persistentOpenGlFailureFallsBackToSoftware() = withWindow { window ->
        val createdApis = mutableListOf<GraphicsApi>()
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            FakeWindowsRenderer(api, failRendering = api == GraphicsApi.OPENGL).also {
                createdApis += api
                createdRenderers += it
            }
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            var delegateCalls = 0
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> delegateCalls += 1 }
            layer.attachTo(window)

            assertTrue(layer.render())

            assertEquals(
                listOf(GraphicsApi.OPENGL, GraphicsApi.OPENGL, GraphicsApi.SOFTWARE_FAST),
                createdApis,
            )
            assertEquals(GraphicsApi.SOFTWARE_FAST, layer.renderApi)
            assertEquals(1, delegateCalls)
            assertEquals(1, layer.diagnostics.contextRecoveryCount)
            assertEquals(1, layer.diagnostics.fallbackCount)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun runtimeRecoveryReachesSoftwareCompat() = withWindow { window ->
        val createdApis = mutableListOf<GraphicsApi>()
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            FakeWindowsRenderer(
                api,
                failRendering =
                    api == GraphicsApi.OPENGL || api == GraphicsApi.SOFTWARE_FAST,
            ).also {
                createdApis += api
                createdRenderers += it
            }
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            var delegateCalls = 0
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> delegateCalls += 1 }
            layer.attachTo(window)

            assertTrue(layer.render())

            assertEquals(
                listOf(
                    GraphicsApi.OPENGL,
                    GraphicsApi.OPENGL,
                    GraphicsApi.SOFTWARE_FAST,
                    GraphicsApi.SOFTWARE_COMPAT,
                ),
                createdApis,
            )
            assertEquals(GraphicsApi.SOFTWARE_COMPAT, layer.renderApi)
            assertEquals(1, delegateCalls)
            assertEquals(1, layer.diagnostics.contextRecoveryCount)
            assertEquals(2, layer.diagnostics.fallbackCount)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun userFailureInExternalOpenGlBlockIsNotRetried() = withWindow { window ->
        val createdRenderers = mutableListOf<FakeWindowsRenderer>()
        windowsLayerRendererFactoryOverride = { api, _, _, _ ->
            FakeWindowsRenderer(api).also(createdRenderers::add)
        }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            layer.attachTo(window)
            var blockCalls = 0

            val failure =
                assertFailsWith<IllegalStateException> {
                    layer.withOpenGlContext {
                        blockCalls += 1
                        error("user failure")
                    }
                }

            assertEquals("user failure", failure.message)
            assertEquals(1, blockCalls)
            assertEquals(1, createdRenderers.size)
            assertEquals(0, layer.diagnostics.contextRecoveryCount)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            createdRenderers.forEach(FakeWindowsRenderer::disposeSurface)
        }
    }

    @Test
    fun detachFromRenderDelegateIsDeferredUntilPresentationCompletes() = withWindow { window ->
        val fakeRenderer = FakeWindowsRenderer(GraphicsApi.OPENGL)
        windowsLayerRendererFactoryOverride = { _, _, _, _ -> fakeRenderer }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            var rendererWasOpenInsideDelegate = false
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ ->
                layer.detach()
                rendererWasOpenInsideDelegate = fakeRenderer.closeCount == 0
            }
            layer.attachTo(window)

            assertTrue(layer.render())

            assertTrue(rendererWasOpenInsideDelegate)
            assertEquals(1, fakeRenderer.closeCount)
            assertNull(layer.component)
        } finally {
            windowsLayerRendererFactoryOverride = null
            fakeRenderer.disposeSurface()
        }
    }

    @Test
    fun windowMessageDispatchContainsDelegateFailure() = withWindow { window ->
        val fakeRenderer = FakeWindowsRenderer(GraphicsApi.OPENGL)
        windowsLayerRendererFactoryOverride = { _, _, _, _ -> fakeRenderer }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = false, renderApi = GraphicsApi.OPENGL)
                )
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> error("frame failure") }
            layer.attachTo(window)

            assertTrue(window.pumpMessages())

            assertEquals(1, fakeRenderer.renderCount)
            assertTrue(layer.diagnostics.lastFailure?.contains("frame failure") == true)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            fakeRenderer.disposeSurface()
        }
    }

    @Test
    fun unthrottledRequestWinsWhenRenderRequestsAreCoalesced() = withWindow { window ->
        val fakeRenderer = FakeWindowsRenderer(GraphicsApi.OPENGL)
        windowsLayerRendererFactoryOverride = { _, _, _, _ -> fakeRenderer }
        try {
            val layer =
                SkiaLayer(
                    SkiaLayerProperties(isVsyncEnabled = true, renderApi = GraphicsApi.OPENGL)
                )
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> }
            layer.attachTo(window)
            layer.render()
            fakeRenderer.waitForVsyncValues.clear()

            layer.needRender(throttledToVsync = true)
            layer.needRender(throttledToVsync = false)
            layer.render()

            assertEquals(listOf(false), fakeRenderer.waitForVsyncValues)
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            fakeRenderer.disposeSurface()
        }
    }

    @Test
    fun publishesRendererAnalyticsAndDiagnostics() = withWindow { window ->
        val analytics = RecordingWindowsAnalytics()
        val fakeRenderer =
            FakeWindowsRenderer(GraphicsApi.OPENGL, effectiveFrameBufferCount = 2)
        windowsLayerRendererFactoryOverride = { _, _, _, _ -> fakeRenderer }
        try {
            val layer =
                SkiaLayer(
                    properties =
                        SkiaLayerProperties(
                            isVsyncEnabled = false,
                            frameBuffering = FrameBuffering.TRIPLE,
                            renderApi = GraphicsApi.OPENGL,
                            adapterPriority = GpuPriority.Discrete,
                            gpuResourceCacheLimit = 16L * 1024L * 1024L,
                        ),
                    analytics = analytics,
                    pixelGeometry = PixelGeometry.RGB_H,
                )
            layer.renderDelegate = SkikoRenderDelegate { _, _, _, _ -> }
            layer.attachTo(window)
            layer.render()

            assertEquals(
                listOf(
                    "renderer.init",
                    "renderer.deviceChosen",
                    "device.init",
                    "device.contextInit",
                    "device.beforeFirstFrame",
                    "device.beforeFrame",
                    "device.afterFirstFrame",
                    "device.afterFrame",
                ),
                analytics.events,
            )
            with(layer.diagnostics) {
                assertEquals(1, renderedFrameCount)
                assertEquals(FrameBuffering.TRIPLE, frameBuffering)
                assertEquals(2, effectiveFrameBufferCount)
                assertEquals(GpuPriority.Discrete, adapterPriority)
                assertEquals(16L * 1024L * 1024L, gpuResourceCacheLimit)
                assertEquals(PixelGeometry.RGB_H, pixelGeometry)
                assertFalse(transparencyRequested)
                assertFalse(hasTransparentWindowBuffer)
            }
            layer.detach()
        } finally {
            windowsLayerRendererFactoryOverride = null
            fakeRenderer.disposeSurface()
        }
    }

    @Test
    fun reportsRequestedAndActiveWindowTransparencyForExternalHwnd() = withExternalWindow { hwnd ->
        val fakeRenderer =
            FakeWindowsRenderer(
                renderApi = GraphicsApi.OPENGL,
                transparencySupported = true,
            )
        windowsLayerRendererFactoryOverride = { _, _, _, _ -> fakeRenderer }
        try {
            val layer = SkiaLayer()
            layer.transparency = true
            layer.attachTo(hwnd)

            assertTrue(layer.transparency)
            assertTrue(layer.diagnostics.transparencyRequested)
            assertTrue(layer.diagnostics.hasTransparentWindowBuffer)
            assertFailsWith<IllegalStateException> { layer.transparency = false }

            layer.detach()
            layer.transparency = false
            assertFalse(layer.diagnostics.transparencyRequested)
        } finally {
            windowsLayerRendererFactoryOverride = null
            fakeRenderer.disposeSurface()
        }
    }

    @Test
    fun parsesWindowsRendererConfiguration() {
        assertEquals(GraphicsApi.DIRECT3D, SkikoProperties.parseRenderApi(null))
        assertEquals(GraphicsApi.DIRECT3D, SkikoProperties.parseRenderApi("direct3d"))
        assertEquals(GraphicsApi.SOFTWARE_FAST, SkikoProperties.parseRenderApi("SOFTWARE"))
        assertEquals(GraphicsApi.SOFTWARE_COMPAT, SkikoProperties.parseRenderApi("software_compat"))
        assertEquals(16L * 1024L * 1024L, SkikoProperties.parseSize("16M"))
        assertEquals(
            listOf(GraphicsApi.OPENGL, GraphicsApi.SOFTWARE_FAST, GraphicsApi.SOFTWARE_COMPAT),
            SkikoProperties.fallbackRenderApiQueue(GraphicsApi.OPENGL),
        )
        assertEquals(
            listOf(
                GraphicsApi.DIRECT3D,
                GraphicsApi.OPENGL,
                GraphicsApi.SOFTWARE_FAST,
                GraphicsApi.SOFTWARE_COMPAT,
            ),
            SkikoProperties.fallbackRenderApiQueue(GraphicsApi.DIRECT3D),
        )
    }
}

private class FakeWindowsRenderer(
    override val renderApi: GraphicsApi,
    private val failRendering: Boolean = false,
    override val effectiveFrameBufferCount: Int? = null,
    override val transparencySupported: Boolean = false,
) : WindowsLayerRenderer {
    override val description: String = "Fake $renderApi"
    override val deviceName: String = "Fake device"
    private val rasterSurface = Surface.makeRasterN32Premul(32, 24)
    private var surfaceDisposed = false
    var contextLost = false
    var closedAfterContextLoss = false
    var closeCount = 0
    var renderCount = 0
    val waitForVsyncValues = mutableListOf<Boolean>()

    override fun render(
        width: Int,
        height: Int,
        waitForVsync: Boolean,
        block: (Canvas) -> Unit,
    ) {
        if (failRendering) throw RenderException("simulated renderer failure")
        renderCount += 1
        waitForVsyncValues += waitForVsync
        block(rasterSurface.canvas)
    }

    override fun snapshot(width: Int, height: Int): Bitmap =
        Bitmap().also { check(it.allocN32Pixels(width, height)) }

    override fun isContextLost(): Boolean = contextLost

    override fun <T> withExternalOpenGl(block: () -> T): T = block()

    override fun close(contextLost: Boolean) {
        closeCount += 1
        closedAfterContextLoss = contextLost
    }

    fun disposeSurface() {
        if (!surfaceDisposed) {
            surfaceDisposed = true
            rasterSurface.close()
        }
    }
}

@OptIn(ExperimentalSkikoApi::class)
private class RecordingWindowsAnalytics : SkiaLayerAnalytics {
    val events = mutableListOf<String>()

    override fun renderer(
        skikoVersion: String,
        os: OS,
        api: GraphicsApi,
    ): SkiaLayerAnalytics.RendererAnalytics =
        object : SkiaLayerAnalytics.RendererAnalytics {
            override fun init() {
                events += "renderer.init"
            }

            override fun deviceChosen() {
                events += "renderer.deviceChosen"
            }
        }

    override fun device(
        skikoVersion: String,
        os: OS,
        api: GraphicsApi,
        deviceName: String?,
    ): SkiaLayerAnalytics.DeviceAnalytics =
        object : SkiaLayerAnalytics.DeviceAnalytics {
            override fun init() {
                events += "device.init"
            }

            override fun contextInit() {
                events += "device.contextInit"
            }

            override fun beforeFirstFrameRender() {
                events += "device.beforeFirstFrame"
            }

            override fun beforeFrameRender() {
                events += "device.beforeFrame"
            }

            override fun afterFirstFrameRender() {
                events += "device.afterFirstFrame"
            }

            override fun afterFrameRender() {
                events += "device.afterFrame"
            }
        }
}

private inline fun withWindow(block: (WindowsNativeWindow) -> Unit) {
    val window = WindowsNativeWindow.create("Skiko Windows native test", 96, 64)
    try {
        block(window)
    } finally {
        window.close()
    }
}

private inline fun withExternalWindow(block: (Long) -> Unit) {
    val hwnd =
        checkNotNull(
            CreateWindowExW(
                0u,
                "STATIC",
                "Skiko external HWND test",
                WS_OVERLAPPEDWINDOW.toUInt(),
                CW_USEDEFAULT,
                CW_USEDEFAULT,
                96,
                64,
                null,
                null,
                GetModuleHandleW(null),
                null,
            ),
        )
    try {
        block(hwnd.rawValue.toLong())
    } finally {
        DestroyWindow(hwnd)
    }
}
