#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SKIKO_DIR="$ROOT_DIR/skiko"
OUT_DIR="$SKIKO_DIR/build/wine-smoke"

if ! command -v wine >/dev/null 2>&1; then
    echo "wine is required" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    if [[ -z "${JAVA_HOME:-}" ]]; then
        JAVA_HOME=$(find "$HOME/.jdks" -maxdepth 3 -type f -path '*/bin/java' -printf '%h\n' 2>/dev/null \
            | sed 's#/bin$##' | sort -V | head -n 1 || true)
        export JAVA_HOME
    fi
    if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
        echo "A JDK is required; set JAVA_HOME" >&2
        exit 1
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
fi

KOTLIN_VERSION=$(awk -F'"' '/^kotlin = / { print $2; exit }' "$ROOT_DIR/dependencies.toml")
KONAN_HOME=${KONAN_HOME:-"$HOME/.konan/kotlin-native-prebuilt-linux-x86_64-$KOTLIN_VERSION"}
KONANC="$KONAN_HOME/bin/konanc"

"$ROOT_DIR/gradlew" -p "$SKIKO_DIR" mingwX64MainKlibrary \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true \
    --console=plain

if [[ ! -x "$KONANC" ]]; then
    echo "Kotlin/Native compiler not found at $KONANC" >&2
    exit 1
fi

SKIKO_KLIB="$SKIKO_DIR/build/classes/kotlin/mingwX64/main/klib/skiko"
ATOMICFU=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-mingwx64/0.23.1" -name 'atomicfu.klib' -print -quit)
ATOMICFU_CINTEROP=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-mingwx64/0.23.1" -name 'atomicfu-cinterop-interop.klib' -print -quit)
COROUTINES=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-mingwx64/1.8.0" -name 'kotlinx-coroutines-core.klib' -print -quit)
SKIKO_CINTEROP="$SKIKO_DIR/build/classes/kotlin/mingwX64/main/cinterop/skiko-cinterop-skiko"
CINTEROP_ARGS=()
if [[ -d "$SKIKO_CINTEROP" ]]; then
    CINTEROP_ARGS=(-library "$SKIKO_CINTEROP")
fi

for dependency in "$SKIKO_KLIB" "$ATOMICFU" "$ATOMICFU_CINTEROP" "$COROUTINES"; do
    if [[ -z "$dependency" || ! -e "$dependency" ]]; then
        echo "Required KLIB is missing: $dependency" >&2
        exit 1
    fi
done

mkdir -p "$OUT_DIR"
cat > "$OUT_DIR/main.kt" <<'KOTLIN'
import org.jetbrains.skia.Color
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skiko.FrameBuffering
import org.jetbrains.skiko.GpuPriority
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.Version
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkiaLayerProperties
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.WindowsNativeWindow
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.skiko.hostArch
import org.jetbrains.skiko.hostOs
import org.jetbrains.skiko.kotlinBackend

fun main() {
    val color = Color.makeRGB(0x12, 0x34, 0x56)
    check(Color.getR(color) == 0x12)
    check(Color.getG(color) == 0x34)
    check(Color.getB(color) == 0x56)

    val properties = SkiaLayerProperties(
        isVsyncEnabled = false,
        frameBuffering = FrameBuffering.TRIPLE,
        renderApi = GraphicsApi.SOFTWARE_FAST,
        adapterPriority = GpuPriority.Discrete,
        gpuResourceCacheLimit = 16L * 1024L * 1024L,
    )
    check(properties.copy() == properties)
    val layer = SkiaLayer(properties = properties, pixelGeometry = PixelGeometry.BGR_H)
    check(layer.contentScale == 1f)
    check(layer.renderApi == GraphicsApi.SOFTWARE_FAST)
    check(layer.pixelGeometry == PixelGeometry.BGR_H)
    check(currentSystemTheme in SystemTheme.entries)

    val window = WindowsNativeWindow.create("Skiko Wine smoke", 320, 200)
    check(window.isValid)
    window.close()

    println("skiko=${Version.skiko}")
    println("skia=${Version.skia}")
    println("host=${hostOs.id}-${hostArch.id}")
    println("backend=${kotlinBackend.id}")
    println("theme=${currentSystemTheme.name.lowercase()}")
    println("renderApi=${layer.renderApi.name.lowercase()}")
    println("frameBuffering=${properties.frameBuffering.name.lowercase()}")
    println("pixelGeometry=${layer.pixelGeometry.name.lowercase()}")
    println("windowHost=ok")
    println("color=${color.toUInt().toString(16)}")
}
KOTLIN

"$KONANC" \
    -target mingw_x64 \
    -produce program \
    -library "$SKIKO_KLIB" \
    -library "$ATOMICFU" \
    -library "$ATOMICFU_CINTEROP" \
    -library "$COROUTINES" \
    "${CINTEROP_ARGS[@]}" \
    "$OUT_DIR/main.kt" \
    -o "$OUT_DIR/skiko-wine-smoke"

cat > "$OUT_DIR/native-library-smoke.kt" <<'KOTLIN'
import org.jetbrains.skiko.loadAngleLibrary
import org.jetbrains.skiko.loadOpenGLLibrary

fun main() {
    loadOpenGLLibrary()
    println("opengl=loaded")
    try {
        loadAngleLibrary()
        println("angle=loaded")
    } catch (error: Throwable) {
        println("angle=unavailable:${error.message}")
    }
}
KOTLIN

"$KONANC" \
    -target mingw_x64 \
    -produce program \
    -friend-modules "$SKIKO_KLIB" \
    -library "$SKIKO_KLIB" \
    -library "$ATOMICFU" \
    -library "$ATOMICFU_CINTEROP" \
    -library "$COROUTINES" \
    "${CINTEROP_ARGS[@]}" \
    "$OUT_DIR/native-library-smoke.kt" \
    -o "$OUT_DIR/native-library-smoke"

RENDERER_SMOKE_EXE=""
if [[ -n "${SKIKO_WINDOWS_SDK_ROOT:-}" ]]; then
    cat > "$OUT_DIR/renderer-smoke.kt" <<'KOTLIN'
@file:OptIn(org.jetbrains.skiko.InternalSkikoApi::class)

import org.jetbrains.skia.Color
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkiaLayerProperties
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.WindowsNativeWindow

fun main() {
    val window = WindowsNativeWindow.create("Skiko renderer smoke", 160, 120)
    val layer = SkiaLayer(SkiaLayerProperties(renderApi = GraphicsApi.OPENGL))
    layer.transparency = true
    var renderedFrames = 0
    try {
        layer.renderDelegate = SkikoRenderDelegate { canvas, width, height, _ ->
            check(width > 0 && height > 0)
            canvas.clear(Color.makeRGB(0x12, 0x34, 0x56))
            renderedFrames += 1
        }
        layer.attachTo(window)
        var dispatchAttempts = 0
        while (renderedFrames == 0 && dispatchAttempts < 20) {
            check(window.pumpMessages())
            dispatchAttempts += 1
        }
        println("renderer=${layer.rendererDescription}")
        println("frames=${layer.diagnostics.renderedFrameCount}")
        println("fallbacks=${layer.diagnostics.fallbackCount}")
        println("contextRecoveries=${layer.diagnostics.contextRecoveryCount}")
        println("transparency=${layer.diagnostics.transparencyRequested}/${layer.diagnostics.hasTransparentWindowBuffer}")
        println("lastFailure=${layer.diagnostics.lastFailure}")
        check(renderedFrames > 0)
        check(layer.diagnostics.transparencyRequested)
        if (layer.renderApi == GraphicsApi.OPENGL) {
            check(layer.diagnostics.hasTransparentWindowBuffer)
        } else {
            // Wine has no DWM compositor, so the transparent WGL request must fail closed and use
            // the normal renderer fallback path instead of advertising an opaque buffer as alpha.
            check(!layer.diagnostics.hasTransparentWindowBuffer)
            check(layer.diagnostics.fallbackCount > 0)
            check(layer.diagnostics.lastFailure != null)
        }

        layer.snapshot(16, 16).close()
    } finally {
        if (layer.component != null) layer.detach()
        window.close()
    }
}
KOTLIN

    "$KONANC" \
        -target mingw_x64 \
        -produce program \
        -library "$SKIKO_KLIB" \
        -library "$ATOMICFU" \
        -library "$ATOMICFU_CINTEROP" \
        -library "$COROUTINES" \
        "${CINTEROP_ARGS[@]}" \
        "$OUT_DIR/renderer-smoke.kt" \
        -o "$OUT_DIR/renderer-smoke"
    RENDERER_SMOKE_EXE="$OUT_DIR/renderer-smoke.exe"
fi

WINEDEBUG_VALUE=${WINEDEBUG:--all}
WINEDEBUG="$WINEDEBUG_VALUE" wine "$OUT_DIR/skiko-wine-smoke.exe"
WINEDEBUG="$WINEDEBUG_VALUE" wine "$OUT_DIR/native-library-smoke.exe"
if [[ -n "$RENDERER_SMOKE_EXE" ]]; then
    WINEDEBUG="$WINEDEBUG_VALUE" wine "$RENDERER_SMOKE_EXE"
fi
