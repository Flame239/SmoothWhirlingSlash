package org.poe2

import org.poe2.config.AppSettings
import org.poe2.config.SettingsStore
import java.nio.file.Files
import kotlin.test.*

class SettingsStoreTest {
    @Test fun `legacy window title is ignored while calibration and shortcuts survive`() {
        val directory = Files.createTempDirectory("whirling-legacy-settings")
        val path = directory.resolve("settings.json")
        try {
            Files.writeString(path, """{"windowTitle":"Custom old title","innerX":175.0,"snapKey":70,
                "hotkeys":{"visibility":{"modifiers":3,"key":118},"calibration":{"modifiers":3,"key":122}}}""")
            val store = SettingsStore(path)
            val loaded = store.load()
            assertEquals(175.0, loaded.innerX)
            assertEquals(70, loaded.snapKey)
            assertEquals(118, loaded.hotkeys.visibility.key)
            assertEquals(122, loaded.hotkeys.showSettings.key)
            store.save(loaded)
            val saved = Files.readString(path)
            assertFalse(saved.contains("windowTitle"))
            assertTrue(saved.contains("\"calibration\""))
            assertFalse(saved.contains("showSettings"))
            assertFalse(saved.contains("rangeCalibration"))
            assertEquals(loaded, store.load())
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory.resolve("settings.json.tmp"))
            Files.deleteIfExists(directory)
        }
    }

    @Test fun `settings round trip and corrupt input is preserved`() {
        val directory = Files.createTempDirectory("whirling-settings-test")
        val path = directory.resolve("settings.json")
        try {
            val store = SettingsStore(path)
            assertEquals(AppSettings(), store.load())
            val settings = AppSettings(innerX = 115.0, offsetY = 35.0, dashedTarget = false)
            store.save(settings)
            assertEquals(settings, store.load())
            Files.writeString(path, "broken json")
            assertFailsWith<kotlinx.serialization.SerializationException> { store.load() }
            assertEquals("broken json", Files.readString(path))
            Files.writeString(path, """{"innerX":-1.0}""")
            assertFailsWith<IllegalArgumentException> { store.load() }
            assertEquals("""{"innerX":-1.0}""", Files.readString(path))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory.resolve("settings.json.tmp"))
            Files.deleteIfExists(directory)
        }
    }
}
