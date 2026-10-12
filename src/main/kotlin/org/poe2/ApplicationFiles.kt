package org.poe2

import java.nio.file.Path

/** Packaged launchers supply their directory; development runs use the working directory. */
object ApplicationFiles {
    val directory: Path = Path.of(System.getProperty("whirlingSlash.directory", "")).toAbsolutePath().normalize()
    val settings: Path = directory.resolve("settings.json")
    val log: Path = directory.resolve("application.log")
}
