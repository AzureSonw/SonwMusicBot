# SeVileMusicBot

Private Discord music bot based on `arif-banai/MusicBot v0.6.2`.

Project page: https://github.com/AzureSonw/SeVileMusicBot

## Features

- Apple Music playback through LavaSrc mirror search
- Bilibili playback through lavabili-plugin
- Bilibili BV, multi-part video, favlist, season/list/collection support
- YouTube OAuth playback client fix for Apple Music mirror tracks
- Common music and DJ command replies translated to Chinese

## Build

Install JDK 25 and Maven, then run:

```bat
BUILD.bat
```

The script installs the bundled local lavabili plugin jar into Maven first, then builds MusicBot.

Output:

```text
target\JMusicBot-0.6.2-All.jar
```

## Run

```bat
java -jar target\JMusicBot-0.6.2-All.jar
```

## Config Notes

The default config enables:

- `playback.audioSources.applemusic`
- `playback.audioSources.bilibili`

Useful options:

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

If Apple Music playlists load but tracks fail during playback, delete `youtubetoken.txt` and reauthorize YouTube OAuth.
