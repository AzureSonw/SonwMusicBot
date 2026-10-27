# Bilibili playback in 0.6.7

## 更新后先直接重试

停止旧版，将 `SonwMusicBot-0.6.7.jar` 放进原来的机器人目录，再启动新版。
保留原来的 `config.txt`、播放列表和 YouTube 授权文件，不需要删除或重新授权。
新增配置项会按现有配置更新流程补齐，原配置会保留 `.bak` 备份。

保留 0.6.6 的带签名视频详情接口读取 BV/AV 信息，不再依赖网页内嵌
`__INITIAL_STATE__` 或旧的无签名 `/x/web-interface/view` 接口。
保留分 P、收藏夹、合集和系列列表；列表中的每个视频使用同一条修复后的解析流程。
0.6.7 补齐音频请求的 Range、Referer 和 User-Agent；遇到 HTTP 567 或部分临时节点错误时，
只在开始读取音频前尝试同一音轨的官方备用地址，最多共 4 个地址，每个一次。
播放开始后不会从头切换节点，避免重复播放。搜索结果、分 P、列表、旧保存歌曲及克隆歌曲都使用此流程。
只使用接口返回的原始 HTTPS 签名地址，不改域名、不使用代理，也不把登录 Cookie 发送到音频 CDN。
官方 lavabili-plugin 1.4.1 的字节码保持不变；音频传输修复位于 SonwMusicBot 源码中。

匿名播放默认开启，不需要为了升级先填写 Cookie。视频已删除、需要账号权限、地区
限制或 B站持续限流的情况，不保证仅靠客户端更新就能解决；不会无限重试。

## 可选：只在本机配置登录信息

只有持续提示需要登录或请求受限时，才考虑此选项。先停止机器人，再编辑机器人目录
中的 `config.txt`。在现有 `playback.bilibili.auth` 中填写，不要重复建立第二份配置：

```hocon
playback.bilibili.auth {
  enabled = true
  sessdata = "在本机填写自己的 SESSDATA 值"
  biliJct = ""
  dedeUserId = ""
  buvid3 = ""
  buvid4 = ""
}
```

这些值可由你在已登录 B站的浏览器开发者工具中自行查看：Application/应用 →
Cookies → B站域名。对应关系为 `SESSDATA` → `sessdata`、`bili_jct` → `biliJct`、
`DedeUserID` → `dedeUserId`；`buvid3`、`buvid4` 同名。只填写单个值，保持原值，
不要粘贴整个 Cookie 请求头。`sessdata` 是启用登录的必填项，其余可选。
保存后重启机器人；禁用时改回 `enabled = false`。

Cookie 等同登录凭据。不要上传、截图或向聊天、Discord、GitHub 分享 Cookie、
`config.txt` 或其 `.bak` 备份。机器人不会自动读取浏览器，也不会自动更新账号 Cookie。
本次没有启用上游 Cookie 自动续期，以避免它输出更新后的敏感值；过期时由你在本机更新。
不要把启用详细 HTTP 调试后产生的日志直接公开。

## 错误含义

- HTTP 412 / API -412、-352：B站限制请求，稍后再试；必要时检查本机登录配置。
- HTTP 429：请求过于频繁，暂停请求。
- HTTP 401/403 / API -101、-403：登录或内容访问权限问题。
- HTTP 404 / API -404：视频可能已删除或不可用。
- HTTP 567：音频节点拒绝请求；若官方备用地址也不可用，会停止并明确提示，不能保证客户端更新解决所有节点限制。

如果浏览器能播放而机器人仍失败，记录视频链接、错误码和新版本号即可；不要附带 Cookie。

## English summary

0.6.7 adds initial Range headers and bounded, startup-only failover to API-provided
URLs of the same AAC representation. Access-denied/rate-limit responses stop;
there is no hostname rewriting, browser credential import or mid-track restart.
Media requests use a separate cookieless client, and responses are closed on stop,
seek and failure. The official plugin binary and old saved-track format are unchanged.
The WBI-signed detail metadata fix from 0.6.6 is retained. Anonymous
playback remains the default. Optional `playback.bilibili.auth` values must be entered
locally by the user; credentials are not imported from browsers or automatically
refreshed. Keep config files/backups private. Service-side restrictions may still apply.
