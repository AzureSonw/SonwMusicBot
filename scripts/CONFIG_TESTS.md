# YouTube OAuth configuration checks

0.6.9 changes the current `reference.conf` default to
`playback.youtube.useOAuth = true`. Existing explicit values are preserved,
including `false` in current configurations and legacy `useyoutubeoauth=false`.
Missing values inherit the new default. The legacy reference fixture is retained
unchanged; the boolean in the ConfigOption enum denotes whether a key is required,
not its default value.

After `BUILD.bat`, run with JDK 25+:

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.9.1.jar -d target/config-check scripts/YoutubeOAuthConfigCheck.java
java -cp "target/config-check;target/SonwMusicBot-0.6.9.1.jar" com.jagrosh.jmusicbot.config.YoutubeOAuthConfigCheck
```

The checks exercise the packaged template, runtime defaults, current and legacy
configuration merging, normal and fallback rendering for all update types, and
writing/reloading a new temporary config. They do not start the bot, contact
YouTube/Discord/Bilibili, read user configuration or request authorization.
The temporary fixture contains only dummy values and is removed after the check.
These independent release-JAR checks do not run the legacy full test suite.

## Authorization message localization

The owner DM uses SonwMusicBot branding and the requested Chinese warning text
with literal `#` markers. The authorization URL and device code still come from
the current upstream prompt; no live code is hardcoded. This only changes the
bot's outgoing message, not Google's authorization page or token storage.

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.9.1.jar -d target/config-check scripts/YoutubeAuthorizationMessageCheck.java
java -cp "target/config-check;target/SonwMusicBot-0.6.9.1.jar" com.jagrosh.jmusicbot.YoutubeAuthorizationMessageCheck
```

This check feeds two dummy device codes into the existing prompt capture path,
then verifies exact text, branding and replacement of the previous code. It does
not handle refresh tokens, send Discord messages or contact Google.

## Four-part release version

```powershell
javac -encoding UTF-8 -cp target/SonwMusicBot-0.6.9.1.jar -d target/config-check scripts/ReleaseVersionCheck.java
java -cp "target/config-check;target/SonwMusicBot-0.6.9.1.jar" com.jagrosh.jmusicbot.verification.ReleaseVersionCheck 0.6.9.1
```

This offline check verifies the packaged runtime version and update comparison
for 0.6.9, 0.6.9.1, later four-part versions and 0.6.10 without querying GitHub.
