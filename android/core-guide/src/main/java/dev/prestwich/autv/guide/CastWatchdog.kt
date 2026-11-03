package dev.prestwich.autv.guide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Repeated BUFFERING/status events cannot keep extending the recovery deadline. */
class CastWatchdog(private val scope: CoroutineScope, private val expired: (Long) -> Unit) {
    private var job: Job? = null
    private var ticket: Long? = null
    fun arm(generation: Long) {
        if (ticket == generation && job?.isActive == true) return
        cancel()
        ticket = generation
        job = scope.launch { delay(30_000); expired(generation) }
    }
    fun cancel() { job?.cancel(); job = null; ticket = null }
}
