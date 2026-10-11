package org.poe2.win32

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MSG
import org.poe2.shortcuts.ShortcutBindings
import org.poe2.ApplicationErrors
import org.poe2.shortcuts.ShortcutAction
import org.poe2.shortcuts.ShortcutRegistrationStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** Registration and unregistration always happen on the thread owning the Win32 message queue. */
class GlobalShortcuts(
    initial: ShortcutBindings,
    private val onAction: (ShortcutAction) -> Unit,
    private val onStatus: (ShortcutRegistrationStatus) -> Unit,
) : AutoCloseable {
    private data class Command(val bindings: ShortcutBindings?, val suspended: Boolean?, val done: CompletableFuture<Unit>)
    private val commands = ConcurrentLinkedQueue<Command>()
    private val ready = CompletableFuture<Int>()
    @Volatile private var stopping = false
    private val thread = Thread({ processShortcutMessages(initial) }, "global-hotkeys").apply { isDaemon = true; start() }

    fun updateBindings(bindings: ShortcutBindings): CompletableFuture<Unit> = enqueueRegistrationChange(bindings, null)

    fun setSuspended(suspended: Boolean): CompletableFuture<Unit> = enqueueRegistrationChange(null, suspended)

    private fun enqueueRegistrationChange(bindings: ShortcutBindings?, suspended: Boolean?): CompletableFuture<Unit> {
        val done = CompletableFuture<Unit>()
        if (stopping) return done.also { it.cancel(false) }
        val command = Command(bindings, suspended, done)
        commands.add(command)
        ApplicationErrors.observe(ready.whenComplete { threadId, error ->
            if (error != null) { commands.remove(command); done.completeExceptionally(error) }
            else try { signalMessageThread(threadId, UPDATE_BINDINGS_MESSAGE) } catch (failure: Throwable) {
                commands.remove(command); done.completeExceptionally(failure)
                throw failure
            }
        })
        if (stopping && commands.remove(command)) done.cancel(false)
        return done
    }

    private fun signalMessageThread(threadId: Int, message: Int) {
        check(User32.INSTANCE.PostThreadMessage(threadId, message, WPARAM(0), LPARAM(0)) != 0) { "Could not signal hotkey thread." }
    }

    private fun processShortcutMessages(initial: ShortcutBindings) {
        val user = User32.INSTANCE
        val registration = ShortcutRegistration(initial, { id, binding ->
            if (user.RegisterHotKey(null, id, binding.modifiers or WinUser.MOD_NOREPEAT, binding.key)) null
            else "Win32 ${Native.getLastError()}"
        }, { user.UnregisterHotKey(null, it) })
        try {
            val message = MSG()
            user.PeekMessage(message, null, 0, 0, PM_NOREMOVE) // Create the queue before publishing its thread ID.
            ready.complete(Kernel32.INSTANCE.GetCurrentThreadId())
            onStatus(registration.applyBindings())
            while (!stopping) {
                val result = user.GetMessage(message, null, 0, 0)
                check(result != GET_MESSAGE_ERROR) { "GetMessage failed (${Native.getLastError()})." }
                if (result == GET_MESSAGE_QUIT || stopping) break
                when (message.message) {
                    UPDATE_BINDINGS_MESSAGE -> {
                        while (true) {
                            val command = commands.poll() ?: break
                            try {
                                if (command.suspended == false) {
                                    // Discard pre-capture messages before the applied shortcuts become active again.
                                    val stale = MSG()
                                    while (user.PeekMessage(stale, null, WinUser.WM_HOTKEY, WinUser.WM_HOTKEY, PM_REMOVE)) { }
                                }
                                onStatus(registration.applyBindings(command.bindings, command.suspended))
                                command.done.complete(Unit)
                            } catch (e: Exception) {
                                command.done.completeExceptionally(e)
                                throw e
                            }
                        }
                    }
                    WinUser.WM_HOTKEY -> {
                        registration.registeredAction(message.wParam.toInt())?.let(onAction)
                    }
                }
            }
        } catch (e: Throwable) {
            ready.completeExceptionally(e)
            throw e
        } finally {
            stopping = true
            registration.unregisterAll()
            while (true) {
                val command = commands.poll() ?: break
                command.done.cancel(false)
            }
        }
    }

    override fun close() {
        // Wake the live message loop before allowing it to exit and destroy its queue.
        if (!stopping && thread.isAlive) {
            signalMessageThread(ready.get(THREAD_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS), WinUser.WM_QUIT)
        }
        stopping = true
        if (Thread.currentThread() !== thread) {
            try { thread.join(THREAD_JOIN_TIMEOUT_MS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    companion object {
        private const val UPDATE_BINDINGS_MESSAGE = WM_APP + 1
        private const val GET_MESSAGE_ERROR = -1
        private const val GET_MESSAGE_QUIT = 0
        private const val THREAD_READY_TIMEOUT_SECONDS = 5L
        private const val THREAD_JOIN_TIMEOUT_MS = 1000L
    }
}
