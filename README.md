# SonwMusicBot

[English](README.md) | [简体中文](README.zh-CN.md)

A customized Discord music bot extended from [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot).

This version restores music playback functionality, adds Apple Music and Bilibili support, and translates most important bot messages into Simplified Chinese.

---

## ✨ Features

- Restores music playback functionality
- Supports Apple Music through LavaSrc mirror search
- Supports Bilibili through `lavabili-plugin`
- Supports Bilibili BV links, multi-part videos, favorites, seasons, lists, and collections
- Includes a YouTube OAuth playback-client fix for Apple Music mirror tracks
- Translates most important music, playback, queue, and DJ command messages into Simplified Chinese

---

## 📦 Project Information

- **Original project:** [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot)
- **Base version:** JMusicBot `0.4.3`
- **Project name:** SonwMusicBot
- **Maintainer:** AzureSonw
- **Primary language:** Java
- **Supported operating system:** Windows
- **Current release:** `0.6.3`

---

## 🎵 Supported Sources

### Apple Music

Apple Music links are resolved through LavaSrc mirror search.

### Bilibili

Supported Bilibili content includes BV links, multi-part videos, favorite lists, seasons, lists, and collections.

### Other Sources

Other sources inherited from the original bot may remain available depending on the configured playback clients and plugins.

---

## 🛠️ Requirements

- Java installed on the computer
- A Discord bot token
- Windows file association configured to open `.jar` files with Java
- JDK 25 and Maven are only required when building from source

---

## 🚀 Running the Bot

1. Download `SonwMusicBot-0.6.3.jar` from Releases.
2. Put the JAR in its own folder.
3. Double-click the JAR to start SonwMusicBot.
4. On first launch, the bot creates its configuration file automatically.
5. Open the generated configuration file, enter your Discord bot token, and edit the other settings as needed.
6. Save the file and double-click the JAR again.

Command-line startup remains available:

```bat
java -jar SonwMusicBot-0.6.3.jar
```

If double-clicking does nothing, verify that Java is installed and that `.jar` files are associated with Java.

---

## ⚙️ Configuration

The generated configuration enables Apple Music and Bilibili audio sources. Relevant options include:

```hocon
playback {
  appleMusic {
    countryCode = "US"
    mediaAPIToken = ""
    maxPlaylistPages = 6
    maxAlbumPages = 6
  }

  bilibili {
    maxPlaylistPages = 6
  }
}
```

Full configuration documentation: **N/A**

---

## 🔨 Building From Source

Install JDK 25 and Maven, then run:

```bat
BUILD.bat
```

The script installs the bundled local `lavabili-plugin` JAR before building the bot.

Build output:

```text
target\SonwMusicBot-0.6.3.jar
```

---

## 🔧 Troubleshooting

### Apple Music links load, but tracks do not play

Delete `youtubetoken.txt`, then authorize YouTube OAuth again.

### The JAR does not open when double-clicked

Install Java and ensure `.jar` files are associated with Java, or start it from Command Prompt with `java -jar`.

### Other playback problems

Known issues and additional fixes: **N/A**

---

## 🌐 Chinese Localization

Most important user-facing music, playback, queue, and DJ command messages have been translated into Simplified Chinese. Some less frequently used messages may remain in English.

---

## 📥 Releases

Prebuilt downloads and release notes: **N/A**

---

## 📝 Credits

SonwMusicBot is a modified derivative of [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot). Additional playback support is provided by related projects such as LavaSrc and `lavabili-plugin`; their respective authors retain ownership of their work.

---

## 📄 License

SonwMusicBot is distributed under the **Apache License 2.0**, following the license used by the original MusicBot project.

See [`LICENSE`](LICENSE) for the full license text and [`NOTICE`](NOTICE) for attribution and modification information. Copyright and attribution notices from the upstream project and third-party dependencies must be retained when redistributing this project.

---

## 🔎 Search Keywords

Discord music bot, SonwMusicBot, JMusicBot, Apple Music Discord bot, Bilibili Discord bot, Chinese Discord music bot, LavaSrc, lavabili-plugin, Discord audio playback fix, JMusicBot Chinese localization.

---

## ⚠️ Disclaimer

This is an unofficial customized project and is not affiliated with Discord, Apple Music, Bilibili, or the original MusicBot maintainers.

Playback availability may change when third-party platforms, APIs, authentication systems, or plugins are updated. Use the bot in accordance with the terms of service and copyright rules of the relevant platforms.
