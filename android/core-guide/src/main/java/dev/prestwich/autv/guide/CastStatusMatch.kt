package dev.prestwich.autv.guide

/** Pure status correlation; an SDK load acknowledgement alone never takes playback ownership. */
object CastStatusMatch {
    fun matches(expectedUrl: String?,expectedId: String?,contentId: String?,contentUrl: String?,returnedId: String?,
        acknowledged: Boolean,sessionId: Long?,previousSessionId: Long?): Boolean {
        if(expectedUrl==null) return false
        if(!returnedId.isNullOrBlank()) return expectedId!=null && returnedId==expectedId
        val sameMedia=contentId==expectedUrl || contentUrl==expectedUrl
        // Receivers may omit customData. Require both the ticketed LOAD response and
        // a fresh media session, so an old PLAYING status for the same URL is not adopted.
        return acknowledged && sameMedia && sessionId!=null && sessionId>0 && sessionId!=previousSessionId
    }
}
