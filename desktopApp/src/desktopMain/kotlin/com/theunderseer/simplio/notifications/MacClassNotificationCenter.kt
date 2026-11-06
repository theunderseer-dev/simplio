package com.theunderseer.simplio.notifications

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.resume

internal class MacClassNotificationCenter private constructor() : ClassNotificationCenter {
    private val incoming = Channel<ClassNotificationEvent>(Channel.UNLIMITED)
    override val events = incoming.receiveAsFlow()

    override suspend fun authorization(): NotificationAuthorization =
        NotificationAuthorization.valueOf(awaitNative(::nativeAuthorization))

    override suspend fun requestAuthorization(): NotificationAuthorization =
        NotificationAuthorization.valueOf(awaitCallback(::nativeRequestAuthorization))

    override suspend fun pending(): Map<String, String> = awaitNative(::nativePending).mapNotNull {
        val split = it.lastIndexOf('|')
        if (split < 0) null else it.substring(0, split) to it.substring(split + 1)
    }.toMap()

    override suspend fun delivered(): Set<String> = awaitNative(::nativeDelivered).toSet()

    override suspend fun remove(pending: Set<String>, delivered: Set<String>) {
        if (pending.isNotEmpty() || delivered.isNotEmpty()) {
            nativeRemove(pending.toTypedArray(), delivered.toTypedArray())
        }
    }

    override suspend fun add(notification: ClassNotification, immediately: Boolean) {
        val error = awaitNative<String> { callback ->
            nativeAdd(
                notification.id,
                notification.revision,
                notification.title,
                notification.body,
                notification.endsAt.epochSeconds,
                immediately,
                callback,
            )
        }
        check(error.isEmpty()) { error }
    }

    private suspend fun <T> awaitNative(start: (Consumer<T>) -> Unit): T = withTimeout(10_000) { awaitCallback(start) }

    private suspend fun <T> awaitCallback(start: (Consumer<T>) -> Unit): T =
        suspendCancellableCoroutine { continuation ->
            start(Consumer { continuation.resume(it) })
        }

    private external fun nativeInitialize(events: BiConsumer<String, String>, diagnostics: Boolean): Boolean
    private external fun nativeAuthorization(callback: Consumer<String>)
    private external fun nativeRequestAuthorization(callback: Consumer<String>)
    private external fun nativePending(callback: Consumer<Array<String>>)
    private external fun nativeDelivered(callback: Consumer<Array<String>>)
    private external fun nativeRemove(pending: Array<String>, delivered: Array<String>)
    private external fun nativeAdd(
        id: String,
        revision: String,
        title: String,
        body: String,
        endsAtEpochSeconds: Long,
        immediately: Boolean,
        callback: Consumer<String>,
    )

    companion object {
        /** Called before Compose starts the application event loop so a cold-start click is queued. */
        fun open(diagnostics: Boolean): MacClassNotificationCenter? {
            if (!System.getProperty("os.name").startsWith("Mac")) return null
            val resources = System.getProperty("compose.application.resources.dir")
            if (resources == null) {
                if (diagnostics) System.err.println("[reminders] missing packaged Compose resources directory")
                return null
            }
            val library = Path.of(resources).resolve("libSimplioNotifications.dylib").toAbsolutePath()
            if (!Files.isRegularFile(library)) {
                if (diagnostics) System.err.println("[reminders] native library not found in packaged resources")
                return null
            }
            return try {
                System.load(library.toString())
                val center = MacClassNotificationCenter()
                val initialized = center.nativeInitialize(
                    BiConsumer { kind, id ->
                        val event = when (kind) {
                            "refresh" -> ClassNotificationEvent.Refresh
                            "open" -> ClassNotificationEvent.Observed(id, open = true)
                            "observed" -> ClassNotificationEvent.Observed(id, open = false)
                            else -> return@BiConsumer
                        }
                        center.incoming.trySend(event)
                    },
                    diagnostics,
                )
                center.takeIf { initialized }
            } catch (error: LinkageError) {
                System.err.println("Simplio notifications could not be loaded: ${error.message}")
                null
            }
        }
    }
}
