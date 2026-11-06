# AUTV — TV guide and channel directory

**Catalogue snapshot:** 6 November 2026 · **FAST/IPTV master guide:** 639 active channels · **Source:** [channel_guide.csv](channel_guide.csv)

This guide covers the numbered FAST/IPTV lineup controlled by the repository. **Australian TV broadcaster channels are managed separately by the Android app** and must not be counted as part of this CSV unless separately reconciled. The list is a catalogue of configured channels, **not a guarantee of live playback** at this moment.

## Getting started

1. Install the **latest owner-approved AUTV Android TV / Google TV APK** (obtain the verified build from the project release/build distribution; do not use the historical v0.4.0 instructions).
2. Open AUTV and allow the guide and playlist to load. Use the TV remote direction keys to navigate, **OK** to select and **Back** to return to the previous screen.
3. Browse the guide by category and channel number. Use the guide to tune without leaving the application.
4. In the guide, **long-press OK** on a supported channel to add/remove it from **Favourites**. Set a preferred starting channel in **Settings** where the current build offers this option.
5. Open **Australian TV** for broadcaster channels. Link supported accounts in Settings; follow the TV activation instructions/QR code shown on the actual device where offered.
6. In a build containing the refresh control, open **Settings → Refresh channels and TV guide** to fetch current numbered channels and programme data without restarting. Older APKs require an update.

### Australian TV — connection guide

Australian TV is distinct from the FAST/IPTV directory below. **7plus, 9Now and SBS** have previously been reported working on the owner's build; **ABC iview and Network 10** remain subject to integration/authorisation validation. These are dated project reports, **not a live status check**. Available channels and provider status must be confirmed against the latest installed APK. Do not assume signing in guarantees an authorised playable stream.

If a provider says **Configuration required**, it is not connected; a local restart or repeated sign-in may not solve missing provider provisioning. Use Settings to inspect the precise status. Never enter passwords or tokens into a guide document.

### Problems and refresh

- **No live stream:** refresh the guide once and retry. Some listed services can be temporarily unavailable; a visible channel is not proof its stream is playable.
- **No programme information:** the channel may have no reliable electronic programme guide (EPG). See the *Guide* column below.
- **Provider sign-in/configuration:** re-open the app's provider Settings and follow the current on-screen flow; report persistent authorisation/configuration states separately.
- **Guide appears outdated:** open **Settings → Refresh channels and TV guide** in the updated app. Verify the channel number and name before reporting a mismatch.

## Catalogue summary

| Category | Configured active channels |
| --- | ---: |
| News | 24 |
| Sport | 96 |
| Movies | 36 |
| Game Shows | 20 |
| Crime | 45 |
| Comedy | 44 |
| Entertainment | 149 |
| Reality & Lifestyle | 91 |
| Factual | 55 |
| Kids & Animation | 50 |
| Music | 29 |
| **FAST/IPTV total** | **639** |

The current list is generated from the authoritative CSV. The published M3U may contain fewer channels due to source availability or refresh output. Australian TV native-provider channels are **not included** in this count.

## Full numbered FAST/IPTV directory

| Number | Channel | Category | Provider/source | Guide | Description |
| ---: | --- | --- | --- | --- | --- |
| 100 | 110 LeadStory Breaking News | News | Curated | Full | Rolling news, current affairs, weather or business coverage. |
| 101 | 105 News24 Now | News | Curated | Full | Rolling news, current affairs, weather or business coverage. |
| 102 | 112 Breaking Finance News | News | Curated | Full | Rolling news, current affairs, weather or business coverage. |
| 103 | Sky News | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 104 | ABC News Live | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 105 | BBC News | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 106 | CNNi | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 107 | NBC News NOW | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 108 | CBS News 24/7 | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 109 | Scripps News | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 110 | USA TODAY | News | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 111 | The Reuters 60 | News | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 112 | CNN HEADLINES | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 113 | Global News National | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 114 | CBC News | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 115 | Euronews | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 116 | GB News | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 117 | NewsWorld | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 118 | Newsmax2 | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 119 | Bloomberg TV | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 120 | Bloomberg Originals | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 121 | Yahoo Finance | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 122 | The Weather Network | News | Pluto | Full | Rolling news, current affairs, weather or business coverage. |
| 123 | WeatherSpy | News | Plex AU | Full | Rolling news, current affairs, weather or business coverage. |
| 200 | Watch AEW | Sport | Tubi | Channel only | 24/7 AEW FAST channel. Tubi remains primary; Roku carries the same feed as a tested fallback. |
| 201 | CW Presents WWE NXT | Sport | Curated | Full | WWE NXT FAST channel carrying current-era NXT archive programming. |
| 202 | TNA Wrestling Channel | Sport | Roku | Channel only | TNA Wrestling FAST channel using Roku as primary test source; Pluto US retained as alternate. |
| 204 | UFC | Sport | Plex AU | Full | Combat-sports channel featuring UFC programming and fight content. |
| 205 | PFL MMA | Sport | Plex AU | Full | Combat-sports FAST channel featuring fights, events, highlights or archive programming. |
| 206 | ONE Championship | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 207 | GLORY Kickboxing | Sport | Plex AU | Full | Combat-sports FAST channel featuring fights, events, highlights or archive programming. |
| 208 | Bellator MMA | Sport | Pluto | Full | Combat-sports FAST channel featuring fights, events, highlights or archive programming. |
| 209 | 345 MMA TV | Sport | Curated | Full | Combat-sports FAST channel featuring fights, events, highlights or archive programming. |
| 210 | DAZN Ringside | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 211 | HBO Boxing | Sport | Xumo | Full | Combat-sports FAST channel featuring fights, events, highlights or archive programming. |
| 212 | Top Rank Classics | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 213 | XFC TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 214 | Fight Network | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 215 | Lucha Plus | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 216 | MUTV | Sport | Curated | Channel only | Manchester United club television. Brightcove/Fastly HLS tested playable in AUTV on an Australian device (6 Nov 2026); distribution authorisation and long-term source continuity not established. |
| 217 | FIFA+ | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 218 | UEFA Champions League | Sport | Pluto | Full | Football FAST channel featuring UEFA competitions, highlights or archive programming. |
| 219 | UEFA.tv | Sport | Tubi | Full | Football FAST channel featuring UEFA competitions, highlights or archive programming. |
| 220 | Real Madrid TV | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 221 | TOP BARÇA | Sport | LG AU | Channel only | Football FAST channel focused on club, league or competition programming. |
| 222 | Man City 24/7 | Sport | LG GB | Channel only | Football FAST channel focused on club, league or competition programming. |
| 223 | INTER 24/7 | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 224 | FIFA+ Women | Sport | Samsung GB | Channel only | Football FAST channel featuring league, club, tournament or archive programming. |
| 225 | GFN Football | Sport | Samsung GB | Channel only | Football FAST channel featuring league, club, tournament or archive programming. |
| 226 | ViX Lo Mejor de Liga MX | Sport | Roku | Channel only | Football FAST channel focused on club, league or competition programming. |
| 227 | beIN SPORTS XTRA | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 228 | The NBA Channel | Sport | Xumo | Full | Dedicated NBA FAST channel with highlights, archive games, original programming and basketball coverage. |
| 229 | Hoop TV | Sport | SoFast | Channel only | Dedicated basketball FAST channel with games, highlights, news, interviews and basketball culture programming. |
| 230 | NFL Channel | Sport | Plex AU | Full | American football FAST channel with NFL programming, highlights or archive content. |
| 231 | NFL Super Bowl Champions | Sport | Plex AU | Full | American football FAST channel with NFL programming, highlights or archive content. |
| 232 | MLB | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 233 | NHL | Sport | Tubi | Full | Ice-hockey FAST channel featuring NHL programming and related coverage. |
| 234 | Hard Knocks | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 235 | NASCAR | Sport | Tubi | Full | NASCAR motorsport FAST channel with race, archive and related programming. |
| 236 | Monster Jam | Sport | Plex AU | Full | Monster Jam motorsport channel featuring monster-truck events and related programming. |
| 237 | FloRacing 24/7 | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 238 | Racing America | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 239 | Rally TV | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 240 | Red Bull TV | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 241 | NHRA TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 242 | RACER International | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 243 | Tennis TV | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 244 | Tennis+ | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 245 | TennisChannel 2 | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 246 | GolfPass | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 247 | PickleTV | Sport | Plex AU | Full | Pickleball-focused sports channel featuring matches, highlights and related programming. |
| 248 | PGA TOUR | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 249 | MODUS Super Series Darts | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 250 | Pluto TV Snooker 900 | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 251 | Billiard TV | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 252 | World Billiards TV | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 253 | Triton Poker | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 254 | Poker Night TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 255 | The World Poker Tour | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 256 | PokerGO | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 257 | World Surf League 24/7 | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 258 | Waypoint TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 259 | Wired2Fish | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 260 | Gone Fishing | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 261 | Field & Stream TV | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 262 | Powder TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 263 | PursuitUP | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 264 | SLOPES | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 265 | Surfer TV | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 266 | WFN | Sport | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 267 | Outdoor Channel (Australia) | Sport | Curated | Full | Outdoor sports channel featuring fishing, hunting and adventure programming. |
| 268 | Strongman Champions | Sport | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 269 | ACL Cornhole TV | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 270 | Bowling TV | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 271 | Team USA TV | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 272 | Women's Sports Network | Sport | Tubi | Full | Women's Sports Network FAST channel; direct Tubi HLS is primary after the Plex tokenised feed failed. |
| 273 | SWAC TV | Sport | Wurl / Triple B | Full | SWAC TV direct Wurl/Triple-B HLS replacing failed Plex token; portable static source. |
| 274 | FOX Sports | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 275 | NBC Sports NOW | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 276 | CBS Sports HQ | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 277 | Fubo Sports Network | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 278 | Scripps Sports Network | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 279 | Stadium | Sport | Tubi | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 280 | SportsGrid | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 281 | FTF Sports | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 282 | SI TV | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 283 | Breaking Sports News | Sport | Plex AU | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 284 | Ryz Sports Network | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 285 | Unbeaten Sports | Sport | Roku | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 286 | ACC Digital Network | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 287 | Big 12 Studios | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 288 | FloHockey 24/7 | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 289 | Willow Sports | Sport | Xumo | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 290 | CombaTV | Sport | Ottera | Full | CombaTV restored using direct Ottera HLS network 1153 after Plex transport failure. |
| 291 | Cricket Gold | Sport | SOFAST AU | Full | Cricket Gold direct SOFAST feed replacing failed Plex transport. |
| 292 | FUEL TV | Sport | Samsung AU | Full | FUEL TV direct Australian Amagi/Samsung feed replacing failed Plex transport. |
| 293 | Pac-12 Insider | Sport | Samsung US | Full | Pac-12 Insider Samsung TV Plus feed replacing failed Plex transport; channel remains active in 2026. |
| 294 | Trace Stars | Sport | Curated | Full | Sports channel featuring athlete, competition and sports-entertainment programming. |
| 295 | LacrosseTV | Sport | Curated | Channel only | 24/7 lacrosse channel with games, highlights and related programming. |
| 296 | Rugby Pass TV | Sport | Pluto | Full | Sports FAST channel featuring live events, archive coverage, highlights or sports programming. |
| 300 | MGM Presents | Movies | Xumo | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 301 | Paramount Movie Channel | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 302 | Universal Movies | Movies | Xumo | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 303 | MOVIESPHERE | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 304 | Gravitas Movies | Movies | Xumo | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 305 | FilmRise Free Movies | Movies | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 306 | Movie Hub | Movies | Roku | Full | Broad movie FAST channel with a rotating mix of feature films. |
| 307 | MyTime Movie Network | Movies | Curated | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 308 | Action Hollywood Movies | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 309 | MGM Presents: Action | Movies | Xumo | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 310 | Movie Hub Action | Movies | Roku | Full | Action-focused movie FAST channel featuring action and adventure films. |
| 311 | Pluto TV Action Movies | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 312 | Hi-Yah! | Movies | Xumo | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 314 | Pluto TV Horror | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 315 | Pluto TV Thrillers | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 316 | Pluto TV Cult Films | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 317 | FrightFlix | Movies | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 318 | Watch it SCREAM | Movies | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 319 | Hallmark Movies & More | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 320 | Lifetime Movie Favorites | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 321 | Sparkle Movies - Light & Love | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 322 | British Screen Classics | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 323 | Classic Movies Channel | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 324 | Curzon | Movies | Pluto | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 325 | Maverick Black Cinema | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 326 | Pam Grier's Soul Flix | Movies | Roku | Full | Black cinema movie channel centred on soul cinema, cult films and Pam Grier-associated programming. |
| 327 | FilmRise Western | Movies | Roku | Full | Western movie channel featuring classic and modern western films. |
| 328 | Grjngo - Western Movies | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 329 | MGM Presents: Westerns | Movies | Xumo | Full | Western films and classic western series. |
| 330 | Universal Westerns | Movies | Curated | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 331 | Western Bound | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 332 | PopCorn Central | Movies | Roku | Full | Movie and entertainment channel featuring a broad catalogue of films and selected series. |
| 333 | Surf Cinema | Movies | Plex AU | Full | Movie-focused FAST channel with scheduled films or genre collections. |
| 334 | CINEPRIDE | Movies | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 335 | The Asylum (Australia) | Movies | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 336 | Trailers From Hell | Movies | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 400 | Family Feud | Game Shows | Xumo | Full | Game-show and competition programming. |
| 401 | Family Feud Classic | Game Shows | Xumo | Full | Game-show and competition programming. |
| 402 | The Price is Right | Game Shows | Pluto | Full | Game-show and competition programming. |
| 403 | The Price is Right: The Barker Era | Game Shows | Pluto | Full | Game-show and competition programming. |
| 404 | Let's Make a Deal | Game Shows | Roku | Full | Game-show and competition programming. |
| 405 | Deal or No Deal | Game Shows | Plex AU | Full | Deal or No Deal game-show channel featuring episodes and competition programming. |
| 406 | Supermarket Sweep | Game Shows | Roku | Full | Game-show and competition programming. |
| 407 | Pointless (Australia) | Game Shows | Curated | Full | Game-show and competition programming. |
| 408 | 25 Words or Less | Game Shows | Xumo | Full | Game-show and competition programming. |
| 409 | WipeoutXtra | Game Shows | Plex AU | Full | Physical competition and obstacle-course game-show programming. |
| 410 | Robot Wars by Mech+ | Game Shows | Plex AU | Full | Robot-combat competition channel featuring Robot Wars and related mechanical competition programming. |
| 411 | Xumo Free Game Show TV | Game Shows | Xumo | Full | Game-show and competition programming. |
| 412 | America's Next Top Model | Game Shows | Pluto | Full | Competition-format reality programming. |
| 413 | Project Runway (Australia) | Game Shows | Curated | Full | Competition-format reality programming. |
| 414 | Survivor | Game Shows | Pluto | Full | Competition-format reality programming. |
| 415 | The Amazing Race | Game Shows | Pluto | Full | Competition-format reality programming. |
| 416 | The Biggest Loser | Game Shows | Plex AU | Full | Competition-format reality programming. |
| 417 | The Challenge | Game Shows | Pluto | Full | Competition-format reality programming. |
| 418 | The Masked Singer | Game Shows | Roku | Full | Competition-format reality programming. |
| 419 | Ink Master | Game Shows | Pluto | Full | Competition-format reality programming. |
| 500 | American Crime Channel | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 501 | Bloodline Detectives | Crime | Wurl | Full | Crime, investigation, court, mystery or forensic programming. |
| 502 | Cold Case Files by A&E | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 503 | Crime 360 | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 504 | Crime Scene TV | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 505 | Crimes That Shook Britain | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 506 | Criminal Confessions | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 507 | Dateline 24/7 | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 508 | Deadly Women | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 509 | Evidence of Evil | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 511 | Forensic Files | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 512 | Medical Detectives | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 513 | Oxygen True Crime Archives | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 514 | Real Crime | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 515 | Real Crime Uncovered | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 516 | The First 48 by A&E | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 517 | The New Detectives | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 518 | True Crime Now | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 519 | Unsolved Mysteries (Australia) | Crime | Curated | Full | Crime, investigation, court, mystery or forensic programming. |
| 520 | Women Behind Bars | Crime | Plex AU | Full | True-crime and prison-focused programming centred on incarcerated women. |
| 521 | 48 Hours | Crime | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 522 | Homicide Hunter | Crime | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 523 | TruBlu Crime Channel | Crime | Roku | Full | True-crime channel featuring criminal investigations, cases and law-enforcement programming. |
| 524 | A&E Alaska State Troopers | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 525 | A&E Live PD: Greatest Shifts | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 526 | COPS | Crime | Plex AU | Full | Reality policing channel following law-enforcement activity and arrests. |
| 527 | COPS Reloaded | Crime | Plex AU | Full | COPS archive channel featuring selected policing and arrest footage. |
| 528 | JAIL | Crime | Plex AU | Full | Crime, investigation, court, mystery or forensic programming. |
| 529 | Live PD: Presents | Crime | Roku | Full | Crime, investigation, court, mystery or forensic programming. |
| 530 | World's Wildest Police Videos | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 531 | Dog The Bounty Hunter | Crime | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 532 | Sheriffs: El Dorado County | Crime | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 533 | Court TV | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 534 | Court TV Legendary Trials | Crime | Roku | Full | Crime, investigation, court, mystery or forensic programming. |
| 535 | Law&Crime | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 536 | Paternity Court | Crime | Roku | Full | Court-reality channel focused on paternity disputes and relationship cases. |
| 537 | The Judge Judy Channel | Crime | Pluto | Full | Court-reality channel featuring Judge Judy episodes and legal disputes. |
| 538 | Judge Nosey | Crime | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 539 | Dr. G: Medical Examiner | Crime | Roku | Full | Crime, investigation, court, mystery or forensic programming. |
| 540 | Cagney & Lacey | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 541 | Diagnosis Murder | Crime | Pluto | Full | Crime-drama series channel centred on the Diagnosis Murder franchise. |
| 542 | FilmRise Mysteries | Crime | Roku | Full | Crime, investigation, court, mystery or forensic programming. |
| 543 | Hawaii Five-O | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 544 | JAG | Crime | Pluto | Full | Crime, investigation, court, mystery or forensic programming. |
| 545 | Universal Crime | Crime | Xumo | Full | Crime, investigation, court, mystery or forensic programming. |
| 600 | Cheers | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 601 | Frasier | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 602 | King of Queens | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 603 | Married... with Children | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 604 | South Park | Comedy | Pluto | Full | Animated comedy channel dedicated to South Park episodes and specials. |
| 605 | The Conners | Comedy | Pluto | Full | Modern sitcom channel featuring The Conners. |
| 606 | Girlfriends | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 607 | Family Ties | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 608 | Saved by the Bell | Comedy | Xumo | Full | Comedy, sitcom or stand-up programming. |
| 609 | Portlandia | Comedy | Xumo | Full | Comedy, sitcom or stand-up programming. |
| 610 | SNL Vault | Comedy | Roku | Full | Saturday Night Live sketch-comedy archive channel featuring classic and selected SNL material. |
| 611 | The Carol Burnett Show | Comedy | Plex AU | Full | Comedy, sitcom or stand-up programming. |
| 612 | Whose Line Is It Anyway? | Comedy | Pluto | Full | Improvisational comedy and panel-show channel featuring Whose Line Is It Anyway? |
| 613 | 8 Out of 10 Cats (Australia) | Comedy | Curated | Full | Comedy, sitcom or stand-up programming. |
| 614 | Tosh.0 | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 616 | Comedy Central Pluto TV | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 617 | Comedy Dynamics | Comedy | Xumo | Full | Comedy, sitcom or stand-up programming. |
| 618 | Dry Bar Comedy (Australia) | Comedy | Curated | Full | Comedy, sitcom or stand-up programming. |
| 619 | LOL Network | Comedy | Roku | Full | Comedy, sitcom or stand-up programming. |
| 620 | NBC Comedy Vault | Comedy | Xumo | Full | Comedy, sitcom or stand-up programming. |
| 621 | Bounce XL | Comedy | Xumo | Full | Comedy, sitcom or stand-up programming. |
| 622 | Just For Laughs Gags | Comedy | Plex AU | Full | Comedy, sitcom or stand-up programming. |
| 623 | Always Funny Videos | Comedy | Roku | Full | Comedy, sitcom or stand-up programming. |
| 624 | MST3K | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 625 | RiffTrax | Comedy | Pluto | Full | Comedy, sitcom or stand-up programming. |
| 626 | Mr Bean Animated (Australia) | Comedy | Curated | Full | Comedy, sitcom or stand-up programming. |
| 627 | Stokes Twins | Comedy | Roku | Full | Comedy, sitcom or stand-up programming. |
| 628 | Taxi | Comedy | Pluto | Full | Classic sitcom channel featuring Taxi episodes. |
| 629 | Three's Company | Comedy | Pluto | Full | Classic sitcom channel featuring Three's Company. |
| 630 | The Addams Family | Comedy | Pluto | Full | Classic sitcom channel featuring The Addams Family. |
| 631 | The Andy Griffith Show | Comedy | Pluto | Full | Classic sitcom channel featuring The Andy Griffith Show. |
| 632 | The Beverly Hillbillies | Comedy | Pluto | Full | Classic sitcom channel featuring The Beverly Hillbillies. |
| 633 | Green Acres | Comedy | Xumo | Full | Classic television series and archive entertainment. |
| 634 | Leave It to Beaver | Comedy | Xumo | Full | Classic television series and archive entertainment. |
| 635 | ALF | Comedy | Roku | Full | Comedy, sitcom or stand-up programming. |
| 636 | Anger Management (Australia) | Comedy | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 637 | Corner Gas | Comedy | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 638 | Dick Van Dyke | Comedy | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 639 | Happy Days | Comedy | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 640 | Hogan's Heroes | Comedy | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 641 | I Love Lucy | Comedy | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 642 | Mr. Bean | Comedy | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 643 | Mr. Bean Live Action | Comedy | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 644 | The Kids in the Hall | Comedy | Plex AU | Full | Adult sketch-comedy channel featuring The Kids in the Hall. |
| 700 | BBC Drama | Entertainment | Xumo | Full | Scripted drama, action or series programming. |
| 701 | Blue Bloods | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 702 | Criminal Minds | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 703 | CSI | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 704 | CSI: Miami | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 705 | CSI: NY | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 706 | Law & Order | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 707 | NCIS | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 708 | NCIS: Los Angeles | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 709 | Narcos | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 710 | Midsomer Murders | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 711 | BritBox Mysteries | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 712 | Doc Martin | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 713 | MacGyver | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 714 | Nash Bridges | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 715 | Rookie Blue | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 716 | Sea Patrol | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 717 | Stories by AMC | Entertainment | Xumo | Full | Scripted drama, action or series programming. |
| 718 | Universal Action | Entertainment | Xumo | Full | Scripted drama, action or series programming. |
| 719 | ION | Entertainment | Xumo | Full | Scripted drama, action or series programming. |
| 720 | ION Plus | Entertainment | Xumo | Full | Scripted drama, action or series programming. |
| 721 | ION Mystery | Entertainment | Xumo | Full | Mystery and crime-drama network featuring procedural and investigative series. |
| 722 | Beverly Hills 90210 | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 723 | Baywatch | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 724 | Degrassi | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 725 | Jane the Virgin | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 726 | Dynasty | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 727 | Northern Exposure | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 728 | Nashville | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 729 | The Bold and the Beautiful | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 730 | Weeds / Nurse Jackie | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 731 | Heartland | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 732 | McLeod's Daughters | Entertainment | Xumo | Full | Australian drama series channel featuring McLeod's Daughters. |
| 733 | 21 Jump Street | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 734 | Hunter | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 735 | Matlock | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 736 | Relic Hunter | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 737 | Lone Star | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 738 | Asian Drama | Entertainment | Plex AU | Full | Scripted drama, action or series programming. |
| 739 | Star Trek | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 740 | Star Trek: The Next Generation | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 741 | Star Trek: Deep Space Nine | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 742 | Star Trek: Voyager | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 743 | Star Trek: Enterprise | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 744 | Stargate | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 745 | Battlestar Galactica | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 746 | Doctor Who Classic | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 747 | Farscape | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 748 | Andromeda | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 749 | Charmed | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 750 | The X-Files | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 751 | The Twilight Zone | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 752 | The Outer Limits | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 753 | The Walking Dead Universe | Entertainment | Pluto | Full | Scripted drama, action or series programming. |
| 754 | Z Nation | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 755 | Dark Matter TV | Entertainment | Xumo | Full | Science-fiction, horror, supernatural or cult programming. |
| 756 | Earth: Final Conflict | Entertainment | Pluto | Full | Science-fiction series channel featuring Earth: Final Conflict. |
| 757 | Ghost Whisperer | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 758 | Hercules: The Legendary Journeys | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 759 | Xena Warrior Princess | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 760 | The Incredible Hulk | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 761 | The Bionic Woman | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 762 | The Six-Million Dollar Man | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 763 | Mutant X | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 764 | Pluto TV Alien Invasion | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 765 | Pluto TV Space | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 766 | AMC Thrillers | Entertainment | Xumo | Full | Thriller and suspense entertainment channel from AMC. |
| 767 | Alfred Hitchcock Presents | Entertainment | Xumo | Full | Classic suspense anthology channel featuring Alfred Hitchcock Presents. |
| 768 | Camp Spoopy | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 769 | Scare Tactics | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 770 | Perry Mason | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 771 | Mission Impossible | Entertainment | Pluto | Full | Classic action/spy series channel featuring Mission: Impossible. |
| 772 | Ironside | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 773 | In the Heat of the Night | Entertainment | Xumo | Full | Classic television series and archive entertainment. |
| 774 | Rawhide | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 775 | The Rifleman | Entertainment | Xumo | Full | Classic western television series channel featuring The Rifleman. |
| 776 | The Lone Ranger | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 777 | Little House on the Prairie | Entertainment | Xumo | Full | Classic drama series channel featuring Little House on the Prairie. |
| 778 | Lassie | Entertainment | Curated | Full | Scripted drama, action or series programming. |
| 779 | Highway to Heaven | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 780 | Touched By An Angel | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 781 | Death Valley Days | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 782 | FilmRise Classic TV | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 783 | Flashback 70s | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 784 | Electric Now | Entertainment | Roku | Full | Broad entertainment channel featuring scripted series, action, genre and library programming. |
| 785 | FilmRise British TV | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 786 | FilmRise Black TV | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 787 | AfroLandTV | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 788 | Grit Xtra | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 789 | Dove | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 790 | Revry | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 791 | Brat TV | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 792 | Candyjar | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 793 | Glewed TV | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 794 | JoySauce Network | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 795 | Bravo Vault | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 796 | OUTflix Proud | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 797 | POP | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 798 | RCM | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 799 | Stars Central | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 800 | Alternative | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 801 | Galxy TV | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 802 | More U | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 803 | Legacy | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 804 | INFAST (Australia) | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 805 | FilmRise Creators | Entertainment | Plex AU | Full | Creator-led entertainment channel featuring digital personalities, web series and online video programming. |
| 806 | Complex TV | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 807 | Creator Television | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 808 | Hot Ones | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 809 | Mythical 24/7 | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 810 | YoungHollywood | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 811 | Made In Hollywood | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 812 | Johnny Carson TV | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 813 | The Ed Sullivan Show | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 814 | The Graham Norton Show | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 815 | 5-Minute Crafts | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 816 | FailArmy | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 817 | People are Awesome | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 818 | MTV Ridiculousness | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 819 | Penn & Teller: Fool Us | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 820 | America's Got Talent | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 821 | American Ninja Warrior | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 822 | Fear Factor | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 823 | Reelz Famous and Infamous | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 824 | Growing Up Hip Hop WeTV | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 825 | AWE Plus | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 826 | Bassmaster | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 827 | Bizaar | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 828 | Bon Voyage | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 829 | BOOT | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 830 | ChopperTown (Australia) | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 831 | Fifth Gear (UK) | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 832 | Globetrotter | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 833 | GrowthDay Network | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 834 | Horse & Country | Entertainment | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 835 | Inside Outside | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 836 | Journy | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 837 | Life Down Under | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 838 | Powernation | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 839 | SKWAD | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 840 | SPEEDVISION | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 841 | The Prof G Channel | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 842 | Unbeaten | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 843 | Xtreme Caution | Entertainment | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 844 | Life with Derek | Entertainment | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 845 | Walker Texas Ranger | Entertainment | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 846 | Top Gear | Entertainment | Pluto | Full | Automotive entertainment series featuring cars, challenges and motoring features. |
| 847 | Comet TV | Entertainment | Curated | Channel only | English-language sci-fi, fantasy and cult entertainment channel. NWA Powerrr airs at scheduled times. Owner-approved AUTV trial; Australian playback, rights and guide availability require verification. |
| 848 | CHARGE! | Entertainment | Curated | Channel only | English-language action, crime and adventure entertainment channel. Owner-approved AUTV trial; Australian playback, rights and guide availability require verification. |
| 1000 | Dr. Phil | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1001 | Nosey | Reality & Lifestyle | Plex AU | Full | Daytime talk and conflict-driven reality programming, including relationship and personal-dispute formats. |
| 1002 | Undercover Boss | Reality & Lifestyle | Plex AU | Full | Workplace reality series following senior executives working anonymously inside their own organisations. |
| 1003 | Warner Bros. TV Say Yes to the Dress | Reality & Lifestyle | Pluto | Full | Wedding and bridal reality programming centred on dress selection and customer stories. |
| 1004 | Don't Tell The Bride (Australia) | Reality & Lifestyle | Curated | Full | Wedding reality series in which one partner plans the wedding without the other's involvement. |
| 1005 | Four in a Bed | Reality & Lifestyle | Plex AU | Full | Hospitality reality competition following B&B owners reviewing each other's properties. |
| 1006 | American Pickers | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1007 | Ax Men | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1008 | Bar Rescue | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1009 | Bring It! | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1010 | Cheaters | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1011 | Duck Dynasty | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1012 | Hardcore Pawn | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1013 | Highway Thru Hell | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1014 | Ice Pilots NWT | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1015 | Ice Road Truckers | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1016 | Intervention | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1017 | Jade Fever | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1018 | Operation Repo | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1019 | Storage Wars by A&E | Reality & Lifestyle | Pluto | Full | Auction and resale reality series centred on storage-unit bidding. |
| 1020 | The Hotel Inspector | Reality & Lifestyle | Plex AU | Full | Hospitality reality series focused on improving struggling hotels and accommodation businesses. |
| 1021 | Come Dine With Me | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1022 | CraftsyTV | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1023 | SPARK TV | Reality & Lifestyle | Roku | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1024 | The Wicked Tuna Channel | Reality & Lifestyle | Plex AU | Full | Workplace reality series following commercial tuna fishing crews and their competition at sea. |
| 1025 | America's Test Kitchen | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1026 | Anthony Bourdain: Parts Unknown | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1027 | BBC Food | Reality & Lifestyle | Xumo | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1028 | Best of Bobby Flay by Food Network | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1029 | Cooking Panda | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1030 | Gordon Ramsay | Reality & Lifestyle | Tubi | Full | Gordon Ramsay-focused FAST channel featuring food, competition and reality programming. |
| 1031 | Hell's Kitchen | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1032 | Hell's Kitchen \| Kitchen Nightmares | Reality & Lifestyle | Roku | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1033 | Home Cooking by Food Network | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1034 | Iron Chef | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1035 | Kitchen Battles | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1036 | Love Wine | Reality & Lifestyle | Curated | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1037 | NEW KFOOD | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1038 | Great British Menu | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1039 | GUSTOTV | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1040 | The Jamie Oliver Channel | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1041 | The Emeril Lagasse Channel | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1042 | The Martha Stewart Channel | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1043 | Top Chef | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1044 | BBC Home & Garden | Reality & Lifestyle | Xumo | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1045 | GARDEN with Monty Don | Reality & Lifestyle | Roku | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1046 | Homeful | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1047 | Homes Under the Hammer | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1048 | Million Dollar Dream Home | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1049 | Obsessive Compulsive Cleaners | Reality & Lifestyle | LG AU | Full | British factual-reality series channel focused on extreme cleaning and household transformations. |
| 1050 | Property Brothers | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1051 | Smart Healthy Green Living | Reality & Lifestyle | Curated | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1052 | Tastemade Home | Reality & Lifestyle | Roku | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1053 | The Bob Ross Channel | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1054 | The Design Network | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1055 | This Old House | Reality & Lifestyle | Pluto | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1056 | Tiny House Nation | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1057 | Escape to the Country (Australia) | Reality & Lifestyle | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 1058 | Million Dollar Listing Vault | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1059 | Antiques Road Trip | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1060 | Autentic Travel | Reality & Lifestyle | Curated | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1061 | Nomad Travel | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1062 | PBS Antiques Roadshow | Reality & Lifestyle | Roku | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1063 | Travel Escapes | Reality & Lifestyle | Plex AU | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1064 | Travelxp | Reality & Lifestyle | Curated | Full | Lifestyle, food, home, garden, travel or renovation programming. |
| 1065 | FashionTV | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1066 | Life+Style | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1067 | The Spa | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1068 | Bondi Vet | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1069 | Cesar's Pack Leader TV | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1070 | Dog Whisperer | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1071 | Pet Club TV (Australia) | Reality & Lifestyle | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 1072 | The Pet Collective | Reality & Lifestyle | Plex AU | Full | Animal and pet-focused entertainment and lifestyle programming. |
| 1073 | Love After Lockup We TV | Reality & Lifestyle | Xumo | Full | Reality and unscripted entertainment programming. |
| 1074 | MTV Reality | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1075 | Real Housewives Vault | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1076 | The Real Housewives | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1077 | World of Love Island | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1078 | Bad Girls Club | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1079 | Big Brother | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1080 | Black Ink Crew | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1081 | Braxton Family Values | Reality & Lifestyle | Roku | Full | General entertainment FAST channel with mixed or broad programming. |
| 1082 | Catfish | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1083 | Dance Moms | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1084 | Geordie Shore | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1085 | Little Women: LA | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1086 | Love & Hip Hop | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1087 | MTV Dating | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1088 | MTV Jersey Shore | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1089 | Teen Mom | Reality & Lifestyle | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1090 | The Osbournes | Reality & Lifestyle | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1100 | BBC Earth | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1101 | Smithsonian Channel Selects | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1102 | Documentary+ | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1103 | CNN Originals | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1104 | 60 Minutes | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1105 | Docsville | Factual | Curated | Full | General entertainment FAST channel with mixed or broad programming. |
| 1106 | INWONDER (Australia) | Factual | Curated | Full | Documentary, factual, history, science or real-world programming. |
| 1107 | Wonder | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1108 | Adventure Earth | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1109 | Love Nature | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1110 | PBS Nature | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1111 | WildEarth | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1112 | earthtouch | Factual | Curated | Full | Documentary, factual, history, science or real-world programming. |
| 1113 | Real Wild | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1114 | MagellanTV Wildest | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1115 | INWILD (Australia) | Factual | Curated | Full | Documentary, factual, history, science or real-world programming. |
| 1116 | Go Wild | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1117 | Evolution Earth | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1118 | Dinos 24/7 | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1119 | Nature Moments | Factual | Roku | Full | Documentary, factual, history, science or real-world programming. |
| 1120 | Naturetime (Australia) | Factual | Curated | Full | Documentary, factual, history, science or real-world programming. |
| 1121 | River Monsters | Factual | Plex AU | Full | Wildlife and adventure factual series focused on freshwater predators and extreme fishing expeditions. |
| 1122 | Eddie's Wonderland | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1123 | PBS Science | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1124 | Pluto TV Science | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1125 | Popular Science | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1126 | Mythbusters | Factual | Plex AU | Full | Science and engineering entertainment testing myths through experiments. |
| 1127 | Modern Marvels | Factual | Plex AU | Full | Technology, engineering and industrial-history factual programming. |
| 1128 | Startalk TV | Factual | Plex AU | Full | Science and space discussion programming centred on astronomy and astrophysics. |
| 1129 | I Shouldn't Be Alive | Factual | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1130 | I Survived… | Factual | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1131 | Mayday: Air Disaster | Factual | Plex AU | Full | Aviation-disaster documentary series examining major air accidents and investigations. |
| 1132 | Real Emergency | Factual | Plex AU | Full | Real-world emergency and rescue factual programming. |
| 1133 | Untold Stories of the ER | Factual | Plex AU | Full | Medical factual series based on unusual emergency-room cases. |
| 1134 | Ancient Aliens | Factual | Pluto | Full | Unexplained-history and speculative archaeology factual programming. |
| 1135 | Beyond Belief | Factual | Plex AU | Full | Unexplained and paranormal factual entertainment. |
| 1136 | Beyond Paranormal | Factual | Plex AU | Full | Paranormal factual channel featuring unexplained phenomena, investigations and supernatural subjects. |
| 1137 | Paranormal Chronicles | Factual | Roku | Full | Documentary, factual, history, science or real-world programming. |
| 1138 | Ghost Hunters Channel | Factual | Plex AU | Full | Paranormal investigation programming focused on haunted locations. |
| 1139 | Ghost | Factual | Plex AU | Full | Paranormal and supernatural factual programming. |
| 1140 | Haunt TV | Factual | Curated | Full | Paranormal investigation and supernatural factual programming. |
| 1141 | Haunt TV (Australia) | Factual | Curated | Full | Paranormal investigation and supernatural factual programming. |
| 1142 | Most Haunted | Factual | Plex AU | Full | Paranormal investigation channel featuring haunted locations and supernatural investigations. |
| 1143 | Sam and Colby | Factual | Roku | Full | Paranormal exploration and creator-led investigation programming focused on haunted and unexplained locations. |
| 1144 | Pluto TV Conspiracy | Factual | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1145 | History Hit | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1146 | True History | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1147 | PBS History | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1148 | Pluto TV History | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1149 | Autentic History | Factual | Curated | Full | Documentary, factual, history, science or real-world programming. |
| 1150 | History & Warfare | Factual | Plex AU | Full | Documentary, factual, history, science or real-world programming. |
| 1151 | History Hunters | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1152 | World War TV | Factual | Pluto | Full | Military-history channel focused on major conflicts, campaigns and wartime history. |
| 1153 | Pluto TV Military | Factual | Pluto | Full | Documentary, factual, history, science or real-world programming. |
| 1154 | Pluto TV Britain at War | Factual | Pluto | Full | General entertainment FAST channel with mixed or broad programming. |
| 1200 | The Wiggles | Kids & Animation | Plex AU | Full | Preschool and family channel featuring The Wiggles. |
| 1201 | Peppa Pig | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1202 | Baby Shark TV | Kids & Animation | Samsung AU | Full | Kids, family and animation programming. |
| 1203 | Baby Einstein | Kids & Animation | Plex AU | Full | Kids, family and animation programming. |
| 1204 | Super Simple Songs | Kids & Animation | Roku | Full | Kids, family and animation programming. |
| 1205 | Teletubbies | Kids & Animation | Plex AU | Full | Preschool channel featuring Teletubbies programming. |
| 1206 | Max & Ruby | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1207 | Mister Rogers' Neighborhood | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1208 | Moonbug Kids | Kids & Animation | Roku | Full | Preschool and young-kids channel featuring Moonbug children's programming. |
| 1209 | Cocobi | Kids & Animation | Curated | Full | Kids, family and animation programming. |
| 1210 | Robocar POLI | Kids & Animation | Curated | Full | Kids, family and animation programming. |
| 1211 | Nick Jr Club | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1212 | Nick Jr. Pluto TV | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1213 | Strawberry Shortcake | Kids & Animation | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1214 | SpongeBob SquarePants | Kids & Animation | Pluto | Full | Kids animation channel featuring SpongeBob SquarePants. |
| 1215 | Pokémon | Kids & Animation | Pluto UK | Full | Kids, family and animation programming. |
| 1216 | LEGO Kids TV | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1217 | Garfield and Friends | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1218 | The Smurfs | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1219 | Smurf TV | Kids & Animation | Plex AU | Full | Kids animation channel featuring Smurfs-related programming. |
| 1220 | Sonic The Hedgehog | Kids & Animation | Plex AU | Full | Kids animation channel featuring Sonic the Hedgehog programming. |
| 1221 | Sonic TV | Kids & Animation | Plex AU | Full | Kids animation channel featuring Sonic-branded animated programming. |
| 1223 | He-Man & Masters Of The Universe | Kids & Animation | Curated | Full | Kids, family and animation programming. |
| 1224 | Winx Club TV | Kids & Animation | Curated | Full | Kids, family and animation programming. |
| 1225 | Super Mario Brothers | Kids & Animation | Roku | Full | Kids, family and animation programming. |
| 1226 | TOTALLY TURTLES | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1227 | Arthur | Kids & Animation | Plex AU | Full | Kids, family and animation programming. |
| 1228 | Mona The Vampire | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1229 | The Raccoons | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1230 | Kartoon Channel (Australia) | Kids & Animation | Curated | Full | Kids, family and animation programming. |
| 1231 | Nickelodeon Pluto TV | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1232 | NickToons | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1233 | Pluto TV Retro Toons | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1234 | Animation+ | Kids & Animation | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1235 | Toon Goggles | Kids & Animation | Plex AU | Full | Kids animation channel with family-friendly cartoons and animated series. |
| 1236 | Watch it Kid | Kids & Animation | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1237 | TG Junior | Kids & Animation | Plex AU | Full | General entertainment FAST channel with mixed or broad programming. |
| 1238 | Crunchyroll | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1239 | ANIME x HIDIVE | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1240 | Naruto | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1241 | One Piece | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1242 | Sailor Moon | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1243 | Inuyasha | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1244 | Yu-Gi-Oh! | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1245 | iCarly TV | Kids & Animation | Pluto | Full | Kids, family and animation programming. |
| 1246 | 123GO! | Kids & Animation | Plex AU | Full | Youth creator channel featuring challenge, craft and entertainment videos. |
| 1247 | pocket.watch Game-On | Kids & Animation | Plex AU | Full | Youth gaming and creator-led entertainment channel. |
| 1248 | Preston & Brianna | Kids & Animation | Plex AU | Full | Youth creator channel featuring gaming, challenges and family-friendly entertainment. |
| 1249 | Ryan and Friends | Kids & Animation | Plex AU | Full | Children's creator-led entertainment featuring Ryan and related family programming. |
| 1250 | Unspeakable | Kids & Animation | Plex AU | Full | Youth creator channel featuring gaming, challenges and family-friendly entertainment. |
| 1300 | Stingray Greatest Hits | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1301 | Stingray Hit List | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1302 | Stingray Pop Adult | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1303 | Stingray 2000s | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1304 | Nothin' But 90s | Music | Plex AU | Full | 1990s music channel featuring pop, rock and hits from the decade. |
| 1305 | Remember the ‘80s | Music | Plex AU | Full | 1980s music channel featuring hits and music videos from the decade. |
| 1306 | Y2K | Music | Plex AU | Full | 2000s-era music channel featuring pop and hits from the Y2K period. |
| 1307 | MTV Spankin' New | Music | Pluto | Full | Music videos, performances or genre-based music programming. |
| 1308 | TRACE UK | Music | Plex AU | Full | UK-focused music channel featuring contemporary hits and music videos. |
| 1309 | Stingray Classic Rock | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1310 | Metal.Rocks | Music | Plex AU | Full | Rock and metal music channel featuring heavy music videos and related programming. |
| 1311 | Hip-Hop/R&B | Music | Plex AU | Full | Hip-hop and R&B music channel featuring music videos and genre programming. |
| 1312 | Yo! MTV | Music | Pluto | Full | Music videos, performances or genre-based music programming. |
| 1313 | TRACE Urban | Music | Plex AU | Full | Urban music channel featuring hip-hop, R&B and related genres. |
| 1314 | Stingray Soul Storm | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1315 | Shades of Black | Music | Roku | Full | Music videos, performances or genre-based music programming. |
| 1316 | Stingray DJAZZ | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1317 | Smooth Jazz | Music | Plex AU | Full | Smooth jazz music channel featuring instrumental and contemporary jazz programming. |
| 1318 | Qwest TV | Music | Plex AU | Full | Jazz, soul and world-music performance channel. |
| 1319 | Classica | Music | Pluto | Full | Music videos, performances or genre-based music programming. |
| 1320 | Stingray Hot Country | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1321 | Qello Concerts | Music | Pluto | Full | Music videos, performances or genre-based music programming. |
| 1322 | Stingray Karaoke | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1323 | XITE Gospel | Music | Pluto | Full | Music videos, performances or genre-based music programming. |
| 1324 | Stingray Easy Listening | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1325 | Stingray SPA | Music | Samsung | Channel only | Music videos, performances or genre-based music programming. |
| 1326 | Stingray Naturescape | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1327 | Stingray Holidayscapes | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |
| 1328 | ZenLIFE by Stingray | Music | Plex AU | Full | Music videos, performances or genre-based music programming. |

## Editorial notes

- The channel list is ordered by its final assigned AUTV number; the source-provider's numbering has no authority.
- **Full** means a schedule is expected, not that data is confirmed on every refresh. **Channel only** indicates no reliable schedule is expected.
- Historical channel removal and streaming investigations belong in issues/maintenance records, not the home TV guide.

**Document maintenance:** Regenerate from `channel_guide.csv` after approved lineup changes. The app's separate Australian TV catalogue requires a release-verified export before a complete combined Word/channel guide can be certified.
