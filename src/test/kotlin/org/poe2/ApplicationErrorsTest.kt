package org.poe2

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ApplicationErrorsTest {
    private val java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString()
    private val classpath = System.getProperty("test.runtimeClasspath")

    private fun run(directory: Path, mode: String, log: String = "application.log",
        mainClass: String = "org.poe2.ApplicationErrorsProbe"): Pair<Int, String> {
        val output = directory.resolve("console.txt")
        val command = listOf(java, "--enable-native-access=ALL-UNNAMED", "-cp", classpath, mainClass) +
            if (mainClass.endsWith("Probe")) listOf(mode, log) else emptyList()
        val process = ProcessBuilder(command).directory(directory.toFile())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Child process did not terminate for $mode")
            return process.exitValue() to Files.readString(output)
        } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
    }

    private fun temporary(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("whirling-log-test")
        try { block(directory) } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun `fatal failures from all execution boundaries are flushed and exit nonzero`() {
        for (mode in listOf("startup", "background", "swing", "scheduled", "future", "callback")) temporary { directory ->
            val (code, console) = run(directory, mode)
            assertEquals(1, code, mode)
            val log = Files.readString(directory.resolve("application.log"))
            assertTrue(log.contains("probe-$mode"), mode)
            assertTrue(log.contains("Caused by: java.lang.IllegalArgumentException: probe-cause"), mode)
            assertTrue(log.contains("Suppressed: java.lang.IllegalArgumentException: probe-suppressed"), mode)
            assertTrue(log.contains("at org.poe2.ApplicationErrorsProbe.main"), mode)
            assertTrue(Regex("\\[\\d{4}-\\d{2}-\\d{2}T[^]]+] FATAL \\[[^]]+]").containsMatchIn(log), mode)
            assertEquals(1, Regex("FATAL").findAll(log).count(), mode)
            assertTrue(console.contains("probe-$mode"), mode)
        }
    }

    @Test fun `console output is UTF8 retained in console and appended across launches`() = temporary { directory ->
        repeat(2) {
            val (code, console) = run(directory, "console")
            assertEquals(0, code)
            assertTrue(console.contains("probe-out"))
            assertTrue(console.contains("probe-err"))
        }
        val log = Files.readString(directory.resolve("application.log"))
        assertEquals(2, Regex("probe-out Grüße").findAll(log).count())
        assertEquals(2, Regex("probe-err").findAll(log).count())
        assertFalse(log.contains("FATAL"))
        assertEquals(0, run(directory, "normal").first)
        assertEquals(0, run(directory, "cancelled").first)
        assertEquals(0, run(directory, "hotkey-exit").first)
    }

    @Test fun `log open and write failures report to original stderr and terminate`() = temporary { directory ->
        Files.createDirectory(directory.resolve("log-directory"))
        val (openCode, openConsole) = run(directory, "normal", "log-directory")
        assertEquals(1, openCode)
        assertTrue(openConsole.contains("Could not open or write application.log"))
        val (writeCode, writeConsole) = run(directory, "write-failure")
        assertEquals(1, writeCode)
        assertTrue(writeConsole.contains("Could not open or write application.log"))
        assertTrue(writeConsole.contains("java.nio.channels.ClosedChannelException"))
    }

    @Test fun `native shortcut changes queued during startup complete in order and close cleanly`() = temporary { directory ->
        val (code, console) = run(directory, "hotkey-changes")
        assertEquals(0, code, console)
        assertTrue(console.contains("probe-hotkey-changes-completed"))
        assertFalse(Files.readString(directory.resolve("application.log")).contains("FATAL"))
    }

    @Test fun `startup configures physical pixels and accepts existing DPI awareness`() = temporary { directory ->
        val (code, console) = run(directory, "dpi-startup")
        assertEquals(0, code, console)
        assertTrue(console.contains("probe-dpi-startup-completed"))
    }

    @Test fun `shutdown failures are logged without blocking an existing fatal exit`() = temporary { directory ->
        assertEquals(1, run(directory, "shutdown").first)
        val log = Files.readString(directory.resolve("application.log"))
        assertTrue(log.contains("probe-shutdown"))
        assertTrue(log.contains("probe-cleanup"))
        assertEquals(2, Regex("FATAL").findAll(log).count())
    }

    @Test fun `actual startup logs malformed settings and preserves the file`() = temporary { directory ->
        Files.writeString(directory.resolve("settings.json"), "broken json")
        assertEquals(1, run(directory, "settings", mainClass = "org.poe2.MainKt").first)
        val log = Files.readString(directory.resolve("application.log"))
        assertTrue(log.contains("JsonDecodingException"))
        assertEquals("broken json", Files.readString(directory.resolve("settings.json")))
    }
}
