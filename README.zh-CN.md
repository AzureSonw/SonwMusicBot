# SonwMusicBot

[English](README.md) | [简体中文](README.zh-CN.md)

一个从 [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot) 延伸开发的自定义 Discord 音乐机器人。

此版本修复了音乐无法正常播放的问题，添加了 Apple Music 和 Bilibili 支持，并将大多数重要的机器人消息汉化为简体中文。

---

## ✨ 功能

- 修复音乐无法正常播放的问题
- 通过 LavaSrc 镜像搜索支持 Apple Music
- 通过 `lavabili-plugin` 支持 Bilibili
- 支持 Bilibili BV 链接、分 P 视频、收藏夹、番剧、列表和合集
- 为 Apple Music 镜像曲目加入 YouTube OAuth 播放客户端修复
- 汉化大多数重要的音乐播放、队列和 DJ 指令消息

---

## 📦 项目信息

- **原始项目：** [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot)
- **基础版本：** JMusicBot `0.4.3`
- **项目名称：** SonwMusicBot
- **维护者：** AzureSonw
- **主要语言：** Java
- **支持的操作系统：** Windows
- **当前版本：** `0.6.3`

---

## 🎵 支持的来源

### Apple Music

Apple Music 链接会通过 LavaSrc 镜像搜索进行解析。

### Bilibili

支持 BV 链接、分 P 视频、收藏夹、番剧、列表和合集。

### 其他来源

从原版机器人继承的其他来源可能仍然可用，具体取决于当前启用的播放客户端和插件。

---

## 🛠️ 环境要求

- 电脑已安装 Java
- Discord Bot Token
- Windows 已设置使用 Java 打开 `.jar` 文件
- 只有从源码构建时才需要 JDK 25 和 Maven

---

## 🚀 运行机器人

1. 从 Releases 下载 `SonwMusicBot-0.6.3.jar`。
2. 将 JAR 单独放进一个文件夹。
3. 直接双击 JAR 启动 SonwMusicBot。
4. 第一次启动时，机器人会自动生成配置文件。
5. 打开生成的配置文件，填写 Discord Bot Token，并按需要修改其他设置。
6. 保存配置文件，然后再次双击 JAR 启动。

也可以通过命令行启动：

```bat
java -jar SonwMusicBot-0.6.3.jar
```

如果双击没有反应，请确认 Java 已安装，并确认 Windows 已将 `.jar` 文件关联到 Java。

---

## ⚙️ 配置

自动生成的配置会启用 Apple Music 和 Bilibili 音频来源。相关选项包括：

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

完整配置文档：**N/A**

---

## 🔨 从源码构建

安装 JDK 25 和 Maven，然后运行：

```bat
BUILD.bat
```

构建脚本会先安装项目附带的本地 `lavabili-plugin` JAR，然后构建机器人。

构建输出：

```text
target\SonwMusicBot-0.6.3.jar
```

---

## 🔧 故障排除

### Apple Music 链接可以读取，但曲目无法播放

删除 `youtubetoken.txt`，然后重新进行 YouTube OAuth 授权。

### 双击 JAR 没有反应

安装 Java，并确认 `.jar` 文件已关联到 Java；也可以在命令提示符中使用 `java -jar` 启动。

### 其他播放问题

已知问题及额外修复：**N/A**

---

## 🌐 中文汉化

大多数重要的音乐播放、队列和 DJ 指令消息已汉化为简体中文。部分较少使用的消息仍可能显示英文。

---

## 📥 Releases

预编译下载和版本说明：**N/A**

---

## 📝 致谢

SonwMusicBot 是 [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot) 的修改衍生项目。额外播放支持由 LavaSrc、`lavabili-plugin` 等相关项目提供，其权利归各自作者所有。

---

## 📄 许可证

SonwMusicBot 按照原始 MusicBot 项目所使用的 **Apache License 2.0** 进行分发。

完整许可证见 [`LICENSE`](LICENSE)，原项目归属和本项目修改说明见 [`NOTICE`](NOTICE)。重新分发本项目时，必须保留上游项目以及第三方依赖中的版权和归属声明。

---

## 🔎 搜索关键词

Discord 音乐机器人、SonwMusicBot、JMusicBot、Apple Music Discord Bot、Bilibili Discord Bot、中文 Discord 音乐机器人、LavaSrc、lavabili-plugin、Discord 音频播放修复、JMusicBot 中文汉化。

---

## ⚠️ 免责声明

这是一个非官方自定义项目，与 Discord、Apple Music、Bilibili 或原 MusicBot 维护者没有隶属关系。

第三方平台、API、身份验证系统或插件更新后，播放功能可能发生变化。使用本机器人时，请遵守相关平台的服务条款和版权规定。
