package dev.prestwich.autv.tifprobe

import android.content.ContentValues
import android.content.Context
import android.content.ContentUris
import android.content.ComponentName
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import dev.prestwich.autv.data.GuideRepository
import dev.prestwich.autv.guide.ChannelTuning
import dev.prestwich.autv.guide.TuneTarget

object ProbeCatalogue {
    fun inputId(context: Context): String? {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return null
        val component = ComponentName(context, ProbeInputService::class.java)
        return manager.tvInputList.firstOrNull { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) == component }?.id
    }
    fun publish(context: Context): List<Uri> {
        val input = inputId(context) ?: error("System has not registered the probe input")
        val samples = ChannelTuning.featured(GuideRepository().load().channels, limit = 3)
        require(samples.isNotEmpty()) { "No catalogue streams currently available" }
        val resolver = context.contentResolver
        val old = mutableMapOf<String, Long>()
        resolver.query(TvContract.buildChannelsUriForInput(input), arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID), null, null, null)?.use {
            while (it.moveToNext()) old[it.getString(1)] = it.getLong(0)
        }
        val result = samples.map { channel ->
            val key = channel.number.toString()
            val values = ContentValues().apply {
                put(TvContract.Channels.COLUMN_INPUT_ID, input)
                put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER)
                put(TvContract.Channels.COLUMN_SERVICE_TYPE, TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
                put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, key)
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, "AU TV · ${channel.name}")
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, key)
            }
            old.remove(key)?.let { id -> TvContract.buildChannelUri(id).also { resolver.update(it, values, null, null) } }
                ?: resolver.insert(TvContract.Channels.CONTENT_URI, values) ?: error("TV provider rejected channel")
        }
        old.values.forEach { resolver.delete(TvContract.buildChannelUri(it), null, null) }
        return result
    }
    fun target(context: Context, uri: Uri): TuneTarget? {
        if (!TvContract.isChannelUriForTunerInput(uri)) return null
        val input = inputId(context) ?: return null
        return context.contentResolver.query(uri, arrayOf(TvContract.Channels.COLUMN_INPUT_ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID), null, null, null)?.use {
            if (it.moveToFirst() && it.getString(0) == input) it.getString(1)?.toIntOrNull()?.let(TuneTarget::Number) else null
        }
    }
}
