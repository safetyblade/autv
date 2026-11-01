# Startup channel

Settings → Playback → Startup channel provides Last watched (default), Favourite #1,
Australian TV, Preferred news channel and Preferred sports channel. The last three
allow an explicit channel or Automatic. The settings use the existing playback
SharedPreferences and canonical FavouriteIdentity, with a separate key for each
mode's preferred channel. Changing channels never changes these settings.

Last watched is updated only after local playback starts or Cast reports PLAYING.
Existing numeric history is migrated against the first available catalogue, including
an old cached guide before a refresh renumbers channels. Without a matching catalogue
row, numeric history remains a fallback until a successful watch saves a stable identity. Existing favourites membership is read without migration or modification;
Favourite #1 follows the guide's ascending channel-number order.

Startup tries the configured candidate, Favourite #1, LeadStory Breaking News
(`autv.110-leadstory-breaking-news`, currently channel 100), then remaining tunable
channels in guide order, deduplicated by identity. Native channels use existing
provider discovery and resolution. A URL alone never establishes playback success.
Each attempt has an eight-second deadline. Automatic startup stops after at most
60 seconds of attempts and leaves the guide open, with no media or buffering.
Provider discovery has a separate bounded wait of up to eight seconds. The guide
remains accessible during attempts; manual tuning or opening the guide cancels
startup. No channel IDs, favourites, provider implementations, EPG or Cast protocols
are changed.

The full debug APK is built with `:app:assembleFullAppDebug -PautvMediaLab=false`.
This is a test-signed full AUTV app, not a permanently signed distribution update.
