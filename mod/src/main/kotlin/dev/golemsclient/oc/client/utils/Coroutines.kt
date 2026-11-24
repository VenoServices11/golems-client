// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending code, which rethrows a [CancellationException] if the calling coroutine has been cancelled.
 *
 * Cancellation exceptions that don't stem from the caller's own cancellation, such as a nested `withTimeout` expiring,
 * are still captured as failures.
 */
suspend inline fun <R> runSuspendCatching(block: () -> R): Result<R> =
    runCatching(block).onFailure { if (it is CancellationException) currentCoroutineContext().ensureActive() }
