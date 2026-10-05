package dev.prestwich.autv

import android.content.ContentValues
import android.content.Context
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.prestwich.autv.data.Guide
import dev.prestwich.autv.guide.ChannelTuning
import dev.prestwich.autv.guide.TuneTarget

/** Best-effort, package-owned preview content. Provider absence never blocks playback. */
class TvHomePublisher(private val context: Context) {
    fun publish(guide: Guide, recent: Int?) {
        if (Build.VERSION.SDK_INT < 26 || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return
        try {
            val resolver = context.contentResolver
            val prefs = context.getSharedPreferences("tv-home", 0)
            var id = prefs.getLong("channel", -1)
            val existing = if (id >= 0) resolver.query(TvContract.buildChannelUri(id), arrayOf(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID), null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == "autv-live"
            } == true else false
            if (!existing) {
                val channel = ContentValues().apply {
                    put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
                    put(TvContract.Channels.COLUMN_DISPLAY_NAME, "AU TV Live")
                    put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, "autv-live")
                    put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, android.content.Intent(context, MainActivity::class.java).toUri(android.content.Intent.URI_INTENT_SCHEME))
                }
                id = resolver.insert(TvContract.Channels.CONTENT_URI, channel)?.let(ContentUris::parseId) ?: return
                prefs.edit().putLong("channel", id).apply()
                val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                ContextCompat.getDrawable(context, R.drawable.app_icon)?.let { drawable ->
                    drawable.setBounds(0, 0, 256, 256); drawable.draw(Canvas(bitmap))
                    resolver.openOutputStream(TvContract.buildChannelLogoUri(id))?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                bitmap.recycle()
                TvContract.requestChannelBrowsable(context, id)
            }
            val old = mutableMapOf<String, Long>()
            resolver.query(TvContract.buildPreviewProgramsUriForChannel(id), arrayOf(TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID), null, null, null)?.use {
                while (it.moveToNext()) old[it.getString(1)] = it.getLong(0)
            }
            ChannelTuning.featured(guide.channels, recent).forEachIndexed { index, channel ->
                val key = channel.number.toString()
                val values = ContentValues().apply {
                    put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, id)
                    put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, key)
                    put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_CHANNEL)
                    put(TvContract.PreviewPrograms.COLUMN_TITLE, "${channel.number}  ${channel.name}")
                    put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, "Live · ${channel.genre}")
                    put(TvContract.PreviewPrograms.COLUMN_LIVE, 1)
                    put(TvContract.PreviewPrograms.COLUMN_WEIGHT, 100 - index)
                    put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, ChannelIntents.intent(context, TuneTarget.Number(channel.number)).toUri(android.content.Intent.URI_INTENT_SCHEME))
                    put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, channel.logoUrl ?: "android.resource://${context.packageName}/drawable/tv_banner")
                    put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, if (channel.logoUrl == null) TvContract.PreviewPrograms.ASPECT_RATIO_16_9 else TvContract.PreviewPrograms.ASPECT_RATIO_1_1)
                }
                val program = old.remove(key)
                if (program == null) resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
                else resolver.update(TvContract.buildPreviewProgramUri(program), values, null, null)
            }
            old.values.forEach { resolver.delete(TvContract.buildPreviewProgramUri(it), null, null) }
            prefs.edit().putString("status", "Published AU TV Live ($id)").apply()
        } catch (error: Exception) {
            context.getSharedPreferences("tv-home", 0).edit().putString("status", "Unavailable: ${error.javaClass.simpleName}: ${error.message}").apply()
            Log.w("AUTV Home", "TV home provider unavailable; playback remains independent", error)
        }
    }
}
