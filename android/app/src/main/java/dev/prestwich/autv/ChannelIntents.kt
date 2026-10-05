package dev.prestwich.autv

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.prestwich.autv.guide.TuneTarget

object ChannelIntents {
    const val EXTRA_NUMBER = "dev.prestwich.autv.CHANNEL_NUMBER"
    const val EXTRA_ID = "dev.prestwich.autv.CHANNEL_ID"

    fun target(intent: Intent): TuneTarget? {
        if (intent.hasExtra(EXTRA_ID)) return intent.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() }?.let { TuneTarget.Id(it) }
        if (intent.hasExtra(EXTRA_NUMBER)) return intent.getIntExtra(EXTRA_NUMBER, -1).takeIf { it >= 0 }?.let { TuneTarget.Number(it) }
        val uri = intent.data ?: return null
        if (uri.scheme != "autv" || uri.host != "channel") return null
        return when {
            uri.pathSegments.size == 1 -> uri.lastPathSegment?.toIntOrNull()?.takeIf { it >= 0 }?.let { TuneTarget.Number(it) }
            uri.pathSegments.size == 2 && uri.pathSegments[0] == "id" -> uri.pathSegments[1].takeIf { it.isNotBlank() }?.let { TuneTarget.Id(it) }
            else -> null
        }
    }
    fun isTuneRequest(intent: Intent) = intent.data != null || intent.hasExtra(EXTRA_NUMBER) || intent.hasExtra(EXTRA_ID)
    fun uri(target: TuneTarget): Uri = Uri.Builder().scheme("autv").authority("channel").apply {
        when (target) {
            is TuneTarget.Number -> appendPath(target.value.toString())
            is TuneTarget.Id -> { appendPath("id"); appendPath(target.value) }
        }
    }.build()
    fun intent(context: Context, target: TuneTarget) = Intent(Intent.ACTION_VIEW, uri(target), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
