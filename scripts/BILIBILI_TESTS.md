# Bilibili upgrade checks

The bundled `libs/lavabili-plugin-1.4.2.jar` is the unmodified official release:

- Upstream: https://github.com/ParrotXray/lavabili-plugin/releases/tag/1.4.2
- SHA-256: `aa8258dac43abd4e722016555005d52382181be2b8bf25dbd7817e5d11fdc7eb`

Version 1.4.2 fixes the upstream Cookie auto-refresh CorrespondPath generation.
The bot continues to use anonymous playback; this update does not configure
Bilibili login credentials or change the playlist wrappers.

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

These checks intentionally run against the release JAR independently of the
repository's legacy test suite. On JDK 25, `mvn verify` runs 232 unit tests but
reports five failures and three errors in the existing YouTube OAuth/client and
audio-source configuration tests. The same eight failures were reproduced on
the unmodified 1.4.1 baseline; integration tests are not reached.
