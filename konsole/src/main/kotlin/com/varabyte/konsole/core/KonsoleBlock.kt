package com.varabyte.konsole.core

import com.varabyte.konsole.ansi.Ansi
import com.varabyte.konsole.core.internal.MutableKonsoleTextArea
import kotlinx.coroutines.*
import net.jcip.annotations.GuardedBy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object ActiveBlockKey : KonsoleData.Key<KonsoleBlock> {
    override val lifecycle = KonsoleBlock.Lifecycle
}

class KonsoleBlock internal constructor(
    internal val app: KonsoleApp,
    private val block: KonsoleScope.() -> Unit) {
    object Lifecycle : KonsoleData.Lifecycle

    class RunScope(
        val data: KonsoleData,
        private val rerenderRequested: () -> Unit
    ) {
        private val waitLatch = CountDownLatch(1)
        fun rerender() = rerenderRequested()
        fun waitForSignal() {
            waitLatch.await()
        }

        fun signal() {
            waitLatch.countDown()
        }
    }

    private val textArea = MutableKonsoleTextArea()

    private val renderLock = ReentrantLock()
    @GuardedBy("renderLock")
    private var renderRequested = false

    internal fun applyCommand(command: KonsoleCommand) {
        command.applyTo(textArea)
    }

    internal fun requestRerender() {
        renderLock.withLock {
            // If we get multiple render requests in a short period of time, we only need to handle one of them - the
            // remaining requests are redundant and will be covered by the initial one.
            if (!renderRequested) {
                renderRequested = true
                renderOnceAsync()
            }
        }
    }

    private fun renderOnceAsync(): Job {
        val self = this
        return CoroutineScope(app.executor.asCoroutineDispatcher()).launch {
            renderLock.withLock { renderRequested = false }

            val clearBlockCommand = buildString {
                if (!textArea.isEmpty()) {
                    // To clear an existing block of 'n' lines, completely delete all but one of them, and then delete the
                    // last one down to the beginning (in other words, don't consume the \n of the previous line)
                    for (i in 0 until textArea.numLines) {
                        append('\r')
                        append(Ansi.Csi.Codes.Erase.CURSOR_TO_LINE_END.toFullEscapeCode())
                        if (i < textArea.numLines - 1) {
                            append(Ansi.Csi.Codes.Cursor.MOVE_TO_PREV_LINE.toFullEscapeCode())
                        }
                    }
                }
            }

            textArea.clear()
            KonsoleScope(self).block()
            // Send the whole set of instructions through "write" at once so the clear and updates are processed
            // in one pass.
            app.terminal.write(clearBlockCommand + textArea.toString())
        }
    }
    private fun renderOnce() = runBlocking {
        renderOnceAsync().join()
    }

    fun run(block: (suspend RunScope.() -> Unit)? = null) {
        // Note: The data we're adding here will be removed by the dispose call below
        if (!app.data.tryPut(ActiveBlockKey) { this }) {
            throw IllegalStateException("Cannot run this Konsole block while another block is already running")
        }

        renderOnce()
        if (block != null) {
            val job = CoroutineScope(Dispatchers.Default).launch {
                val scope = RunScope(app.data, rerenderRequested = { requestRerender() })
                scope.block()
            }

            runBlocking { job.join() }
        }

        if (!textArea.isEmpty() && !textArea.endsWithNewline()) {
            app.terminal.write("\n")
        }

        app.data.dispose(Lifecycle)
    }
}

fun KonsoleBlock.runUntilSignal(block: suspend KonsoleBlock.RunScope.() -> Unit) {
    run {
        block()
        waitForSignal()
    }
}

// TODO: fun KonsoleBlock.runUntilTextEntered(block: suspend RunUntilScope.() -> Unit) { ... }