import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Filters incompatible MSVC linker directives from COFF archives before Kotlin/Native consumes them.
 *
 * JetBrains' Windows Skia archives are built with the static MSVC runtime (`/MT`). Kotlin/Native's
 * Windows executables use MinGW startup and CRT objects. Letting both runtimes' default-library
 * directives participate in the final link produces conflicting TLS and heap initialization.
 * Blanking only `DEFAULTLIB` and `FAILIFMISMATCH` tokens lets the native target provide one explicit
 * MSVC ABI compatibility layer. Other directives, especially `ALTERNATENAME` fallbacks used by the
 * standard library and Skia, must remain intact.
 */
abstract class StripCoffDirectivesTask : DefaultTask() {
    @get:InputFiles
    abstract val inputLibraries: ListProperty<File>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun strip() {
        val destination = outputDir.get().asFile
        destination.mkdirs()
        inputLibraries.get().forEach { input ->
            val output = destination.resolve(input.name)
            val bytes = input.readBytes()
            blankIncompatibleDirectives(bytes)
            output.writeBytes(bytes)
        }
    }

    private fun blankIncompatibleDirectives(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val prefix = REMOVED_DIRECTIVE_PREFIXES.firstOrNull { matchesAscii(bytes, offset, it) }
            if (prefix == null) {
                offset++
                continue
            }

            var end = offset + prefix.length
            if (end < bytes.size && bytes[end] == QUOTE) {
                end++
                while (end < bytes.size && bytes[end] != QUOTE && bytes[end] != NUL) end++
                if (end < bytes.size && bytes[end] == QUOTE) end++
            } else {
                while (end < bytes.size && !bytes[end].isDirectiveSeparator()) end++
            }
            bytes.fill(SPACE, offset, end)
            offset = end
        }
    }

    private fun matchesAscii(bytes: ByteArray, offset: Int, expected: String): Boolean {
        if (offset + expected.length > bytes.size) return false
        return expected.indices.all { index ->
            bytes[offset + index].toInt().toChar().lowercaseChar() == expected[index]
        }
    }

    private fun Byte.isDirectiveSeparator(): Boolean =
        this == NUL || this == SPACE || this == TAB || this == CR || this == LF

    private companion object {
        val REMOVED_DIRECTIVE_PREFIXES =
            listOf("/defaultlib:", "-defaultlib:", "/failifmismatch:", "-failifmismatch:")

        val NUL = 0.toByte()
        val SPACE = ' '.code.toByte()
        val TAB = '\t'.code.toByte()
        val CR = '\r'.code.toByte()
        val LF = '\n'.code.toByte()
        val QUOTE = '"'.code.toByte()
    }
}
