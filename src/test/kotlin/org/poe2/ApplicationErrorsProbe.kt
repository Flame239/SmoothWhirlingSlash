package org.poe2

import java.io.OutputStream
import org.poe2.shortcuts.ShortcutBindings
import org.poe2.win32.GlobalShortcuts
import org.poe2.win32.DpiAwareness
import org.poe2.win32.ExtendedUser32
import org.poe2.win32.DPI_AWARENESS_PER_MONITOR_AWARE
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.poe2.shortcuts.ShortcutRegistrationStatus
import org.poe2.shortcuts.ShortcutBinding
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** Separate JVM entry point: none of these failures can terminate the test runner. */
object ApplicationErrorsProbe {
    @JvmStatic fun main(args: Array<String>) {
        ApplicationErrors.install(Path.of(args.getOrElse(1) { "application.log" }))
        val failure = IllegalStateException("probe-${args[0]}", IllegalArgumentException("probe-cause")).apply {
            addSuppressed(IllegalArgumentException("probe-suppressed"))
        }
        when (args[0]) {
            "startup" -> throw failure
            "background" -> Thread({ throw failure }, "probe-worker").start()
            "swing" -> {
                ApplicationErrors.installSwingHandler()
                SwingUtilities.invokeLater { throw failure }
            }
            "scheduled" -> Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay({
                ApplicationErrors.guard { throw failure }
            }, 0, 10, TimeUnit.MILLISECONDS)
            "future" -> ApplicationErrors.observe(CompletableFuture.runAsync { throw failure })
            "callback" -> ApplicationErrors.observe(CompletableFuture.completedFuture(Unit).thenRun { throw failure })
            "shutdown" -> {
                Runtime.getRuntime().addShutdownHook(Thread({ error("probe-cleanup") }, "probe-cleanup"))
                throw failure
            }
            "write-failure" -> {
                val field = ApplicationErrors::class.java.getDeclaredField("file").apply { isAccessible = true }
                (field.get(ApplicationErrors) as OutputStream).close()
                System.out.println("probe-write")
            }
            "console" -> {
                println("probe-out Grüße")
                System.err.println("probe-err")
                exitProcess(0)
            }
            "normal" -> exitProcess(0)
            "dpi-startup" -> {
                DpiAwareness.usePhysicalPixelCoordinates()
                check(System.getProperty("sun.java2d.uiScale") == "1")
                val api = ExtendedUser32.INSTANCE
                check(api.GetAwarenessFromDpiAwarenessContext(api.GetThreadDpiAwarenessContext()) == DPI_AWARENESS_PER_MONITOR_AWARE)
                // Repeating startup setup must also accept an already configured process.
                DpiAwareness.usePhysicalPixelCoordinates()
                println("probe-dpi-startup-completed")
                exitProcess(0)
            }
            "hotkey-exit" -> {
                repeat(5) {
                    val registered = CountDownLatch(1)
                    val hotkeys = GlobalShortcuts(ShortcutBindings(), {}, { registered.countDown() })
                    registered.await()
                    hotkeys.close()
                }
                exitProcess(0)
            }
            "hotkey-changes" -> {
                val statuses = CopyOnWriteArrayList<ShortcutRegistrationStatus>()
                GlobalShortcuts(ShortcutBindings(), {}, { statuses += it }).use { shortcuts ->
                    // Queue immediately, including before the native thread has published its ID.
                    shortcuts.setSuspended(true).get(5, TimeUnit.SECONDS)
                    shortcuts.updateBindings(ShortcutBindings(visibility = ShortcutBinding(3, 118)))
                        .get(5, TimeUnit.SECONDS)
                    shortcuts.setSuspended(false).get(5, TimeUnit.SECONDS)
                    check(statuses.size == 4)
                    check(!statuses[1].emergencyAvailable && statuses[1].errors.isEmpty())
                    check(!statuses[2].emergencyAvailable && statuses[2].errors.isEmpty())
                }
                println("probe-hotkey-changes-completed")
                exitProcess(0)
            }
            "cancelled" -> {
                val pending = CompletableFuture<Unit>()
                ApplicationErrors.observe(pending.thenApply { it })
                pending.cancel(false)
                exitProcess(0)
            }
        }
        CountDownLatch(1).await()
    }
}
