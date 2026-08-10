# Skiko Native

Skiko exposes the [Skia](https://skia.org) graphics API to Kotlin Multiplatform. It supports
offscreen drawing, GPU rendering, text and paragraph layout, images, paths, effects, and native
window rendering through `SkiaLayer`.

This fork adds complete Kotlin/Native desktop targets for Linux and Windows. Native applications
do not require a JVM at runtime.

## Supported targets

| Kotlin target | Architectures | Status |
| --- | --- | --- |
| JVM Linux | x64, arm64 | Available |
| JVM Windows | x64 | Available |
| JVM macOS | x64, arm64 | Available |
| Android | x64, arm64; API 24+ | Available |
| JavaScript and WebAssembly | Browser | Available |
| Kotlin/Native Linux | x64, arm64 | Available |
| Kotlin/Native Windows | x64 | Available |
| Kotlin/Native macOS | x64, arm64 | Available |
| Kotlin/Native iOS | Device and simulator | Available |
| Kotlin/Native Windows arm64 | — | Not currently available |

The Linux and Windows native artifacts are development snapshots. Pin the repository revision and
artifact version used by your application.

## Features

Skiko provides Kotlin APIs for:

* `Canvas`, `Paint`, paths, regions, clipping, transforms, and blend modes
* Raster and GPU-backed surfaces
* PNG, JPEG, WebP, and other Skia-supported image formats
* Fonts, text blobs, Unicode text shaping, and SkParagraph layout
* Gradients, shaders, color filters, image filters, masks, and runtime effects
* SVG DOM and animation-related Skia APIs
* Pictures, drawables, vertices, meshes, and encoded image output
* Native resource loading
* Skottie through the separate `skiko-skottie` artifacts

### Native desktop rendering

| Feature | Linux Native | Windows Native |
| --- | --- | --- |
| Default GPU renderer | OpenGL/Ganesh | Direct3D 12/Ganesh |
| Additional GPU renderer | — | WGL/OpenGL |
| Fast software renderer | Yes | Yes |
| Compatible software renderer | Yes | Yes |
| Automatic GPU fallback | OpenGL → software | Direct3D → OpenGL → software |
| Context/device-loss recovery | Yes | Yes |
| VSync and frame limiting | Yes | Yes |
| Double/triple buffering requests | Yes | Yes |
| Transparent window buffers | Host/compositor dependent | DirectComposition or DWM dependent |
| GPU priority selection | Yes | Yes |
| GPU compatibility filtering | Host dependent | Yes |
| GPU resource-cache limits | Yes | Yes |
| Pixel geometry | Yes | Yes |
| Frame snapshots | Yes | Yes |
| Renderer analytics and diagnostics | Yes | Yes |
| FPS and long-frame diagnostics | Yes | Yes |
| System theme | Supplied by the window host | Read from Windows settings |

`SkiaLayer` can switch renderers at runtime and automatically retries or falls back after renderer
initialization and rendering failures. Diagnostics report the requested and active renderer,
device, fallback and recovery counts, effective framebuffer count, transparency state, pixel
geometry, GPU cache limit, and FPS statistics.

Transparency and exact presentation behavior depend on the operating system, graphics driver, and
desktop compositor. A triple-buffering request creates a three-buffer DXGI swap chain on Windows,
but final presentation scheduling remains driver/compositor controlled.

ANGLE can be detected and loaded on Windows, but it is not currently a Kotlin/Native renderer
backend.

## Requirements

All source builds require:

* Git
* JDK 21
* A C/C++ compiler and LLVM tools for the selected native target

Gradle downloads the matching Skia archives and Kotlin/Native toolchain when needed.

## Build Kotlin/Native for Linux

Install the Linux development tools and graphics headers. On Debian or Ubuntu:

```shell
sudo apt install ninja-build clang fontconfig libfontconfig1-dev libglu1-mesa-dev \
    libxrandr-dev libdbus-1-dev libx11-dev zip
```

Build the Linux x64 KLIB:

```shell
./gradlew -p skiko linuxX64MainKlibrary \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.linux.enabled=true
```

Publish the multiplatform metadata and Linux x64 artifact to Maven Local:

```shell
./gradlew -p skiko \
    publishKotlinMultiplatformPublicationToMavenLocal \
    publishLinuxX64PublicationToMavenLocal \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.linux.enabled=true
```

For Linux arm64, replace `linuxX64` with `linuxArm64` in the task name.

## Build Kotlin/Native for Windows

### On a Windows host

Install:

* Visual Studio Build Tools 2022 with **Desktop development with C++**
* A Windows SDK
* LLVM with `clang-cl` available on `PATH`

Set `SKIKO_VSBT_PATH` when Visual Studio Build Tools is not installed at its default location:

```text
SKIKO_VSBT_PATH=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools
```

Build and publish:

```shell
./gradlew -p skiko mingwX64MainKlibrary \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true

./gradlew -p skiko \
    publishKotlinMultiplatformPublicationToMavenLocal \
    publishMingwX64PublicationToMavenLocal \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true
```

### Cross-compile Windows from Linux

The verified Linux cross-build uses:

* `clang-cl`, `lld-link`, and `llvm-lib` on `PATH`
* [xwin](https://github.com/Jake-Shadle/xwin)
* Wine for optional runtime tests

Create the SDK layout:

```shell
xwin --accept-license --arch x86_64 splat --output "$PWD/.xwin"
export SKIKO_WINDOWS_SDK_ROOT="$PWD/.xwin"
```

Build or publish with the same Windows tasks:

```shell
./gradlew -p skiko mingwX64MainKlibrary \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true

./gradlew -p skiko \
    publishKotlinMultiplatformPublicationToMavenLocal \
    publishMingwX64PublicationToMavenLocal \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true
```

`SKIKO_WINDOWS_SDK_ROOT` is required for a rendering-capable Windows KLIB when building on Linux.
Without it, the build can compile the pure Kotlin Windows API surface but cannot include the Skia
C++ bridge.

The Windows native build handles Skia's MSVC `/MT` archives and Kotlin/Native's MinGW CRT
internally. Gradle embeds the filtered static MSVC runtime archives needed by the Skia bridge and
wraps Skia's process-exit `atexit` registration, avoiding the incompatible mixed-CRT startup/exit
path. Consumers should not add replacement `msvcrt` compatibility archives or duplicate MSVC CRT
linker flags manually.

## Use in a Kotlin Multiplatform project

The native development version in this fork is `0.0.1-linux-native-SNAPSHOT`. Publish the required
target to Maven Local first, then configure the consumer:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.20"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    linuxX64()
    mingwX64()

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.skiko:skiko:0.0.1-linux-native-SNAPSHOT")
    }
}
```

Kotlin Multiplatform selects the corresponding `skiko-linuxx64` or `skiko-mingwx64` KLIB from the
root `skiko` publication.

### Minimal drawing example

The Skia API can be used without a window:

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

fun drawFrame() = Surface.makeRasterN32Premul(640, 360).use { surface ->
    surface.canvas.clear(Color.WHITE)
    Paint().use { paint ->
        paint.color = Color.makeRGB(90, 55, 180)
        surface.canvas.drawCircle(320f, 180f, 96f, paint)
    }
    surface.makeImageSnapshot()
}
```

Use `SkiaLayer` when rendering into a native window. Skiko supplies the rendering layer; a complete
widget toolkit and ready-made Linux/Windows window host are available in
[Compose Native](https://github.com/brahmkshatriya/compose-native).

## JVM dependency

For a JVM desktop application, add the common AWT API and the runtime matching the host:

```kotlin
val skikoVersion = "<version>"

dependencies {
    implementation("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion")
}
```

Runtime artifact suffixes include `linux-x64`, `linux-arm64`, `windows-x64`, `macos-x64`, and
`macos-arm64`.

## Windows runtime packaging

Windows applications using text shaping must place the matching `icudtl.dat` beside the executable.
The `skiko-mingwx64` publication provides it through the `icudtl` classifier:

```text
org.jetbrains.skiko:skiko-mingwx64:0.0.1-linux-native-SNAPSHOT:icudtl@dat
```

Application packagers must also include any DLLs directly imported by their final executable or
other native libraries. Skiko's MSVC runtime pieces used by the Skia bridge are linked as filtered
static archives by the build; they are not an extra packaging step. The Compose Native Windows
package task stages Skiko's ICU data, SDL3, and the required MinGW runtime DLLs automatically.

## Runtime configuration

Native desktop defaults can be supplied with `SkiaLayerProperties` or environment variables:

| Variable | Values | Default |
| --- | --- | --- |
| `SKIKO_RENDER_API` | Linux: `OPENGL`, `SOFTWARE_FAST`, `SOFTWARE_COMPAT`; Windows also supports `DIRECT3D` | OpenGL on Linux, Direct3D on Windows |
| `SKIKO_FRAME_BUFFERING` | `DEFAULT`, `DOUBLE`, `TRIPLE` | `DEFAULT` |
| `SKIKO_VSYNC_ENABLED` | `true`, `false` | `true` |
| `SKIKO_VSYNC_FRAMELIMIT_FALLBACK_ENABLED` | `true`, `false` | `true` |
| `SKIKO_GPU_PRIORITY` | `auto`, `integrated`, `discrete` | `auto` |
| `SKIKO_GPU_RESOURCE_CACHE_LIMIT` | Bytes, or a value with a `K`, `M`, or `G` suffix | Skia default |
| `SKIKO_PIXEL_GEOMETRY` | A `PixelGeometry` enum name | `UNKNOWN` |
| `SKIKO_FPS_ENABLED` | `true`, `false` | `false` |

FPS reporting can be refined with:

* `SKIKO_FPS_PERIOD_SECONDS`
* `SKIKO_FPS_LONG_FRAMES_SHOW`
* `SKIKO_FPS_LONG_FRAMES_MILLIS`

## Tests

Run the Linux native tests:

```shell
./gradlew -p skiko linuxX64Test \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.linux.enabled=true
```

Build the Windows native tests:

```shell
./gradlew -p skiko linkDebugTestMingwX64 \
    -Pskiko.awt.enabled=false \
    -Pskiko.native.windows.enabled=true
```

On Linux with Wine, run the Windows compilation, linking, Win32 window, test, and real WGL smoke
checks with:

```shell
./skiko/tools/run-windows-native-smoke-on-wine.sh
```

Set `SKIKO_WINDOWS_SDK_ROOT` before running the script to include the real C++ bridge and renderer
checks. A real Windows machine remains the authoritative environment for Direct3D presentation and
DirectComposition transparency testing.

Run JVM tests with:

```shell
./gradlew -p skiko awtTest
```

Enable interactive UI tests with `-Dskiko.test.ui.enabled=true`.

## Current limitations

* Native desktop artifacts are development snapshots and are not published to Maven Central.
* Kotlin/Native Windows is currently x64 only.
* ANGLE is not yet a Kotlin/Native Windows renderer backend.
* DirectComposition transparency requires a compatible real Windows compositor.
* Final VSync and buffering behavior can vary by driver and compositor.
* Cross-compiling the MSVC-compatible Windows bridge from Linux is experimental.

## API documentation

Generated Skiko API documentation is available at
[jetbrains.github.io/skiko](https://jetbrains.github.io/skiko/).

## License

Skiko is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
