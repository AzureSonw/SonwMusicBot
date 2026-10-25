# Bilibili upgrade checks

The bundled `libs/lavabili-plugin-1.4.2.jar` is the unmodified official release:

- Upstream: https://github.com/ParrotXray/lavabili-plugin/releases/tag/1.4.2
- SHA-256: `aa8258dac43abd4e722016555005d52382181be2b8bf25dbd7817e5d11fdc7eb`

Version 1.4.2 fixes the upstream Cookie auto-refresh CorrespondPath generation.
The bot defaults to anonymous playback, with optional process-local session
cookies described in [Bilibili login setup](../docs/BILIBILI_LOGIN.md).
Automatic cookie refresh remains disabled; the playlist wrappers are unchanged.

Favorites, seasons and series from the previous custom binary are now handled by
`BilibiliPlaylistLoader` and `BilibiliSourceWithLists`. Video loading and playback
remain delegated to the official plugin. The configured playlist page limit is
preserved. This upgrade does not add Discord commands for Lavalink search/lyrics APIs.

After running `BUILD.bat`, run these commands from the repository root with JDK 25+:

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.5.jar -d target/upgrade-check scripts/BilibiliUpgradeCheck.java
java -cp "target/upgrade-check;target/SonwMusicBot-0.6.5.jar" com.jagrosh.jmusicbot.audio.BilibiliUpgradeCheck
```

The offline checks cover URL routing, ID validation, pagination limits, duplicate
pages, non-video favorites, AV fallback and selecting one part per list entry.
They make no network requests and need no Discord credentials.

Optional real-network smoke test:

```powershell
java --enable-native-access=ALL-UNNAMED -cp "target/upgrade-check;target/SonwMusicBot-0.6.5.jar" com.jagrosh.jmusicbot.audio.BilibiliUpgradeCheck --online
```

This additionally checks a public BV link, track serialization, 100 decoded audio
frames and Bilibili search. It uses the plugin's anonymous configuration; it does
not load browser cookies or connect to Discord. Bilibili availability and region/
rate restrictions can affect this optional test. It does not exercise Discord
voice delivery or fetch real favorite/collection pages.

The session cookie regression tests can be run separately without credentials:

```text
mvn -Dtest=BilibiliSessionTest test
```

These tests run the actual upstream request filter followed by HttpClient's cookie
interceptor, checking session delivery to HTTPS Bilibili and non-delivery to short
links, CDN hosts, lookalike domains and plain HTTP. They also check anonymous mode,
preserving existing cookies and credential-free validation errors.

The legacy suite has five failures and three errors in existing YouTube OAuth/client
and audio-source configuration tests. All eight were reproduced on the unmodified
1.4.1 baseline (232 tests); integration tests are not reached by `mvn verify`.
