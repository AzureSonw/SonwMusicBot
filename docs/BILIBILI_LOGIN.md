# Bilibili HTTP 412 与登录会话

`Bilibili request ... failed with HTTP 412` 表示 Bilibili 拒绝了当前请求。
它通常与风控有关；单凭这条日志无法确定是 IP、请求频率还是会话状态。
`YoutubeAccessTokenTracker: Updating YouTube visitor id succeeded` 是另一音源的正常日志。

本版本支持从环境变量读取 Bilibili 登录 Cookie。没有设置 `BILIBILI_SESSDATA`
时继续匿名播放。登录会话可能改善匿名请求受限的问题，但不保证解除 HTTP 412。

## 在运行机器上配置

1. 在自己的浏览器登录 Bilibili，并确认目标视频可以正常打开。
2. 打开开发者工具 → Application/应用 → Cookies，选择 Bilibili 网站。
3. 从同一个登录会话中取下面的 Cookie **值**，不要包含 Cookie 名、分号或整个 Cookie 请求头。

| Cookie 名 | 环境变量 | 用途 |
| --- | --- | --- |
| `SESSDATA` | `BILIBILI_SESSDATA` | 必需，登录会话 |
| `bili_jct` | `BILIBILI_BILI_JCT` | 可选，登录关联 Cookie |
| `DedeUserID` | `BILIBILI_DEDE_USER_ID` | 可选，账号标识 |
| `buvid3` | `BILIBILI_BUVID3` | 建议与登录会话一起提供 |
| `buvid4` | `BILIBILI_BUVID4` | 建议与登录会话一起提供 |

停止旧机器人，在 JAR 与原配置文件所在文件夹打开 PowerShell。下面的输入不会明文回显，
也不会把输入的值写进命令历史；可选项可直接回车。将最后一行的文件名改成实际的新 JAR：

```powershell
function Read-BiliCookie([string]$Name) {
    $secureCookie = Read-Host "$Name (optional fields may be left blank)" -AsSecureString
    try { [System.Net.NetworkCredential]::new('', $secureCookie).Password }
    finally { $secureCookie.Dispose() }
}
$env:BILIBILI_SESSDATA = Read-BiliCookie 'SESSDATA - required'
$env:BILIBILI_BILI_JCT = Read-BiliCookie 'bili_jct'
$env:BILIBILI_DEDE_USER_ID = Read-BiliCookie 'DedeUserID'
$env:BILIBILI_BUVID3 = Read-BiliCookie 'buvid3'
$env:BILIBILI_BUVID4 = Read-BiliCookie 'buvid4'
java --enable-native-access=ALL-UNNAMED -jar .\SonwMusicBot-0.6.5-bilibili-session.jar
```

必须在设置变量的同一个 PowerShell 窗口启动机器人；随后双击 JAR 不会继承这些进程环境变量。
停止机器人后关闭这个 PowerShell 窗口，即可清除本次设置的变量。
Linux、Docker 或服务部署需将同名变量传入 Java 进程。

启动日志中出现 `Bilibili session cookies configured from environment` 只表示已加载配置，
不代表已验证登录有效或播放成功。请再测试原先失败的 BV 链接。

## 会话与故障处理

- 登录 Cookie 只保存在进程内存中；机器人不把它们写入配置文件或日志。
- `SESSDATA`、`bili_jct`、`DedeUserID` 通过 HttpClient CookieStore 限制为 HTTPS
  `bilibili.com` 及其子域名；短链、外部 CDN 和明文 HTTP 不会收到这些登录凭据。
- 不启用上游将登录 Cookie 加到所有请求的全局认证头，也不启用自动刷新。
  Cookie 失效后，请在浏览器重新登录、更新环境变量并重启机器人。
- 如果仍然出现 412，暂停重复点播，先在同一运行机器的浏览器中检查访问状态；
  如网站要求登录或验证，按网站提示完成。不要持续重试受限请求。
- 请勿把 Cookie 发到聊天、Discord、GitHub 或错误报告中。

上游说明：[lavabili-plugin](https://github.com/ParrotXray/lavabili-plugin#configuration-options)。
