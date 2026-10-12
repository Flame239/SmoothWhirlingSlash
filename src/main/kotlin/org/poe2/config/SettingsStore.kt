package org.poe2.config

import kotlinx.serialization.json.Json
import org.poe2.ApplicationFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SettingsStore(val path: Path = ApplicationFiles.settings) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppSettings {
        if (!Files.exists(path)) return AppSettings()
        val settings = json.decodeFromString<AppSettings>(Files.readString(path))
        settings.validationError()?.let { throw IllegalArgumentException(it) }
        return settings
    }

    @Synchronized
    fun save(settings: AppSettings) {
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(settings))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
