package org.jetbrains.skiko

import org.jetbrains.skiko.internal.fastNone

internal data class NotSupportedAdapter(
    val os: OS,
    val api: GraphicsApi,
    val pattern: String,
    /** Whether the adapter name must match [pattern] exactly instead of by prefix. */
    val exactPattern: Boolean = true,
)

private val notSupportedAdapters: List<NotSupportedAdapter> by lazy {
    listOf(
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "Intel(R) HD Graphics 520"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "Intel(R) HD Graphics 530"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "Intel(R) HD Graphics 4400"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "Intel(R) HD Graphics 4600"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "NVIDIA GeForce GTX 750 Ti"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "NVIDIA GeForce GTX 960M"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.DIRECT3D, "NVIDIA Quadro M2000M"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.OPENGL, "Intel(R) HD Graphics 2000"),
        NotSupportedAdapter(OS.Windows, GraphicsApi.OPENGL, "Intel(R) HD Graphics 3000"),
        NotSupportedAdapter(OS.Linux, GraphicsApi.OPENGL, "llvmpipe", exactPattern = false),
        NotSupportedAdapter(OS.Linux, GraphicsApi.OPENGL, "virgl", exactPattern = false),
    )
}

internal fun isVideoCardSupported(api: GraphicsApi, hostOs: OS, name: String): Boolean =
    notSupportedAdapters.fastNone { adapter ->
        if (adapter.os != hostOs || adapter.api != api) return@fastNone false

        if (adapter.exactPattern) adapter.pattern == name else name.startsWith(adapter.pattern)
    }
