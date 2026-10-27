# Bilibili upgrade checks

The bundled `libs/lavabili-plugin-1.4.1.jar` is the unmodified official release:

- Upstream: https://github.com/ParrotXray/lavabili-plugin/releases/tag/1.4.1
- SHA-256: `c2ea7ce41ef84c6133c98a49ca4c801e3fa29abd706a899c8283a832ae2647a8`

Favorites, seasons and series from the previous custom binary are now handled by
`BilibiliPlaylistLoader` and `BilibiliSourceWithLists`. In 0.6.6, BV/AV metadata uses
`BilibiliVideoLoader` and the WBI-signed `/x/web-interface/wbi/view/detail` endpoint.
WBI signing and upstream metadata remain provided by the unchanged official plugin.
In 0.6.7, `SonwBilibiliAudioTrack` keeps its track metadata and serialization format,
but uses a cookieless media client, correct range requests and bounded startup
failover to signed URLs returned for the same AAC representation. Playlist page
limits are preserved. No Discord lyrics commands were added.

After running `BUILD.bat`, run these commands from the repository root with JDK 25+:

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.7.jar -d target/upgrade-check scripts/BilibiliUpgradeCheck.java scripts/Bilibili412Check.java scripts/Bilibili567Check.java
java -cp "target/upgrade-check;target/SonwMusicBot-0.6.7.jar" com.jagrosh.jmusicbot.audio.BilibiliUpgradeCheck
java -cp "target/upgrade-check;target/SonwMusicBot-0.6.7.jar" com.jagrosh.jmusicbot.audio.Bilibili412Check
java -cp "target/upgrade-check;target/SonwMusicBot-0.6.7.jar" com.jagrosh.jmusicbot.audio.Bilibili567Check
```

The offline checks cover URL routing, ID validation, pagination limits, duplicate
pages, non-video favorites, AV fallback and selecting one part per list entry.
They make no network requests and need no Discord credentials.

The additional 412 checks cover BV/AV identity, multipart CID/duration, invalid page
parameters, error mapping, redirect host restrictions, optional authentication,
config-update preservation and credential-redacted validation errors.

The 567 fixtures cover primary/backup ordering, AAC selection, URL deduplication
and host validation, bounded attempts, initial/seek Range headers, 200/206 handling,
range/length validation, failed-response cleanup, no credentials in media requests,
cancellation, timeouts, access/rate-limit/redirect stopping, redacted errors and
no cross-CDN replay once playback has begun.

Optional real-network smoke test:

```powershell
java --enable-native-access=ALL-UNNAMED -cp "target/upgrade-check;target/SonwMusicBot-0.6.7.jar" com.jagrosh.jmusicbot.audio.BilibiliUpgradeCheck --online
```

This defaults to the reported `BV1ZrJi6UEwN` video and checks old/new track serialization,
cloning, list selection, 100 initial audio frames, 100 frames after seeking halfway,
full-track decoding (90-second wall-clock limit) and Bilibili search wrapping.
Use short videos for the full-track test. It uses the plugin's anonymous configuration; it does
not load browser cookies or connect to Discord. Bilibili availability and region/
rate restrictions can affect this optional test. It does not exercise Discord
voice delivery or fetch real favorite/collection pages.

To check another link, append its URL and expected BV identifier after `--online`.
For example: `--online https://b23.tv/BV1ZrJi6UEwN BV1ZrJi6UEwN`.
The HTTP 567 regression link is `--online https://www.bilibili.com/video/BV1yj411f7f8/?p=1 BV1yj411f7f8`.

Primary upstream report for the transport regression:
https://github.com/yt-dlp/yt-dlp/issues/17847. No arbitrary host replacement from
that discussion is used; only URLs returned by Bilibili's own playback API are tried.

These checks intentionally run against the release JAR independently of the
repository's legacy test suite, which still has unrelated API compilation errors.
