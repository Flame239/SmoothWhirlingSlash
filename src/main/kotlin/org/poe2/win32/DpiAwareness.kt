package org.poe2.win32

object DpiAwareness {
    /** Must run before AWT initialization so drawing and cursor movement share physical pixels. */
    fun usePhysicalPixelCoordinates() {
        System.setProperty("sun.java2d.uiScale", "1")
        val api = ExtendedUser32.INSTANCE
        if (api.SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)) return
        // A launcher may have established awareness already; Windows then rejects setting it again.
        check(api.GetAwarenessFromDpiAwarenessContext(api.GetThreadDpiAwarenessContext()) == DPI_AWARENESS_PER_MONITOR_AWARE) {
            "Per-monitor DPI awareness could not be established. Restart with -Dsun.java2d.uiScale=1."
        }
    }
}
