package org.poe2

import org.poe2.config.SettingsStore
import org.poe2.win32.GameWindow
import org.poe2.win32.NativeKeyboardAndCursor
import org.poe2.win32.DpiAwareness
import org.poe2.ui.UiTheme
import javax.swing.SwingUtilities

const val GAME_WINDOW_TITLE = "Path of Exile 2"

fun main() {
    ApplicationErrors.install()
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        error("SmoothWhirlingSlash requires Windows 10/11.")
    }
    DpiAwareness.usePhysicalPixelCoordinates()
    ApplicationErrors.installSwingHandler()
    val store = SettingsStore()
    val settings = store.load()
    val game = GameWindow.findByTitle(GAME_WINDOW_TITLE)
        ?: error("PoE2 was not found. Open it in borderless-windowed mode, then restart this application. " +
            "Expected window title containing '$GAME_WINDOW_TITLE'.")
    SwingUtilities.invokeLater {
        UiTheme.install()
        val app = WhirlingSlashApplication(game, NativeKeyboardAndCursor(), store, settings)
        Runtime.getRuntime().addShutdownHook(Thread({ app.close() }, "overlay-cleanup"))
        app.start()
    }
}
