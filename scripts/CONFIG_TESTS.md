# YouTube OAuth configuration checks

0.6.9 changes the current `reference.conf` default to
`playback.youtube.useOAuth = true`. Existing explicit values are preserved,
including `false` in current configurations and legacy `useyoutubeoauth=false`.
Missing values inherit the new default. The legacy reference fixture is retained
unchanged; the boolean in the ConfigOption enum denotes whether a key is required,
not its default value.

After `BUILD.bat`, run with JDK 25+:

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.9.jar -d target/config-check scripts/YoutubeOAuthConfigCheck.java
java -cp "target/config-check;target/SonwMusicBot-0.6.9.jar" com.jagrosh.jmusicbot.config.YoutubeOAuthConfigCheck
```

The checks exercise the packaged template, runtime defaults, current and legacy
configuration merging, normal and fallback rendering for all update types, and
writing/reloading a new temporary config. They do not start the bot, contact
YouTube/Discord/Bilibili, read user configuration or request authorization.
The temporary fixture contains only dummy values and is removed after the check.
These independent release-JAR checks do not run the legacy full test suite.
