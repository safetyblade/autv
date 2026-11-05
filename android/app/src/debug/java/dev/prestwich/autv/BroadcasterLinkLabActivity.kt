package dev.prestwich.autv

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.provider.ActivationDestination
import dev.prestwich.autv.provider.ActivationQr
import dev.prestwich.autv.provider.createAbcProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Debug-only real provider linking. No borrowed keys or app identity.
 * A successful account link is NOT evidence of authorised live media playback.
 */
class BroadcasterLinkLabActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val network = LinkLabNetwork()
    private lateinit var abc: AbcAdapter
    private lateinit var abcStatus: TextView
    private lateinit var abcCode: TextView
    private lateinit var abcQr: ImageView
    private lateinit var tenStatus: TextView
    private lateinit var tenCode: TextView
    private var tenSession: TenChallenge? = null
    private var running = false
    private var active = true
    private val polling = object : Runnable {
        override fun run() {
            if (!active || running) return
            val pending = abc.pendingActivation ?: return
            if (System.currentTimeMillis() >= pending.expiresAt) {
                abcStatus.text = "ABC code expired. Request a fresh code."
                return
            }
            running = true
            work({ abc.pollSignIn(pending) }, { approved ->
                running = false
                if (approved) {
                    abcStatus.text = "ABC account LINKED. Live video still requires verification."
                    abcCode.text = ""
                    abcQr.setImageDrawable(null)
                } else {
                    abcStatus.text = "ABC approval pending. Enter code at abc.net.au/linktv."
                    schedule()
                }
            }, { error ->
                running = false
                abcStatus.text = "ABC poll: ${safe(error)}"
                if (abc.pendingActivation != null) schedule()
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        abc = createAbcProvider(this, LabAbcAccess(network), "debug-link-only",experimental=true)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setBackgroundColor(0xff101722.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)
        fun label(value: String, size: Float = 18f): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xfff2efe9.toInt())
            setPadding(0, dp(8), 0, dp(8))
            root.addView(this)
        }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value
            isAllCaps = false
            minHeight = dp(48)
            isFocusable = true
            setOnClickListener { action() }
            root.addView(this)
        }
        label("AUTV — LIVE BROADCASTER LINK TEST", 24f)
        label("Genuine account challenge requests. No fabricated credentials. Playback NOT verified.", 14f)
        label("ABC iview", 22f)
        abcStatus = label("Request a real four-character ABC linking code.")
        abcCode = label("", 34f)
        abcQr = ImageView(this).apply {
            val side = dp(200)
            root.addView(this, LinearLayout.LayoutParams(side, side))
        }
        label("On your phone: https://www.abc.net.au/linktv", 16f)
        button("Request real ABC code") { beginAbc() }
        button("Check ABC approval") { handler.removeCallbacks(polling); polling.run() }
        label("Network 10", 22f)
        tenStatus = label("Try the real Network 10 configuration and device-code flow.")
        tenCode = label("", 34f)
        label("On your phone, if a code is issued: https://10.com.au/activate", 16f)
        button("Try real Network 10 code") { beginTen() }
        button("Check 10 approval") { pollTen() }
        button("Return to AUTV") { finish() }
        when (abc.connection) {
            ProviderConnection.CONNECTED -> abcStatus.text = "ABC account already linked; video unverified."
            ProviderConnection.CONNECTING -> abc.pendingActivation?.let { showAbc(it); schedule() }
            else -> Unit
        }
    }
    private fun beginAbc() {
        if (running) return
        running = true
        abcStatus.text = "Requesting ABC code from the real broadcaster service…"
        work({ abc.beginSignIn() }, { challenge ->
            running = false
            showAbc(challenge)
            schedule()
        }, { error ->
            running = false
            abcStatus.text = "ABC link request: ${safe(error)}"
        })
    }
    private fun showAbc(challenge: DeviceActivation) {
        abcCode.text = challenge.userCode
        abcStatus.text = "ABC issued this code. Approve on phone; waiting for account link."
        ActivationQr.encode(challenge, ActivationDestination.ABC)?.let { matrix ->
            val pixels = IntArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            }
            abcQr.setImageBitmap(Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888))
        }
    }
    private fun schedule() {
        handler.removeCallbacks(polling)
        if (active && abc.pendingActivation != null) handler.postDelayed(polling, 4000)
    }

    private data class TenChallenge(val code: String, val deviceId: String, val expiry: Long, val validation: String)
    private fun beginTen() {
        tenCode.text = ""
        tenStatus.text = "Requesting Network 10's official activation configuration…"
        work({
            val response = network.call("GET", "https://10.com.au/api/v1/config")
            val source = response.optJSONObject("data") ?: response
            val auth = source.optJSONObject("authConfig") ?: error("No authConfig")
            val generate = auth.optString("generateCode")
            val validate = auth.optString("validateCode")
            require(network.tenHost(generate) && network.tenHost(validate)) { "No official code URLs" }
            val id = UUID.randomUUID().toString()
            val generated = network.call("POST", generate, JSONObject()
                .put("deviceIdentifier", id).put("deviceName", "AUTV"),
                TenPublicRequestContext.headers("POST"))
            val body = generated.optJSONObject("data") ?: generated
            val code = body.optString("code").ifBlank { body.optString("activationCode") }
            require(code.length in 4..12 && code.all(Char::isLetterOrDigit)) { "No code in response" }
            val expiry = body.optLong("expiry").takeIf { it > 0 }
                ?: (System.currentTimeMillis() / 1000 + body.optLong("expiresIn", 600))
            TenChallenge(code, id, expiry, validate)
        }, { result ->
            tenSession = result
            tenCode.text = result.code
            tenStatus.text = "Network 10 issued a real code. Approve at 10.com.au/activate."
        }, { error ->
            tenStatus.text = "Network 10: ${safe(error)}. No code issued."
        })
    }
    private fun pollTen() {
        val pending = tenSession ?: run { tenStatus.text = "Request a real 10 code first."; return }
        work({
            val response = network.call("POST", pending.validation, JSONObject()
                .put("code", pending.code).put("deviceIdentifier", pending.deviceId)
                .put("expiry", pending.expiry), TenPublicRequestContext.headers("POST"))
            val body = response.optJSONObject("data") ?: response
            body.has("alternativeToken") || body.has("accessToken") || body.optJSONObject("jwt") != null
        }, { linked ->
            tenStatus.text = if (linked) "Network 10 returned an account session; video unverified."
                             else "Network 10 approval still pending."
        }, { error -> tenStatus.text = "10 approval: ${safe(error)}" })
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun safe(e: Throwable): String = when (e) {
        is ProviderException -> "${e.stage?.code ?: "LINK"} ${e.failure} HTTP ${e.httpStatus ?: "n/a"}"
        is LabHttpFailure -> "HTTP ${e.status}"
        is java.net.SocketTimeoutException -> "connection timed out"
        is java.io.IOException -> "network unavailable"
        else -> "unrecognised response (${e.javaClass.simpleName})"
    }
    private fun <T> work(operation: () -> T, onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit) {
        worker.execute {
            val answer = runCatching(operation)
            handler.post { if (active) answer.fold(onSuccess, onFailure) }
        }
    }
    override fun onDestroy() {
        active = false
        handler.removeCallbacks(polling)
        worker.shutdownNow()
        super.onDestroy()
    }
}

private class LabHttpFailure(val status: Int) : RuntimeException()

/** Strict HTTPS and first-party hosts only. No request/response body is logged. */
private class LinkLabNetwork {
    fun tenHost(address: String): Boolean = runCatching {
        val u = URI(address)
        val host = u.host ?: return@runCatching false
        u.scheme == "https" && u.port in setOf(-1, 443) && u.userInfo == null &&
            u.rawFragment == null && u.path.startsWith("/api/") &&
            (host == "10.com.au" || host.endsWith(".10.com.au") ||
             host == "tenplay.com.au" || host.endsWith(".tenplay.com.au"))
    }.getOrDefault(false)
    fun call(method: String, address: String, payload: JSONObject? = null,
             headers: Map<String, String> = emptyMap(), pending404: Boolean = false): JSONObject {
        val u = URI(address)
        require(u.scheme == "https" && u.port in setOf(-1, 443) &&
                u.userInfo == null && u.rawFragment == null)
        require(u.host in setOf("mylogin-api.abc.net.au", "api.seesaw.abc.net.au", "api.iview.abc.net.au") ||
                tenHost(address))
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AUTV Native Link Lab")
            headers.forEach { (key, value) ->
                require(key.matches(Regex("[A-Za-z0-9-]{1,64}")) && value.length <= 4096)
                connection.setRequestProperty(key, value)
            }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status == 204 || (pending404 && status == 404)) return JSONObject()
            if (status !in 200..299) throw LabHttpFailure(status)
            val content = connection.inputStream.use { input ->
                val stream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(stream.size() + size <= 128 * 1024) { "Payload too large" }
                    stream.write(buffer, 0, size)
                }
                stream.toString("UTF-8")
            }
            return JSONObject(content)
        } finally { connection.disconnect() }
    }
}

/** Real ABC session flow; media signing explicitly unavailable in this debug lab. */
private class LabAbcAccess(private val net: LinkLabNetwork) : AbcProviderAccess {
    private val metadata = AbcHttpAccess()
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun guarded(op: () -> JSONObject): JSONObject = try { op() }
    catch (e: LabHttpFailure) {
        throw ProviderException(if (e.status == 401) ProviderFailure.SESSION_EXPIRED
                                else ProviderFailure.UNAVAILABLE, e.status)
    }
    override fun link(deviceId: String) = guarded {
        net.call("POST", "https://mylogin-api.abc.net.au/latest/ctv/link", JSONObject()
            .put("productId", "autv").put("deviceId", deviceId).put("deviceName", "AUTV"))
    }
    override fun linkStatus(deviceId: String, requestId: String?) = guarded {
        val args = mutableListOf("productId=autv", "deviceId=${encode(deviceId)}")
        requestId?.let { args.add("requestId=${encode(it)}") }
        net.call("GET", "https://mylogin-api.abc.net.au/latest/ctv/status?${args.joinToString("&")}",
            pending404 = true)
    }
    override fun accountToken(uid: String) = guarded {
        net.call("GET", "https://api.seesaw.abc.net.au/v2/token?UID=${encode(uid)}&source=iview")
    }
    override fun unlink(deviceId: String) {
        guarded {
            net.call("POST", "https://mylogin-api.abc.net.au/latest/ctv/unlink", JSONObject()
                .put("productId", "autv").put("deviceId", deviceId))
        }
    }
    override fun home() = metadata.home()
    override fun collection(id: String) = metadata.collection(id)
    override fun programme(officialUrl: String) = metadata.programme(officialUrl)
    override fun authoriseMedia(houseNumber: String, originalHls: String, accountToken: String?): String =
        throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
}
