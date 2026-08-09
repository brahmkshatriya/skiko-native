import org.gradle.api.invocation.Gradle
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.*
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.msvcpp.*
import org.gradle.util.internal.VersionNumber
import java.io.File
import kotlin.math.abs

data class WindowsSdkPaths(
    val compiler: File,
    val linker: File,
    val librarian: File,
    val dumpbin: File?,
    val includeDirs: Collection<File>,
    val libDirs: Collection<File>,
    val toolchainVersion: VersionNumber,
    val isCrossCompiling: Boolean = false,
)

private const val ENV_SKIKO_VSBT_PATH = "SKIKO_VSBT_PATH"
private const val ENV_SKIKO_VSBT_VERSION = "SKIKO_VSBT_VERSION"
private const val ENV_SKIKO_WINDOWS_SDK_VERSION = "SKIKO_WINDOWS_SDK_VERSION"
const val ENV_SKIKO_WINDOWS_SDK_ROOT = "SKIKO_WINDOWS_SDK_ROOT"

val isLinuxToWindowsMsvcCrossCompilationEnabled: Boolean
    get() =
        hostOs == OS.Linux &&
            !System.getenv(ENV_SKIKO_WINDOWS_SDK_ROOT).isNullOrBlank()

fun findWindowsSdkPaths(gradle: Gradle, arch: Arch): WindowsSdkPaths {
    if (hostOs == OS.Linux) {
        check(isLinuxToWindowsMsvcCrossCompilationEnabled) {
            "Cross-compiling the Windows MSVC bridge from Linux requires " +
                "$ENV_SKIKO_WINDOWS_SDK_ROOT to point to an xwin splat directory"
        }
        return findXwinWindowsSdkPaths(arch)
    }

    check(hostOs.isWindows) {
        "Unexpected host os: $hostOs, expected Windows or an opt-in Linux xwin toolchain"
    }

    val hostPlatform = host()
    val finder = GradleWindowsComponentFinderWrapper(gradle, hostPlatform, arch)
    val visualCpp = finder.findVisualCpp()
    val windowsSdk = finder.findWindowsSdk()
    val ucrt = finder.findUcrt()
    val winrt = finder.findWinrt()
    val systemLibraries = listOf(visualCpp, windowsSdk, ucrt, winrt)
    val compiler = visualCpp.compilerExecutable.fixPathFor(arch)
    val linker = visualCpp.linkerExecutable.fixPathFor(arch)
    return WindowsSdkPaths(
        compiler = compiler,
        linker = linker,
        librarian = linker.parentFile.resolve("lib.exe"),
        dumpbin = linker.parentFile.resolve("dumpbin.exe"),
        includeDirs = systemLibraries.flatMap { it.includeDirs }.map { it.fixPathFor(arch) },
        libDirs = systemLibraries.flatMap { it.libDirs }.map { it.fixPathFor(arch) },
        toolchainVersion = visualCpp.implementationVersion,
    )
}

private fun findXwinWindowsSdkPaths(arch: Arch): WindowsSdkPaths {
    val architectureNames = when (arch) {
        Arch.X64 -> listOf("x86_64", "x64")
        Arch.Arm64 -> listOf("aarch64", "arm64")
        Arch.Wasm -> error("xwin does not support the $arch architecture")
    }
    val sdkRoot = File(
        System.getenv(ENV_SKIKO_WINDOWS_SDK_ROOT)
            ?: error("$ENV_SKIKO_WINDOWS_SDK_ROOT is not set")
    ).absoluteFile
    check(sdkRoot.isDirectory) {
        "$ENV_SKIKO_WINDOWS_SDK_ROOT points to a missing directory: $sdkRoot\n" +
            "Create it with: xwin --accept-license --arch ${architectureNames.first()} " +
            "splat --output $sdkRoot"
    }

    val crtInclude = sdkRoot.resolve("crt/include").requireDirectory("MSVC CRT headers")
    val sdkInclude = sdkRoot.resolve("sdk/include")
    val includeDirs = buildList {
        add(crtInclude)
        add(sdkInclude.resolve("ucrt").requireDirectory("Windows UCRT headers"))
        add(sdkInclude.resolve("um").requireDirectory("Windows UM headers"))
        add(sdkInclude.resolve("shared").requireDirectory("Windows shared headers"))
        add(sdkInclude.resolve("winrt").requireDirectory("Windows WinRT headers"))
        sdkInclude.resolve("cppwinrt").takeIf { it.isDirectory }?.let { add(it) }
    }

    val libDirs = listOf(
        sdkRoot.resolve("crt/lib").findArchitectureDirectory(architectureNames, "MSVC CRT libraries"),
        sdkRoot.resolve("sdk/lib/um").findArchitectureDirectory(architectureNames, "Windows UM libraries"),
        sdkRoot.resolve("sdk/lib/ucrt").findArchitectureDirectory(architectureNames, "Windows UCRT libraries"),
    )

    return WindowsSdkPaths(
        compiler = findExecutableOnPath("clang-cl"),
        linker = findExecutableOnPath("lld-link"),
        librarian = findExecutableOnPath("llvm-lib"),
        // Native bridge builds don't extract COFF symbols. Keep this absent instead of pretending
        // llvm-nm emits dumpbin-compatible output if this toolchain is later used for JVM builds.
        dumpbin = null,
        includeDirs = includeDirs,
        libDirs = libDirs,
        toolchainVersion = VersionNumber.parse("0.0"),
        isCrossCompiling = true,
    )
}

fun WindowsSdkPaths.requireDumpbin(): File =
    dumpbin ?: error(
        "dumpbin is unavailable in the Linux-to-Windows cross toolchain; " +
            "cross-host Windows JVM bridge builds are not supported"
    )

private fun File.requireDirectory(description: String): File {
    check(isDirectory) {
        "$description are missing from the xwin splat: $this\n" +
            "Recreate $ENV_SKIKO_WINDOWS_SDK_ROOT with xwin's default splat layout"
    }
    return this
}

private fun File.findArchitectureDirectory(names: List<String>, description: String): File =
    names.asSequence()
        .map(::resolve)
        .firstOrNull(File::isDirectory)
        ?: error(
            "$description are missing below $this; expected one of " +
                names.joinToString { resolve(it).toString() }
        )

private fun findExecutableOnPath(name: String): File {
    val path = System.getenv("PATH").orEmpty()
    return path.split(File.pathSeparatorChar)
        .asSequence()
        .filter(String::isNotBlank)
        .map { File(it).resolve(name) }
        .firstOrNull { it.isFile && it.canExecute() }
        ?: error(
            "$name is required for Linux-to-Windows MSVC cross-compilation but was not found on PATH"
        )
}

// workaround until https://github.com/gradle/gradle/pull/21780 is merged
private fun File.fixPathFor(arch: Arch) = File(absolutePath.replace("x64", arch.id))

private class GradleWindowsComponentFinderWrapper(
    private val gradle: Gradle,
    private val hostPlatform: NativePlatformInternal,
    private val arch: Arch,
) {
    fun findVisualCpp(): VisualCpp {
        val skikoVsbtPath = System.getenv(ENV_SKIKO_VSBT_PATH)

        val vsLocator = gradle.serviceOf<VisualStudioLocator>()
        val vsComponent = if (skikoVsbtPath != null) {
            val vsbtDir = File(skikoVsbtPath)
            check(vsbtDir.isDirectory) {
                "Environment variable '$ENV_SKIKO_VSBT_PATH' points to non-existing directory: '$skikoVsbtPath'\n" +
                        "Please set it to existing Visual Studio Build Tools installation"
            }
            val searchResult = vsLocator.locateComponent(vsbtDir)
            if (!searchResult.isAvailable)
                error("Could not find valid Visual Studio Build Tools installation " +
                        "at the location specified by '$ENV_SKIKO_VSBT_PATH': $skikoVsbtPath"
                )
            else searchResult.component
        } else {
            vsLocator.locateAllComponents().chooseComponentByPreferredVersion(
                componentType = "VS Build Tools",
                preferredVersionEnvVar = ENV_SKIKO_VSBT_VERSION
            )
        }

        return vsComponent.visualCpp.forPlatform(hostPlatform)
            ?: error("Visual Studio location component for host platform '$hostPlatform' is null")
    }

    fun findWindowsSdk(): SystemLibraries {
        val windowsSdkLocator = gradle.serviceOf<WindowsSdkLocator>()
        val windowsSdkComponent = windowsSdkLocator.locateAllComponents()
            .chooseComponentByPreferredVersion(
                componentType = "Windows SDK",
                preferredVersionEnvVar = ENV_SKIKO_WINDOWS_SDK_VERSION
            )
        return windowsSdkComponent.forPlatform(hostPlatform)
            ?: error("Windows SDK component for host platform '$hostPlatform' is null")
    }

    fun findUcrt(): SystemLibraries {
        val ucrtLocator = gradle.serviceOf<UcrtLocator>()
        val ucrtComponent = ucrtLocator.locateAllComponents()
            .chooseComponentByPreferredVersion(
                componentType = "UCRT",
                preferredVersionEnvVar = ENV_SKIKO_WINDOWS_SDK_VERSION
            )
        return ucrtComponent.getCRuntime(hostPlatform)
            ?: error("UCRT component for host platform '$hostPlatform' is null")
    }

    fun findWinrt(): SystemLibraries {
        // Gradle doesn't have a Locator for WinRT, so we take the UCRT one and fix the path
        return object : EmptySystemLibraries() {
            override fun getIncludeDirs(): List<File> {
                return findUcrt().includeDirs.map { File(it.path.replace("ucrt", "winrt")) }
            }
        }
    }

    private fun <T : Any> List<T>.chooseComponentByPreferredVersion(
        componentType: String,
        preferredVersionEnvVar: String,
    ): T = chooseComponentByPreferredVersion(componentType, preferredVersionEnvVar, this)

    private fun <T : Any> chooseComponentByPreferredVersion(
        componentType: String,
        preferredVersionEnvVar: String,
        components: List<T>
    ): T {
        return when (components.size) {
            0 -> error("Could not find any $componentType locations")
            1 -> components.single()
            else -> {
                val versions = components.associateBy { component ->
                    when (component) {
                        is WindowsKitInstall -> component.version
                        is WindowsSdkInstall -> component.version
                        is VisualStudioInstall -> component.version
                        else -> error("Unknown class of $componentType: ${component.javaClass.canonicalName}")
                    }
                }

                val preferredVersion = System.getenv(preferredVersionEnvVar)
                if (preferredVersion != null) {
                    for ((version, component) in versions.entries) {
                        if (preferredVersion == version.toString()) return component
                    }
                }

                val latestVersion = versions.keys.maxOf { it }
                val warningMessage = buildString {
                    appendLine("w: Multiple $componentType versions are found: ${versions.keys.joinToString(", ") { "'$it'"}}")
                    appendLine("Using the latest version '$latestVersion'")
                    appendLine("Use '$preferredVersionEnvVar' environment variable to specify the preferred version")
                }
                gradle.rootProject.logger.warn(warningMessage)
                versions[latestVersion]!!
            }
        }
    }
}
