package org.poe2

import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** One process-wide destination for console output and failures, including executor failures. */
object ApplicationErrors {
    private val originalOut = System.out
    private val originalErr = System.err
    private val lock = Any()
    private val exiting = AtomicBoolean(false)
    private val loggingFailed = AtomicBoolean(false)
    private val reported = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    private var file: OutputStream? = null

    fun install(path: Path = Path.of("application.log")) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> fatal(error, thread) }
        try {
            file = Files.newOutputStream(path, CREATE, APPEND)
            System.setOut(PrintStream(ConsoleOutput(originalOut), true, UTF_8))
            System.setErr(PrintStream(ConsoleOutput(originalErr), true, UTF_8))
            Runtime.getRuntime().addShutdownHook(Thread({
                synchronized(lock) {
                    try { file?.flush() } catch (error: Throwable) { logFailure(error) }
                }
            }, "log-flush"))
        } catch (error: Throwable) {
            logFailure(error)
            throw error
        }
    }

    /** Install only after Win32 DPI awareness, since this initializes AWT. */
    fun installSwingHandler() {
        Toolkit.getDefaultToolkit().systemEventQueue.push(object : EventQueue() {
            override fun dispatchEvent(event: AWTEvent) {
                try { super.dispatchEvent(event) } catch (error: Throwable) { fatal(error) }
            }
        })
    }

    fun fatal(failure: Throwable, thread: Thread = Thread.currentThread()) {
        val error = unwrap(failure)
        synchronized(lock) {
            if (reported.add(error)) {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                val entry = "\n[${Instant.now()}] FATAL [${thread.name}]\n$trace"
                originalErr.print(entry)
                if (!loggingFailed.get()) {
                    try {
                        file?.write(entry.toByteArray(UTF_8))
                        file?.flush()
                    } catch (loggingError: Throwable) { logFailure(loggingError) }
                }
            }
        }
        startExit()
    }

    /** Scheduled executors store failures instead of invoking the uncaught-exception handler. */
    fun <T> guard(action: () -> T): T = try { action() } catch (error: Throwable) {
        fatal(error)
        throw error
    }

    /** CompletableFuture also stores callback failures; observe the resulting stage. */
    fun <T> observe(future: CompletableFuture<T>): CompletableFuture<T> {
        future.whenComplete { _, error ->
            if (error != null && unwrap(error) !is CancellationException) fatal(error)
        }
        return future
    }

    private fun unwrap(error: Throwable): Throwable =
        if ((error is CompletionException || error is ExecutionException) && error.cause != null) unwrap(error.cause!!) else error

    private fun startExit() {
        if (exiting.compareAndSet(false, true)) Thread({ exitProcess(1) }, "fatal-exit").start()
    }

    private fun logFailure(error: Throwable) {
        if (loggingFailed.compareAndSet(false, true)) {
            originalErr.println("Could not open or write application.log:")
            error.printStackTrace(originalErr)
            originalErr.flush()
        }
        startExit()
    }

    private class ConsoleOutput(private val console: PrintStream) : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            console.write(bytes, offset, length)
            synchronized(lock) {
                if (!loggingFailed.get()) {
                    try { file?.write(bytes, offset, length) } catch (error: Throwable) { logFailure(error) }
                }
            }
        }
        override fun flush() {
            console.flush()
            synchronized(lock) {
                if (!loggingFailed.get()) {
                    try { file?.flush() } catch (error: Throwable) { logFailure(error) }
                }
            }
        }
        // Closing stdout/stderr must not close the shared file or original console.
        override fun close() = flush()
    }
}
