package dev.prestwich.autv

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.*
import org.junit.Assert.*
import org.junit.Test

class StartupPlaybackTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < end) {
            if (main(check)) return
            SystemClock.sleep(50)
        }
        fail("Startup did not reach expected playback state")
    }
    private fun row(number: Int, id: String, uri: String) = Channel(number,id,"News","","","",true,uri,id,null)
    private val good = row(100,"autv.110-leadstory-breaking-news","asset:///pip-test.m3u8")
    private val bad = row(101,"broken","asset:///missing-startup.m3u8")
    private fun exercise(rows: List<Channel>, cache: GuideCache? = null, configure: () -> Unit = {}, verify: (PlaybackModel) -> Unit) {
        val prefs = app.getSharedPreferences("playback",0)
        val favs = app.getSharedPreferences("favourites-v1",0)
        val savedPrefs = prefs.all
        val savedFavourites = favs.getStringSet("channels",emptySet())!!.toSet()
        val store = ViewModelStore()
        try {
            prefs.edit().clear().commit(); favs.edit().remove("channels").commit(); configure()
            val model = main {
                PlaybackModel(app,{ Guide(rows.size,rows.size,rows) },{ Epg(emptyMap()) },{ DebugCastConnection(it) }, guideCache=cache)
                    .also { store.put("startup",it); it.player.repeatMode=Player.REPEAT_MODE_ALL }
            }
            verify(model)
        } finally {
            main { store.clear() }
            val editor=prefs.edit().clear()
            savedPrefs.forEach { (key,value) -> when(value) {
                is String -> editor.putString(key,value)
                is Int -> editor.putInt(key,value)
                is Boolean -> editor.putBoolean(key,value)
                is Long -> editor.putLong(key,value)
                is Float -> editor.putFloat(key,value)
            } }
            editor.commit(); favs.edit().putStringSet("channels",savedFavourites).commit()
        }
    }
    @Test fun brokenHistoryFallsThroughFavouriteAndPersistsOnlyWorkingIdentity() = exercise(listOf(good,bad), configure = {
        app.getSharedPreferences("playback",0).edit().putString("last-watched-id",FavouriteIdentity.key(bad)).commit()
        app.getSharedPreferences("favourites-v1",0).edit().putStringSet("channels",setOf(FavouriteIdentity.key(good)!!)).commit()
    }) { model ->
        await { model.selected==good && model.player.isPlaying && !model.guideOpen }
        assertEquals(FavouriteIdentity.key(good),app.getSharedPreferences("playback",0).getString("last-watched-id",null))
        assertEquals(setOf(FavouriteIdentity.key(good)),app.getSharedPreferences("favourites-v1",0).getStringSet("channels",null))
    }
    @Test fun oldNumericHistoryMigratesBeforeCachedNewsNumbersRefresh() {
        val saved = good.copy(number=101)
        val oldCache = object : GuideCache {
            override fun read() = Guide(2,2,listOf(saved,bad.copy(number=100)))
            override fun write(guide: Guide) = Unit
        }
        exercise(listOf(good,bad), oldCache, {
            app.getSharedPreferences("playback",0).edit().putInt("channel",101).commit()
        }) { model ->
            await { model.selected==good && model.player.isPlaying && !model.guideOpen }
            assertEquals(FavouriteIdentity.key(good), app.getSharedPreferences("playback",0).getString("last-watched-id",null))
        }
    }
    @Test fun allBrokenOpensGuideAndClearsBuffering() = exercise(listOf(bad)) { model ->
        await { model.notice?.startsWith("No startup channel") == true }
        main { assertTrue(model.guideOpen); assertFalse(model.buffering); assertNull(model.selected); assertEquals(0,model.player.mediaItemCount) }
    }
    @Test fun manualTuningDoesNotOverwriteConfiguredStartupChoice() = exercise(listOf(good,bad)) { model ->
        main { model.startupPreferences.choose(StartupMode.NEWS); model.startupPreferences.chooseChannel(FavouriteIdentity.key(bad)) }
        main { model.request(TuneTarget.Number(good.number)) }
        await { model.player.isPlaying }
        main {
            val restored=StartupPreferences(app.getSharedPreferences("playback",0))
            assertEquals(StartupMode.NEWS,restored.mode)
            assertEquals(FavouriteIdentity.key(bad),restored.identity)
        }
    }
}
