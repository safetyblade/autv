package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class AustralianChannelOrderTest {
    private fun native(provider: String, name: String, number: Int)=Channel(number,name,"Australian TV",provider,"","",true,null,
        "provider:$provider:$name",null,ProviderChannel(ProviderChannelId(provider,name),name,name))
    @Test fun networksAndCompanionsOrderWithoutChangingNumbersOrFastIdentity() {
        val fast=Channel(100,"Seven","News","","","",true,"https://public.example/live","fast-seven",null)
        val seven=native("seven","Seven",-10)
        val rows=listOf(native("ten","10",-1),native("9now","Nine",-8),native("seven","7mate",-9),seven,
            native("sbs","SBS",-3),native("abc","ABC TV",-5))
        val ordered=AustralianChannelOrder.order(rows)
        assertEquals(listOf("ABC TV","SBS","Seven","7mate","Nine","10"),ordered.map { it.name })
        assertEquals(rows.associate { it.tvgId to it.number },ordered.associate { it.tvgId to it.number })
        val slots=AustralianChannelOrder.withinNativeSlots(rows.take(2)+fast+rows.drop(2))
        assertSame(fast,slots[2]);assertEquals("fast-seven",slots[2].tvgId)
        assertNotEquals(FavouriteIdentity.key(fast),FavouriteIdentity.key(seven))
    }
}
