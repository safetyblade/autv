package dev.prestwich.autv

import dev.prestwich.autv.cast.CastConnection
import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*

/** Offline application integration only. These adapters never claim broadcaster connectivity. */
class AustralianIntegrationTestActivity : MainActivity() {
    val nine=FixtureProviderAdapter()
    val ten=IntegrationProvider("ten",TenAdapter.ACTIVATION)
    val abc=IntegrationProvider("abc",AbcAdapter.ACTIVATION,anonymous=true)
    override fun createPlaybackModel()=PlaybackModel(application,{
        Guide(1,1,listOf(Channel(100,"Integration FAST","News","","","",true,"asset:///recovery/playlist.m3u8","integration-fast",null)))
    },{ Epg(emptyMap()) },{ _ -> object : CastConnection {
        override val connected=false
        override fun start()=Unit
        override fun load(channel: Channel,reusePlayingMedia: Boolean)=Unit
        override fun release()=Unit
    } },providerAdapter=nine,providerOverrides=mapOf("ten" to ten,"abc" to abc),anonymousProviderIds=setOf("abc"))
        .also { it.player.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ALL }
}
class IntegrationProvider(val id: String,private val activationUrl: String,private val anonymous: Boolean=false): ProviderAdapter {
    override var connection=ProviderConnection.NOT_CONNECTED
    var approved=false
    var denied=false
    var blocked=false
    var reverse=false
    val entered=java.util.concurrent.CountDownLatch(1)
    val release=java.util.concurrent.CountDownLatch(1)
    override var pendingActivation: DeviceActivation?=null
    override fun beginSignIn(): DeviceActivation {
        connection=ProviderConnection.CONNECTING
        return (pendingActivation ?: DeviceActivation("TV123","integration-$id",activationUrl,System.currentTimeMillis()+120000,1000)).also { pendingActivation=it }
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if(approved) { connection=ProviderConnection.CONNECTED;pendingActivation=null };return approved
    }
    override fun channels(): List<ProviderChannel> {
        check(anonymous || connection==ProviderConnection.CONNECTED)
        val now=System.currentTimeMillis()
        val rows=listOf("main","extra").map { key -> ProviderChannel(ProviderChannelId(id,"integration-$key"),key,"$id $key fixture",
            programmes=listOf(ProviderProgramme("$id current fixture",now-60000,now+600000))) }
        return if(reverse) rows.reversed() else rows
    }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if(blocked) { entered.countDown();release.await(10,java.util.concurrent.TimeUnit.SECONDS) }
        if(denied) throw ProviderException(ProviderFailure.REGION_DENIED)
        return ProviderPlaybackRequest(channel,"asset:///recovery/playlist.m3u8","application/x-mpegURL")
    }
    override fun signOut() { connection=ProviderConnection.NOT_CONNECTED;pendingActivation=null }
}
