package dev.prestwich.autv.data

import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

interface GuideCache {
    fun read(): Guide?
    fun write(guide: Guide)
}

/** Atomic replacement; a failed parse/write never truncates a good snapshot. */
class FileGuideCache(private val file: File) : GuideCache {
    private val repository = GuideRepository()
    override fun read(): Guide? = runCatching {
        require(file.length() in 1..8 * 1024 * 1024)
        repository.validated(file.readText())
    }.getOrNull()

    override fun write(guide: Guide) {
        val channels = JSONArray()
        guide.channels.forEach { channel -> channels.put(JSONObject().apply {
            put("number", channel.number); put("name", channel.name); put("genre", channel.genre)
            put("subgenre", channel.subgenre); put("description", channel.description); put("epg", channel.epg)
            put("streamUrl", channel.streamUrl ?: JSONObject.NULL); put("tvgId", channel.tvgId ?: JSONObject.NULL)
            put("logoUrl", channel.logoUrl ?: JSONObject.NULL)
        }) }
        val body = JSONObject().put("channels", channels).toString()
        repository.validated(body)
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        try {
            FileOutputStream(temporary).use { it.write(body.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temporary.renameTo(file)) { "Could not replace guide cache" }
        } finally { temporary.delete() }
    }
}

enum class GuideStartupState { LOADING, CACHED, READY, ERROR }
data class GuideUpdate(val guide: Guide?, val state: GuideStartupState, val refreshFailed: Boolean = false)

/** Publish cache/JSON before optional playlist enrichment, with bounded UI waits. */
class GuideStartup(
    private val cache: GuideCache?,
    private val remote: () -> Guide,
    private val playlist: ((Guide) -> Guide)? = null,
    private val timeoutMs: Long = GuideRepository.REQUEST_TIMEOUT_MS,
) {
    private suspend fun <T> bounded(timeout: Long = timeoutMs, block: () -> T): T {
        // Await outside the IO worker's lifetime: cancellation of an HttpURLConnection
        // read may take until its socket timeout. The UI deadline must not wait for it.
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val request = CoroutineScope(currentCoroutineContext() + job + Dispatchers.IO).async { block() }
        return try { withTimeout(timeout) { request.await() } } finally { job.cancel() }
    }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
    catch (error: Exception) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        null
    }

    suspend fun refresh(current: Guide? = null, publish: (GuideUpdate) -> Unit) {
        val saved = current ?: optional { bounded(2_000) { cache?.read() } }
        publish(GuideUpdate(saved, if (saved == null) GuideStartupState.LOADING else GuideStartupState.CACHED))
        val fresh = optional { bounded { remote() } }
        if (fresh == null) {
            publish(GuideUpdate(saved, if (saved == null) GuideStartupState.ERROR else GuideStartupState.CACHED, refreshFailed = true))
            return
        }
        publish(GuideUpdate(fresh, GuideStartupState.READY))
        optional { bounded(2_000) { cache?.write(fresh) } }
        val enriched = playlist?.let { optional { bounded { it(fresh) } } }
        if (enriched != null && enriched != fresh) publish(GuideUpdate(enriched, GuideStartupState.READY))
    }
}
